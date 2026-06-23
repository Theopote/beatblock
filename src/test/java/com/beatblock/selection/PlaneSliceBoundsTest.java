package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaneSliceBoundsTest {

	private static final int BOTTOM = -64;
	private static final int TOP = 319;
	private static final ChunkPos CHUNK = new ChunkPos(0, 0);

	@Test
	void horizontalSliceUsesChunkWhenNoSelection() {
		BlockPos hit = new BlockPos(3, 64, 5);
		PlaneSliceBounds bounds = PlaneSliceBounds.compute(
			hit, Direction.UP, null, null, CHUNK, BOTTOM, TOP);

		assertFalse(bounds.isEmpty());
		assertEquals(0, bounds.minX());
		assertEquals(15, bounds.maxX());
		assertEquals(64, bounds.minY());
		assertEquals(64, bounds.maxY());
		assertEquals(0, bounds.minZ());
		assertEquals(15, bounds.maxZ());
		assertEquals(16 * 16, bounds.volume());
	}

	@Test
	void horizontalSliceClipsToSelectionBounds() {
		BlockPos hit = new BlockPos(2, 64, 2);
		BlockPos selMin = new BlockPos(1, 63, 1);
		BlockPos selMax = new BlockPos(3, 65, 4);
		PlaneSliceBounds bounds = PlaneSliceBounds.compute(
			hit, Direction.UP, selMin, selMax, CHUNK, BOTTOM, TOP);

		assertEquals(1, bounds.minX());
		assertEquals(3, bounds.maxX());
		assertEquals(64, bounds.minY());
		assertEquals(64, bounds.maxY());
		assertEquals(1, bounds.minZ());
		assertEquals(4, bounds.maxZ());
		assertEquals(3L * 4L, bounds.volume());
	}

	@Test
	void returnsEmptyWhenSliceOutsideSelectionHeight() {
		BlockPos hit = new BlockPos(2, 70, 2);
		BlockPos selMin = new BlockPos(0, 64, 0);
		BlockPos selMax = new BlockPos(5, 68, 5);

		PlaneSliceBounds bounds = PlaneSliceBounds.compute(
			hit, Direction.UP, selMin, selMax, CHUNK, BOTTOM, TOP);

		assertTrue(bounds.isEmpty());
		assertEquals(0, bounds.volume());
	}

	@Test
	void verticalXSliceUsesWorldHeightWithoutSelection() {
		BlockPos hit = new BlockPos(8, 100, 4);
		PlaneSliceBounds bounds = PlaneSliceBounds.compute(
			hit, Direction.EAST, null, null, CHUNK, BOTTOM, TOP);

		assertEquals(8, bounds.minX());
		assertEquals(8, bounds.maxX());
		assertEquals(BOTTOM, bounds.minY());
		assertEquals(TOP, bounds.maxY());
		assertEquals(0, bounds.minZ());
		assertEquals(15, bounds.maxZ());
	}

	@Test
	void positionsEnumeratesVolume() {
		PlaneSliceBounds bounds = new PlaneSliceBounds(0, 1, 64, 64, 0, 1);
		List<BlockPos> positions = bounds.positions();
		assertEquals(4, positions.size());
		assertTrue(positions.contains(new BlockPos(0, 64, 0)));
		assertTrue(positions.contains(new BlockPos(1, 64, 1)));
	}
}
