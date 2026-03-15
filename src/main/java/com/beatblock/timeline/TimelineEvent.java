package com.beatblock.timeline;

import java.util.Collections;
import java.util.Map;

/**
 * 时间线事件（最小单位）：时间 + 类型 + 参数，便于序列化与扩展。
 */
public class TimelineEvent {

	private String id;
	private double timeSeconds;
	private EventType type;
	private Map<String, Object> parameters;

	public TimelineEvent() {
		this("", 0, EventType.ANIMATION, Collections.emptyMap());
	}

	public TimelineEvent(String id, double timeSeconds, EventType type, Map<String, Object> parameters) {
		this.id = id != null ? id : "";
		this.timeSeconds = Math.max(0, timeSeconds);
		this.type = type != null ? type : EventType.ANIMATION;
		this.parameters = parameters != null ? new java.util.HashMap<>(parameters) : new java.util.HashMap<>();
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id != null ? id : ""; }
	public double getTimeSeconds() { return timeSeconds; }
	public void setTimeSeconds(double timeSeconds) { this.timeSeconds = Math.max(0, timeSeconds); }
	public EventType getType() { return type; }
	public void setType(EventType type) { this.type = type != null ? type : EventType.ANIMATION; }
	public Map<String, Object> getParameters() { return Collections.unmodifiableMap(parameters); }
	public void setParameter(String key, Object value) {
		if (parameters == null) parameters = new java.util.HashMap<>();
		parameters.put(key, value);
	}
	public Object getParameter(String key) { return parameters != null ? parameters.get(key) : null; }
}
