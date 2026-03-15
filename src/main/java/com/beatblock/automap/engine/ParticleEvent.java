package com.beatblock.automap.engine;

/**
 * 自动粒子事件：时间 + 类型，写入全局事件轨道（name 标识粒子类型）。
 */
public final class ParticleEvent {

	private final double timeSeconds;
	private final ParticleType type;

	public ParticleEvent(double timeSeconds, ParticleType type) {
		this.timeSeconds = Math.max(0, timeSeconds);
		this.type = type != null ? type : ParticleType.SPARK;
	}

	public double getTimeSeconds() { return timeSeconds; }
	public ParticleType getType() { return type; }
}
