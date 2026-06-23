package com.beatblock.timeline.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatGridPacingTest {

	@Test
	void firstBeatIndexAtOrAfterFindsLowerBound() {
		double[] beats = {0.5, 1.0, 1.5, 2.0};
		assertEquals(0, BeatGridPacing.firstBeatIndexAtOrAfter(0.0, beats));
		assertEquals(2, BeatGridPacing.firstBeatIndexAtOrAfter(1.25, beats));
		assertEquals(4, BeatGridPacing.firstBeatIndexAtOrAfter(3.0, beats));
	}

	@Test
	void computeTimestampsUsesReferenceBeats() {
		double[] beats = {1.0, 2.0, 3.0, 4.0};
		var request = new PacingRequest(3, 0.5, false, beats, 120, 0.5);
		var timestamps = PacingStrategy.beatGrid().computeTimestamps(request);

		assertEquals(3, timestamps.size());
		assertEquals(1.0, timestamps.get(0), 1e-9);
		assertEquals(2.0, timestamps.get(1), 1e-9);
		assertEquals(3.0, timestamps.get(2), 1e-9);
	}

	@Test
	void computeTimestampsCanStartImmediatelyAtAnchor() {
		var request = new PacingRequest(2, 2.5, true, new double[]{1.0, 3.0}, 120, 0.5);
		var timestamps = PacingStrategy.fixedInterval().computeTimestamps(request);

		assertEquals(2, timestamps.size());
		assertEquals(2.5, timestamps.get(0), 1e-9);
		assertTrue(timestamps.get(1) > timestamps.get(0));
	}
}
