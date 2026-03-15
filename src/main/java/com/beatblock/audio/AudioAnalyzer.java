package com.beatblock.audio;

import com.beatblock.timeline.FrequencyBand;
import com.beatblock.timeline.FrequencyEvent;
import com.beatblock.timeline.TimelineModel;
import com.beatblock.timeline.WaveformData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 音频分析：从解码后的 PCM 生成波形数据与频段事件，写入 TimelineModel。
 * 波形为预计算下采样；频段为 FFT 窗内低/中/高能量。
 */
public final class AudioAnalyzer {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioAnalyzer.class);

	/** 波形输出点数（时间线显示用） */
	public static final int WAVEFORM_SAMPLES = 2000;
	/** FFT 窗大小（2 的幂） */
	public static final int FFT_SIZE = 2048;
	/** 频段分析步进（采样点数），与 FFT_SIZE 相同则无重叠 */
	public static final int FFT_STEP = 1024;
	/** 频段能量阈值，超过则生成事件 */
	public static final float ENERGY_THRESHOLD = 0.02f;
	/** 低频 bin 上限（约 200Hz @ 44.1k: bin 约 9） */
	public static final int LOW_BIN_END = 10;
	/** 中频 bin 上限（约 2kHz） */
	public static final int MID_BIN_END = 93;
	/** 高频为其余 bin */

	public static void analyzeAndFillTimeline(DecodedAudio audio, TimelineModel timeline) {
		if (audio == null || timeline == null) return;
		double duration = audio.getDurationSeconds();
		int sampleRate = audio.getSampleRate();
		float[] samples = audio.getSamples();
		if (samples.length == 0) return;

		timeline.setDurationSeconds(duration);
		timeline.setWaveform(buildWaveform(samples, sampleRate, duration));
		timeline.clearFrequencyEvents();
		for (FrequencyEvent e : buildFrequencyEvents(samples, sampleRate, duration)) {
			timeline.addFrequencyEvent(e);
		}
		timeline.sortAll();
		LOGGER.info("BeatBlock: 已分析音频 duration={}s, waveform={} 点, 频段事件 {} 个",
			duration, timeline.getWaveform() != null ? timeline.getWaveform().getSampleCount() : 0,
			timeline.getFrequencyEvents().size());
	}

	/**
	 * 生成波形：按时间均分为 WAVEFORM_SAMPLES 段，每段取最大绝对值并归一到 0..1。
	 */
	public static WaveformData buildWaveform(float[] samples, int sampleRate, double durationSeconds) {
		if (samples == null || samples.length == 0 || durationSeconds <= 0) {
			return new WaveformData(new float[0], 0, sampleRate);
		}
		int n = Math.min(WAVEFORM_SAMPLES, (int) (durationSeconds * 10));
		n = Math.max(1, n);
		float[] out = new float[n];
		int samplesPerBucket = samples.length / n;
		for (int i = 0; i < n; i++) {
			int start = i * samplesPerBucket;
			int end = i == n - 1 ? samples.length : (i + 1) * samplesPerBucket;
			float max = 0;
			for (int j = start; j < end; j++) {
				float a = Math.abs(samples[j]);
				if (a > max) max = a;
			}
			out[i] = max;
		}
		return new WaveformData(out, durationSeconds, sampleRate);
	}

	/**
	 * FFT 窗滑动，每窗得到 low/mid/high 能量，超过阈值则生成 FrequencyEvent。
	 */
	public static List<FrequencyEvent> buildFrequencyEvents(float[] samples, int sampleRate, double durationSeconds) {
		List<FrequencyEvent> events = new ArrayList<>();
		if (samples == null || samples.length < FFT_SIZE || sampleRate <= 0) return events;

		RealFFT fft = new RealFFT(FFT_SIZE);
		float[] power = new float[FFT_SIZE / 2 + 1];
		int numWindows = (samples.length - FFT_SIZE) / FFT_STEP + 1;
		float maxLow = 0, maxMid = 0, maxHigh = 0;
		for (int w = 0; w < numWindows; w++) {
			int offset = w * FFT_STEP;
			fft.powerSpectrum(samples, offset, power);
			float low = sumPower(power, 0, LOW_BIN_END);
			float mid = sumPower(power, LOW_BIN_END, MID_BIN_END);
			float high = sumPower(power, MID_BIN_END, power.length - 1);
			float total = low + mid + high;
			if (total < 1e-10f) continue;
			low /= total;
			mid /= total;
			high /= total;
			maxLow = Math.max(maxLow, low);
			maxMid = Math.max(maxMid, mid);
			maxHigh = Math.max(maxHigh, high);
			double timeSeconds = (offset + FFT_SIZE / 2) / (double) sampleRate;
			if (timeSeconds >= durationSeconds) break;
			if (low >= ENERGY_THRESHOLD) events.add(new FrequencyEvent(timeSeconds, FrequencyBand.LOW, Math.min(1f, low * 2f)));
			if (mid >= ENERGY_THRESHOLD) events.add(new FrequencyEvent(timeSeconds, FrequencyBand.MID, Math.min(1f, mid * 2f)));
			if (high >= ENERGY_THRESHOLD) events.add(new FrequencyEvent(timeSeconds, FrequencyBand.HIGH, Math.min(1f, high * 2f)));
		}
		return events;
	}

	private static float sumPower(float[] power, int from, int to) {
		float s = 0;
		for (int i = from; i <= to && i < power.length; i++) {
			s += power[i];
		}
		return s;
	}
}
