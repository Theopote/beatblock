package com.beatblock.timeline.generation;

import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraStepModulationTest {

	@Test
	void frustumGatingMovesHiddenBlocksToEnd() {
		List<BlockPos> ordered = List.of(
			new BlockPos(0, 64, -20),
			new BlockPos(0, 64, 5),
			new BlockPos(0, 64, 8)
		);
		Vec3d camera = new Vec3d(0, 64, 0);
		Vec3d forward = new Vec3d(0, 0, 1);
		Map<String, Object> params = Map.of("cameraFrustumGating", true);

		List<BlockPos> reordered = CameraStepModulation.reorderForFrustumGating(
			ordered, camera, forward, params);

		assertEquals(new BlockPos(0, 64, 5), reordered.get(0));
		assertEquals(new BlockPos(0, 64, 8), reordered.get(1));
		assertEquals(new BlockPos(0, 64, -20), reordered.get(2));
	}

	@Test
	void adaptiveTimingScalesFarBlocksLater() {
		var event = new TimelineAnimationEvent(
			"e1", 0.0, 0.5, "WaveMotion", "stage", 1f,
			Map.of(
				"dispatchModel", "STEP",
				"stepStartMode", "IMMEDIATE",
				"blocksPerBeat", 1,
				"cameraAdaptiveStep", true,
				"cameraNearDistance", 8.0,
				"cameraFarDistance", 48.0,
				"cameraNearScale", 0.5,
				"cameraFarScale", 2.0
			)
		);
		List<BlockPos> blocks = List.of(
			new BlockPos(0, 64, 2),
			new BlockPos(0, 64, 40)
		);
		var planned = StepSequencePlanner.plan(blocks, event, new double[] {0.0, 1.0}, 120);
		Vec3d camera = new Vec3d(0, 64, 0);
		var adjusted = CameraStepModulation.applyAdaptiveTiming(planned, blocks, camera, event.getParameters());

		assertTrue(adjusted.get(1).startTimeSeconds() > planned.get(1).startTimeSeconds());
	}
}
