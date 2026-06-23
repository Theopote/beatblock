package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editor.TimelineViewState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CameraTrackHitTestTest {

	@Test
	void hitClipEdgeDetectsLeftAndRightEdges() {
		Timeline timeline = Timeline.createDefault();
		CameraTrackFactory.addPathSegment(timeline, 2.0, 0, 64, 0, 0, 0);
		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();

		TimelineViewState view = new TimelineViewState();
		view.setViewStartTimeSeconds(0);
		view.setZoom(100f);

		float contentLeft = 50f;
		float rowY = 200f;
		float rowHeight = 24f;
		float contentWidth = 800f;

		float leftX = contentLeft + view.timeToScreen(clip.getStartTimeSeconds());
		float rightX = contentLeft + view.timeToScreen(clip.getEndTimeSeconds());

		CameraTrackHitTest.EdgeHit left = CameraTrackHitTest.hitClipEdge(
			timeline, leftX, rowY + 10f, rowY, rowHeight,
			contentLeft, contentWidth, view, 6f);
		assertNotNull(left);
		assertEquals(clip.getId(), left.clipId());
		assertEquals(true, left.leftEdge());

		CameraTrackHitTest.EdgeHit right = CameraTrackHitTest.hitClipEdge(
			timeline, rightX, rowY + 10f, rowY, rowHeight,
			contentLeft, contentWidth, view, 6f);
		assertNotNull(right);
		assertEquals(false, right.leftEdge());
	}

	@Test
	void returnsNullOutsideRowOrContent() {
		Timeline timeline = Timeline.createDefault();
		CameraTrackFactory.addPathSegment(timeline, 1.0, 0, 64, 0, 0, 0);
		TimelineViewState view = new TimelineViewState();

		assertNull(CameraTrackHitTest.hitClipEdge(
			timeline, 100f, 50f, 200f, 24f, 50f, 800f, view, 6f));
		assertNull(CameraTrackHitTest.hitClipEdge(
			timeline, 10f, 210f, 200f, 24f, 50f, 800f, view, 6f));
	}
}
