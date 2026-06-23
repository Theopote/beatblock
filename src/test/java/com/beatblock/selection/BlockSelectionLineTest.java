package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockSelectionLineTest {

	@Test
	void betweenIncludesEndpointsAndIntermediateCells() {
		List<BlockPos> line = BlockSelectionLine.between(new BlockPos(0, 64, 0), new BlockPos(3, 64, 0));
		assertFalse(line.isEmpty());
		assertEquals(new BlockPos(0, 64, 0), line.getFirst());
		assertEquals(new BlockPos(3, 64, 0), line.getLast());
		assertTrue(line.size() >= 2);
	}

	@Test
	void betweenSamePointReturnsSingleBlock() {
		BlockPos p = new BlockPos(1, 64, 2);
		List<BlockPos> line = BlockSelectionLine.between(p, p);
		assertEquals(1, line.size());
		assertEquals(p, line.getFirst());
	}

	@Test
	void thickSegmentIncludesBlocksWithinRadius() {
		List<BlockPos> thick = BlockSelectionLine.blocksForSegment(
			new BlockPos(0, 64, 0),
			new BlockPos(4, 64, 0),
			1,
			10_000
		);
		assertTrue(thick.size() > 5);
		Set<BlockPos> unique = new HashSet<>(thick);
		assertEquals(thick.size(), unique.size());
	}

	@Test
	void returnsNullWhenCandidateCountExceedsMaxBlocks() {
		assertNull(BlockSelectionLine.blocksForSegment(
			new BlockPos(0, 64, 0),
			new BlockPos(40, 64, 0),
			3,
			20
		));
	}

	@Test
	void zeroRadiusUsesThinCenterlineOnly() {
		List<BlockPos> thin = BlockSelectionLine.blocksForSegment(
			new BlockPos(0, 64, 0),
			new BlockPos(5, 64, 0),
			0,
			10_000
		);
		assertEquals(BlockSelectionLine.between(new BlockPos(0, 64, 0), new BlockPos(5, 64, 0)), thin);
	}
}
