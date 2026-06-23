package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioTrackDataTest {

	@Test
	void featureTracksStaySortedByTime() {
		AudioTrackData data = new AudioTrackData();
		data.addFeatureEvent("kick", "Kick", new FeatureEvent(2.0, 0.8f));
		data.addFeatureEvent("kick", new FeatureEvent(0.5, 0.3f));
		data.addFeatureEvent("kick", new FeatureEvent(1.0, 0.5f));

		FeatureTrack track = data.getFeatureTrack("kick");
		assertEquals("Kick", track.getLabel());
		assertEquals(3, track.size());
		assertEquals(0.5, track.getEvents().get(0).getTimeSeconds(), 1e-9);
		assertEquals(2.0, track.getEvents().get(2).getTimeSeconds(), 1e-9);
	}

	@Test
	void stemWaveformsAndClearAll() {
		AudioTrackData data = new AudioTrackData();
		data.setWaveform(new WaveformData(new float[]{0.5f}, 1.0, 44100));
		data.setStemWaveform("drums", new WaveformData(new float[]{0.1f}, 1.0, 44100));
		data.addFeatureEvent("snare", new FeatureEvent(1.0, 0.5f));

		assertTrue(data.hasFeatureTracks());
		assertTrue(data.hasStemWaveforms());
		assertEquals(1, data.getStemWaveformKeys().size());

		data.clearAll();
		assertFalse(data.hasFeatureTracks());
		assertFalse(data.hasStemWaveforms());
	}

	@Test
	void timelineDelegatesFeatureEventsToAudioTrack() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("hihat", new FeatureEvent(0.25, 0.4f));

		assertTrue(timeline.hasFeatureTracks());
		assertEquals(1, timeline.getFeatureEvents("hihat").size());
		assertEquals(0.4f, timeline.getFeatureEvents("hihat").getFirst().getEnergy(), 1e-6);
	}
}
