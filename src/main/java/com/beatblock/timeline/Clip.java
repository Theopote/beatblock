package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 片段：时间段容器，可整体拖动、复制、裁剪。
 */
public class Clip {

	private String id;
	private double startTimeSeconds;
	private double endTimeSeconds;
	private final List<TimelineEvent> events = new ArrayList<>();

	public Clip() { this("", 0, 0); }

	public Clip(String id, double startTimeSeconds, double endTimeSeconds) {
		this.id = id != null ? id : "";
		this.startTimeSeconds = Math.max(0, startTimeSeconds);
		this.endTimeSeconds = Math.max(this.startTimeSeconds, endTimeSeconds);
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id != null ? id : ""; }
	public double getStartTimeSeconds() { return startTimeSeconds; }
	public void setStartTimeSeconds(double startTimeSeconds) {
		this.startTimeSeconds = Math.max(0, startTimeSeconds);
		if (endTimeSeconds < this.startTimeSeconds) endTimeSeconds = this.startTimeSeconds;
	}
	public double getEndTimeSeconds() { return endTimeSeconds; }
	public void setEndTimeSeconds(double endTimeSeconds) {
		this.endTimeSeconds = Math.max(startTimeSeconds, endTimeSeconds);
	}
	public double getDurationSeconds() { return endTimeSeconds - startTimeSeconds; }
	public List<TimelineEvent> getEvents() { return Collections.unmodifiableList(events); }
	public void addEvent(TimelineEvent event) {
		if (event != null) { events.add(event); sortEvents(); }
	}
	public boolean removeEvent(String eventId) {
		return events.removeIf(e -> eventId != null && eventId.equals(e.getId()));
	}
	public TimelineEvent getEvent(String eventId) {
		for (TimelineEvent e : events)
			if (eventId != null && eventId.equals(e.getId())) return e;
		return null;
	}
	private void sortEvents() {
		events.sort(Comparator.comparingDouble(TimelineEvent::getTimeSeconds));
	}
}
