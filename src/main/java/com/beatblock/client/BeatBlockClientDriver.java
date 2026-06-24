package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.client.vfx.VfxEmitter;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.WorldMutationSink;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 第 3 层 — 客户端播放编排：按 Timeline 时钟推进音频预览与舞台/相机回放。
 * <p>
 * 只派发 {@link TimelineAnimationEvent} 给 {@link com.beatblock.engine.BlockAnimationEngine}；
 * 相机由 {@link com.beatblock.client.camera.TimelineCameraController} 独立处理。
 */
public final class BeatBlockClientDriver {

	public record TimelineActionExecutionReport(
		long timestampMs,
		String eventId,
		String targetObjectId,
		TimelineAnimationActionMode actionMode,
		int mutationCount,
		String status,
		String detail
	) {}

	private static BeatBlockClientDriver instance;

	private final Supplier<BeatBlockContext> contextSource;

	private volatile long lastTickNanos;
	private volatile boolean driving;
	private final Set<String> scheduledTimelineAnimationIds = new HashSet<>();
	private final Set<String> scheduledAutoAnimationIds = new HashSet<>();
	private final Set<String> scheduledBuildReverseIds = new HashSet<>();
	private static final double TIMELINE_EVENT_EPSILON = 1e-4;
	private volatile double lastTimelineAnimationTime;
	private volatile double lastAutoAnimationTime;
	private volatile double lastBuildReverseTime;
	private final Map<BlockPos, BlockState> timelineMutationSnapshot = new HashMap<>();
	private RegistryKey<World> timelineMutationWorldKey;
	private volatile TimelineActionExecutionReport lastTimelineActionExecutionReport;
	private final Map<String, TimelineActionExecutionReport> timelineActionReportByEventId = new ConcurrentHashMap<>();
	private static final int MAX_ACTION_REPORT_CACHE_SIZE = 4096;

	public BeatBlockClientDriver(Supplier<BeatBlockContext> contextSource) {
		this.contextSource = contextSource != null ? contextSource : BeatBlock::getContext;
	}

	public static void install(Supplier<BeatBlockContext> contextSource) {
		instance = new BeatBlockClientDriver(contextSource);
	}

	static void resetForTests() {
		instance = null;
	}

	private static BeatBlockClientDriver requireInstance() {
		if (instance == null) {
			install(BeatBlock::getContext);
		}
		return instance;
	}

	private BeatBlockContext ctx() {
		return contextSource.get();
	}

	public static void onClientTick() {
		requireInstance().tick();
	}

	void tick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		var engine = ctx().blockAnimationEngine();
		if (engine != null && mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
			engine.setRuntimeCameraPosition(mc.gameRenderer.getCamera().getCameraPos());
		}
		com.beatblock.client.camera.TimelineCameraController.getInstance().tick();

		if (driving) {
			if (world == null) return;

			long now = System.nanoTime();
			double delta = lastTickNanos > 0 ? (now - lastTickNanos) / 1e9 : 1.0 / 20.0;
			lastTickNanos = now;

			var musicPlayer = ctx().musicPlayer();
			if (musicPlayer != null) {
				musicPlayer.tick(delta);
			}
			syncStemMixerToMusicPlayer();
			double currentTime = musicPlayer != null ? musicPlayer.getCurrentTimeSeconds() : 0.0;
			tickBlockAnimationEngine(currentTime, false, world);
			return;
		}

