package com.beatblock.timeline.util;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapSystemTest {

	@Test
	void snapsToGridWhenWithinThreshold() {
		Timeline timeline = Timeline.createDefault();
		double snapped = SnapSystem.snap(1.04, timeline, true, 0.5, false, 0, false, null);
		assertEquals(1.0, snapped, 1e-9);
	}

	@Test
	void snapsToBeatWhenBpmProvided() {
		Timeline timeline = Timeline.createDefault();
		double snapped = SnapSystem.snap(1.02, timeline, false, 0, true, 120, false, null);
		assertEquals(1.0, snapped, 1e-9);
	}

	@Test
	void magnetSnapsToTimelineEventMarkerAndClipEdge() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new TimelineMarker("m1", 2.0, "Hit", MarkerType.GENERIC));
		timeline.addFeatureEvent("kick", new FeatureEvent(4.0, 1f));

		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		Clip clip = TimelineOperations.addClip(cam, 6.0, 8.0);
		TimelineOperations.addEvent(clip, 7.0, EventType.CAMERA_KEYFRAME, java.util.Map.of());

		assertEquals(2.0, SnapSystem.snap(2.05, timeline, false, 0, false, 0, true, null), 1e-9);
		assertEquals(4.0, SnapSystem.snap(3.97, timeline, false, 0, false, 0, true, null), 1e-9);
		assertEquals(6.0, SnapSystem.snap(5.94, timeline, false, 0, false, 0, true, null), 1e-9);
		assertEquals(8.0, SnapSystem.snap(8.06, timeline, false, 0, false, 0, true, null), 1e-9);
		assertEquals(7.0, SnapSystem.snap(6.93, timeline, false, 0, false, 0, true, null), 1e-9);
	}

	@Test
	void excludesEventIdFromMagnetTargets() {
		Timeline timeline = Timeline.createDefault();
		Track block = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		Clip clip = TimelineOperations.addClip(block, 1.0, 2.0);
		var event = TimelineOperations.addEvent(clip, 1.5, EventType.ANIMATION, java.util.Map.of());

		double snapped = SnapSystem.snap(1.48, timeline, false, 0, false, 0, true, event.getId());
		assertEquals(1.48, snapped, 1e-9);
	}

	@Test
	void snapWithGuidesReturnsAlignedReferenceTimes() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new TimelineMarker("m1", 3.0, "A", MarkerType.GENERIC));

		SnapSystem.SnapResult result = SnapSystem.snapWithGuides(
			3.04, timeline, false, 0, false, 0, true, null);

		assertEquals(3.0, result.timeSeconds(), 1e-9);
		assertArrayEquals(new double[] {3.0}, result.guideTimes(), 1e-9);
	}

	@Test
	void returnsOriginalTimeWhenNothingWithinThreshold() {
		SnapSystem.SnapResult result = SnapSystem.snapWithGuides(
			9.99, Timeline.createDefault(), false, 0, false, 0, true, null);
		assertEquals(9.99, result.timeSeconds(), 1e-9);
		assertEquals(0, result.guideTimes().length);
	}
}
