package com.beatblock.engine;

import com.beatblock.engine.influence.InfluenceFrame;
import com.beatblock.selection.BlockStateLookup;
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

	private static final BlockState AIR = new BlockStateToken("air");
	private static final BlockState PLACED = new BlockStateToken("placed");
	private static final BlockState GOLD = new BlockStateToken("gold");

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

		sequencer.enqueueBuildInstance(new BuildSequencer.BuildInstance(
			"ev1", List.of(p0, p1), PLACED, null, 10.0, 12.0, false));

		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(p0, AIR);
		world.put(p1, AIR);
		BlockStateLookup lookup = pos -> world.getOrDefault(pos.toImmutable(), AIR);

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 9.0, lookup, pos -> true);
		assertEquals(0, frame.getWorldMutations().size());

		sequencer.contributeExistenceMutations(frame, 11.0, lookup, pos -> true);
		assertEquals(1, frame.getWorldMutations().size());
		assertEquals(PLACED, frame.getWorldMutations().getFirst().toState());

		sequencer.contributeExistenceMutations(frame, 12.0, lookup, pos -> true);
		assertEquals(2, frame.getWorldMutations().size());
		assertTrue(sequencer.getActiveInstances().isEmpty());
	}

	@Test
	void layerRevealUsesCapturedStates() {
		BlockPos pos = new BlockPos(0, 64, 0);

		Map<BlockPos, BlockState> perBlock = new LinkedHashMap<>();
		perBlock.put(pos, GOLD);
		sequencer.enqueueBuildInstance(new BuildSequencer.BuildInstance(
			"ev_layer", List.of(pos), AIR, perBlock, 5.0, 6.0, true));

		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(pos, AIR);
		BlockStateLookup lookup = p -> world.getOrDefault(p.toImmutable(), AIR);

		InfluenceFrame frame = new InfluenceFrame();
		sequencer.contributeExistenceMutations(frame, 6.0, lookup, p -> true);

		assertEquals(1, frame.getWorldMutations().size());
		assertEquals(GOLD, frame.getWorldMutations().getFirst().toState());
	}

	/** 测试用 {@link BlockState} 替身（不触发注册表）。 */
	private static final class BlockStateToken extends BlockState {
		private final String label;

		private BlockStateToken(String label) {
			super(null, null, null);
			this.label = label;
		}

		@Override
		public boolean isAir() {
			return "air".equals(label);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BlockStateToken other && label.equals(other.label);
		}

		@Override
		public int hashCode() {
			return label.hashCode();
		}
	}
}
