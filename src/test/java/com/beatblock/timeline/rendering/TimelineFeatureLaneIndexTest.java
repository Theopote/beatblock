package com.beatblock.timeline.rendering;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimelineFeatureLaneIndexTest {

	private static TrackDefinition impulse(String key) {
		return new TrackDefinition(key, key, TrackDefinition.VisualType.IMPULSE, TrackDefinition.GROUP_NONE);
	}

	private static TrackDefinition animControl(String trackId) {
		return new TrackDefinition(trackId, trackId, TrackDefinition.VisualType.ANIMATION_CLIP, TrackDefinition.GROUP_RHYTHM);
	}

	@Test
	void audioImpulseRowsMapsSlotToRowIndex() {
		List<TrackDefinition> audio = List.of(
			new TrackDefinition("waveform", "主混音", TrackDefinition.VisualType.WAVEFORM, TrackDefinition.GROUP_NONE),
			impulse("kick"));

		Map<String, Integer> rows = TimelineFeatureLaneIndex.audioImpulseRows(audio);

		assertEquals(TimelineTrackMeta.ROW_AUDIO_SUBS_START + 1, rows.get("kick"));
	}

	@Test
	void hoveredFeatureKeyFromAudioImpulseRow() {
		List<TrackDefinition> audio = List.of(
			new TrackDefinition("waveform", "主混音", TrackDefinition.VisualType.WAVEFORM, TrackDefinition.GROUP_NONE),
			impulse("snare"));
		int snareRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START + 1;

		assertEquals("snare", TimelineFeatureLaneIndex.hoveredFeatureKey(snareRow, audio, List.of()));
		assertNull(TimelineFeatureLaneIndex.hoveredFeatureKey(TimelineTrackMeta.ROW_AUDIO_SUBS_START, audio, List.of()));
	}

	@Test
	void controlFeatureRowsMapsAnimationTrackId() {
		List<TrackDefinition> anim = List.of(animControl("animation_block_feature_hihat"));

		Map<String, Integer> rows = TimelineFeatureLaneIndex.controlFeatureRows(anim);

		assertEquals(TimelineTrackMeta.ROW_ANIM_FEATURES_START, rows.get("hihat"));
	}
}
