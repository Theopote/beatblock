package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 音频轨道专用数据：波形 + 低/中/高频段事件。
 * 每个频段列表内部始终按 timeSeconds 升序保序，避免每帧排序开销。
 */
public class AudioTrackData {

	private static final Comparator<FrequencyEvent> BY_TIME =
			Comparator.comparingDouble(FrequencyEvent::getTimeSeconds);

	private WaveformData waveform;
	private final List<FrequencyEvent> lowBand = new ArrayList<>();
	private final List<FrequencyEvent> midBand = new ArrayList<>();
	private final List<FrequencyEvent> highBand = new ArrayList<>();

	public WaveformData getWaveform() { return waveform; }
	public void setWaveform(WaveformData waveform) { this.waveform = waveform; }

	/** 返回夹生不可变视图，内部列表始终有序。 */
	public List<FrequencyEvent> getLowBand() { return Collections.unmodifiableList(lowBand); }
	public List<FrequencyEvent> getMidBand() { return Collections.unmodifiableList(midBand); }
	public List<FrequencyEvent> getHighBand() { return Collections.unmodifiableList(highBand); }

	/** 按 timeSeconds 有序插入，保持列表内部有序。 */
	public void addFrequencyEvent(FrequencyEvent e) {
		if (e == null) return;
		List<FrequencyEvent> target = switch (e.getBand()) {
			case LOW -> lowBand;
			case MID -> midBand;
			case HIGH -> highBand;
		};
		int idx = Collections.binarySearch(target, e, BY_TIME);
		// binarySearch 返回负数时表示未找到相同元素：insertionPoint = -idx - 1
		if (idx < 0) idx = -idx - 1;
		target.add(idx, e);
	}

	public void clearAllBands() {
		lowBand.clear();
		midBand.clear();
		highBand.clear();
	}
}
