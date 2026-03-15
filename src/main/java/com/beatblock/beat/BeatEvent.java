package com.beatblock.beat;

/**
 * 单次节拍事件：时间戳、类型、强度、可选轨道。
 */
public final class BeatEvent {

	public enum Type {
		KICK,
		SNARE,
		HIHAT,
		BASS,
		MELODY,
		CUSTOM
	}

	private final double timestamp;
	private final Type type;
	private final float intensity;
	private final int lane;

	public BeatEvent(double timestamp, Type type, float intensity, int lane) {
		this.timestamp = timestamp;
		this.type = type;
		this.intensity = Math.max(0f, Math.min(1f, intensity));
		this.lane = lane;
	}

	public BeatEvent(double timestamp, Type type, float intensity) {
		this(timestamp, type, intensity, 0);
	}

	public double getTimestamp() {
		return timestamp;
	}

	public Type getType() {
		return type;
	}

	public float getIntensity() {
		return intensity;
	}

	public int getLane() {
		return lane;
	}
}
