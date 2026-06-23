package com.beatblock.engine.layer;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLayerManagerTest {

	private StageObjectSystem stageObjectSystem;
	private BuildLayerManager manager;

	@BeforeEach
	void setUp() {
		stageObjectSystem = new StageObjectSystem();
		manager = new BuildLayerManager(stageObjectSystem);
	}

	@Test
	void registerRestoredClaimsBlocksForLookup() {
		BlockPos shared = new BlockPos(0, 64, 0);
		BlockPos unique = new BlockPos(1, 64, 0);
		StageObject layerOne = StageObjectSystem.fromBlocks("s1", "L1", List.of(shared, unique));
		manager.registerRestored(new BuildLayer(
			"layer-1", "Layer 1", layerOne, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		assertTrue(manager.isBlockClaimed(shared));
		assertTrue(manager.isBlockClaimed(unique));
		assertFalse(manager.isBlockClaimed(new BlockPos(9, 64, 0)));
		assertNotNull(manager.getLayerOwningBlock(shared));
		assertEquals("layer-1", manager.getLayerOwningBlock(shared).getId());
	}

	@Test
	void countClaimedBlocksAndFilterUnclaimed() {
		BlockPos claimed = new BlockPos(0, 64, 0);
		BlockPos free = new BlockPos(2, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "L1", List.of(claimed));
		manager.registerRestored(new BuildLayer(
			"layer-1", "Layer 1", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		List<BlockPos> candidates = List.of(claimed, free, new BlockPos(3, 64, 0));
		assertEquals(1, manager.countClaimedBlocks(candidates));
		assertEquals(List.of(free, new BlockPos(3, 64, 0)), manager.filterUnclaimedBlocks(candidates));
	}

	@Test
	void overlappingRegistrationUpdatesOwnerToLatestLayer() {
		BlockPos pos = new BlockPos(0, 64, 0);
		StageObject first = StageObjectSystem.fromBlocks("s1", "First", List.of(pos));
		StageObject second = StageObjectSystem.fromBlocks("s2", "Second", List.of(pos));
		manager.registerRestored(new BuildLayer(
			"layer-a", "A", first, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));
		manager.registerRestored(new BuildLayer(
			"layer-b", "B", second, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		assertEquals("layer-b", manager.getLayerOwningBlock(pos).getId());
	}

	@Test
	void isNameTakenIgnoresExcludedLayerId() {
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Stage", List.of(new BlockPos(0, 64, 0)));
		manager.registerRestored(new BuildLayer(
			"layer-1", "Build A", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		assertTrue(manager.isNameTaken("Build A", null));
		assertFalse(manager.isNameTaken("Build A", "layer-1"));
		assertTrue(manager.isNameTaken("build a", null));
	}

	@Test
	void getByClipIdFindsBoundLayer() {
		BlockPos pos = new BlockPos(0, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Stage", List.of(pos));
		manager.registerRestored(new BuildLayer(
			"layer-1", "Layer", stage, LayerVisibilityState.BOUND_TO_TRACK, Map.of(), "clip-99"));

		assertNull(manager.getByClipId(null));
		assertNull(manager.getByClipId("missing"));
		assertNotNull(manager.getByClipId("clip-99"));
		assertEquals("layer-1", manager.getByClipId("clip-99").getId());
	}

	@Test
	void hideAndShowLayerReturnFalseWithoutWorld() {
		BlockPos pos = new BlockPos(0, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Stage", List.of(pos));
		BuildLayer layer = new BuildLayer(
			"layer-vis", "Stage", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null);
		manager.registerRestored(layer);

		assertFalse(manager.hideLayer(layer, null));
		assertEquals(LayerVisibilityState.FREE_VISIBLE, layer.getState());
		assertFalse(manager.showLayer(layer, null));
	}
}
