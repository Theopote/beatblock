package com.beatblock.audio;

/**
 * 最小实数 FFT：实输入 → 各 bin 的功率（幅度平方）。
 * Radix-2，仅支持 2 的幂长度。用于频段能量分析。
 */
public final class RealFFT {

	private final int n;
	private final float[] cosTable;
	private final float[] sinTable;

	public RealFFT(int size) {
		if (size <= 0 || (size & (size - 1)) != 0) {
			throw new IllegalArgumentException("size must be a power of 2");
		}
		this.n = size;
		this.cosTable = new float[n / 2];
		this.sinTable = new float[n / 2];
		for (int k = 0; k < n / 2; k++) {
			double angle = -2 * Math.PI * k / n;
			cosTable[k] = (float) Math.cos(angle);
			sinTable[k] = (float) Math.sin(angle);
		}
	}

	/**
	 * 对实输入做 FFT，结果写入 real 和 imag（长度各 n）。实输入时 imag 初始为 0。
	 */
	public void forward(float[] real, float[] imag) {
		if (real.length < n || imag.length < n) {
			throw new IllegalArgumentException("buffer size");
		}
		bitReverseCopy(real, imag);
		for (int len = 2; len <= n; len *= 2) {
			int half = len / 2;
			int step = n / len;
			for (int j = 0; j < n; j += len) {
				for (int i = 0; i < half; i++) {
					int k = step * i;
					float r = real[j + i + half], im = imag[j + i + half];
					float tRe = r * cosTable[k] + im * sinTable[k];
					float tIm = im * cosTable[k] - r * sinTable[k];
					real[j + i + half] = real[j + i] - tRe;
					imag[j + i + half] = imag[j + i] - tIm;
					real[j + i] += tRe;
					imag[j + i] += tIm;
				}
			}
		}
		// 实输入 FFT：虚部在 0 和 n/2 为 0；这里我们只关心幅度，所以保持 real/imag 即可
	}

	private void bitReverseCopy(float[] real, float[] imag) {
		int bits = Integer.numberOfTrailingZeros(n);
		for (int i = 0; i < n; i++) {
			int j = Integer.reverse(i) >>> (32 - bits);
			if (i < j) {
				float tr = real[i], ti = imag[i];
				real[i] = real[j]; imag[i] = imag[j];
				real[j] = tr; imag[j] = ti;
			}
		}
	}

	/**
	 * 从实输入计算各 bin 的功率（幅度平方），写入 power[0..n/2]。
	 * 输入 data 长度至少 n；power 长度至少 n/2+1。
	 */
	public void powerSpectrum(float[] data, int offset, float[] power) {
		float[] real = new float[n];
		float[] imag = new float[n];
		for (int i = 0; i < n; i++) {
			real[i] = offset + i < data.length ? data[offset + i] : 0;
		}
		forward(real, imag);
		float scale = 1f / n;
		for (int k = 0; k <= n / 2; k++) {
			float re = real[k] * scale;
			float im = (k > 0 && k < n) ? imag[k] * scale : 0;
			power[k] = re * re + im * im;
		}
	}
}
