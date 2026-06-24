package com.beatblock.timeline.rendering;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 高分辨率特征补全与 beatmap 分析完成后的自动回填。 */
public final class TimelineDenseFeatureApplier {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineDenseFeatureApplier.class);
	private static final long DENSE_FAILURE_COOLDOWN_MS = 30_000L;
	private static final long PENDING_DENSE_PAYLOAD_TTL_MS = 120_000L;

	private final ExecutorService denseFeatureExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-dense-feature");
		t.setDaemon(true);
		return t;
	});
	private volatile boolean denseFeatureExecutorShutdown;
	private final ConcurrentMap<String, DenseApplyPayload> pendingDenseApplies = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Boolean> denseAnalysisInFlight = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Long> denseAnalysisFailureUntilMs = new ConcurrentHashMap<>();
	private String lastAutoAppliedBeatmapSignature;

	private record DenseApplyPayload(AudioAsset asset, AudioFeatureTimeline feature, long createdAtMs) {}

	public void resetAutoApplySignature() {
		lastAutoAppliedBeatmapSignature = null;
	}

	public void applyPendingUpdates(BeatBlockContext context, Timeline timeline) {
		if (pendingDenseApplies.isEmpty()) return;
		pruneStalePendingDenseApplies();
		if (timeline == null || pendingDenseApplies.isEmpty()) return;
		String timelineAudioKey = TimelineAudioFeatureFillSupport.getTimelineAudioPathKey(timeline);
		if (timelineAudioKey == null) return;
		DenseApplyPayload payload = pendingDenseApplies.remove(timelineAudioKey);
		if (payload != null) {
			applyDenseFeatureData(context, timeline, payload.asset(), payload.feature(), timelineAudioKey);
		}
	}

	public void tryAutoApplyAnalyzedBeatmap(TimelineAudioDropHost host, Timeline timeline) {
		if (host == null || timeline == null || host.context().audioAnalysisEngine() == null) return;
		String timelineAudioKey = TimelineAudioFeatureFillSupport.getTimelineAudioPathKey(timeline);
		if (timelineAudioKey == null) return;

		Object awaitingRaw = timeline.getMetadata("awaitingAnalyzedBeatmap");
		String awaitingKey = awaitingRaw != null
			? TimelineAudioFeatureFillSupport.normalizeAudioPath(awaitingRaw.toString()) : null;
		if (!Objects.equals(awaitingKey, timelineAudioKey)) return;

		AudioAsset matched = TimelineAudioFeatureFillSupport.findAssetByAudioKey(timelineAudioKey);
		if (matched == null || matched.getBeatmap() == null) return;

		String signature = TimelineAudioFeatureFillSupport.buildBeatmapApplySignature(timelineAudioKey, matched.getBeatmap());
		if (Objects.equals(signature, lastAutoAppliedBeatmapSignature)) return;

		double startOffset = TimelineAudioFeatureFillSupport.readClipOffset(timeline, timelineAudioKey);
		double prevDuration = timeline.getDurationSeconds();
		Map<String, TimelineAudioFeatureFillSupport.SavedFeatureTrack> savedFeatureEvents =
			TimelineAudioFeatureFillSupport.saveFeatureEvents(timeline);
		host.context().audioAnalysisEngine().fillTimelineFromBeatmap(timeline, matched.getBeatmap());
		TimelineAudioFeatureFillSupport.shiftFeatureEventsByOffset(timeline, startOffset);
		TimelineAudioFeatureFillSupport.restoreFeatureEvents(timeline, savedFeatureEvents);
		timeline.setDurationSeconds(prevDuration);
		requestEnrichment(host.context(), timeline, matched);
		host.bindStemAudioIfDemucs(matched.getBeatmap());
		timeline.setMetadata("awaitingAnalyzedBeatmap", null);
		lastAutoAppliedBeatmapSignature = signature;
		host.syncClockDuration();
		LOGGER.info("BeatBlock Timeline: auto-applied analyzed beatmap path={}", matched.getPath());
	}

	public void requestEnrichment(BeatBlockContext context, Timeline timeline, AudioAsset asset) {
		if (timeline == null || asset == null || context == null || context.audioAnalysisEngine() == null) return;
		if (denseFeatureExecutorShutdown) return;

		String audioKey = TimelineAudioFeatureFillSupport.buildAudioAssetKey(asset);
		if (audioKey == null) return;
		long now = System.currentTimeMillis();
		Long failureUntil = denseAnalysisFailureUntilMs.get(audioKey);
		if (failureUntil != null && failureUntil > now) return;

		AudioFeatureTimeline cachedFeature = asset.getFeatureTimeline();
		if (cachedFeature != null) {
			denseAnalysisFailureUntilMs.remove(audioKey);
			applyDenseFeatureData(context, timeline, asset, cachedFeature, audioKey);
			return;
		}

		Path audioPath = asset.getPath();
		if (audioPath == null) return;
		if (denseAnalysisInFlight.putIfAbsent(audioKey, Boolean.TRUE) != null) return;

		denseFeatureExecutor.submit(() -> {
			try {
				AudioFeatureTimeline analyzed = context.audioAnalysisEngine().analyze(audioPath);
				if (analyzed == null) {
					denseAnalysisFailureUntilMs.put(audioKey, System.currentTimeMillis() + DENSE_FAILURE_COOLDOWN_MS);
					LOGGER.warn(
						"BeatBlock Timeline: dense feature enrichment returned null path={} (cooldown={}ms)",
						audioPath, DENSE_FAILURE_COOLDOWN_MS);
					return;
				}
				asset.setFeatureTimeline(analyzed);
				denseAnalysisFailureUntilMs.remove(audioKey);
				pendingDenseApplies.put(audioKey, new DenseApplyPayload(asset, analyzed, System.currentTimeMillis()));
			} catch (Exception e) {
				denseAnalysisFailureUntilMs.put(audioKey, System.currentTimeMillis() + DENSE_FAILURE_COOLDOWN_MS);
				LOGGER.warn("BeatBlock Timeline: dense feature enrichment failed path={} reason={}", audioPath, e.toString());
			} finally {
				denseAnalysisInFlight.remove(audioKey);
			}
		});
	}

	public void shutdown() {
		if (denseFeatureExecutorShutdown) {
			LOGGER.debug("BeatBlock Timeline: dense feature executor already shut down");
			return;
		}
		int pendingCount = pendingDenseApplies.size();
		int inflightCount = denseAnalysisInFlight.size();
		int cooldownCount = denseAnalysisFailureUntilMs.size();
		denseFeatureExecutorShutdown = true;
		pendingDenseApplies.clear();
		denseAnalysisInFlight.clear();
		denseAnalysisFailureUntilMs.clear();
		denseFeatureExecutor.shutdownNow();
		LOGGER.info(
			"BeatBlock Timeline: dense feature executor shutdown (pending={}, inflight={}, cooldown={})",
			pendingCount,
			inflightCount,
			cooldownCount
		);
	}

	private void pruneStalePendingDenseApplies() {
		long now = System.currentTimeMillis();
		pendingDenseApplies.entrySet().removeIf(e -> now - e.getValue().createdAtMs() > PENDING_DENSE_PAYLOAD_TTL_MS);
	}

	private void applyDenseFeatureData(
		BeatBlockContext context,
		Timeline timeline,
		AudioAsset asset,
		AudioFeatureTimeline feature,
		String expectedAudioKey
	) {
		if (timeline == null || asset == null || feature == null || context.audioAnalysisEngine() == null) return;
		String timelineAudioKey = TimelineAudioFeatureFillSupport.getTimelineAudioPathKey(timeline);
		if (!Objects.equals(timelineAudioKey, expectedAudioKey)) return;

		double startOffset = TimelineAudioFeatureFillSupport.readClipOffset(timeline, expectedAudioKey);
		double prevDuration = timeline.getDurationSeconds();
		context.audioAnalysisEngine().fillTimelineFromFeature(timeline, feature, asset.getSampleRate());
		TimelineAudioFeatureFillSupport.shiftFeatureEventsByOffset(timeline, startOffset);
		timeline.setDurationSeconds(prevDuration);
		if (asset.getBeatmap() != null && asset.getBeatmap().meta != null) {
			timeline.setMetadata("bpm", asset.getBeatmap().meta.bpm());
			timeline.setMetadata("beatCount", asset.getBeatmap().beats.size());
		}
		if (context.timelineEditor() != null) {
			context.timelineEditor().syncClockDuration();
		}
	}
}
