package com.beatblock.timeline;

import java.util.UUID;

/**
 * 时间线标记点（Marker）：用于快速定位段落、镜头点、Drop、转场等关键时刻。
 */
public final class TimelineMarker {

	private final String id;
	private final double timeSeconds;
	private final String name;

	public TimelineMarker(double timeSeconds, String name) {
		this(UUID.randomUUID().toString(), timeSeconds, name);
	}

	public TimelineMarker(String id, double timeSeconds, String name) {
		this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
		this.timeSeconds = Math.max(0, timeSeconds);
		this.name = name != null ? name : "";
	}

	public String getId() {
		return id;
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}

	public String getName() {
		return name;
	}
}
