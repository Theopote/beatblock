package com.beatblock.engine;

import com.beatblock.engine.influence.BlockInfluencePresets;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineAnimationInstanceTest {

	@Test
	void progressInterpolatesAcrossDuration() {
		var def = new AnimationDefinition(BlockInfluencePresets.get("Pulse"));
		var target = StageObjectSystem.fromBlocks("s1", "Stage", List.of(new BlockPos(0, 64, 0)));
		var instance = new EngineAnimationInstance(def, target, 2.0, 4.0, 1f);

		assertFalse(instance.isActiveAt(1.9));
		assertTrue(instance.isActiveAt(2.0));
		assertEquals(0f, instance.getProgress(1.0), 1e-6);
		assertEquals(0.5f, instance.getProgress(3.0), 1e-6);
		assertEquals(1f, instance.getProgress(5.0), 1e-6);
	}

	@Test
	void clampsEnergyAndPreservesExtraParams() {
		var def = new AnimationDefinition(BlockInfluencePresets.get("Pulse"));
		var target = StageObjectSystem.fromBlocks("s1", "Stage", List.of(new BlockPos(0, 64, 0)));
		var instance = new EngineAnimationInstance(
			def, target, 0, 1, 5f, Map.of("buildMode", "wall"));

		assertEquals(1f, instance.getEnergy(), 1e-6);
		assertEquals("wall", instance.getExtraParams().get("buildMode"));
	}
}
