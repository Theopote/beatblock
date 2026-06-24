package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.HitType;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineClipDragSessionTest {

	@Test
	void beginAudioClipDragSnapshotsLinkedEventsInRange() {
		Timeline timeline = Timeline.createDefault();
		Clip audioClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 1.0, 4.0);
		Clip blockClip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, 0, 10);
		TimelineEvent inRange = TimelineOperations.addEvent(blockClip, 2.5, EventType.ANIMATION, Map.of());
		TimelineEvent outOfRange = TimelineOperations.addEvent(blockClip, 5.0, EventType.ANIMATION, Map.of());

		InteractionState state = new InteractionState();
		TimelineViewState viewState = new TimelineViewState();
		viewState.setZoom(100f);
		TimelineLayout layout = new TimelineLayout();
		layout.contentLeft = 0f;

		TimelineClipDragSession session = TimelineClipDragSession.beginAudioClipDrag(
			timeline, Timeline.TRACK_ID_AUDIO, audioClip.getId(), audioClip,
			state, viewState, layout, 150f, 100f);

		assertEquals(1.0, session.initialStart(), 1e-9);
		assertEquals(4.0, session.initialEnd(), 1e-9);
		assertNotNull(session.undoSnapshot());
		assertTrue(session.linkedEventOriginalTimes().containsKey(inRange.getId()));
		assertFalse(session.linkedEventOriginalTimes().containsKey(outOfRange.getId()));
		assertEquals(InteractionMode.DRAG_CLIP, state.getMode());
	}

	@Test
	void beginCameraClipDragSnapshotsInClipEvents() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_CAMERA, 1.0, 5.0);
		TimelineEvent event = TimelineOperations.addEvent(clip, 2.0, EventType.CAMERA_KEYFRAME, Map.of());

		InteractionState state = new InteractionState();
		TimelineViewState viewState = new TimelineViewState();
		viewState.setZoom(100f);
		TimelineLayout layout = new TimelineLayout();
		layout.contentLeft = 0f;

		TimelineClipDragSession session = TimelineClipDragSession.beginCameraClipDrag(
			timeline, Timeline.TRACK_ID_CAMERA, clip.getId(), clip,
			state, viewState, layout, 200f, 100f);

		assertEquals(1, session.cameraClipEventOriginalTimes().size());
		assertEquals(2.0, session.cameraClipEventOriginalTimes().get(event.getId()), 1e-9);
	}

	@Test
	void tryBeginFromClipHitReturnsNullForEventHit() {
		Timeline timeline = Timeline.createDefault();
		Clip clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 0, 3);
		HitResult hit = new HitResult(HitType.CLIP, Timeline.TRACK_ID_AUDIO, clip.getId(), "evt-1", 0);

		TimelineClipDragSession session = TimelineClipDragCoordinator.tryBeginFromClipHit(
			timeline, hit, clip, new InteractionState(), new TimelineViewState(), new TimelineLayout(), 0f, 0f);

		assertEquals(null, session);
	}
}
