package com.beatblock.timeline.rendering;

import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 音频拖放/beatmap 回填时的特征轨偏移与路径 key 工具。 */
public final class TimelineAudioFeatureFillSupport {

	public record SavedFeatureTrack(String label, List<FeatureEvent> events) {}

	private TimelineAudioFeatureFillSupport() {}

	public static String buildAudioAssetKey(com.beatblock.audio.assets.AudioAsset asset) {
		if (asset == null || asset.getPath() == null) return null;
		return normalizeAudioPath(asset.getPath().toAbsolutePath().normalize().toString());
	}

	public static String getTimelineAudioPathKey(Timeline timeline) {
		if (timeline == null) return null;
		Object audioPath = timeline.getMetadata("audioPath");
		if (audioPath == null) return null;
		return normalizeAudioPath(audioPath.toString());
	}

	public static String normalizeAudioPath(String rawPath) {
		if (rawPath == null || rawPath.isBlank()) return null;
		return rawPath.trim().toLowerCase();
	}

	public static double computeNextClipStartOffset(Timeline timeline) {
		if (timeline == null) return 0.0;
		var audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null || audioTrack.getClips().isEmpty()) return 0.0;
		double maxEnd = 0.0;
		for (var c : audioTrack.getClips()) {
			if (c != null) maxEnd = Math.max(maxEnd, c.getEndTimeSeconds());
		}
		return maxEnd;
	}

	public static Map<String, SavedFeatureTrack> saveFeatureEvents(Timeline timeline) {
		if (timeline == null) return Map.of();
		Map<String, FeatureTrack> tracks = timeline.getFeatureTracks();
		if (tracks == null || tracks.isEmpty()) return Map.of();
		Map<String, SavedFeatureTrack> result = new HashMap<>();
		for (Map.Entry<String, FeatureTrack> e : tracks.entrySet()) {
			result.put(e.getKey(), new SavedFeatureTrack(e.getValue().getLabel(), new ArrayList<>(e.getValue().getEvents())));
		}
		return result;
	}

	public static void shiftFeatureEventsByOffset(Timeline timeline, double offset) {
		if (offset <= 0 || timeline == null) return;
		Map<String, FeatureTrack> tracks = timeline.getFeatureTracks();
		if (tracks == null || tracks.isEmpty()) return;
		for (FeatureTrack ft : tracks.values()) {
			List<FeatureEvent> evts = new ArrayList<>(ft.getEvents());
			ft.clear();
			for (FeatureEvent e : evts) {
				ft.addEvent(new FeatureEvent(e.getTimeSeconds() + offset, e.getEnergy()));
			}
		}
	}

	public static void restoreFeatureEvents(Timeline timeline, Map<String, SavedFeatureTrack> saved) {
		if (timeline == null || saved == null || saved.isEmpty()) return;
		for (Map.Entry<String, SavedFeatureTrack> e : saved.entrySet()) {
			for (FeatureEvent fe : e.getValue().events()) {
				timeline.addFeatureEvent(e.getKey(), e.getValue().label(), fe);
			}
		}
	}

	public static double readClipOffset(Timeline timeline, String audioKey) {
		if (timeline == null || audioKey == null) return 0.0;
		Object raw = timeline.getMetadata("audioClipOffset_" + audioKey);
		if (raw instanceof Number n) return n.doubleValue();
		return 0.0;
	}

	public static AudioAsset findAssetByAudioKey(String audioKey) {
		if (audioKey == null) return null;
		for (AudioAsset asset : AudioAssetManager.getInstance().getAssets()) {
			String key = buildAudioAssetKey(asset);
			if (java.util.Objects.equals(key, audioKey)) {
				return asset;
			}
		}
		return null;
	}

	public static String buildBeatmapApplySignature(String audioKey, com.beatblock.audio.beatmap.Beatmap beatmap) {
		if (beatmap == null) return audioKey + "|none";
		String beatmapPath = beatmap.beatmapFilePath != null ? beatmap.beatmapFilePath.toString() : "";
		String generatedAt = (beatmap.meta != null && beatmap.meta.generatedAt() != null)
			? beatmap.meta.generatedAt() : "";
		return audioKey + "|" + beatmapPath + "|" + generatedAt;
	}
}
