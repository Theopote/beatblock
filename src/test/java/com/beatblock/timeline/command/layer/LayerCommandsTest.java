package com.beatblock.timeline.command.layer;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.Timeline;
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

class LayerCommandsTest {

	private StageObjectSystem stageObjectSystem;
	private BuildLayerManager layerManager;
	private Timeline timeline;

	@BeforeEach
	void setUp() {
		stageObjectSystem = new StageObjectSystem();
		layerManager = new BuildLayerManager(stageObjectSystem);
		timeline = Timeline.createDefault();
	}

	@Test
	void createLayerCommandCreatesAndUndoRemovesLayer() {
		BlockPos pos = new BlockPos(0, 64, 0);
		CreateLayerCommand command = new CreateLayerCommand(
			layerManager, "Tower", List.of(pos));
		command.execute();

		BuildLayer created = command.getCreatedLayer();
		assertNotNull(created);
		assertEquals("Tower", created.getName());
		assertTrue(layerManager.isBlockClaimed(pos));

		command.undo();
		assertNull(layerManager.get(created.getId()));
		assertTrue(layerManager.getAll().isEmpty());
	}

	@Test
	void renameLayerCommandUndoRestoresPreviousName() {
		BlockPos pos = new BlockPos(1, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Old Name", List.of(pos));
		layerManager.registerRestored(new BuildLayer(
			"layer-1", "Old Name", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		RenameLayerCommand command = new RenameLayerCommand(layerManager, "layer-1", "New Name");
		command.execute();
		assertEquals("New Name", layerManager.get("layer-1").getName());

		command.undo();
		assertEquals("Old Name", layerManager.get("layer-1").getName());
	}

	@Test
	void deleteLayerCommandUndoRestoresLayerRegistration() {
		BlockPos pos = new BlockPos(2, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "To Delete", List.of(pos));
		BuildLayer layer = new BuildLayer(
			"layer-del", "To Delete", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null);
		layerManager.registerRestored(layer);

		DeleteLayerCommand command = new DeleteLayerCommand(layerManager, "layer-del");
		command.execute();
		assertNull(layerManager.get("layer-del"));
		assertFalse(layerManager.isBlockClaimed(pos));

		command.undo();
		assertNotNull(layerManager.get("layer-del"));
		assertTrue(layerManager.isBlockClaimed(pos));
	}

	@Test
	void deleteHiddenLayerUndoRestoresHiddenState() {
		BlockPos pos = new BlockPos(3, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Hidden", List.of(pos));
		BuildLayer layer = new BuildLayer(
			"layer-hidden", "Hidden", stage, LayerVisibilityState.FREE_HIDDEN, Map.of(), null);
		layerManager.registerRestored(layer);

		DeleteLayerCommand command = new DeleteLayerCommand(layerManager, "layer-hidden");
		command.execute();
		assertNull(layerManager.get("layer-hidden"));

		command.undo();
		BuildLayer restored = layerManager.get("layer-hidden");
		assertNotNull(restored);
		assertEquals(LayerVisibilityState.FREE_HIDDEN, restored.getState());
	}

	@Test
	void bindLayerToTrackCommandCreatesClipAndUndoRestoresBinding() {
		BlockPos pos = new BlockPos(4, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("stage-x", "Bind Me", List.of(pos));
		layerManager.registerRestored(new BuildLayer(
			"layer-bind", "Bind Me", stage, LayerVisibilityState.FREE_HIDDEN, Map.of(), null));

		BindLayerToTrackCommand command = new BindLayerToTrackCommand(
			timeline, layerManager, null, "layer-bind", 1.0, 2.0);
		command.execute();

		BuildLayer bound = layerManager.get("layer-bind");
		assertNotNull(bound);
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, bound.getState());
		assertNotNull(command.getCreatedClipId());

		var track = timeline.getTrack(Timeline.TRACK_ID_BUILD_REVERSE);
		assertNotNull(track);
		assertNotNull(track.getClip(command.getCreatedClipId()));

		command.undo();
		assertEquals(LayerVisibilityState.FREE_HIDDEN, layerManager.get("layer-bind").getState());
		assertNull(layerManager.get("layer-bind").getBoundClipId());
		assertNull(track.getClip(command.getCreatedClipId()));
	}

	@Test
	void toggleVisibilityCommandNoOpsWithoutWorld() {
		BlockPos pos = new BlockPos(5, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Visible", List.of(pos));
		layerManager.registerRestored(new BuildLayer(
			"layer-vis", "Visible", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		ToggleLayerVisibilityCommand command = new ToggleLayerVisibilityCommand(layerManager, "layer-vis");
		command.execute();
		assertEquals(LayerVisibilityState.FREE_VISIBLE, layerManager.get("layer-vis").getState());

		command.undo();
		assertEquals(LayerVisibilityState.FREE_VISIBLE, layerManager.get("layer-vis").getState());
	}

	@Test
	void renameLayerCommandRejectsDuplicateName() {
		BlockPos pos = new BlockPos(6, 64, 0);
		StageObject a = StageObjectSystem.fromBlocks("s1", "Alpha", List.of(pos));
		StageObject b = StageObjectSystem.fromBlocks("s2", "Beta", List.of(new BlockPos(7, 64, 0)));
		layerManager.registerRestored(new BuildLayer(
			"layer-a", "Alpha", a, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));
		layerManager.registerRestored(new BuildLayer(
			"layer-b", "Beta", b, LayerVisibilityState.FREE_VISIBLE, Map.of(), null));

		RenameLayerCommand command = new RenameLayerCommand(layerManager, "layer-a", "Beta");
		command.execute();
		assertEquals("Alpha", layerManager.get("layer-a").getName());
	}

	@Test
	void deleteLayerCommandSkipsBoundLayers() {
		BlockPos pos = new BlockPos(8, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Bound", List.of(pos));
		layerManager.registerRestored(new BuildLayer(
			"layer-bound", "Bound", stage, LayerVisibilityState.BOUND_TO_TRACK, Map.of(), "clip-x"));

		DeleteLayerCommand command = new DeleteLayerCommand(layerManager, "layer-bound");
		command.execute();
		assertNotNull(layerManager.get("layer-bound"));
	}
}
