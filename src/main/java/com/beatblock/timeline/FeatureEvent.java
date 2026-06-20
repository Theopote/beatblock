package com.beatblock.timeline;

/**
 * 第 1 层 — 音频参考轨上的能量点（可手改，不进入播放器派发）。
 */
public final class FeatureEvent {

	private final double timeSeconds;
	private final float  energy;

	public FeatureEvent(double timeSeconds, float energy) {
		this.timeSeconds = timeSeconds;
		this.energy      = Math.max(0f, Math.min(1f, energy));
	}

	public double getTimeSeconds() { return timeSeconds; }
	public float  getEnergy()      { return energy;      }
}
