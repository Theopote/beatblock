package com.beatblock.engine;

import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockControlExecutorTest {

	private BlockControlExecutor executor;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		StageObjectSystem stageObjectSystem = new StageObjectSystem();
		executor = new BlockControlExecutor(stageObjectSystem);
		stageObjectSystem.register(StageObjectSystem.fromBlocks(
			"stage-a", "Stage", List.of(new BlockPos(2, 64, 2))));
	}

	@Test
	void planReportsInvalidInputWhenWorldOrEventMissing() {
		assertEquals(BlockControlExecutor.ControlSkipReason.INVALID_INPUT,
			executor.plan(null, null).skipReason());

		var event = new TimelineAnimationEvent(
			"ev", 0, 1, "clear", "stage-a", 1f,
			Map.of("actionMode", TimelineAnimationActionMode.CLEAR.name()));
		assertEquals(BlockControlExecutor.ControlSkipReason.INVALID_INPUT,
			executor.plan(null, event).skipReason());
	}

	@Test
	void applyMutationsIgnoresNullWorld() {
		executor.applyMutations(null, List.of());
		assertTrue(true);
	}
}
