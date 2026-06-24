package com.beatblock.timeline.rendering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelinePairedFeatureLaneSyncTest {

	private static TrackDefinition impulse(String key) {
		return new TrackDefinition(key, key, TrackDefinition.VisualType.IMPULSE, TrackDefinition.GROUP_NONE);
	}

	private static TrackDefinition animControl(String trackId) {
		return new TrackDefinition(trackId, trackId, TrackDefinition.VisualType.ANIMATION_CLIP, TrackDefinition.GROUP_RHYTHM);
	}

	@Test
	void hidesControlRowWhenAudioFeatureHidden() {
		TimelineTrackListState state = new TimelineTrackListState();
		List<TrackDefinition> audio = List.of(
			new TrackDefinition("waveform", "主混音", TrackDefinition.VisualType.WAVEFORM, TrackDefinition.GROUP_NONE),
			impulse("kick"));
		List<TrackDefinition> anim = List.of(animControl("animation_block_feature_kick"));
		int audioRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START + 1;
		int controlRow = TimelineTrackMeta.ROW_ANIM_FEATURES_START;

		state.setVisible(audioRow, false);
		state.setVisible(controlRow, true);

		new TimelinePairedFeatureLaneSync().sync(state, audio, anim);

		assertFalse(state.isVisible(audioRow));
		assertFalse(state.isVisible(controlRow));
	}

	@Test
	void showsControlRowWhenAudioFeatureShown() {
		TimelineTrackListState state = new TimelineTrackListState();
		List<TrackDefinition> audio = List.of(
			new TrackDefinition("waveform", "主混音", TrackDefinition.VisualType.WAVEFORM, TrackDefinition.GROUP_NONE),
			impulse("kick"));
		List<TrackDefinition> anim = List.of(animControl("animation_block_feature_kick"));
		int audioRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START + 1;
		int controlRow = TimelineTrackMeta.ROW_ANIM_FEATURES_START;

		state.setVisible(audioRow, true);
		state.setVisible(controlRow, false);

		new TimelinePairedFeatureLaneSync().sync(state, audio, anim);

		assertTrue(state.isVisible(audioRow));
		assertTrue(state.isVisible(controlRow));
	}
}
