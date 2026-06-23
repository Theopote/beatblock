package com.beatblock.timeline.generation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacingModeTest {

	@Test
	void fromValueParsesAliases() {
		assertEquals(PacingMode.DISTANCE, PacingMode.fromValue("dist"));
		assertEquals(PacingMode.FIXED_INTERVAL, PacingMode.fromValue("interval"));
		assertEquals(PacingMode.BEAT_GRID, PacingMode.fromValue("auto"));
	}

	@Test
	void fromParamsPrefersExplicitMode() {
		assertEquals(PacingMode.DISTANCE,
			PacingMode.fromParams(Map.of("pacingMode", "DISTANCE"), true));
	}

	@Test
	void fromParamsFallsBackToBeatGridOrFixedInterval() {
		assertEquals(PacingMode.BEAT_GRID, PacingMode.fromParams(null, true));
		assertEquals(PacingMode.FIXED_INTERVAL, PacingMode.fromParams(null, false));
	}
}
