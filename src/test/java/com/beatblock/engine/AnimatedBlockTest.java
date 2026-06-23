package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimatedBlockTest {

	@Test
	void resetToOriginalRestoresDefaults() {
		AnimatedBlock block = new AnimatedBlock(new BlockPos(2, 64, 3));
		block.setPosition(10, 20, 30);
		block.setScale(2f);
		block.setRotationYaw(45f);

		block.resetToOriginal();
		assertEquals(2.5, block.getPosition().x, 1e-6);
		assertEquals(64.0, block.getPosition().y, 1e-6);
		assertEquals(3.5, block.getPosition().z, 1e-6);
		assertEquals(1f, block.getScale(), 1e-6);
		assertEquals(0f, block.getRotationYaw(), 1e-6);
	}

	@Test
	void scaleClampsToMinimum() {
		AnimatedBlock block = new AnimatedBlock(BlockPos.ORIGIN);
		block.setScale(0f);
		assertEquals(0.01f, block.getScale(), 1e-6);
	}
}
