package com.beatblock.ui.presenter;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLayersPresenterTest {

	private BuildLayerManager layerManager;
	private CommandManager commandManager;
	private BuildLayersPresenter presenter;

	@BeforeEach
	void setUp() {
		layerManager = new BuildLayerManager(new StageObjectSystem());
		commandManager = new CommandManager();
		presenter = new BuildLayersPresenter(() -> commandManager, () -> layerManager);
	}

	@Test
	void renameLayerRejectsEmptyName() {
		BlockPos pos = new BlockPos(0, 64, 0);
		CreateLayerCommand create = new CreateLayerCommand(layerManager, "Tower", List.of(pos));
		commandManager.execute(create);
		BuildLayer layer = create.getCreatedLayer();
		assertNotNull(layer);

		var outcome = presenter.renameLayer(layer.getId(), "   ");
		assertFalse(outcome.result().ok());
		assertEquals(layer.getName(), outcome.committedName());
	}

	@Test
	void renameLayerExecutesCommandWhenValid() {
		BlockPos pos = new BlockPos(1, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Old", List.of(pos)));
		BuildLayer layer = layerManager.getAll().iterator().next();

		var outcome = presenter.renameLayer(layer.getId(), "New Name");
		assertTrue(outcome.result().ok());
		assertEquals("New Name", layerManager.get(layer.getId()).getName());
		assertEquals("New Name", outcome.committedName());
	}

	@Test
	void createLayerFromSelectionReturnsCreatedId() {
		BlockPos pos = new BlockPos(2, 64, 0);
		var outcome = presenter.createLayerFromSelection("Layer A", List.of(pos));

		assertTrue(outcome.result().ok());
		assertNotNull(outcome.createdLayerId());
		assertEquals(1, outcome.blocksToRemoveFromSelection().size());
		assertTrue(layerManager.isBlockClaimed(pos));
	}

	@Test
	void createLayerFromSelectionRejectsEmptySelection() {
		var outcome = presenter.createLayerFromSelection("Layer A", List.of());
		assertFalse(outcome.result().ok());
		assertEquals("请先建立方块选区。", outcome.result().messageOrEmpty());
	}

	@Test
	void renameLayerRejectsDuplicateName() {
		BlockPos first = new BlockPos(3, 64, 0);
		BlockPos second = new BlockPos(4, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Alpha", List.of(first)));
		commandManager.execute(new CreateLayerCommand(layerManager, "Beta", List.of(second)));

		BuildLayer beta = layerManager.getAll().stream()
			.filter(layer -> "Beta".equals(layer.getName()))
			.findFirst()
			.orElseThrow();

		var outcome = presenter.renameLayer(beta.getId(), "Alpha");
		assertFalse(outcome.result().ok());
		assertEquals("Beta", outcome.committedName());
	}

	@Test
	void deleteLayerRemovesLayer() {
		BlockPos pos = new BlockPos(5, 64, 0);
		commandManager.execute(new CreateLayerCommand(layerManager, "Delete Me", List.of(pos)));
		BuildLayer layer = layerManager.getAll().iterator().next();

		var outcome = presenter.deleteLayer(layer.getId());
		assertTrue(outcome.result().ok());
		assertTrue(layerManager.getAll().isEmpty());
	}

	@Test
	void registerRestoredLayerCanBeRenamed() {
		layerManager.registerRestored(new BuildLayer(
			"layer-x",
			"Original",
			StageObjectSystem.fromBlocks("s1", "Original", List.of(new BlockPos(6, 64, 0))),
			LayerVisibilityState.FREE_VISIBLE,
			Map.of(),
			null
		));

		var outcome = presenter.renameLayer("layer-x", "Updated");
		assertTrue(outcome.result().ok());
		assertEquals("Updated", layerManager.get("layer-x").getName());
	}
}
