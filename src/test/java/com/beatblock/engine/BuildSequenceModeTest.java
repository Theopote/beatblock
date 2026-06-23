package com.beatblock.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildSequenceModeTest {

	@Test
	void fromValueParsesKnownModes() {
		assertEquals(BuildSequenceMode.TOWER, BuildSequenceMode.fromValue("tower"));
		assertEquals(BuildSequenceMode.DISSOLVE, BuildSequenceMode.fromValue("DISSOLVE"));
	}

	@Test
	void fromValueDefaultsToWall() {
		assertEquals(BuildSequenceMode.WALL, BuildSequenceMode.fromValue(null));
		assertEquals(BuildSequenceMode.WALL, BuildSequenceMode.fromValue("invalid"));
	}
}
