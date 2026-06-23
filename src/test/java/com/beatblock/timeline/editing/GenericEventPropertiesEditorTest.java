package com.beatblock.timeline.editing;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericEventPropertiesEditorTest {

	@Test
	void buildUpdatedSnapshotParsesTimeAndParameters() {
		var result = GenericEventPropertiesEditor.buildUpdatedSnapshot(
			2.5,
			Map.of("energy", "0.8", "mode", "BURST"),
			Map.of("energy", true),
			1.0,
			4.0
		);
		assertInstanceOf(GenericEventPropertiesEditor.Result.Ok.class, result);
		AnimationEventSnapshot snapshot = ((GenericEventPropertiesEditor.Result.Ok) result).snapshot();
		assertEquals(2.5, snapshot.timeSeconds(), 1e-9);
		assertEquals(0.8, snapshot.parameters().get("energy"));
		assertEquals("BURST", snapshot.parameters().get("mode"));
		assertEquals(1.0, snapshot.clipStartSeconds(), 1e-9);
		assertEquals(4.0, snapshot.clipEndSeconds(), 1e-9);
	}

	@Test
	void buildUpdatedSnapshotRejectsInvalidNumber() {
		var result = GenericEventPropertiesEditor.buildUpdatedSnapshot(
			0.0,
			Map.of("energy", "not-a-number"),
			Map.of("energy", true),
			0.0,
			1.0
		);
		assertInstanceOf(GenericEventPropertiesEditor.Result.Err.class, result);
	}

	@Test
	void buildUpdatedSnapshotClampsNegativeTime() {
		var result = GenericEventPropertiesEditor.buildUpdatedSnapshot(
			-1.0, Map.of(), Map.of(), 0.0, 1.0
		);
		AnimationEventSnapshot snapshot = ((GenericEventPropertiesEditor.Result.Ok) result).snapshot();
		assertTrue(snapshot.timeSeconds() >= 0.0);
	}
}
