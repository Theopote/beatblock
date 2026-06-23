package com.beatblock.client.selection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LassoPointInPolygonTest {

	private static List<double[]> unitSquare() {
		return List.of(
			new double[] {0, 0},
			new double[] {10, 0},
			new double[] {10, 10},
			new double[] {0, 10}
		);
	}

	@Test
	void pointInsideSquareIsDetected() {
		assertTrue(BeatBlockLassoSelector.pointInPolygon(5, 5, unitSquare()));
	}

	@Test
	void pointOutsideSquareIsRejected() {
		assertFalse(BeatBlockLassoSelector.pointInPolygon(15, 5, unitSquare()));
		assertFalse(BeatBlockLassoSelector.pointInPolygon(-1, 5, unitSquare()));
	}

	@Test
	void trianglePolygonWorks() {
		List<double[]> triangle = List.of(
			new double[] {0, 0},
			new double[] {10, 0},
			new double[] {5, 10}
		);
		assertTrue(BeatBlockLassoSelector.pointInPolygon(5, 3, triangle));
		assertFalse(BeatBlockLassoSelector.pointInPolygon(1, 9, triangle));
	}
}
