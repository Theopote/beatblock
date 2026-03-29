package com.beatblock.beat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 节拍图：BPM、总时长、按时间排序的节拍事件列表。
 */
public class Beatmap {

	private final String name;
	private final double bpm;
	private final double durationSeconds;
	private final List<BeatEvent> events;

	public Beatmap(String name, double bpm, double durationSeconds, List<BeatEvent> events) {
		this.name = name != null ? name : "";
		this.bpm = Math.max(0.1, bpm);
		this.durationSeconds = Math.max(0, durationSeconds);
		this.events = new ArrayList<>(events != null ? events : List.of());
		this.events.sort(Comparator.comparingDouble(BeatEvent::getTimestamp));
	}

	public String getName() {
		return name;
	}

	public double getBpm() {
		return bpm;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public List<BeatEvent> getEvents() {
		return Collections.unmodifiableList(events);
	}

	public List<BeatEvent> getEventsInRange(double fromTimeSeconds, double toTimeSeconds) {
		List<BeatEvent> result = new ArrayList<>();
		for (BeatEvent e : events) {
			double t = e.getTimestamp();
			if (t >= fromTimeSeconds && t <= toTimeSeconds) {
				result.add(e);
			}
		}
		return result;
	}
}
