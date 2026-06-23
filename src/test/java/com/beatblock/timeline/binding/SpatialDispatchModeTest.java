package com.beatblock.timeline.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialDispatchModeTest {

	@Test
	void fromValueParsesKnownModes() {
		assertEquals(SpatialDispatchMode.RADIAL, SpatialDispatchMode.fromValue("radial"));
		assertEquals(SpatialDispatchMode.SPIRAL, SpatialDispatchMode.fromValue("SPIRAL"));
	}

	@Test
	void fromValueDefaultsToAll() {
		assertEquals(SpatialDispatchMode.ALL, SpatialDispatchMode.fromValue(null));
		assertEquals(SpatialDispatchMode.ALL, SpatialDispatchMode.fromValue("invalid"));
	}
}
