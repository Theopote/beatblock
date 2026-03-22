package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时间线根对象：名称、时长、轨道列表、元数据。单一时序数据源，替代原 TimelineModel。
 */
public class Timeline {

	public static final String TRACK_ID_AUDIO = "audio";
	public static final String TRACK_ID_ANIMATION_BLOCK = "animation_block";
	public static final String TRACK_ID_ANIMATION_AUTO = "animation_auto";
	public static final String TRACK_ID_CAMERA = "camera";
	public static final String TRACK_ID_GLOBAL = "global";

	private String name = "";
	private double durationSeconds = 0;
	private final List<Track> tracks = new ArrayList<>();
	private final Map<String, Object> metadata = new ConcurrentHashMap<>();
	private final List<TimelineMarker> markers = new ArrayList<>();
	private final List<TimelineMarker> markerView = Collections.unmodifiableList(markers);
	private final List<TimelineAnimationEvent> blockAnimationCache = new ArrayList<>();
	private final List<TimelineAnimationEvent> autoAnimationCache = new ArrayList<>();
	private final List<TimelineAnimationEvent> blockAnimationCacheView = Collections.unmodifiableList(blockAnimationCache);
	private final List<TimelineAnimationEvent> autoAnimationCacheView = Collections.unmodifiableList(autoAnimationCache);
	private boolean blockAnimationDirty = true;
	private boolean autoAnimationDirty = true;

	public String getName() { return name; }
	public void setName(String name) { this.name = name != null ? name : ""; }
	public double getDurationSeconds() { return durationSeconds; }
	public void setDurationSeconds(double durationSeconds) { this.durationSeconds = Math.max(0, durationSeconds); }
	public List<Track> getTracks() { return Collections.unmodifiableList(tracks); }
	public void addTrack(Track track) {
		if (track != null) {
			tracks.add(track);
			markAnimationEventsDirty(track.getId());
		}
	}
	public boolean removeTrack(String trackId) {
		boolean removed = tracks.removeIf(t -> trackId != null && trackId.equals(t.getId()));
		if (removed) markAnimationEventsDirty(trackId);
		return removed;
	}
	public Track getTrack(String trackId) {
		for (Track t : tracks) if (trackId != null && trackId.equals(t.getId())) return t;
		return null;
	}
	public Track getTrackByType(TrackType type) {
		for (Track t : tracks) if (t.getType() == type) return t;
		return null;
	}
	public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
	public void setMetadata(String key, Object value) { if (key != null) metadata.put(key, value); }
	public Object getMetadata(String key) { return metadata.get(key); }
	public List<TimelineMarker> getMarkers() { return markerView; }
	/** BPM（由音频分析填入 metadata["bpm"]），未设置时返回 0。 */
	public double getBpm() {
		Object v = metadata.get("bpm");
		if (v instanceof Number) return ((Number) v).doubleValue();
		return 0;
	}

	public void addMarker(TimelineMarker marker) {
		if (marker == null) return;
		markers.add(marker);
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
	}

	public void clearMarkers() {
		markers.clear();
	}

	public void setMarkers(List<TimelineMarker> newMarkers) {
		markers.clear();
		if (newMarkers != null) {
			markers.addAll(newMarkers);
			markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		}
	}

	public int findMarkerIndexById(String markerId) {
		if (markerId == null || markerId.isBlank()) return -1;
		for (int i = 0; i < markers.size(); i++) {
			TimelineMarker marker = markers.get(i);
			if (markerId.equals(marker.getId())) return i;
		}
		return -1;
	}

	public boolean removeMarker(int index) {
		if (index < 0 || index >= markers.size()) return false;
		markers.remove(index);
		return true;
	}

	public boolean removeMarker(String markerId) {
		int index = findMarkerIndexById(markerId);
		return removeMarker(index);
	}

	public boolean updateMarker(int index, double timeSeconds, String name) {
		if (index < 0 || index >= markers.size()) return false;
		TimelineMarker prev = markers.get(index);
		markers.set(index, new TimelineMarker(prev.getId(), timeSeconds, name, prev.getType()));
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return true;
	}

