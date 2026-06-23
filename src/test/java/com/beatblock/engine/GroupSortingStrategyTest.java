package com.beatblock.engine;

import com.beatblock.timeline.binding.SpatialDispatchMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupSortingStrategyTest {

	@Test
	void fromValueResolvesByCodeAndName() {
		assertEquals(GroupSortingStrategy.RADIAL, GroupSortingStrategy.fromValue("RADIAL"));
		assertEquals(GroupSortingStrategy.SPIRAL, GroupSortingStrategy.fromValue("spiral"));
	}

	@Test
	void fromValueMapsSpatialDispatchMode() {
		assertEquals(GroupSortingStrategy.SEQUENTIAL, GroupSortingStrategy.fromValue("SEQUENTIAL"));
		assertEquals(GroupSortingStrategy.RANDOM, GroupSortingStrategy.fromValue("RANDOM"));
	}

	@Test
	void fromValueDefaultsToSequential() {
		assertEquals(GroupSortingStrategy.SEQUENTIAL, GroupSortingStrategy.fromValue(null));
		assertEquals(GroupSortingStrategy.SEQUENTIAL, GroupSortingStrategy.fromValue(""));
	}

	@Test
	void toSpatialDispatchModeMatchesMapping() {
		assertEquals(SpatialDispatchMode.RADIAL, GroupSortingStrategy.RADIAL.toSpatialDispatchMode());
		assertEquals(SpatialDispatchMode.ALL, GroupSortingStrategy.ALL.toSpatialDispatchMode());
	}
}
