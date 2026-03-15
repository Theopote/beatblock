package com.beatblock.beat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 根据当前播放时间从 Beatmap 派发已触达的 BeatEvent。
 */
public class BeatScheduler {

	private Beatmap beatmap;
	private double lastDispatchedTime = -1;
	private final List<Consumer<BeatEvent>> listeners = new ArrayList<>();

	public void setBeatmap(Beatmap beatmap) {
		this.beatmap = beatmap;
		this.lastDispatchedTime = -1;
	}

	public Beatmap getBeatmap() {
		return beatmap;
	}

	public void addListener(Consumer<BeatEvent> listener) {
		if (listener != null && !listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void removeListener(Consumer<BeatEvent> listener) {
		listeners.remove(listener);
	}

	/**
	 * 根据当前播放时间派发 [lastDispatchedTime, currentTimeSeconds] 区间内的所有事件。
	 */
	public void tick(double currentTimeSeconds) {
		if (beatmap == null || listeners.isEmpty()) {
			return;
		}
		double from = lastDispatchedTime < 0 ? 0 : lastDispatchedTime;
		if (currentTimeSeconds < from) {
			// 例如倒退或重置
			lastDispatchedTime = currentTimeSeconds;
			return;
		}
		for (BeatEvent event : beatmap.getEventsInRange(from, currentTimeSeconds)) {
			for (Consumer<BeatEvent> listener : listeners) {
				listener.accept(event);
			}
		}
		lastDispatchedTime = currentTimeSeconds;
	}

	public void reset() {
		lastDispatchedTime = -1;
	}
}
