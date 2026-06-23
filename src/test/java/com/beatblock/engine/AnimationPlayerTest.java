package com.beatblock.engine;

import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationPlayerTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void updateAppliesPresetToTargetBlocks() {
		AnimationPlayer player = new AnimationPlayer();
		var def = new AnimationDefinition(BlockInfluencePresets.get("Pulse"));
		var target = StageObjectSystem.fromBlocks("s1", "Stage", List.of(new BlockPos(0, 64, 0)));
		player.addInstance(new EngineAnimationInstance(def, target, 0, 1, 1f));

		player.update(0.5);
		assertEquals(1, player.getCurrentFrameBlocks().size());
		AnimatedBlock block = player.getCurrentFrameBlocks().values().iterator().next();
		assertTrue(block.getScale() > 1f);
	}

	@Test
	void removeEndedDropsInactiveInstances() {
		AnimationPlayer player = new AnimationPlayer();
		var def = new AnimationDefinition(BlockInfluencePresets.get("Pulse"));
		var target = StageObjectSystem.fromBlocks("s1", "Stage", List.of(new BlockPos(0, 64, 0)));
		player.addInstance(new EngineAnimationInstance(def, target, 0, 1, 1f));

		player.removeEnded(2.0);
		assertTrue(player.getActiveInstances().isEmpty());
	}
}
