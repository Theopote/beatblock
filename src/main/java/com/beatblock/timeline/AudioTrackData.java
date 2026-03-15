package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 音频轨道专用数据：波形 + 低/中/高频段事件。
 */
public class AudioTrackData {

	private WaveformData waveform;
	private final List<FrequencyEvent> lowBand = new ArrayList<>();
	private final List<FrequencyEvent> midBand = new ArrayList<>();
	private final List<FrequencyEvent> highBand = new ArrayList<>();

	public WaveformData getWaveform() { return waveform; }
	public void setWaveform(WaveformData waveform) { this.waveform = waveform; }
	public List<FrequencyEvent> getLowBand() { return Collections.unmodifiableList(lowBand); }
	public List<FrequencyEvent> getMidBand() { return Collections.unmodifiableList(midBand); }
	public List<FrequencyEvent> getHighBand() { return Collections.unmodifiableList(highBand); }

	public void addFrequencyEvent(FrequencyEvent e) {
		if (e == null) return;
		switch (e.getBand()) {
			case LOW -> lowBand.add(e);
			case MID -> midBand.add(e);
			case HIGH -> highBand.add(e);
		}
	}

	public void clearAllBands() {
		lowBand.clear();
		midBand.clear();
		highBand.clear();
	}
}