		if (world != null && engine != null && ctx().timeline() != null) {
			tickBlockAnimationEngine(previewTimelineTimeSeconds(), true, world);
		}
	}

	private void tickBlockAnimationEngine(double currentTime, boolean previewOnly, World world) {
		var engine = ctx().blockAnimationEngine();
		if (engine == null) return;
		syncTimelineBlockAnimationEvents(currentTime, previewOnly);
		syncTimelineAutoAnimationEvents(currentTime, previewOnly);
		syncTimelineBuildReverseEvents(currentTime, previewOnly);
		WorldMutationSink sink = previewOnly
			? WorldMutationSink.NO_OP
			: BeatBlockAuthoritativeWorldMutator.sinkFor(engine.getBlockControlExecutor(), world);
		engine.tick(currentTime, previewOnly ? null : world, sink);
		if (!previewOnly && world != null) {
			VfxEmitter.emit(MinecraftClient.getInstance(), engine.getLastInfluenceFrame());
		}
	}

	private double[] readReferenceBeatTimes() {
		var timeline = ctx().timeline();
		if (timeline == null) {
			return new double[0];
		}
		return ReferenceBeatResolver.resolveBeatTimesSeconds(timeline);
	}

	private void syncStemMixerToMusicPlayer() {
		var stemMixer = ctx().stemMixer();
		var musicPlayer = ctx().musicPlayer();
		if (stemMixer == null || !stemMixer.hasStems() || musicPlayer == null) return;
		boolean musicPlaying = musicPlayer.isPlaying();
		double musicTime = musicPlayer.getCurrentTimeSeconds();
		double stemTime = stemMixer.getCurrentTimeSeconds();

		if (Math.abs(stemTime - musicTime) > 0.05) {
			stemMixer.setCurrentTimeSeconds(musicTime);
		}

		if (musicPlaying) {
			if (!stemMixer.isPlaying()) {
				stemMixer.setCurrentTimeSeconds(musicTime);
				stemMixer.play();
			}
		} else if (stemMixer.isPlaying()) {
			stemMixer.pause();
		}
	}

	public static void startDriving() {
		requireInstance().startDrivingInternal();
	}

	private void startDrivingInternal() {
		lastTickNanos = 0;
		resetTimelineAnimationScheduling();
		driving = true;
	}

	public static void stopDriving() {
		requireInstance().stopDrivingInternal();
	}

	private void stopDrivingInternal() {
		driving = false;
		resetTimelineAnimationScheduling();
	}

	public static boolean isDriving() {
		return requireInstance().driving;
	}

	public static void stopPlayback() {
		requireInstance().stopPlaybackInternal();
	}

	private void stopPlaybackInternal() {
		var musicPlayer = ctx().musicPlayer();
		if (musicPlayer != null) {
			musicPlayer.pause();
		}
		var stemMixer = ctx().stemMixer();
		if (stemMixer != null && stemMixer.hasStems()) {
			stemMixer.pause();
		}
		resetTimelineAnimationScheduling();
		stopDrivingInternal();
		com.beatblock.client.camera.TimelineCameraController.getInstance().onTimelineUiClosed();
	}

	public static double previewTimelineTimeSeconds() {
		return requireInstance().previewTimelineTimeSecondsInternal();
	}

	private double previewTimelineTimeSecondsInternal() {
		var editor = ctx().timelineEditor();
		if (editor != null) {
			return editor.getClock().getCurrentTimeSeconds();
		}
		var musicPlayer = ctx().musicPlayer();
		return musicPlayer != null ? musicPlayer.getCurrentTimeSeconds() : 0.0;
	}

	private void syncTimelineBlockAnimationEvents(double currentTime, boolean previewOnly) {
		var timeline = ctx().timeline();
		var engine = ctx().blockAnimationEngine();
		if (timeline == null || engine == null) return;
		if (currentTime + TIMELINE_EVENT_EPSILON < lastTimelineAnimationTime) {
			resetTimelineAnimationScheduling();
		}
		for (TimelineAnimationEvent event : timeline.getBlockAnimationEvents()) {
			if (event.getTimeSeconds() > currentTime + TIMELINE_EVENT_EPSILON) {
				break;
			}
			String scheduleKey = scheduleKey(event);
			if (!scheduledTimelineAnimationIds.add(scheduleKey)) continue;
			applyTimelineActionEvent(event, previewOnly);
		}
		lastTimelineAnimationTime = currentTime;
	}

	private void syncTimelineAutoAnimationEvents(double currentTime, boolean previewOnly) {
		var timeline = ctx().timeline();
		var engine = ctx().blockAnimationEngine();
		if (timeline == null || engine == null) return;
		if (currentTime + TIMELINE_EVENT_EPSILON < lastAutoAnimationTime) {
			scheduledAutoAnimationIds.clear();
			lastAutoAnimationTime = 0.0;
		}
		for (TimelineAnimationEvent event : timeline.getAutoAnimationEvents()) {
			if (event.getTimeSeconds() > currentTime + TIMELINE_EVENT_EPSILON) {
				break;
			}
			String key = scheduleKey(event);
			if (!scheduledAutoAnimationIds.add(key)) continue;
			applyTimelineActionEvent(event, previewOnly);
		}
		lastAutoAnimationTime = currentTime;
	}

	private void syncTimelineBuildReverseEvents(double currentTime, boolean previewOnly) {
		var timeline = ctx().timeline();
		var engine = ctx().blockAnimationEngine();
		if (timeline == null || engine == null) return;
		if (currentTime + TIMELINE_EVENT_EPSILON < lastBuildReverseTime) {
			scheduledBuildReverseIds.clear();
			lastBuildReverseTime = 0.0;
		}
		for (TimelineAnimationEvent event : timeline.getBuildReverseEvents()) {
			if (event.getTimeSeconds() > currentTime + TIMELINE_EVENT_EPSILON) {
				break;
			}
			String key = scheduleKey(event);
			if (!scheduledBuildReverseIds.add(key)) continue;
			applyTimelineActionEvent(event, previewOnly);
		}
		lastBuildReverseTime = currentTime;
	}

	private void applyTimelineActionEvent(TimelineAnimationEvent event, boolean previewOnly) {
		var engine = ctx().blockAnimationEngine();
		if (event == null || engine == null) return;
		if (!passesEnergyThreshold(event)) {
			recordActionReport(event, 0, "SKIPPED", "energy-below-threshold");
			return;
		}
		TimelineAnimationActionMode actionMode = event.getActionMode();
		if (previewOnly && actionMode != TimelineAnimationActionMode.ANIMATE) {
			return;
		}
		if (actionMode == TimelineAnimationActionMode.ANIMATE) {
			double[] beats = readReferenceBeatTimes();
			var timeline = ctx().timeline();
			double bpm = timeline != null ? timeline.getBpm() : 120.0;
			engine.scheduleTimelineEvent(event, beats, bpm > 0 ? bpm : 120.0);
			recordActionReport(event, 0, "ANIMATE", "scheduled");
			return;
		}

		if (actionMode == TimelineAnimationActionMode.BUILD) {
			var inst = engine.getBuildSequencer().schedule(event);
			if (inst != null) {
				recordActionReport(event, inst.getTotalBlocks(), "BUILD", "scheduled-" + inst.getTotalBlocks() + "-blocks");
			} else {
				recordActionReport(event, 0, "SKIPPED", "build-no-target");
			}
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world == null) {
			recordActionReport(event, 0, "SKIPPED", "no-world");
			return;
		}
		var plan = engine.planControl(event, world);
		var mutations = plan.mutations();
		if (mutations == null || mutations.isEmpty()) {
			String detail = plan.skipReason() != null
				? "skip-" + plan.skipReason().name().toLowerCase(Locale.ROOT)
				: "skip-no-change";
			recordActionReport(event, 0, "SKIPPED", detail);
			return;
		}
		for (BlockControlExecutor.BlockMutation mutation : mutations) {
			captureTimelineMutationOriginalState(world, mutation.pos(), mutation.fromState());
		}
		WorldMutationSink sink = BeatBlockAuthoritativeWorldMutator.sinkFor(
			engine.getBlockControlExecutor(), world);
		engine.applyControlMutations(mutations, sink);
		recordActionReport(event, mutations.size(), "APPLIED", "ok");
	}

	private void recordActionReport(TimelineAnimationEvent event, int mutationCount, String status, String detail) {
		if (event == null) return;
		TimelineActionExecutionReport report = new TimelineActionExecutionReport(
			System.currentTimeMillis(),
			event.getEventId(),
			event.getTargetObjectId(),
			event.getActionMode(),
			Math.max(0, mutationCount),
			status != null ? status : "UNKNOWN",
			detail != null ? detail : ""
		);
		lastTimelineActionExecutionReport = report;
		String eventId = event.getEventId();
		if (eventId != null && !eventId.isBlank()) {
			if (timelineActionReportByEventId.size() > MAX_ACTION_REPORT_CACHE_SIZE) {
				timelineActionReportByEventId.clear();
			}
			timelineActionReportByEventId.put(eventId, report);
		}
	}

	private boolean passesEnergyThreshold(TimelineAnimationEvent event) {
		if (event == null) return false;
		Object raw = event.getParameters().get("energyThreshold");
		double threshold = 0.0;
		if (raw instanceof Number n) {
			threshold = n.doubleValue();
		} else if (raw != null) {
			try {
				threshold = Double.parseDouble(String.valueOf(raw).trim());
			} catch (Exception ignored) {
				threshold = 0.0;
			}
		}
		threshold = Math.max(0.0, Math.min(1.0, threshold));
		return event.getEnergy() + 1e-6 >= threshold;
	}

	private void captureTimelineMutationOriginalState(World world, BlockPos pos, BlockState currentState) {
		if (!shouldRestoreTimelineMutations()) return;
		if (world == null || pos == null || currentState == null) return;
		RegistryKey<World> worldKey = world.getRegistryKey();
		if (timelineMutationWorldKey == null) {
			timelineMutationWorldKey = worldKey;
		} else if (!timelineMutationWorldKey.equals(worldKey)) {
			restoreTimelineMutationSnapshot();
			timelineMutationWorldKey = worldKey;
		}
		timelineMutationSnapshot.putIfAbsent(pos.toImmutable(), currentState);
	}

	private void restoreTimelineMutationSnapshot() {
		if (timelineMutationSnapshot.isEmpty()) {
			timelineMutationWorldKey = null;
			return;
		}
		if (!shouldRestoreTimelineMutations()) {
			timelineMutationSnapshot.clear();
			timelineMutationWorldKey = null;
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world != null && timelineMutationWorldKey != null && timelineMutationWorldKey.equals(world.getRegistryKey())) {
			BeatBlockAuthoritativeWorldMutator.restoreAuthoritative(world, Map.copyOf(timelineMutationSnapshot));
		}
		timelineMutationSnapshot.clear();
		timelineMutationWorldKey = null;
	}

	private boolean shouldRestoreTimelineMutations() {
		var timeline = ctx().timeline();
		if (timeline == null) return true;
		Object raw = timeline.getMetadata("timelineActionRollbackMode");
		if (raw == null) return true;
		String mode = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
		return !"persistent".equals(mode) && !"performance".equals(mode);
	}

	private void resetTimelineAnimationScheduling() {
		restoreTimelineMutationSnapshot();
		scheduledTimelineAnimationIds.clear();
		scheduledAutoAnimationIds.clear();
		scheduledBuildReverseIds.clear();
		lastTimelineAnimationTime = 0.0;
		lastAutoAnimationTime = 0.0;
		lastBuildReverseTime = 0.0;
		var engine = ctx().blockAnimationEngine();
		if (engine != null) {
			engine.clear();
		}
	}

	private static String scheduleKey(TimelineAnimationEvent event) {
		if (event.getEventId() != null && !event.getEventId().isBlank()) {
			return event.getEventId();
		}
		return String.format(Locale.ROOT, "%s|%.6f|%s|%s",
			event.getActionMode().name(),
			event.getTimeSeconds(),
			event.getAnimationTypeId(),
			event.getTargetObjectId());
	}

	public static TimelineActionExecutionReport getLastTimelineActionExecutionReport() {
		return requireInstance().lastTimelineActionExecutionReport;
	}

	public static TimelineActionExecutionReport getTimelineActionExecutionReport(String eventId) {
		if (eventId == null || eventId.isBlank()) return null;
		return requireInstance().timelineActionReportByEventId.get(eventId);
	}

	public static void togglePlayback() {
		requireInstance().togglePlaybackInternal();
	}

	private void togglePlaybackInternal() {
		var musicPlayer = ctx().musicPlayer();
		if (musicPlayer == null) return;
		if (musicPlayer.isPlaying()) {
			stopPlaybackInternal();
		} else {
			musicPlayer.play();
			startDrivingInternal();
		}
	}
}
