package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;

import java.util.List;

/** 时间线轨道行显示名与类型标签解析。 */
public final class TimelineRowLabelResolver {

	private TimelineRowLabelResolver() {
	}

	public static String resolveDisplayName(
		int rowIndex,
		TimelineTrackListState trackListState,
		List<TrackDefinition> audioSubTracks,
		List<TrackDefinition> animationSubTracks
	) {
		if (trackListState != null) {
			String custom = trackListState.getDisplayName(rowIndex);
			String fallback = TimelineTrackMeta.getDefaultName(rowIndex);
			boolean isCustom = !custom.equals(fallback) && !custom.isEmpty();
			if (isCustom) return custom;
		}
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
			if (slot >= 0 && slot < audioSubTracks.size()) {
				TrackDefinition td = audioSubTracks.get(slot);
				if (td.getVisualType() == TrackDefinition.VisualType.IMPULSE) {
					String key = td.getKey();
					return TrackRegistry.localizedName(key) + " 特征";
				}
				return td.getDisplayName();
			}
		}
		if (TimelineTrackMeta.isAnimationFeatureSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.animationFeatureSubRowSlot(rowIndex);
			if (slot >= 0 && slot < animationSubTracks.size()) {
				String featureKey = Timeline.blockAnimationFeatureKeyFromTrackId(animationSubTracks.get(slot).getKey());
				String base = !featureKey.isBlank()
					? TrackRegistry.localizedName(featureKey)
					: animationSubTracks.get(slot).getDisplayName();
				return base + " 控制";
			}
		}
		return trackListState != null
			? trackListState.getDisplayName(rowIndex)
			: TimelineTrackMeta.getDefaultName(rowIndex);
	}

	public static String resolveTypeLabel(
		int rowIndex,
		List<TrackDefinition> audioSubTracks,
		List<TrackDefinition> animationSubTracks
	) {
		if (rowIndex == TimelineTrackMeta.ROW_AUDIO_GROUP) return "音频片段";
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
			if (slot >= 0 && slot < audioSubTracks.size()) {
				String key = audioSubTracks.get(slot).getKey();
				if ("waveform".equals(key) || (key != null && key.startsWith("stem_wf_"))) {
					return "音频";
				}
				return "节奏特征";
			}
		}
		if (TimelineTrackMeta.isAnimationFeatureSubRow(rowIndex)) {
			return "动画控制";
		}
		return TimelineTrackMeta.getCategoryTypeLabel(rowIndex);
	}
}
