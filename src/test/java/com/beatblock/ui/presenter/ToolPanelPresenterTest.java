package com.beatblock.ui.presenter;

import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPanelPresenterTest {

	private BeatBlockSelectionManager selectionManager;
	private StageObjectSystem stageObjectSystem;
	private ToolPanelPresenter presenter;

	@BeforeEach
	void setUp() {
		selectionManager = BeatBlockSelectionManager.get();
		selectionManager.reset();
		selectionManager.setMode(SelectionMode.BOX);
		stageObjectSystem = new StageObjectSystem();
		presenter = new ToolPanelPresenter(
			() -> selectionManager,
			() -> stageObjectSystem,
			() -> null,
			() -> new BlockPos(10, 64, 10)
		);
	}

	@Test
	void fillCornersFromSelectionUsesBoundingBox() {
		selectionManager.setMode(SelectionMode.LASSO);
		selectionManager.commitLassoSelection(List.of(
			new BlockPos(1, 64, 2),
			new BlockPos(5, 70, 8)
		), SelectionOperation.NEW);

		var outcome = presenter.fillCornersFromSelection();
		assertTrue(outcome.result().ok());
		assertEquals(new BlockPos(1, 64, 2), outcome.corners().posA());
		assertEquals(new BlockPos(5, 70, 8), outcome.corners().posB());
	}

	@Test
	void createFromSelectionSnapshotRegistersObject() {
		selectionManager.setMode(SelectionMode.LASSO);
		selectionManager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		), SelectionOperation.NEW);

		var outcome = presenter.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Tower",
			false,
			GroupSortingStrategy.SEQUENTIAL,
			0.25
		));

		assertTrue(outcome.result().ok());
		assertNotNull(outcome.objectId());
		assertEquals(1, stageObjectSystem.size());
		assertEquals("Tower", stageObjectSystem.get(outcome.objectId()).getName());
	}

	@Test
	void buildUniqueStageObjectIdAvoidsCollisions() {
		stageObjectSystem.register(StageObjectSystem.fromBlocks("tower", "Tower", List.of(new BlockPos(0, 64, 0))));
		String id = ToolPanelPresenter.buildUniqueStageObjectId(stageObjectSystem, "Tower");
		assertEquals("tower_2", id);
	}

	@Test
	void estimateSelectionVolumeComputesInclusiveBounds() {
		long volume = ToolPanelPresenter.estimateSelectionVolume(
			new BlockPos(0, 0, 0),
			new BlockPos(1, 1, 1)
		);
		assertEquals(8, volume);
	}

	@Test
	void setCornerFromCrosshairUsesPicker() {
		var outcome = presenter.setCornerFromCrosshair(true);
		assertTrue(outcome.result().ok());
		assertEquals(new BlockPos(10, 64, 10), outcome.corners().posA());
	}

	@Test
	void removeStageObjectDeletesRegisteredObject() {
		selectionManager.setMode(SelectionMode.LASSO);
		selectionManager.commitLassoSelection(List.of(new BlockPos(2, 64, 2)), SelectionOperation.NEW);
		var created = presenter.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Obj", false, GroupSortingStrategy.ALL, 0.0));
		assertTrue(created.result().ok());

		var removed = presenter.removeStageObject(created.objectId());
		assertTrue(removed.ok());
		assertEquals(0, stageObjectSystem.size());
	}

	@Test
	void fillCornersFromSelectionFailsWithoutSelection() {
		var outcome = presenter.fillCornersFromSelection();
		assertFalse(outcome.result().ok());
		assertEquals("没有可用的方块选区或包围盒。", outcome.result().messageOrEmpty());
	}

	@Test
	void fillCornersFromSelectionFailsWhenManagerMissing() {
		var missing = new ToolPanelPresenter(() -> null, () -> stageObjectSystem, () -> null, () -> null);
		var outcome = missing.fillCornersFromSelection();
		assertFalse(outcome.result().ok());
		assertEquals("选择管理器不可用。", outcome.result().messageOrEmpty());
	}

	@Test
	void setCornerFromCrosshairFailsWhenPickerMisses() {
		var missing = new ToolPanelPresenter(
			() -> selectionManager,
			() -> stageObjectSystem,
			() -> null,
			() -> null
		);
		var outcome = missing.setCornerFromCrosshair(false);
		assertFalse(outcome.result().ok());
		assertEquals("未命中方块。", outcome.result().messageOrEmpty());
	}

	@Test
	void clearCornersResetsStoredPositions() {
		presenter.setCornerFromCrosshair(true);
		presenter.setCornerFromCrosshair(false);
		var outcome = presenter.clearCorners();
		assertTrue(outcome.result().ok());
		assertEquals(null, outcome.corners().posA());
		assertEquals(null, outcome.corners().posB());
	}

	@Test
	void createFromCuboidFailsWithoutWorld() {
		presenter.setCornerFromCrosshair(true);
		presenter.setCornerFromCrosshair(false);

		var outcome = presenter.createFromCuboid(new ToolPanelPresenter.StageObjectCreateRequest(
			"Cuboid", false, GroupSortingStrategy.ALL, 0.0));

		assertFalse(outcome.result().ok());
		assertEquals("当前无世界上下文，无法读取选区。", outcome.result().messageOrEmpty());
	}

	@Test
	void estimateSelectionVolumeCanExceedStageObjectLimit() {
		long volume = ToolPanelPresenter.estimateSelectionVolume(
			new BlockPos(0, 64, 0),
			new BlockPos(ToolPanelPresenter.MAX_STAGE_OBJECT_BLOCKS, 64, ToolPanelPresenter.MAX_STAGE_OBJECT_BLOCKS)
		);
		assertTrue(volume > ToolPanelPresenter.MAX_STAGE_OBJECT_BLOCKS);
	}

	@Test
	void createFromSelectionSnapshotFailsOnEmptySelection() {
		var outcome = presenter.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Empty", false, GroupSortingStrategy.ALL, 0.0));
		assertFalse(outcome.result().ok());
		assertEquals("当前没有方块选区。请先使用选择工具。", outcome.result().messageOrEmpty());
	}

	@Test
	void removeStageObjectRejectsMissingId() {
		assertFalse(presenter.removeStageObject("missing").ok());
		assertFalse(presenter.removeStageObject("  ").ok());
	}

	@Test
	void setSelectionModeUpdatesManager() {
		presenter.setSelectionMode(SelectionMode.BRUSH);
		assertEquals(SelectionMode.BRUSH, selectionManager.getMode());
	}

	@Test
	void selectionToolViewStateReflectsManager() {
		selectionManager.setMode(SelectionMode.LASSO);
		selectionManager.commitLassoSelection(List.of(new BlockPos(3, 64, 3)), SelectionOperation.NEW);

		var state = presenter.selectionToolViewState();
		assertEquals(SelectionMode.LASSO, state.mode());
		assertEquals(1, state.selectionCount());
		assertEquals(new BlockPos(3, 64, 3), state.boundingMin());
	}

	@Test
	void listStageObjectsSortsByName() {
		selectionManager.commitLassoSelection(List.of(new BlockPos(0, 64, 0)), SelectionOperation.NEW);
		presenter.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Zulu", false, GroupSortingStrategy.ALL, 0.0));
		selectionManager.commitLassoSelection(List.of(new BlockPos(1, 64, 0)), SelectionOperation.NEW);
		presenter.createFromSelectionSnapshot(new ToolPanelPresenter.StageObjectCreateRequest(
			"Alpha", false, GroupSortingStrategy.ALL, 0.0));

		var items = presenter.listStageObjects();
		assertEquals(2, items.size());
		assertEquals("Alpha", items.get(0).name());
		assertEquals("Zulu", items.get(1).name());
	}

	@Test
	void selectionToolViewStateReturnsOffWhenManagerMissing() {
		var missing = new ToolPanelPresenter(() -> null, () -> stageObjectSystem, () -> null, () -> null);
		var state = missing.selectionToolViewState();
		assertEquals(SelectionMode.OFF, state.mode());
		assertEquals(0, state.selectionCount());
	}

	@Test
	void staticHelpersParseAndLabel() {
		assertEquals(0.0, ToolPanelPresenter.parseStaggerSeconds("bad"), 1e-9);
		assertEquals(1.5, ToolPanelPresenter.parseStaggerSeconds(" 1.5 "), 1e-9);
		assertEquals(GroupSortingStrategy.RADIAL, ToolPanelPresenter.sortingStrategyAtIndex(1));
		assertEquals("笔刷（球/立方，单击或涂抹）", ToolPanelPresenter.selectionModeLabel(SelectionMode.BRUSH));
		assertEquals("(未设置)", ToolPanelPresenter.formatPos(null));
	}
}
