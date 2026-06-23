package com.beatblock.engine;

import com.beatblock.engine.influence.InfluenceFrame;
import com.beatblock.selection.BlockStateLookup;
import com.beatblock.testutil.TestBlockStates;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildSequencerTest {

	private StageObjectSystem stageObjectSystem;
	private BuildSequencer sequencer;

	@BeforeEach
	void setUp() {
		stageObjectSystem = new StageObjectSystem();
		sequencer = new BuildSequencer(stageObjectSystem, new com.beatblock.engine.layer.BuildLayerManager(stageObjectSystem));
	}

	@Test
	void scheduleReturnsNullWhenTargetMissing() {
		var event = new TimelineAnimationEvent(
			"ev1", 0.0, 1.0, "build", "missing", 1f, Map.of());
		assertNull(sequencer.schedule(event));
	}

	@Test
	void contributeExistenceMutationsPlacesBlocksOverTime() {
		BlockPos p0 = new BlockPos(0, 64, 0);
		BlockPos p1 = new BlockPos(1, 64, 0);
		BlockState air = TestBlockStates.air();
		BlockState placed = TestBlockStates.ofBlock("build");

		sequencer.enqueueBuildInstance(new BuildSequencer.BuildInstance(
			"ev1", List.of(p0, p1), placed, null, 10.0, 12.0, false));

		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(p0, air);
		world.put(p1, air);
		BlockStateLookup lookup = pos -> world.getOrDefault(pos.toImmutable(), air);

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 9.0, lookup, pos -> true);
		assertEquals(0, frame.getWorldMutations().size());

		sequencer.contributeExistenceMutations(frame, 11.0, lookup, pos -> true);
		assertEquals(1, frame.getWorldMutations().size());
		assertEquals(placed, frame.getWorldMutations().getFirst().toState());

		sequencer.contributeExistenceMutations(frame, 12.0, lookup, pos -> true);
		assertEquals(2, frame.getWorldMutations().size());
		assertTrue(sequencer.getActiveInstances().isEmpty());
	}

	@Test
	void layerRevealUsesCapturedStates() {
		BlockPos pos = new BlockPos(0, 64, 0);
		BlockState air = TestBlockStates.air();
		BlockState captured = TestBlockStates.ofBlock("gold");

		Map<BlockPos, BlockState> perBlock = new LinkedHashMap<>();
		perBlock.put(pos, captured);
		sequencer.enqueueBuildInstance(new BuildSequencer.BuildInstance(
			"ev_layer", List.of(pos), air, perBlock, 5.0, 6.0, true));

		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(pos, air);
		BlockStateLookup lookup = p -> world.getOrDefault(p.toImmutable(), air);

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 6.0, lookup, p -> true);

		assertEquals(1, frame.getWorldMutations().size());
		assertEquals(captured, frame.getWorldMutations().getFirst().toState());
	}
}
