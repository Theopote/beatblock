package com.beatblock.selection;

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

	private static final int STONE = 1;
	private static final int DIRT = 2;

	@Test
	void floodFillSelectsSameMaterialNeighbors() {
		Map<BlockPos, Integer> grid = new HashMap<>();
		grid.put(new BlockPos(0, 64, 0), STONE);
		grid.put(new BlockPos(1, 64, 0), STONE);
		grid.put(new BlockPos(2, 64, 0), STONE);
		grid.put(new BlockPos(1, 64, 1), DIRT);

		var result = ConnectedSelectionFloodFill.collect(
			ConnectedCellLookup.fromMaterialGrid(grid),
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
		Map<BlockPos, Integer> grid = new HashMap<>();
		grid.put(new BlockPos(0, 64, 0), STONE);
		grid.put(new BlockPos(1, 64, 0), STONE);
		grid.put(new BlockPos(2, 64, 0), STONE);

		var result = ConnectedSelectionFloodFill.collect(
			ConnectedCellLookup.fromMaterialGrid(grid),
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
		Map<BlockPos, Integer> grid = new HashMap<>();
		for (int x = 0; x < 5; x++) {
			grid.put(new BlockPos(x, 64, 0), STONE);
		}

		var result = ConnectedSelectionFloodFill.collect(
			ConnectedCellLookup.fromMaterialGrid(grid),
			ConnectedSelectionFloodFill.Request.unbounded(
				new BlockPos(0, 64, 0), false, false, 3, 0)
		);

		assertEquals(3, result.blocks().size());
		assertTrue(result.truncated());
	}

	@Test
	void spreadLimitRestrictsDistanceFromSeed() {
		Map<BlockPos, Integer> grid = new HashMap<>();
		for (int x = 0; x < 4; x++) {
			grid.put(new BlockPos(x, 64, 0), STONE);
		}

		var result = ConnectedSelectionFloodFill.collect(
			ConnectedCellLookup.fromMaterialGrid(grid),
			ConnectedSelectionFloodFill.Request.unbounded(
				new BlockPos(0, 64, 0), false, false, 100, 1)
		);

		assertEquals(2, result.blocks().size());
	}

	@Test
	void excludeAirSkipsAirNeighbors() {
		Map<BlockPos, Integer> grid = new HashMap<>();
		grid.put(new BlockPos(0, 64, 0), STONE);
		grid.put(new BlockPos(0, 64, 1), STONE);

		var result = ConnectedSelectionFloodFill.collect(
			ConnectedCellLookup.fromMaterialGrid(grid),
			ConnectedSelectionFloodFill.Request.unbounded(
				new BlockPos(0, 64, 0), false, false, 100, 0)
		);

		assertEquals(1, result.blocks().size());
	}
}