	public boolean updateMarker(int index, double timeSeconds, String name, MarkerType type) {
		if (index < 0 || index >= markers.size()) return false;
		TimelineMarker prev = markers.get(index);
		markers.set(index, new TimelineMarker(prev.getId(), timeSeconds, name, type));
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return true;
	}

	public boolean updateMarker(String markerId, double timeSeconds, String name) {
		int index = findMarkerIndexById(markerId);
		if (index < 0) return false;
		TimelineMarker prev = markers.get(index);
		markers.set(index, new TimelineMarker(prev.getId(), timeSeconds, name, prev.getType()));
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return true;
	}

	public boolean updateMarker(String markerId, double timeSeconds, String name, MarkerType type) {
		int index = findMarkerIndexById(markerId);
		if (index < 0) return false;
		TimelineMarker prev = markers.get(index);
		markers.set(index, new TimelineMarker(prev.getId(), timeSeconds, name, type));
		markers.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return true;
	}

	// ----- 便捷 API（兼容原 TimelineModel 读写） -----

	public WaveformData getWaveform() {
		AudioTrackData ad = getAudioTrackData();
		return ad != null ? ad.getWaveform() : null;
	}
	public void setWaveform(WaveformData waveform) {
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.setWaveform(waveform);
	}

	public List<FrequencyEvent> getFrequencyEvents() {
		AudioTrackData ad = getAudioTrackData();
		if (ad == null) return List.of();
		// 三个子列表内部已有序，合并后再排一次即可（调用频率低，无性能压力）
		List<FrequencyEvent> out = new ArrayList<>();
		out.addAll(ad.getLowBand());
		out.addAll(ad.getMidBand());
		out.addAll(ad.getHighBand());
		out.sort(Comparator.comparingDouble(FrequencyEvent::getTimeSeconds));
		return out;
	}
	public List<FrequencyEvent> getFrequencyEventsByBand(FrequencyBand band) {
		AudioTrackData ad = getAudioTrackData();
		if (ad == null) return List.of();
		// 列表内部已按 timeSeconds 有序保序，直接返回无需拷贝或排序
		return switch (band) {
			case LOW -> ad.getLowBand();
			case MID -> ad.getMidBand();
			case HIGH -> ad.getHighBand();
		};
	}
	public void addFrequencyEvent(FrequencyEvent e) {
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.addFrequencyEvent(e);
	}
	public void clearFrequencyEvents() {
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.clearAllBands();
	}

	// ── 新特征轨道 API（kick / snare / hihat 等开放键） ─────────────────

	/**
	 * 向命名特征轨道追加事件（首次写入时自动创建轨道）。
	 *
	 * @param key   轨道键，如 "kick"、"snare"、"hihat"
	 * @param event 特征事件
	 */
	public void addFeatureEvent(String key, FeatureEvent event) {
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.addFeatureEvent(key, event);
	}

	/**
	 * 向命名特征轨道追加事件，并指定显示名称（首次创建时生效）。
	 */
	public void addFeatureEvent(String key, String label, FeatureEvent event) {
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.addFeatureEvent(key, label, event);
	}

	/** 获取指定 key 的特征轨道事件列表，不存在返回空列表。 */
	public List<FeatureEvent> getFeatureEvents(String key) {
		AudioTrackData ad = getAudioTrackData();
		if (ad == null) return List.of();
		FeatureTrack ft = ad.getFeatureTrack(key);
		return ft != null ? ft.getEvents() : List.of();
	}

	/** 获取所有命名特征轨道（保持插入顺序）。 */
	public java.util.Map<String, FeatureTrack> getFeatureTracks() {
		AudioTrackData ad = getAudioTrackData();
		if (ad == null) return Map.of();
		return ad.getFeatureTracks();
	}

	/** 是否包含任何命名特征轨道数据。 */
	public boolean hasFeatureTracks() {
		AudioTrackData ad = getAudioTrackData();
		return ad != null && ad.hasFeatureTracks();
	}

	/** 清空所有命名特征轨道（不清除遗留频段）。 */
	public void clearFeatureTracks() {
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.clearFeatureTracks();
	}

	// ── 茎波形委托（Demucs 模式）──────────────────────────────────────────

