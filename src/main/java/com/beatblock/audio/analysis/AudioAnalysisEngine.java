package com.beatblock.audio.analysis;

import com.beatblock.timeline.FrequencyBand;
import com.beatblock.timeline.FrequencyEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.WaveformData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
	 * 从文件路径加载并完整分析，得到 AudioFeatureTimeline；失败返回 null。
	 */
	public AudioFeatureTimeline analyze(Path path) {
		if (path == null) return null;
		AudioBuffer buffer = AudioDecoder.load(path);
		return analyzeBuffer(buffer);
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

		// 频段事件：从 bands 生成 FrequencyEvent（超过阈值即加）
		float threshold = 0.02f;
		List<FrequencyEvent> events = new ArrayList<>();
		for (FrequencyBands b : ft.getBands()) {
			float low = b.getLow(), mid = b.getMid(), high = b.getHigh();
			float sum = low + mid + high;
			if (sum < 1e-10f) continue;
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

	public AudioFeatureTimeline getLastFeatureTimeline() {
		return lastFeatureTimeline;
	}

	public AudioBuffer getLastBuffer() {
		return lastBuffer;
	}
}
