package com.beatblock.selection;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.LayerVisibilityState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatBlockSelectionManagerTest {

	private BeatBlockSelectionManager manager;
	private BlockAnimationEngine engine;

	@BeforeEach
	void setUp() {
		manager = BeatBlockSelectionManager.get();
		manager.reset();
		engine = new BlockAnimationEngine();
		BeatBlock.blockAnimationEngine = engine;
	}

	@AfterEach
	void tearDown() {
		BeatBlock.blockAnimationEngine = null;
	}

	@Test
	void commitLassoSelectionReplacesSelectionOnNewOperation() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)), SelectionOperation.NEW);

		assertEquals(2, manager.getSelectionCount());
		assertTrue(manager.getSelectedBlocks().contains(new BlockPos(0, 64, 0)));
	}

	@Test
	void commitLassoSelectionSupportsAddSubtractAndIntersect() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0),
			new BlockPos(2, 64, 0)), SelectionOperation.NEW);

		manager.commitLassoSelection(List.of(new BlockPos(2, 64, 0)), SelectionOperation.ADD);
		assertEquals(3, manager.getSelectionCount());

		manager.commitLassoSelection(List.of(new BlockPos(1, 64, 0)), SelectionOperation.SUBTRACT);
		assertEquals(2, manager.getSelectionCount());

		manager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(9, 64, 0)), SelectionOperation.INTERSECT);
		assertEquals(1, manager.getSelectionCount());
		assertTrue(manager.getSelectedBlocks().contains(new BlockPos(0, 64, 0)));
	}

	@Test
	void emptyLassoReportsMessageWithoutChangingSelection() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(new BlockPos(0, 64, 0)), SelectionOperation.NEW);
		manager.commitLassoSelection(List.of(), SelectionOperation.ADD);

		assertEquals(1, manager.getSelectionCount());
		assertTrue(manager.getLastMessage().contains("未选中"));
	}

	@Test
	void clearSelectionResetsCountAndBoundingBox() {
		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(new BlockPos(5, 64, 5)), SelectionOperation.NEW);
		assertEquals(new BlockPos(5, 64, 5), manager.getBoundingMin());

		manager.clearSelection();
		assertEquals(0, manager.getSelectionCount());
		assertNull(manager.getBoundingMin());
	}

	@Test
	void clampsBrushAndReachSettings() {
		manager.setSphereBrushRadius(999);
		assertEquals(32, manager.getSphereBrushRadius());

		manager.setMaxBlocks(100);
		assertEquals(1024, manager.getMaxBlocks());

		manager.setMaxDistanceFromCamera(-5);
		assertEquals(8, manager.getMaxDistanceFromCamera());
	}

	@Test
	void commitLassoSkipsBlocksClaimedByBuildLayer() {
		BlockPos claimed = new BlockPos(0, 64, 0);
		BlockPos free = new BlockPos(1, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Layer", List.of(claimed));
		engine.getBuildLayerManager().registerRestored(new BuildLayer(
			"layer-1", "Layer", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		manager.setMode(SelectionMode.LASSO);
		manager.commitLassoSelection(List.of(claimed, free), SelectionOperation.NEW);

		assertEquals(1, manager.getSelectionCount());
		assertFalse(manager.getSelectedBlocks().contains(claimed));
		assertTrue(manager.getSelectedBlocks().contains(free));
		assertTrue(manager.getLastMessage().contains("跳过"));
	}
}
