package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerTypeTest {

	@Test
	void fromNameResolvesKnownTypes() {
		assertEquals(MarkerType.DROP, MarkerType.fromName("drop"));
		assertEquals(MarkerType.CAMERA, MarkerType.fromName("CAMERA"));
	}

	@Test
	void fromNameDefaultsToGeneric() {
		assertEquals(MarkerType.GENERIC, MarkerType.fromName(null));
		assertEquals(MarkerType.GENERIC, MarkerType.fromName("unknown"));
	}

	@Test
	void displayNamesCoversAllValues() {
		String[] names = MarkerType.displayNames();
		assertEquals(MarkerType.values().length, names.length);
		assertTrue(names[0].length() > 0);
	}
}
