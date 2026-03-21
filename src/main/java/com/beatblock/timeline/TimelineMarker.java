package com.beatblock.timeline;

/**
 * 时间线标记点（Marker）：用于快速定位段落、镜头点、Drop、转场等关键时刻。
 */
public final class TimelineMarker {

	private final double timeSeconds;
	private final String name;

	public TimelineMarker(double timeSeconds, String name) {
		this.timeSeconds = Math.max(0, timeSeconds);
		this.name = name != null ? name : "";
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}

	public String getName() {
		return name;
	}
}
