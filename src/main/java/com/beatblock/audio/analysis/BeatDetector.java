package com.beatblock.audio.analysis;

import com.beatblock.audio.RealFFT;

import java.util.ArrayList;
import java.util.List;

/**
 * 节拍检测：Spectral Flux — 相邻 FFT 帧的频谱变化，超过局部阈值记为 beat。
 * 流程：计算 flux = sum(max(0, spectrum[i] - prevSpectrum[i]))；局部平均为 threshold；flux > threshold 则 beat。
 *
 * 改进版本：
 * 1. 可配置的最小节拍间隔（支持高 BPM 音乐）
 * 2. 频段加权（低频鼓点权重更高）
 * 3. 返回置信度
 */
public final class BeatDetector {

	public static final int FFT_SIZE = 1024;
	public static final int FFT_STEP = 512;
	/** 局部平均窗口（帧数） */
	public static final int FLUX_WINDOW = 12;
	/** 阈值乘数，越大检测越少 */
	public static final float THRESHOLD_MULTIPLIER = 1.2f;
	/** 默认最小节拍间隔（秒），避免连击 */
	public static final double DEFAULT_MIN_BEAT_INTERVAL = 0.05; // 50ms，支持 240 BPM

	// 频段边界（Hz，用于加权）
	private static final double LOW_FREQ_CUTOFF = 200.0;    // 低频上限（鼓点）
	private static final double MID_FREQ_CUTOFF = 2000.0;   // 中频上限
	// 频段权重
	private static final float LOW_FREQ_WEIGHT = 1.5f;      // 低频权重（鼓点更重要）
	private static final float MID_FREQ_WEIGHT = 1.0f;      // 中频权重
	private static final float HIGH_FREQ_WEIGHT = 0.5f;     // 高频权重（钹类不太重要）

	private final RealFFT fft = new RealFFT(FFT_SIZE);
	private final float[] power = new float[FFT_SIZE / 2 + 1];
	private double minBeatInterval = DEFAULT_MIN_BEAT_INTERVAL;

	/**
	 * 创建默认节拍检测器（最小间隔 50ms）。
	 */
	public BeatDetector() {
		this(DEFAULT_MIN_BEAT_INTERVAL);
	}

	/**
	 * 创建可配置最小间隔的节拍检测器。
	 *
	 * @param minBeatInterval 最小节拍间隔（秒），建议 0.05-0.1
	 */
	public BeatDetector(double minBeatInterval) {
		this.minBeatInterval = Math.max(0.03, Math.min(0.2, minBeatInterval));
	}

	/**
	 * 设置最小节拍间隔。
	 *
	 * @param seconds 最小间隔（秒），范围 0.03-0.2
	 */
	public void setMinBeatInterval(double seconds) {
		this.minBeatInterval = Math.max(0.03, Math.min(0.2, seconds));
	}

	/**
	 * 根据预期 BPM 自动设置最小节拍间隔。
	 *
	 * @param bpm 预期 BPM（例如 120）
	 */
	public void setMinBeatIntervalFromBPM(double bpm) {
		if (bpm > 0) {
			// 使用半拍间隔作为最小值（允许检测双倍速）
			double beatInterval = 60.0 / bpm;
			this.minBeatInterval = Math.max(0.03, beatInterval * 0.4);
		}
	}

	public List<DetectedBeat> detect(AudioBuffer buffer) {
		List<DetectedBeat> beats = new ArrayList<>();
		if (buffer == null || buffer.getLength() < FFT_SIZE) return beats;
		float[] samples = buffer.getSamples();
		int sampleRate = buffer.getSampleRate();
		int numWindows = (samples.length - FFT_SIZE) / FFT_STEP + 1;
		if (numWindows < 2) return beats;

		float[] prevSpectrum = null;
		float[] fluxHistory = new float[FLUX_WINDOW];
		int fluxIdx = 0;
		double lastBeatTime = -minBeatInterval * 2;

		// 预计算频段边界索引
		int lowFreqBin = (int) (LOW_FREQ_CUTOFF * FFT_SIZE / sampleRate);
		int midFreqBin = (int) (MID_FREQ_CUTOFF * FFT_SIZE / sampleRate);
		lowFreqBin = Math.max(1, Math.min(power.length - 1, lowFreqBin));
		midFreqBin = Math.max(lowFreqBin + 1, Math.min(power.length - 1, midFreqBin));

		for (int w = 0; w < numWindows; w++) {
			int offset = w * FFT_STEP;
			fft.powerSpectrum(samples, offset, power);
			double time = (offset + FFT_SIZE / 2.0) / sampleRate;

			if (prevSpectrum != null) {
				// 计算加权 Spectral Flux
				float flux = 0;
				for (int i = 0; i < power.length; i++) {
					float diff = power[i] - prevSpectrum[i];
					if (diff > 0) {
						// 应用频段加权
						float weight = getFrequencyWeight(i, lowFreqBin, midFreqBin);
						flux += diff * weight;
					}
				}

				fluxHistory[fluxIdx % FLUX_WINDOW] = flux;
				fluxIdx++;
				float mean = 0;
				int count = Math.min(fluxIdx, FLUX_WINDOW);
				for (int i = 0; i < count; i++) mean += fluxHistory[i];
				mean /= count;
				float threshold = mean * THRESHOLD_MULTIPLIER;

				if (flux > threshold && (time - lastBeatTime) >= minBeatInterval) {
					// 计算强度（归一化）
					float strength = Math.min(1f, (flux - threshold) / (mean + 1e-6f) * 0.5f);
					beats.add(new DetectedBeat(time, strength));
					lastBeatTime = time;
				}
			}
			prevSpectrum = power.clone();
		}
		return beats;
	}

	/**
	 * 获取频段权重。
	 */
	private float getFrequencyWeight(int binIndex, int lowBin, int midBin) {
		if (binIndex < lowBin) {
			return LOW_FREQ_WEIGHT;
		} else if (binIndex < midBin) {
			return MID_FREQ_WEIGHT;
		} else {
			return HIGH_FREQ_WEIGHT;
		}
	}
}
