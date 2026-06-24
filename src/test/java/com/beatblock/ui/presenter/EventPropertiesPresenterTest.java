package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editing.AnimationEventFormInput;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPropertiesPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private CommandManager commandManager;
	private EventPropertiesPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		commandManager = editor.getCommandManager();
		presenter = new EventPropertiesPresenter(
			id -> "target-1".equals(id),
			blockId -> blockId != null && blockId.startsWith("minecraft:"),
			() -> List.of(new EventPropertiesOption("", "未绑定")),
			() -> List.of(new EventPropertiesOption("", "未绑定")),
			() -> new EventPropertiesPresenter.CameraViewSample(1, 2, 3, 90f, 0f)
		);
	}

	@Test
	void resolvePropertiesRefPrefersSelectedEvent() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = TimelineOperations.addEvent(
			clip,
			1.0,
			com.beatblock.timeline.EventType.ANIMATION,
			Map.of("animationType", "pulse", "targetObject", "target-1")
		);
		String eventId = event.getId();

		SelectionState selection = editor.getSelectionState();
		selection.selectEvent(eventId);

		EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, selection);
		assertNotNull(ref);
		assertEquals(eventId, ref.event().getId());
		assertEquals(track.getId(), ref.track().getId());
	}

	@Test
	void resolvePropertiesRefFromSelectedCameraClipWithoutEvent() {
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		assertNotNull(cam);
		var clip = TimelineOperations.addClip(cam, 0.0, 4.0);

		SelectionState selection = editor.getSelectionState();
		selection.selectClip(clip.getId());

		EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, selection);
		assertNotNull(ref);
		assertEquals(clip.getId(), ref.clip().getId());
		assertNull(ref.event());
	}

	@Test
	void isTrackLockedReflectsTrackListState() {
		TimelineTrackListState trackListState = editor.getTrackListState();
		trackListState.setLocked(TimelineTrackMeta.ROW_CAMERA, true);

		assertTrue(presenter.isTrackLocked(timeline, editor, Timeline.TRACK_ID_CAMERA));
		assertFalse(presenter.isTrackLocked(timeline, editor, Timeline.TRACK_ID_AUDIO));
	}

	@Test
	void applyAnimationEventUpdatesEventParameters() {
		Track track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		var event = TimelineOperations.addEvent(
			clip,
			0.0,
			com.beatblock.timeline.EventType.ANIMATION,
			Map.of("animationType", "old", "targetObject", "target-1")
		);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, event);

		AnimationEventFormInput input = new AnimationEventFormInput(
			1.5,
			0.5,
			0.8f,
			0.2f,
			"ANIMATE",
			"pulse",
			"target-1",
			true,
			"ALL",
			0.0,
			false,
			"NEXT_BEAT",
			"KEEP",
			"BEAT_GRID",
			1,
			0.25,
			0.05,
			false,
			false,
			0.0,
			false,
			20.0,
			60.0,
			20.0,
			8.0,
			48.0,
			0.6,
			1.5,
			"minecraft:diamond_block",
			"minecraft:gold_block",
			true
		);

		var result = presenter.applyAnimationEvent(ref, timeline, commandManager, input);
		assertTrue(result instanceof EventPropertiesPresenter.ApplyResult.Ok);
		assertEquals(1.5, event.getTimeSeconds(), 1e-9);
		assertEquals("pulse", event.getParameters().get("animationType"));
		assertEquals("target-1", event.getParameters().get("targetObject"));
	}

	@Test
	void captureSegmentViewParamsReturnsDollyDefaultsFromCameraProvider() {
		Optional<Map<String, String>> captured = presenter.captureSegmentViewParams(
			com.beatblock.timeline.camera.CameraSegmentKind.DOLLY
		);
		assertTrue(captured.isPresent());
		assertEquals("1.000000", captured.get().get("startX"));
		assertEquals("90.000", captured.get().get("baseYawDeg"));
	}

	@Test
	void actionOptionsIncludeAllModes() {
		List<EventPropertiesOption> options = presenter.actionOptions();
		assertFalse(options.isEmpty());
		assertTrue(options.stream().anyMatch(option -> "ANIMATE".equals(option.id())));
		assertTrue(options.stream().anyMatch(option -> "BUILD".equals(option.id())));
	}
}
