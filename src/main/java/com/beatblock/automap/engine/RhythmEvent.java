package com.beatblock.automap.engine;

/**
 * 分类后的节奏事件：时间、类型、能量，供动画映射与镜头使用。
 */
public final class RhythmEvent {

	private final double timeSeconds;
	private final RhythmType type;
	private final float energy;

	public RhythmEvent(double timeSeconds, RhythmType type, float energy) {
		this.timeSeconds = timeSeconds;
		this.type = type != null ? type : RhythmType.KICK;
		this.energy = Math.max(0f, Math.min(1f, energy));
	}

	public double getTimeSeconds() { return timeSeconds; }
	public RhythmType getType() { return type; }
	public float getEnergy() { return energy; }
}
