package com.beatblock.engine.camera;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraViewMathTest {

	@Test
	void adaptiveTimeScaleInterpolatesBetweenNearAndFar() {
		assertEquals(0.6, CameraViewMath.adaptiveTimeScale(4.0, 8.0, 48.0, 0.6, 1.5), 1e-9);
		assertEquals(1.5, CameraViewMath.adaptiveTimeScale(60.0, 8.0, 48.0, 0.6, 1.5), 1e-9);
		double mid = CameraViewMath.adaptiveTimeScale(28.0, 8.0, 48.0, 0.6, 1.5);
		assertTrue(mid > 0.6 && mid < 1.5);
	}

	@Test
	void dynamicWaveRadiusExpandsWithAnimationProgress() {
		double start = CameraViewMath.dynamicWaveRadius(0f, 8, 48, 0.6, 1.5, 48);
		double end = CameraViewMath.dynamicWaveRadius(1f, 8, 48, 0.6, 1.5, 48);
		assertTrue(end > start);
	}

	@Test
	void radialWaveIsZeroOutsideViewRadius() {
		Vec3d camera = new Vec3d(0, 64, 0);
		Vec3d forward = new Vec3d(0, 0, 1);
		BlockPos near = new BlockPos(0, 64, 5);
		BlockPos far = new BlockPos(0, 64, 80);

		double nearWave = CameraViewMath.radialWaveOffsetY(
			near, camera, forward, 1f, 1f, 1.0, 0.5, 0.0,
			8, 48, 0.6, 1.5, 48, 55);
		double farWave = CameraViewMath.radialWaveOffsetY(
			far, camera, forward, 1f, 1f, 1.0, 0.5, 0.0,
			8, 48, 0.6, 1.5, 48, 55);

		assertTrue(Math.abs(nearWave) > 0.01);
		assertEquals(0.0, farWave, 1e-9);
	}

	@Test
	void isInViewRejectsBlocksBehindCamera() {
		Vec3d camera = new Vec3d(0, 64, 0);
		Vec3d forward = new Vec3d(0, 0, 1);
		assertTrue(CameraViewMath.isInView(camera, forward, new BlockPos(0, 64, 10), 64, 55));
		assertFalse(CameraViewMath.isInView(camera, forward, new BlockPos(0, 64, -10), 64, 55));
	}
}
