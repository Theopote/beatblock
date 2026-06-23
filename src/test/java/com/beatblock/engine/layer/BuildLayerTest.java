package com.beatblock.engine.layer;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLayerTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void visibilityCapabilitiesDependOnState() {
		BlockPos pos = new BlockPos(0, 64, 0);
		StageObject stage = StageObjectSystem.fromBlocks("s1", "Layer", List.of(pos));

		BuildLayer visible = new BuildLayer(
			"v", "Visible", stage, LayerVisibilityState.FREE_VISIBLE, Map.of(), null);
		assertTrue(visible.canToggleVisibility());
		assertTrue(visible.canDelete());
		assertFalse(visible.canBindToTrack());

		BuildLayer hidden = new BuildLayer(
			"h", "Hidden", stage, LayerVisibilityState.FREE_HIDDEN, Map.of(), null);
		assertTrue(hidden.canToggleVisibility());
		assertTrue(hidden.canBindToTrack());

		BuildLayer bound = new BuildLayer(
			"b", "Bound", stage, LayerVisibilityState.BOUND_TO_TRACK, Map.of(), "clip-1");
		assertFalse(bound.canToggleVisibility());
		assertFalse(bound.canDelete());
		assertFalse(bound.canBindToTrack());
	}
}