	public void setStemWaveform(String stemKey, WaveformData data) {
		AudioTrackData ad = getAudioTrackData();
		if (ad != null) ad.setStemWaveform(stemKey, data);
	}

	public WaveformData getStemWaveform(String stemKey) {
		AudioTrackData ad = getAudioTrackData();
		return ad != null ? ad.getStemWaveform(stemKey) : null;
	}

	public java.util.Set<String> getStemWaveformKeys() {
		AudioTrackData ad = getAudioTrackData();
		return ad != null ? ad.getStemWaveformKeys() : java.util.Set.of();
	}

	public boolean hasStemWaveforms() {
		AudioTrackData ad = getAudioTrackData();
		return ad != null && ad.hasStemWaveforms();
	}

	public List<TimelineAnimationEvent> getBlockAnimationEvents() {
		if (blockAnimationDirty) {
			rebuildAnimationCache(TRACK_ID_ANIMATION_BLOCK, blockAnimationCache);
			blockAnimationDirty = false;
		}
		return blockAnimationCacheView;
	}
	public void addBlockAnimationEvent(TimelineAnimationEvent e) { addAnimationEvent(TRACK_ID_ANIMATION_BLOCK, e); }
	public void clearBlockAnimationEvents() { clearClips(TRACK_ID_ANIMATION_BLOCK); }

	public List<TimelineAnimationEvent> getAutoAnimationEvents() {
		if (autoAnimationDirty) {
			rebuildAnimationCache(TRACK_ID_ANIMATION_AUTO, autoAnimationCache);
			autoAnimationDirty = false;
		}
		return autoAnimationCacheView;
	}
	public void addAutoAnimationEvent(TimelineAnimationEvent e) { addAnimationEvent(TRACK_ID_ANIMATION_AUTO, e); }
	public void clearAutoAnimationEvents() { clearClips(TRACK_ID_ANIMATION_AUTO); }

	/**
	 * 轨道事件被外部直接修改（如拖拽 setTimeSeconds）后，调用此方法失效动画缓存。
	 */
	public void markAnimationEventsDirty(String trackId) {
		if (TRACK_ID_ANIMATION_BLOCK.equals(trackId)) blockAnimationDirty = true;
		if (TRACK_ID_ANIMATION_AUTO.equals(trackId)) autoAnimationDirty = true;
	}

	public void markAnimationEventsDirty() {
		blockAnimationDirty = true;
		autoAnimationDirty = true;
	}

	public List<CameraKeyframe> getCameraKeyframes() {
		List<CameraKeyframe> out = new ArrayList<>();
		Track t = getTrack(TRACK_ID_CAMERA);
		if (t == null) return out;
		for (Clip c : t.getClips())
			for (TimelineEvent e : c.getEvents())
				if (e.getType() == EventType.CAMERA_KEYFRAME)
					out.add(new CameraKeyframe(e.getTimeSeconds()));
		out.sort(Comparator.comparingDouble(CameraKeyframe::getTimeSeconds));
		return out;
	}
	public void addCameraKeyframe(CameraKeyframe k) {
		if (k == null) return;
		Track t = getTrack(TRACK_ID_CAMERA);
		if (t == null) return;
		Clip clip = TimelineOperations.addClip(t, k.getTimeSeconds(), k.getTimeSeconds() + 0.1);
		if (clip != null) TimelineOperations.addEvent(clip, k.getTimeSeconds(), EventType.CAMERA_KEYFRAME, Map.of());
	}
	public void clearCameraKeyframes() { clearClips(TRACK_ID_CAMERA); }

