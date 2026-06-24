package com.beatblock.timeline.rendering;

import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineAudioFeatureFillSupportTest {

	@Test
	void computeNextClipStartOffsetUsesMaxClipEnd() {
		Timeline timeline = Timeline.createDefault();
		TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 0, 3.0);
		TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 5.0, 8.0);

		assertEquals(8.0, TimelineAudioFeatureFillSupport.computeNextClipStartOffset(timeline), 1e-9);
	}

	@Test
	void shiftFeatureEventsByOffsetMovesEvents() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.5f));

		TimelineAudioFeatureFillSupport.shiftFeatureEventsByOffset(timeline, 2.0);

		assertEquals(3.0, timeline.getFeatureTracks().get("kick").getEvents().getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void normalizeAudioPathLowercasesAndTrims() {
		assertEquals("c:/music/track.wav", TimelineAudioFeatureFillSupport.normalizeAudioPath("  C:/Music/Track.WAV  "));
	}

	@Test
	void saveAndRestoreFeatureEventsRoundTrip() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("snare", new FeatureEvent(2.0, 0.7f));
		var saved = TimelineAudioFeatureFillSupport.saveFeatureEvents(timeline);
		timeline.clearFeatureTracks();
		assertTrue(timeline.getFeatureTracks().isEmpty());

		TimelineAudioFeatureFillSupport.restoreFeatureEvents(timeline, saved);

		assertEquals(1, timeline.getFeatureTracks().get("snare").getEvents().size());
		assertEquals(2.0, timeline.getFeatureTracks().get("snare").getEvents().getFirst().getTimeSeconds(), 1e-9);
	}

	@Test
	void buildBeatmapApplySignatureIncludesPathAndGeneratedAt() {
		com.beatblock.audio.beatmap.BeatmapMeta meta = new com.beatblock.audio.beatmap.BeatmapMeta(
			"song.wav", 60_000, 120, 1.0, "4/4", 44_100, "2026-01-01T00:00:00Z", "", null, null, null);
		com.beatblock.audio.beatmap.Beatmap beatmap = new com.beatblock.audio.beatmap.Beatmap(
			1, meta, java.util.List.of(), java.util.List.of(), null, null);
		beatmap.beatmapFilePath = java.nio.file.Path.of("C:/music/song.beatmap.json");

		String sig = TimelineAudioFeatureFillSupport.buildBeatmapApplySignature("c:/music/song.wav", beatmap);

		assertTrue(sig.startsWith("c:/music/song.wav|"));
		assertTrue(sig.endsWith("|2026-01-01T00:00:00Z"));
		assertTrue(sig.contains("song.beatmap.json"));
	}
}
