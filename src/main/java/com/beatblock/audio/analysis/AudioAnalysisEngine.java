package com.beatblock.audio.analysis;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.MusicSection;
import com.beatblock.timeline.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Audio Analysis Engine 门面：加载 → 解码 → 波形/FFT/能量/节拍/BPM → 生成 FeatureTimeline，并可写入 Timeline。
 * 决定「什么时候动」，与 Block Animation Engine 配合实现踩点可视化。
 */
public final class AudioAnalysisEngine {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioAnalysisEngine.class);

	private final FFTAnalyzer fftAnalyzer = new FFTAnalyzer();
	private final EnergyAnalyzer energyAnalyzer = new EnergyAnalyzer();
	private final BeatDetector beatDetector = new BeatDetector();

	private AudioBuffer lastBuffer;
	private AudioFeatureTimeline lastFeatureTimeline;

	/**
	 * 会话级缓存：key = "绝对路径:mtime(ms)"，避免同一文件在一个 JVM 生命周期内重复解码分析。
	 */
	private final ConcurrentMap<String, AudioFeatureTimeline> featureCache = new ConcurrentHashMap<>();

	/**
	 * 从文件路径加载并完整分析，得到 AudioFeatureTimeline；失败返回 null。
	 * 命中会话缓存时直接返回缓存结果，无需重新解码。
	 */
	public AudioFeatureTimeline analyze(Path path) {
		if (path == null) return null;
		String cacheKey = buildFeatureCacheKey(path);
		if (cacheKey != null) {
			AudioFeatureTimeline cached = featureCache.get(cacheKey);
			if (cached != null) {
				LOGGER.debug("BeatBlock AudioAnalysis: session cache hit path={}", path);
				lastFeatureTimeline = cached;
				return cached;
			}
		}
		AudioBuffer buffer = AudioDecoder.load(path);
		AudioFeatureTimeline result = analyzeBuffer(buffer);
		if (result != null && cacheKey != null) {
			featureCache.put(cacheKey, result);
		}
		return result;
	}

	/**
	 * 构建缓存 key（绝对路径 + ":" + mtime毫秒）。文件不存在或IO异常时返回 null。
	 */
	private String buildFeatureCacheKey(Path path) {
		try {
			Path abs = path.toAbsolutePath().normalize();
			long mtime = Files.getLastModifiedTime(abs).toMillis();
			return abs.toString().toLowerCase() + ":" + mtime;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * 从已有 AudioBuffer 做完整分析（波形、FFT 频段、能量、节拍、BPM、BeatGrid）。
	 */
	public AudioFeatureTimeline analyzeBuffer(AudioBuffer buffer) {
		if (buffer == null || buffer.getLength() < FFTAnalyzer.FFT_SIZE) {
			lastBuffer = null;
			lastFeatureTimeline = null;
			return null;
		}
		lastBuffer = buffer;
		double duration = buffer.getDurationSeconds();

		WaveformExtractor.WaveformFrame[] waveformFrames = WaveformExtractor.extract(buffer);
		FFTAnalyzer.FFTAnalysisResult fftResult = fftAnalyzer.analyze(buffer);
		List<FrequencyBands> bands = fftResult.getBands();
		List<EnergyFrame> energyFrames = energyAnalyzer.analyze(buffer);
		List<DetectedBeat> beats = beatDetector.detect(buffer);
		float bpm = BPMDetector.estimateBPM(beats);
		BeatGrid beatGrid = new BeatGrid(bpm, duration);

		lastFeatureTimeline = new AudioFeatureTimeline(
			duration, beats, energyFrames, bands, waveformFrames, bpm, beatGrid
		);
		LOGGER.info("BeatBlock AudioAnalysis: duration={}s, beats={}, bpm={}, bands={}",
			duration, beats.size(), bpm, bands.size());
		return lastFeatureTimeline;
	}

	private void fillTimelineInternal(Timeline timeline, AudioFeatureTimeline ft, int sampleRate) {
		if (timeline == null || ft == null) return;
		timeline.setDurationSeconds(ft.getDurationSeconds());

		// 波形：转为 Timeline 的 WaveformData（peaks 归一化）
		WaveformExtractor.WaveformFrame[] wf = ft.getWaveformFrames();
		if (wf.length > 0) {
			float[] peaks = new float[wf.length];
			float maxPeak = 0;
			for (int i = 0; i < wf.length; i++) {
				peaks[i] = wf[i].peak;
				if (peaks[i] > maxPeak) maxPeak = peaks[i];
			}
			if (maxPeak > 1e-6f) {
				for (int i = 0; i < peaks.length; i++) peaks[i] /= maxPeak;
			}
			int sr = sampleRate > 0 ? sampleRate : (lastBuffer != null ? lastBuffer.getSampleRate() : 44100);
			timeline.setWaveform(new WaveformData(peaks, ft.getDurationSeconds(), sr));
		}

		// 频段事件：只对能量显著的帧生成 FrequencyEvent（低于全局峰值能量 5% 的帧跳过）
		List<FrequencyBands> bandFrames = ft.getBands();
		float threshold = 0.02f;
		// 第一遍：计算全局峰值总能量，用于绝对阈值过滤
		float maxAbsSum = 0f;
		for (FrequencyBands b : bandFrames) {
			float s = b.getLow() + b.getMid() + b.getHigh();
			if (s > maxAbsSum) maxAbsSum = s;
		}
		float absFloor = maxAbsSum * 0.05f; // 低于峰值能量 5% 的帧视为安静段，不生成事件
		List<FrequencyEvent> events = new ArrayList<>();
		for (FrequencyBands b : bandFrames) {
			float low = b.getLow(), mid = b.getMid(), high = b.getHigh();
			float sum = low + mid + high;
			if (sum < 1e-10f || sum < absFloor) continue;
			low /= sum; mid /= sum; high /= sum;
			if (low >= threshold) events.add(new FrequencyEvent(b.getTimeSeconds(), FrequencyBand.LOW, Math.min(1f, low * 2f)));
			if (mid >= threshold) events.add(new FrequencyEvent(b.getTimeSeconds(), FrequencyBand.MID, Math.min(1f, mid * 2f)));
			if (high >= threshold) events.add(new FrequencyEvent(b.getTimeSeconds(), FrequencyBand.HIGH, Math.min(1f, high * 2f)));
		}
		timeline.clearFrequencyEvents();
		for (FrequencyEvent e : events) timeline.addFrequencyEvent(e);
		timeline.sortAll();

		timeline.setMetadata("bpm", ft.getBpm());
		timeline.setMetadata("beatCount", ft.getBeats().size());
	}

	/**
	 * 将上次分析得到的 FeatureTimeline 写入 Timeline：波形、频段事件、metadata（bpm/beats）。
	 */
	public void fillTimeline(Timeline timeline) {
		if (lastFeatureTimeline == null) return;
		fillTimelineInternal(timeline, lastFeatureTimeline, lastBuffer != null ? lastBuffer.getSampleRate() : 44100);
	}

	/**
	 * 使用指定的分析结果写入 Timeline，仅影响音频相关数据。
	 */
	public void fillTimelineFromFeature(Timeline timeline, AudioFeatureTimeline feature, int sampleRate) {
		fillTimelineInternal(timeline, feature, sampleRate);
	}

	/**
	 * 使用 .beatmap JSON（Python 预处理产物）直接填充时间线。
	 * 运行时无需重新读取/分析音频文件。
	 *
	 * <p>新路径：BeatEvent.bandKey 写入 Timeline.featureTracks（开放键）。
	 * 遗留三频段列表同步保留，兼容尚未迁移的消费方。</p>
	 */
	public void fillTimelineFromBeatmap(Timeline timeline, Beatmap beatmap) {
		if (timeline == null || beatmap == null) return;

		timeline.setDurationSeconds(Math.max(0.0, beatmap.meta.durationMs() / 1000.0));

		if (beatmap.waveformPreview != null && beatmap.waveformPreview.data() != null) {
			float[] peaks = beatmap.waveformPreview.data().clone();
			float max = 0f;
			for (float peak : peaks) {
				if (peak > max) max = peak;
			}
			if (max > 1e-6f && max != 1f) {
				for (int i = 0; i < peaks.length; i++) peaks[i] /= max;
			}
			timeline.setWaveform(new WaveformData(
				peaks,
				timeline.getDurationSeconds(),
				beatmap.meta.sampleRate()
			));
		}

		timeline.clearFrequencyEvents();
		timeline.clearFeatureTracks();
		for (BeatEvent e : beatmap.beats) {
			double timeSec = e.timeMs() / 1000.0;
			float  energy  = Math.max(0f, Math.min(1f, e.energy()));
			String key     = e.bandKey();

			// 写入开放特征轨道（新路径，renderers 按 TrackRegistry 动态渲染）
			timeline.addFeatureEvent(key, localizedFeatureLabel(key), new FeatureEvent(timeSec, energy));
		}

		List<TimelineMarker> preserved = timeline.getMarkers().stream()
			.filter(m -> m.getType() != MarkerType.SECTION)
			.toList();
		timeline.setMarkers(preserved);
		for (MusicSection section : beatmap.sections) {
			double sectionStartSec = Math.max(0.0, section.startMs() / 1000.0);
			String name = "SECTION " + section.label().name();
			timeline.addMarker(new TimelineMarker(sectionStartSec, name, MarkerType.SECTION));
		}

		timeline.setMetadata("bpm", beatmap.meta.bpm());
		timeline.setMetadata("beatCount", beatmap.beats.size());
		timeline.setMetadata("sectionCount", beatmap.sections.size());
		timeline.setMetadata("sourceFile", beatmap.meta.sourceFile());
		if (beatmap.meta.separationMode() != null) {
			timeline.setMetadata("separationMode", beatmap.meta.separationMode());
		}

		// 茎波形（Demucs 模式）
		if (!beatmap.stemWaveforms.isEmpty()) {
			for (var entry : beatmap.stemWaveforms.entrySet()) {
				com.beatblock.audio.beatmap.WaveformPreview wp = entry.getValue();
				if (wp != null && wp.data() != null) {
					float[] peaks = wp.data().clone();
					float max = 0f;
					for (float p : peaks) if (p > max) max = p;
					if (max > 1e-6f && max != 1f) {
						for (int i = 0; i < peaks.length; i++) peaks[i] /= max;
					}
					timeline.setStemWaveform(entry.getKey(), new WaveformData(
						peaks, timeline.getDurationSeconds(), beatmap.meta.sampleRate()
					));
				}
			}
		}

		timeline.sortAll();
	}

	/** 已知 key 的中文显示名称（未知 key 直接返回 key）。 */
	private static String localizedFeatureLabel(String key) {
		return switch (key.toLowerCase()) {
			case "kick"       -> "底鼓";
			case "snare"      -> "军鼓";
			case "snare_hi"   -> "高军鼓";
			case "hihat", "hat" -> "踩镲";
			case "hihat_open" -> "开镲";
			case "bass"       -> "贝斯";
			case "vocals"     -> "人声";
			case "other"      -> "其他";
			case "drums"      -> "鼓组";
			case "low"        -> "低频";
			case "mid"        -> "中频";
			case "high"       -> "高频";
			default           -> key;
		};
	}

	public AudioFeatureTimeline getLastFeatureTimeline() {
		return lastFeatureTimeline;
	}

	public AudioBuffer getLastBuffer() {
		return lastBuffer;
	}
}
