package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 音频轨道专用数据：波形 + 开放式命名特征轨道（kick / snare / hihat 等）。
 *
 * <p>原先固定的低/中/高频 {@link FrequencyEvent} 列表保留为向后兼容的遗留路径，
 * 新分析管线改写为向 {@code featureTracks} 写入 {@link FeatureTrack}（key 由 Python 脚本决定）。</p>
 */
public class AudioTrackData {

	// ── 遗留路径（向后兼容，Java 内置分析仍使用） ─────────────────────────
	private static final Comparator<FrequencyEvent> BY_TIME =
			Comparator.comparingDouble(FrequencyEvent::getTimeSeconds);

	private WaveformData waveform;
	private final List<FrequencyEvent> lowBand  = new ArrayList<>();
	private final List<FrequencyEvent> midBand  = new ArrayList<>();
	private final List<FrequencyEvent> highBand = new ArrayList<>();

	// ── 新路径：开放命名特征轨道 ──────────────────────────────────────────
	/** key → FeatureTrack，插入顺序即渲染顺序（LinkedHashMap）。 */
	private final Map<String, FeatureTrack> featureTracks = new LinkedHashMap<>();

	// ── 波形 ───────────────────────────────────────────────────────────────

	public WaveformData getWaveform() { return waveform; }
	public void setWaveform(WaveformData waveform) { this.waveform = waveform; }

	// ── 遗留频段 API ──────────────────────────────────────────────────────

	/** 返回不可变视图，内部列表始终有序。 */
	public List<FrequencyEvent> getLowBand()  { return Collections.unmodifiableList(lowBand);  }
	public List<FrequencyEvent> getMidBand()  { return Collections.unmodifiableList(midBand);  }
	public List<FrequencyEvent> getHighBand() { return Collections.unmodifiableList(highBand); }

	/** 按 timeSeconds 有序插入，保持列表内部有序（遗留 Java 分析路径）。 */
	public void addFrequencyEvent(FrequencyEvent e) {
		if (e == null) return;
		List<FrequencyEvent> target = switch (e.getBand()) {
			case LOW  -> lowBand;
			case MID  -> midBand;
			case HIGH -> highBand;
		};
		int idx = Collections.binarySearch(target, e, BY_TIME);
		if (idx < 0) idx = -idx - 1;
		target.add(idx, e);
	}

	public void clearAllBands() {
		lowBand.clear();
		midBand.clear();
		highBand.clear();
	}

	// ── 新特征轨道 API ────────────────────────────────────────────────────

	/**
	 * 向命名特征轨道追加一个事件。若轨道不存在则自动创建（label 与 key 相同）。
	 *
	 * @param key   轨道键，如 "kick"、"snare"、"hihat"
	 * @param event 特征事件
	 */
	public void addFeatureEvent(String key, FeatureEvent event) {
		if (key == null || key.isBlank() || event == null) return;
		featureTracks.computeIfAbsent(key, k -> new FeatureTrack(k, k)).addEvent(event);
	}

	/**
	 * 向命名特征轨道追加一个事件，并同时指定显示名称（首次创建时生效）。
	 */
	public void addFeatureEvent(String key, String label, FeatureEvent event) {
		if (key == null || key.isBlank() || event == null) return;
		featureTracks.computeIfAbsent(key, k -> new FeatureTrack(k, label)).addEvent(event);
	}

	/** 获取指定 key 的特征轨道，不存在返回 null。 */
	public FeatureTrack getFeatureTrack(String key) {
		return featureTracks.get(key);
	}

	/** 返回所有特征轨道的不可变视图（保留插入顺序）。 */
	public Map<String, FeatureTrack> getFeatureTracks() {
		return Collections.unmodifiableMap(featureTracks);
	}

	/** 返回所有已注册的特征轨道 key。 */
	public Set<String> getFeatureTrackKeys() {
		return Collections.unmodifiableSet(featureTracks.keySet());
	}

	/** 清空所有特征轨道数据（不清除遗留频段）。 */
	public void clearFeatureTracks() {
		featureTracks.values().forEach(FeatureTrack::clear);
		featureTracks.clear();
	}

	/** 清空全部音频数据（遗留频段 + 新特征轨道）。 */
	public void clearAll() {
		clearAllBands();
		clearFeatureTracks();
	}

	/** 是否存在任何命名特征轨道数据。 */
	public boolean hasFeatureTracks() {
		return !featureTracks.isEmpty();
	}
}
