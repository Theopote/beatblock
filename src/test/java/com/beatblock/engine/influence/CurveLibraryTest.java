package com.beatblock.engine.influence;

import com.beatblock.engine.AnimationLibrary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
		assertNotNull(BlockInfluencePresets.get("BlockJump"));
		assertNotNull(BlockInfluencePresets.get("Meteor"));
		AnimationLibrary library = new AnimationLibrary();
		assertEquals(BlockInfluencePresets.getAll().size(), library.getAll().size());
		for (String id : BlockInfluencePresets.getAll().keySet()) {
			assertTrue(library.getAll().containsKey(id), "missing animation for preset: " + id);
		}
	}

	@Test
	void clampTClampsOutsideUnitInterval() {
		assertEquals(0f, CurveLibrary.clampT(-0.5f), EPS);
		assertEquals(1f, CurveLibrary.clampT(1.5f), EPS);
	}

	@Test
	void lerpInterpolatesBetweenEndpoints() {
		assertEquals(2.5f, CurveLibrary.lerp(2f, 3f, 0.5f), EPS);
	}

	@Test
	void scatterEnvelopeFadesOutWithTime() {
		assertEquals(0.5f, CurveLibrary.scatterEnvelope(0.5f, 1f), EPS);
		assertEquals(0f, CurveLibrary.scatterEnvelope(1f, 1f), EPS);
	}

	@Test
	void constantCurvesReturnFixedValues() {
		assertEquals(0f, CurveLibrary.sample(CurveKind.CONST_ZERO, 0.5f), EPS);
		assertEquals(1f, CurveLibrary.sample(CurveKind.CONST_ONE, 0.5f), EPS);
	}
}
