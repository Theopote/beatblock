package com.beatblock.timeline;

/**
 * 特征轨道中的单个事件（时间点 + 能量），与频段无关。
 * 时间单位：秒。
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
