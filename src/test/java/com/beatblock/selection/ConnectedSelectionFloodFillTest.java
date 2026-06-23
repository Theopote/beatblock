package com.beatblock.selection;

import com.beatblock.testutil.TestBlockStates;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedSelectionFloodFillTest {

	private static BlockStateLookup lookup(Map<BlockPos, BlockState> states) {
		return ConnectedSelectionFloodFill.fromStates(states, TestBlockStates.air());
	}

	@Test
	void floodFillSelectsSameBlockTypeNeighbors() {
		Block block = TestBlockStates.mockBlock("stone");
		BlockState stone = TestBlockStates.ofBlock(block);
		BlockState dirt = TestBlockStates.ofBlock("dirt");
		Map<BlockPos, BlockState> states = new HashMap<>();
		states.put(new BlockPos(0, 64, 0), stone);
		states.put(new BlockPos(1, 64, 0), TestBlockStates.ofBlock(block));
		states.put(new BlockPos(2, 64, 0), TestBlockStates.ofBlock(block));
		states.put(new BlockPos(1, 64, 1), dirt);

		var result = ConnectedSelectionFloodFill.collect(
			lookup(states),
			ConnectedSelectionFloodFill.Request.unbounded(
				new BlockPos(0, 64, 0), false, false, 100, 0)
		);

		assertEquals(3, result.blocks().size());
		assertFalse(result.truncated());
		assertTrue(new HashSet<>(result.blocks()).containsAll(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0)
		)));
	}

	@Test
	void boundedRequestClipsToCuboid() {
		Block block = TestBlockStates.mockBlock("stone");
		BlockState stone = TestBlockStates.ofBlock(block);
		Map<BlockPos, BlockState> states = new HashMap<>();
		states.put(new BlockPos(0, 64, 0), stone);
		states.put(new BlockPos(1, 64, 0), TestBlockStates.ofBlock(block));
		states.put(new BlockPos(2, 64, 0), TestBlockStates.ofBlock(block));

		var result = ConnectedSelectionFloodFill.collect(
			lookup(states),
			new ConnectedSelectionFloodFill.Request(
				new BlockPos(0, 64, 0),
				new BlockPos(0, 64, 0),
				new BlockPos(1, 64, 0),
				false,
				false,
				100,
				0,
				null
			)
		);

		assertEquals(2, result.blocks().size());
	}

	@Test
	void maxBlocksTruncatesExpansion() {
		Block block = TestBlockStates.mockBlock("stone");
		Map<BlockPos, BlockState> states = new HashMap<>();
		for (int x = 0; x < 5; x++) {
			states.put(new BlockPos(x, 64, 0), TestBlockStates.ofBlock(block));
		}

		var result = ConnectedSelectionFloodFill.collect(
			lookup(states),
			ConnectedSelectionFloodFill.Request.unbounded(
				new BlockPos(0, 64, 0), false, false, 3, 0)
		);

		assertEquals(3, result.blocks().size());
		assertTrue(result.truncated());
	}

	@Test
	void spreadLimitRestrictsDistanceFromSeed() {
		Block block = TestBlockStates.mockBlock("stone");
		Map<BlockPos, BlockState> states = new HashMap<>();
		for (int x = 0; x < 4; x++) {
			states.put(new BlockPos(x, 64, 0), TestBlockStates.ofBlock(block));
		}

		var result = ConnectedSelectionFloodFill.collect(
			lookup(states),
			ConnectedSelectionFloodFill.Request.unbounded(
				new BlockPos(0, 64, 0), false, false, 100, 1)
		);

		assertEquals(2, result.blocks().size());
	}

	@Test
	void excludeAirSkipsAirNeighbors() {
		Block block = TestBlockStates.mockBlock("stone");
		BlockState stone = TestBlockStates.ofBlock(block);
		BlockState air = TestBlockStates.air();
		Map<BlockPos, BlockState> states = new HashMap<>();
		states.put(new BlockPos(0, 64, 0), stone);
		states.put(new BlockPos(1, 64, 0), air);
		states.put(new BlockPos(0, 64, 1), TestBlockStates.ofBlock(block));

		var result = ConnectedSelectionFloodFill.collect(
			lookup(states),
			ConnectedSelectionFloodFill.Request.unbounded(
				new BlockPos(0, 64, 0), false, false, 100, 0)
		);

		assertEquals(1, result.blocks().size());
	}
}
