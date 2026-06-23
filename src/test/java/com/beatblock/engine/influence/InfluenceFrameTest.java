package com.beatblock.engine.influence;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceFrameTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void accumulatesAnimatedBlocksMutationsAndVfx() {
		InfluenceFrame frame = new InfluenceFrame();
		BlockPos pos = new BlockPos(0, 64, 0);

		AnimatedBlock block = frame.animatedBlockFor(pos);
		block.setScale(1.2f);
		frame.addWorldMutation(new BlockControlExecutor.BlockMutation(
			pos, Blocks.STONE.getDefaultState(), Blocks.AIR.getDefaultState()));
		frame.addVfxTrigger(new VfxTrigger("pulse", pos, 0.0, 0.5f));

		assertEquals(1.2f, frame.getAnimatedBlocks().get(pos).getScale(), 1e-6);
		assertEquals(1, frame.getWorldMutations().size());
		assertEquals(1, frame.getVfxTriggers().size());
		assertFalse(frame.isEmpty());
	}

	@Test
	void ignoresNullEntries() {
		InfluenceFrame frame = new InfluenceFrame();
		frame.addWorldMutation(null);
		frame.addVfxTrigger(null);
		assertTrue(frame.isEmpty());
	}
}
