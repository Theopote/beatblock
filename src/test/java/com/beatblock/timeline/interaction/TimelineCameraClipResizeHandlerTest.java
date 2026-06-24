package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.editor.TimelineViewState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineCameraClipResizeHandlerTest {

	@Test
	void beginSessionCapturesUndoSnapshot() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 1.0, 4.0);
		TimelineOperations.addEvent(clip, 2.0, com.beatblock.timeline.EventType.CAMERA_KEYFRAME, java.util.Map.of());

		TimelineCameraClipResizeHandler.Session session =
			TimelineCameraClipResizeHandler.beginSession(timeline, clip, clip.getId());

		assertEquals(1.0, session.initialStart(), 1e-9);
		assertEquals(4.0, session.initialEnd(), 1e-9);
		assertNotNull(session.undoSnapshot());
		assertEquals(1, session.eventOrigTimes().size());
	}

	@Test
	void applyDuringDragExtendsClipEnd() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 1.0, 3.0);
		TimelineCameraClipResizeHandler.Session session =
			TimelineCameraClipResizeHandler.beginSession(timeline, clip, clip.getId());

		InteractionState state = new InteractionState();
		state.setActiveClipId(clip.getId());
		state.setResizeLeft(false);

		TimelineViewState viewState = new TimelineViewState();
		viewState.setZoom(100f);
		TimelineLayout layout = new TimelineLayout();
		layout.contentLeft = 0f;
		layout.contentWidth = 800f;
		float mx = layout.contentLeft + viewState.timeToScreen(5.0);

		TimelineCameraClipResizeHandler.applyDuringDrag(
			timeline, session, state, viewState, new TimelineToolbarState(), layout, mx);

		assertTrue(clip.getEndTimeSeconds() > 3.0);
		assertTrue(timeline.getDurationSeconds() >= clip.getEndTimeSeconds());
	}
}
