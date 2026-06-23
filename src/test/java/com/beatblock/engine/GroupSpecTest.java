package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupSpecTest {

	@Test
	void fromSelectionCuboidEncodesCorners() {
		BlockPos a = new BlockPos(1, 64, 2);
		BlockPos b = new BlockPos(3, 66, 4);
		GroupSpec spec = GroupSpec.fromSelectionCuboid(a, b, false, GroupSortingStrategy.RADIAL, 0.5);

		assertEquals("selection_cuboid", spec.getSourceType());
		assertEquals(GroupSortingStrategy.RADIAL, spec.getSortingStrategy());
		assertEquals(0.5, spec.getStaggerDelaySeconds(), 1e-9);
		Map<String, Object> params = spec.getSourceParams();
		assertEquals(1, ((Map<?, ?>) params.get("posA")).get("x"));
		assertEquals(4, ((Map<?, ?>) params.get("posB")).get("z"));
		assertEquals(false, params.get("includeAir"));
	}

	@Test
	void fromSelectionSnapshotComputesBounds() {
		List<BlockPos> blocks = List.of(new BlockPos(0, 64, 0), new BlockPos(2, 65, 1));
		GroupSpec spec = GroupSpec.fromSelectionSnapshot(blocks, GroupSortingStrategy.SEQUENTIAL, 0.0);

		assertEquals("selection_snapshot", spec.getSourceType());
		assertEquals(2, spec.getSourceParams().get("count"));
		Map<?, ?> min = (Map<?, ?>) spec.getSourceParams().get("boundsMin");
		Map<?, ?> max = (Map<?, ?>) spec.getSourceParams().get("boundsMax");
		assertEquals(0, min.get("x"));
		assertEquals(2, max.get("x"));
	}

	@Test
	void manualSnapshotHasEmptyParams() {
		GroupSpec spec = GroupSpec.manualSnapshot();
		assertEquals("manual_snapshot", spec.getSourceType());
		assertTrue(spec.getSourceParams().isEmpty());
		assertEquals(GroupSortingStrategy.SEQUENTIAL, spec.getSortingStrategy());
	}
}
