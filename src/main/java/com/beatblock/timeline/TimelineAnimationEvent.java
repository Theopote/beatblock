package com.beatblock.timeline;

import java.util.Collections;
import java.util.Map;

/**
 * 第 2 层 — 舞台事件（概念上的 StageEvent）。
 * <p>
 * 描述「在时间点对哪个舞台对象执行何种动作」。由创作者编辑或自动映射生成初稿；
 * 第 3 层 {@link com.beatblock.engine.BlockAnimationEngine} 在播放时消费，不区分来源。
 * <p>
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
		Object value = parameters.get("actionMode");
		if (value == null) value = parameters.get("mode");
		return TimelineAnimationActionMode.fromValue(value);
	}

	public Map<String, Object> getParameters() {
		return parameters;
	}
}
