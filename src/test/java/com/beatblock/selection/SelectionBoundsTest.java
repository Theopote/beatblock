package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionBoundsTest {

	@Test
	void fromPositionsReturnsNullBoxForEmptyInput() {
		SelectionBounds.Box box = SelectionBounds.fromPositions(List.of());
		assertTrue(box.isEmpty());
		assertNull(box.min());
		assertNull(box.max());
	}

	@Test
	void fromPositionsComputesInclusiveBounds() {
		SelectionBounds.Box box = SelectionBounds.fromPositions(List.of(
			new BlockPos(2, 65, 1),
			new BlockPos(0, 64, 3),
			new BlockPos(1, 64, 0)
		));

		assertEquals(new BlockPos(0, 64, 0), box.min());
		assertEquals(new BlockPos(2, 65, 3), box.max());
	}
}
