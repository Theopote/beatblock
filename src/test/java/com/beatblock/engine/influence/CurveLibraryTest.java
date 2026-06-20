package com.beatblock.engine.influence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurveLibraryTest {

	private static final float EPS = 1e-5f;

	@Test
	void linearAndInverseEndpoints() {
		assertEquals(0f, CurveLibrary.sample(CurveKind.LINEAR, 0f), EPS);
		assertEquals(1f, CurveLibrary.sample(CurveKind.LINEAR, 1f), EPS);
		assertEquals(1f, CurveLibrary.sample(CurveKind.INVERSE_LINEAR, 0f), EPS);
		assertEquals(0f, CurveLibrary.sample(CurveKind.INVERSE_LINEAR, 1f), EPS);
	}

	@Test
	void sineBumpPeaksAtMidpoint() {
		assertEquals(0f, CurveLibrary.sample(CurveKind.SINE_BUMP, 0f), EPS);
		assertEquals(1f, CurveLibrary.sample(CurveKind.SINE_BUMP, 0.5f), EPS);
		assertEquals(0f, CurveLibrary.sample(CurveKind.SINE_BUMP, 1f), EPS);
	}

	@Test
	void gravityRemainingMatchesMeteorRest() {
		assertEquals(12f, CurveLibrary.gravityRemainingHeight(0f, 12f, 1f), EPS);
		assertEquals(0f, CurveLibrary.gravityRemainingHeight(1f, 12f, 1f), EPS);
	}

	@Test
	void jumpAndPulseHelpersScaleWithEnergy() {
		float jump = CurveLibrary.sineBumpMagnitude(0.5f, 2f, 0.5f);
		assertEquals(1f, jump, EPS);
		float pulse = CurveLibrary.scaleSinePulse(0.5f, 1.3f, 1f);
		assertEquals(1.3f, pulse, EPS);
	}

	@Test
	void meteorApproachScaleEndsAtOne() {
		assertEquals(0.5f, CurveLibrary.meteorApproachScale(0f, 1f), EPS);
		assertEquals(1f, CurveLibrary.meteorApproachScale(1f, 1f), EPS);
	}

	@Test
	void builtInPresetsMirrorAnimationLibraryIds() {
		assertTrue(BlockInfluencePresets.get("BlockJump") != null);
		assertTrue(BlockInfluencePresets.get("Meteor") != null);
		assertEquals(10, BlockInfluencePresets.getAll().size());
	}
}
