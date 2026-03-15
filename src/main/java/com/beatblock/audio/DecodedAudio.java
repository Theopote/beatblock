package com.beatblock.audio;

import java.util.Arrays;

/**
 * 解码后的音频：单声道 float 采样（-1..1）、采样率、时长。
 * 用于波形与频段分析，分析完成后可不保留以节省内存。
 */
public final class DecodedAudio {

	private final float[] samples;
	private final int sampleRate;
	private final double durationSeconds;

	public DecodedAudio(float[] samples, int sampleRate, double durationSeconds) {
		this.samples = samples != null ? Arrays.copyOf(samples, samples.length) : new float[0];
		this.sampleRate = Math.max(1, sampleRate);
		this.durationSeconds = Math.max(0, durationSeconds);
	}

	public float[] getSamples() {
		return Arrays.copyOf(samples, samples.length);
	}

	public int getSampleCount() {
		return samples.length;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}
}
