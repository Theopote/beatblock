package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureEventTest {

	@Test
	void clampsEnergyToUnitRange() {
		assertEquals(1f, new FeatureEvent(1.0, 2f).getEnergy(), 1e-6);
		assertEquals(0f, new FeatureEvent(-1.0, -0.5f).getEnergy(), 1e-6);
		assertEquals(2.5, new FeatureEvent(2.5, 0.5f).getTimeSeconds(), 1e-9);
	}
}
