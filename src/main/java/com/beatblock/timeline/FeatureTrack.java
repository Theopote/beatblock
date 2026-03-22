package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 单条命名特征轨道：包含一组按时间排序的 {@link FeatureEvent}。
 * key 示例："kick"、"snare"、"hihat"，也可是 Python 脚本写入的任意扩展键。
 */
public final class FeatureTrack {

	private static final Comparator<FeatureEvent> BY_TIME =
			Comparator.comparingDouble(FeatureEvent::getTimeSeconds);

	private final String key;
	/** display label（可选，fallback 到 key） */
	private final String label;
	private final List<FeatureEvent> events = new ArrayList<>();

	public FeatureTrack(String key, String label) {
		this.key   = key   != null ? key   : "unknown";
		this.label = label != null ? label : this.key;
	}

	public String getKey()   { return key;   }
	public String getLabel() { return label; }

	/** 返回不可变视图，内部已按时间升序。 */
	public List<FeatureEvent> getEvents() {
		return Collections.unmodifiableList(events);
	}

	/** 有序插入（binarySearch 保持升序）。 */
	public void addEvent(FeatureEvent e) {
		if (e == null) return;
		int idx = Collections.binarySearch(events, e, BY_TIME);
		if (idx < 0) idx = -idx - 1;
		events.add(idx, e);
	}

	public void clear() {
		events.clear();
	}

	public int size() {
		return events.size();
	}
}
