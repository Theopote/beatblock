package com.beatblock.engine.influence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearancePulseTrackerTest {

	@Test
	void crossedMidpointDetectsFirstHalf() {
		assertFalse(AppearancePulseTracker.crossedMidpoint(0.4f, 0.3f));
		assertTrue(AppearancePulseTracker.crossedMidpoint(0.5f, 0.49f));
		assertFalse(AppearancePulseTracker.crossedMidpoint(0.6f, 0.55f));
	}
}