	public List<GlobalEvent> getGlobalEvents() {
		List<GlobalEvent> out = new ArrayList<>();
		Track t = getTrack(TRACK_ID_GLOBAL);
		if (t == null) return out;
		for (Clip c : t.getClips())
			for (TimelineEvent e : c.getEvents()) {
				if (e.getType() != EventType.GLOBAL) continue;
				Map<String, Object> p = e.getParameters();
				String typeStr = (String) p.getOrDefault("type", "SPECIAL");
				String name = (String) p.getOrDefault("name", "");
				try {
					out.add(new GlobalEvent(e.getTimeSeconds(), GlobalEventType.valueOf(typeStr), name));
				} catch (Exception ignored) {
					out.add(new GlobalEvent(e.getTimeSeconds(), GlobalEventType.SPECIAL, name));
				}
			}
		out.sort(Comparator.comparingDouble(GlobalEvent::getTimeSeconds));
		return out;
	}
	public void addGlobalEvent(GlobalEvent e) {
		if (e == null) return;
		Track t = getTrack(TRACK_ID_GLOBAL);
		if (t == null) return;
		Clip clip = TimelineOperations.addClip(t, e.getTimeSeconds(), e.getTimeSeconds() + 0.1);
		if (clip != null) {
			Map<String, Object> params = new HashMap<>();
			params.put("type", e.getType().name());
			params.put("name", e.getName());
			TimelineOperations.addEvent(clip, e.getTimeSeconds(), EventType.GLOBAL, params);
		}
	}
	public void clearGlobalEvents() { clearClips(TRACK_ID_GLOBAL); }

	public void sortAll() {
		// 便捷 getter 已按时间排序返回；若需对 Clip 内 events 原地排序可扩展 Clip.sortEvents()
	}

	private AudioTrackData getAudioTrackData() {
		Track t = getTrack(TRACK_ID_AUDIO);
		return t != null ? t.getAudioData() : null;
	}

	private void rebuildAnimationCache(String trackId, List<TimelineAnimationEvent> out) {
		out.clear();
		Track t = getTrack(trackId);
		if (t == null) return;
		for (Clip c : t.getClips())
			for (TimelineEvent e : c.getEvents()) {
				if (e.getType() != EventType.ANIMATION) continue;
				Map<String, Object> p = e.getParameters();
				Object durObj = p.get("durationSeconds");
				double dur = durObj instanceof Number ? ((Number) durObj).doubleValue() : Math.max(0.01, c.getEndTimeSeconds() - c.getStartTimeSeconds());
				String animId = (String) p.getOrDefault("animationType", "bounce");
				String target = (String) p.getOrDefault("targetObject", "");
				float energy = p.get("energy") instanceof Number ? ((Number) p.get("energy")).floatValue() : 1f;
				out.add(new TimelineAnimationEvent(e.getId(), e.getTimeSeconds(), dur, animId, target, energy, new HashMap<>(p)));
			}
		out.sort(Comparator.comparingDouble(TimelineAnimationEvent::getTimeSeconds));
	}

	private void addAnimationEvent(String trackId, TimelineAnimationEvent e) {
		if (e == null) return;
		Track t = getTrack(trackId);
		if (t == null) return;
		Clip clip = TimelineOperations.addClip(t, e.getTimeSeconds(), e.getEndTimeSeconds());
		if (clip == null) return;
		Map<String, Object> params = new HashMap<>();
		params.put("animationType", e.getAnimationTypeId());
		params.put("targetObject", e.getTargetObjectId());
		params.put("energy", e.getEnergy());
		params.put("durationSeconds", e.getDurationSeconds());
		params.putAll(e.getParameters());
		TimelineOperations.addEvent(clip, e.getTimeSeconds(), EventType.ANIMATION, params);
		markAnimationEventsDirty(trackId);
	}

	private void clearClips(String trackId) {
		Track t = getTrack(trackId);
		if (t == null) return;
		List<String> ids = new ArrayList<>();
		for (Clip c : t.getClips()) ids.add(c.getId());
		for (String id : ids) t.removeClip(id);
		markAnimationEventsDirty(trackId);
	}

	public static Timeline createDefault() {
		Timeline t = new Timeline();
		t.addTrack(new Track(TRACK_ID_AUDIO, "音频", TrackType.AUDIO));
		t.addTrack(new Track(TRACK_ID_ANIMATION_BLOCK, "方块动画", TrackType.ANIMATION));
		t.addTrack(new Track(TRACK_ID_ANIMATION_AUTO, "自动动画", TrackType.ANIMATION));
		t.addTrack(new Track(TRACK_ID_CAMERA, "摄像机", TrackType.CAMERA));
		t.addTrack(new Track(TRACK_ID_GLOBAL, "全局事件", TrackType.EVENT));
		return t;
	}
}
