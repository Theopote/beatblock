package com.beatblock.timeline;

import java.util.Collections;
import java.util.Map;

/**
 * 时间线上的动画事件：由时间驱动，不直接绑定摄像机。
 * 可带 energy 用于高度/速度/粒子数等映射。
 */
public final class TimelineAnimationEvent {

	private final String eventId;
	private final double timeSeconds;
	private final double durationSeconds;
	private final String animationTypeId;
	private final String targetObjectId;
	private final float energy;
	private final Map<String, Object> parameters;

	public TimelineAnimationEvent(String eventId, double timeSeconds, double durationSeconds,
	                             String animationTypeId, String targetObjectId,
	                             float energy, Map<String, Object> parameters) {
		this.eventId = eventId != null ? eventId : "";
		this.timeSeconds = timeSeconds;
		this.durationSeconds = Math.max(0.01, durationSeconds);
		this.animationTypeId = animationTypeId != null ? animationTypeId : "";
		this.targetObjectId = targetObjectId != null ? targetObjectId : "";
		this.energy = Math.max(0f, Math.min(1f, energy));
		this.parameters = parameters != null ? Map.copyOf(parameters) : Collections.emptyMap();
	}

	public String getEventId() {
		return eventId;
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public double getEndTimeSeconds() {
		return timeSeconds + durationSeconds;
	}

	public String getAnimationTypeId() {
		return animationTypeId;
	}

	public String getTargetObjectId() {
		return targetObjectId;
	}

	public float getEnergy() {
		return energy;
	}

	public TimelineAnimationActionMode getActionMode() {
		return TimelineAnimationActionMode.fromValue(parameters.get("actionMode"));
	}

	public Map<String, Object> getParameters() {
		return parameters;
	}
}
