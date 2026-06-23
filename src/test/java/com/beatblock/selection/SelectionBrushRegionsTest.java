package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelectionBrushRegionsTest {

	@Test
	void sphereRadiusOneHasSevenVoxels() {
		List<BlockPos> positions = SelectionBrushRegions.spherePositions(
			new BlockPos(0, 64, 0), 1, 100);
		assertEquals(7, positions.size());
		assertEquals(7, new HashSet<>(positions).size());
	}

	@Test
	void cubeRadiusOneHasTwentySevenVoxels() {
		List<BlockPos> positions = SelectionBrushRegions.cubePositions(
			new BlockPos(0, 64, 0), 1, 100);
		assertEquals(27, positions.size());
	}

	@Test
	void returnsNullWhenEnvelopeExceedsMaxBlocks() {
		assertNull(SelectionBrushRegions.spherePositions(new BlockPos(0, 64, 0), 5, 10));
		assertNull(SelectionBrushRegions.cubePositions(new BlockPos(0, 64, 0), 5, 10));
	}

	@Test
	void returnsEmptyForInvalidInput() {
		assertEquals(0, SelectionBrushRegions.spherePositions(null, 1, 10).size());
		assertEquals(0, SelectionBrushRegions.cubePositions(new BlockPos(0, 64, 0), -1, 10).size());
	}
}
