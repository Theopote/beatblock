package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineMarkersTest {

	@Test
	void addMarkerKeepsSortedOrder() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker late = new TimelineMarker("m2", 5.0, "Late", MarkerType.DROP);
		TimelineMarker early = new TimelineMarker("m1", 1.0, "Early", MarkerType.SECTION);

		timeline.addMarker(late);
		timeline.addMarker(early);

		assertEquals(2, timeline.getMarkers().size());
		assertEquals("m1", timeline.getMarkers().getFirst().getId());
		assertEquals(MarkerType.SECTION, timeline.getMarkers().getFirst().getType());
	}

	@Test
	void updateAndRemoveMarkerById() {
		Timeline timeline = Timeline.createDefault();
		TimelineMarker marker = new TimelineMarker("mk-1", 2.0, "Intro", MarkerType.GENERIC);
		timeline.addMarker(marker);

		assertTrue(timeline.updateMarker("mk-1", 3.0, "Verse", MarkerType.SECTION));
		assertEquals("Verse", timeline.getMarkers().getFirst().getName());
		assertEquals(MarkerType.SECTION, timeline.getMarkers().getFirst().getType());

		assertTrue(timeline.removeMarker("mk-1"));
		assertTrue(timeline.getMarkers().isEmpty());
		assertFalse(timeline.removeMarker("missing"));
	}

	@Test
	void cameraAndGlobalEventsRoundTripThroughTimeline() {
		Timeline timeline = Timeline.createDefault();
		timeline.addCameraKeyframe(new CameraKeyframe(4.0));
		timeline.addGlobalEvent(new GlobalEvent(6.0, GlobalEventType.STAGE, "Act 2"));

		assertEquals(1, timeline.getCameraKeyframes().size());
		assertEquals(4.0, timeline.getCameraKeyframes().getFirst().getTimeSeconds(), 1e-9);

		assertEquals(1, timeline.getGlobalEvents().size());
		assertEquals(GlobalEventType.STAGE, timeline.getGlobalEvents().getFirst().getType());
		assertEquals("Act 2", timeline.getGlobalEvents().getFirst().getName());
	}
}
