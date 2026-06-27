package com.beatblock.engine.influence;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.AnimationLibrary;
import com.beatblock.engine.EffectContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockInfluenceEvaluatorTest {

	private static final float EPS = 1e-4f;

	private final BlockInfluenceEvaluator evaluator = new BlockInfluenceEvaluator();

	@Test
	void jumpPresetMatchesLegacyPeak() {
		BlockInfluencePreset preset = BlockInfluencePresets.get("BlockJump");
		AnimatedBlock block = new AnimatedBlock(new BlockPos(0, 64, 0));
		double baseY = block.getPosition().y;
		evaluator.applyPreset(block, preset, 0.5f, 1f, new EffectContext(block.getPosition()));
		assertEquals(baseY + 2f, block.getPosition().y, EPS);
	}

	@Test
	void pulsePresetScalesAtMidpoint() {
		BlockInfluencePreset preset = BlockInfluencePresets.get("Pulse");
		AnimatedBlock block = new AnimatedBlock(new BlockPos(0, 64, 0));
		evaluator.applyPreset(block, preset, 0.5f, 1f, new EffectContext(block.getPosition()));
		assertEquals(1.3f, block.getScale(), EPS);
	}

	@Test
	void waveMotionUsesCameraRadialWaveWhenCameraPresent() {
		BlockInfluencePreset preset = BlockInfluencePresets.get("WaveMotion");
		AnimatedBlock near = new AnimatedBlock(new BlockPos(0, 64, 5));
		AnimatedBlock far = new AnimatedBlock(new BlockPos(0, 64, 80));
		double baseNearY = near.getPosition().y;
		double baseFarY = far.getPosition().y;
		Vec3d camera = new Vec3d(0, 64, 0);
		Vec3d forward = new Vec3d(0, 0, 1);
		EffectContext ctx = new EffectContext(
			camera,
			Map.of("waveAmplitude", 1.0),
			camera,
			forward
		);

		evaluator.applyPreset(near, preset, 1f, 1f, ctx);
		evaluator.applyPreset(far, preset, 1f, 1f, ctx);

		assertTrue(Math.abs(near.getPosition().y - baseNearY) > 0.01);
		assertEquals(baseFarY, far.getPosition().y, EPS);
	}

	@Test
	void animationLibraryLoadsFromPresets() {
		AnimationLibrary library = new AnimationLibrary();
		AnimationDefinition jump = library.get("BlockJump");
		assertNotNull(jump);
		assertNotNull(jump.getPreset());
		assertEquals(BlockInfluencePresets.getAll().size(), library.getAll().size());
		assertTrue(library.getAll().values().stream().allMatch(def -> def.getPreset() != null));
	}
}
