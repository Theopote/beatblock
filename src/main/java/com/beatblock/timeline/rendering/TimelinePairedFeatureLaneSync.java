package com.beatblock.timeline.rendering;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 同步配对特征轨（音频 impulse ↔ 动画控制）的可见性。 */
public final class TimelinePairedFeatureLaneSync {

	private record PairVisibilitySnapshot(boolean audioVisible, boolean controlVisible) {}

	private final java.util.Map<String, PairVisibilitySnapshot> pairedFeatureVisibility = new java.util.HashMap<>();

	public void sync(
		TimelineTrackListState trackListState,
		List<TrackDefinition> audioSubTracks,
		List<TrackDefinition> animationSubTracks
	) {
		if (trackListState == null) return;

		Map<String, Integer> audioRowsByFeature = TimelineFeatureLaneIndex.audioImpulseRows(audioSubTracks);
		Map<String, Integer> controlRowsByFeature = TimelineFeatureLaneIndex.controlFeatureRows(animationSubTracks);

		Set<String> pairedKeys = new HashSet<>(audioRowsByFeature.keySet());
		pairedKeys.retainAll(controlRowsByFeature.keySet());
		pairedFeatureVisibility.keySet().retainAll(pairedKeys);

		for (String key : pairedKeys) {
			int audioRow = audioRowsByFeature.get(key);
			int controlRow = controlRowsByFeature.get(key);
			boolean audioVisible = trackListState.isVisible(audioRow);
			boolean controlVisible = trackListState.isVisible(controlRow);
			PairVisibilitySnapshot previous = pairedFeatureVisibility.get(key);

			if (audioVisible != controlVisible) {
				if (previous == null) {
					trackListState.setVisible(controlRow, audioVisible);
				} else {
					boolean audioChanged = previous.audioVisible() != audioVisible;
					boolean controlChanged = previous.controlVisible() != controlVisible;
					if (audioChanged && !controlChanged) {
						trackListState.setVisible(controlRow, audioVisible);
					} else if (controlChanged && !audioChanged) {
						trackListState.setVisible(audioRow, controlVisible);
					} else {
						trackListState.setVisible(controlRow, audioVisible);
					}
				}
			}

			pairedFeatureVisibility.put(key, new PairVisibilitySnapshot(
				trackListState.isVisible(audioRow),
				trackListState.isVisible(controlRow)
			));
		}
	}
}
