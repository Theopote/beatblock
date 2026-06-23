package com.beatblock.timeline.editor;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineHitTestTest {

	@Test
	void hitTestTimeRulerReturnsTimeHeaderInsideRuler() {
		TimelineViewState view = new TimelineViewState();
		view.setViewStartTimeSeconds(0);
		view.setZoom(100f);

		HitResult hit = TimelineHitTest.hitTestTimeRuler(
			150f, 10f, 50f, 0f, 20f, 800f, view);

		assertEquals(HitType.TIME_HEADER, hit.getHitType());
		assertEquals(1.0, hit.getTimeSeconds(), 1e-3);
	}

	@Test
	void hitTestTrackContentDetectsEventClipAndTrack() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 1.0, 3.0);
		var event = TimelineOperations.addEvent(clip, 2.0, EventType.ANIMATION, Map.of());

		TimelineViewState view = new TimelineViewState();
		view.setViewStartTimeSeconds(0);
		view.setZoom(100f);

		float contentLeft = 50f;
		float rowY = 100f;
		float rowHeight = 24f;
		float eventX = contentLeft + view.timeToScreen(2.0);

		HitResult eventHit = TimelineHitTest.hitTestTrackContent(
			timeline, track.getId(), eventX, rowY + 10f,
			contentLeft, rowY, rowHeight, 800f, view);
		assertEquals(HitType.EVENT, eventHit.getHitType());
		assertEquals(event.getId(), eventHit.getEventId());

		float clipMidX = contentLeft + view.timeToScreen(1.5);
		HitResult clipHit = TimelineHitTest.hitTestTrackContent(
			timeline, track.getId(), clipMidX, rowY + 10f,
			contentLeft, rowY, rowHeight, 800f, view);
		assertEquals(HitType.CLIP, clipHit.getHitType());

		HitResult trackHit = TimelineHitTest.hitTestTrackContent(
			timeline, track.getId(), contentLeft + 5f, rowY + 10f,
			contentLeft, rowY, rowHeight, 800f, view);
		assertEquals(HitType.TRACK, trackHit.getHitType());
	}

	@Test
	void emptyWhenOutsideRowBounds() {
		Timeline timeline = Timeline.createDefault();
		TimelineViewState view = new TimelineViewState();
		assertTrue(TimelineHitTest.hitTestTrackContent(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, 100f, 50f,
			50f, 200f, 24f, 800f, view).isEmpty());
	}
}
