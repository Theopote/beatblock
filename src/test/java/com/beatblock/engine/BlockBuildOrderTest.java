package com.beatblock.engine;

import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BlockBuildOrderTest {

	@Test
	void wallModeSortsBottomUpThenXZ() {
		List<BlockPos> blocks = List.of(
			new BlockPos(2, 66, 1),
			new BlockPos(1, 64, 0),
			new BlockPos(1, 65, 0)
		);
		StageObject target = StageObjectSystem.fromBlocks("s", "s", blocks);

		List<BlockPos> ordered = BlockBuildOrder.sortBlocks(
			blocks, BuildSequenceMode.WALL, target.getCenter(), null, target);

		assertEquals(new BlockPos(1, 64, 0), ordered.get(0));
		assertEquals(new BlockPos(1, 65, 0), ordered.get(1));
		assertEquals(new BlockPos(2, 66, 1), ordered.get(2));
	}

	@Test
	void bridgeModeSortsAlongXThenZThenY() {
		List<BlockPos> blocks = List.of(
			new BlockPos(3, 65, 2),
			new BlockPos(1, 64, 1),
			new BlockPos(2, 66, 0)
		);
		StageObject target = StageObjectSystem.fromBlocks("s", "s", blocks);

		List<BlockPos> ordered = BlockBuildOrder.sortBlocks(
			blocks, BuildSequenceMode.BRIDGE, target.getCenter(), null, target);

		assertEquals(new BlockPos(1, 64, 1), ordered.get(0));
		assertEquals(new BlockPos(2, 66, 0), ordered.get(1));
		assertEquals(new BlockPos(3, 65, 2), ordered.get(2));
	}

	@Test
	void towerModeSortsByHorizontalDistanceFromCenter() {
		List<BlockPos> blocks = List.of(
			new BlockPos(4, 64, 0),
			new BlockPos(0, 64, 0),
			new BlockPos(0, 65, 0)
		);
		StageObject target = StageObjectSystem.fromBlocks("s", "s", blocks);

		List<BlockPos> ordered = BlockBuildOrder.sortBlocks(
			blocks, BuildSequenceMode.TOWER, target.getCenter(), null, target);

		assertEquals(new BlockPos(0, 64, 0), ordered.get(0));
		assertEquals(new BlockPos(0, 65, 0), ordered.get(1));
		assertEquals(new BlockPos(4, 64, 0), ordered.get(2));
	}

	@Test
	void dissolveModeIsDeterministicForSameSeed() {
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0),
			new BlockPos(3, 64, 0)
		);
		StageObject target = StageObjectSystem.fromBlocks("stage", "stage", blocks);
		var event = new TimelineAnimationEvent("ev1", 10.0, 1.0, "build", "stage", 1f, Map.of());

		List<BlockPos> first = BlockBuildOrder.sortBlocks(
			blocks, BuildSequenceMode.DISSOLVE, target.getCenter(), event, target);
		List<BlockPos> second = BlockBuildOrder.sortBlocks(
			blocks, BuildSequenceMode.DISSOLVE, target.getCenter(), event, target);

		assertEquals(first, second);
		assertNotEquals(blocks, first);
	}

	@Test
	void computeTargetBlockCountHandlesStartEndAndProgress() {
		assertEquals(0, BlockBuildOrder.computeTargetBlockCount(10, 1.0, 3.0, 0.5));
		assertEquals(0, BlockBuildOrder.computeTargetBlockCount(10, 1.0, 3.0, 1.0));
		assertEquals(5, BlockBuildOrder.computeTargetBlockCount(10, 1.0, 3.0, 2.0));
		assertEquals(10, BlockBuildOrder.computeTargetBlockCount(10, 1.0, 3.0, 3.0));
		assertEquals(10, BlockBuildOrder.computeTargetBlockCount(10, 1.0, 3.0, 99.0));
	}

	@Test
	void singleBlockListUnchanged() {
		List<BlockPos> blocks = List.of(new BlockPos(0, 64, 0));
		StageObject target = StageObjectSystem.fromBlocks("s", "s", blocks);
		List<BlockPos> ordered = BlockBuildOrder.sortBlocks(
			blocks, BuildSequenceMode.WALL, Vec3d.ZERO, null, target);
		assertEquals(1, ordered.size());
		assertEquals(blocks.getFirst(), ordered.getFirst());
	}
}
