package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 特征轨（音频 impulse ↔ 动画控制）行号索引。 */
public final class TimelineFeatureLaneIndex {

	private TimelineFeatureLaneIndex() {
	}

	public static Map<String, Integer> audioImpulseRows(List<TrackDefinition> audioSubTracks) {
		Map<String, Integer> rows = new HashMap<>();
		for (int slot = 0; slot < audioSubTracks.size(); slot++) {
			TrackDefinition td = audioSubTracks.get(slot);
			if (td.getVisualType() != TrackDefinition.VisualType.IMPULSE) continue;
			String key = td.getKey();
			if (key == null || key.isBlank()) continue;
			rows.put(key, TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot);
		}
		return rows;
	}

	public static Map<String, Integer> controlFeatureRows(List<TrackDefinition> animationSubTracks) {
		Map<String, Integer> rows = new HashMap<>();
		for (int slot = 0; slot < animationSubTracks.size(); slot++) {
			String key = Timeline.blockAnimationFeatureKeyFromTrackId(animationSubTracks.get(slot).getKey());
			if (key.isBlank()) continue;
			rows.put(key, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
		}
		return rows;
	}

	public static String hoveredFeatureKey(
		int hoveredRow,
		List<TrackDefinition> audioSubTracks,
		List<TrackDefinition> animationSubTracks
	) {
		if (TimelineTrackMeta.isAudioSubRow(hoveredRow)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(hoveredRow);
			if (slot >= 0 && slot < audioSubTracks.size()) {
				TrackDefinition td = audioSubTracks.get(slot);
				if (td.getVisualType() == TrackDefinition.VisualType.IMPULSE) {
					return td.getKey();
				}
			}
		} else if (TimelineTrackMeta.isAnimationFeatureSubRow(hoveredRow)) {
			int slot = TimelineTrackMeta.animationFeatureSubRowSlot(hoveredRow);
			if (slot >= 0 && slot < animationSubTracks.size()) {
				return Timeline.blockAnimationFeatureKeyFromTrackId(animationSubTracks.get(slot).getKey());
			}
		}
		return null;
	}
}
