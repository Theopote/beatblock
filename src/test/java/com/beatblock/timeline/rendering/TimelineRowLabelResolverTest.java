package com.beatblock.timeline.rendering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineRowLabelResolverTest {

	private static TrackDefinition waveform(String key, String displayName) {
		return new TrackDefinition(key, displayName, TrackDefinition.VisualType.WAVEFORM, TrackDefinition.GROUP_NONE);
	}

	private static TrackDefinition impulse(String key) {
		return new TrackDefinition(key, key, TrackDefinition.VisualType.IMPULSE, TrackDefinition.GROUP_NONE);
	}

	private static TrackDefinition animControl(String trackId, String displayName) {
		return new TrackDefinition(trackId, displayName, TrackDefinition.VisualType.ANIMATION_CLIP, TrackDefinition.GROUP_RHYTHM);
	}

	@Test
	void customDisplayNameOverridesDefault() {
		TimelineTrackListState state = new TimelineTrackListState();
		int row = TimelineTrackMeta.ROW_CAMERA;
		state.setCustomName(row, "我的相机");

		String name = TimelineRowLabelResolver.resolveDisplayName(row, state, List.of(), List.of());

		assertEquals("我的相机", name);
	}

	@Test
	void impulseAudioSubRowUsesLocalizedFeatureSuffix() {
		List<TrackDefinition> audio = List.of(waveform("waveform", "主混音"), impulse("kick"));
		int kickRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START + 1;

		assertEquals("底鼓 特征", TimelineRowLabelResolver.resolveDisplayName(
			kickRow, null, audio, List.of()));
		assertEquals("节奏特征", TimelineRowLabelResolver.resolveTypeLabel(kickRow, audio, List.of()));
	}

	@Test
	void stemWaveformRowTypeLabelIsAudio() {
		List<TrackDefinition> audio = List.of(waveform("waveform", "主混音"), waveform("stem_wf_drums", "Drums"));
		int stemRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START + 1;

		assertEquals("音频", TimelineRowLabelResolver.resolveTypeLabel(stemRow, audio, List.of()));
	}

	@Test
	void animationFeatureSubRowUsesControlSuffix() {
		List<TrackDefinition> anim = List.of(
			animControl("animation_block_feature_kick", "Kick Control"));
		int row = TimelineTrackMeta.ROW_ANIM_FEATURES_START;

		assertEquals("底鼓 控制", TimelineRowLabelResolver.resolveDisplayName(row, null, List.of(), anim));
		assertEquals("动画控制", TimelineRowLabelResolver.resolveTypeLabel(row, List.of(), anim));
	}

	@Test
	void audioGroupTypeLabel() {
		assertEquals("音频片段", TimelineRowLabelResolver.resolveTypeLabel(
			TimelineTrackMeta.ROW_AUDIO_GROUP, List.of(), List.of()));
	}
}
