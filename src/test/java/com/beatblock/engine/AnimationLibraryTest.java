package com.beatblock.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationLibraryTest {

	@Test
	void registersBuiltInPresets() {
		AnimationLibrary library = new AnimationLibrary();
		assertTrue(library.getAll().size() >= 10);
		assertNotNull(library.get("Pulse"));
		assertNotNull(library.get("BlockJump"));
	}

	@Test
	void customDefinitionCanBeRegistered() {
		AnimationLibrary library = new AnimationLibrary();
		var preset = com.beatblock.engine.influence.BlockInfluencePresets.get("Pulse");
		library.register(new AnimationDefinition(preset));
		assertEquals("Pulse", library.get("Pulse").getId());
	}
}
