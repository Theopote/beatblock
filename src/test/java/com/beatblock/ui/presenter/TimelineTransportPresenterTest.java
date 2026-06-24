package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTransportPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private TimelineToolbarState toolbarState;
	private AtomicBoolean driving;
	private AtomicBoolean driveStarted;
	private TimelineTransportPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(120.0);
		timeline.setMetadata("bpm", 120.0);
		editor = new TimelineEditor(timeline);
		toolbarState = editor.getToolbarState();
		driving = new AtomicBoolean(false);
		driveStarted = new AtomicBoolean(false);
		presenter = new TimelineTransportPresenter(
			() -> editor,
			() -> timeline,
			() -> null,
			() -> null,
			new TimelineTransportPresenter.TimelineDriveControl() {
				@Override
				public boolean isDriving() {
					return driving.get();
				}

				@Override
				public void startDriving() {
					driveStarted.set(true);
					driving.set(true);
				}

				@Override
				public void stopDriving() {
					driving.set(false);
				}
			}
		);
	}

	@Test
	void viewStateComputesBeatSeekStep() {
		var state = presenter.viewState(editor, false);
		assertEquals(0.5, state.seekStep(), 1e-9);
		assertEquals(0.5, state.stepSeek(), 1e-9);
		assertFalse(state.playing());
		assertFalse(state.positionDisplay().isBlank());
	}

	@Test
	void viewStateUsesFiveSecondStepWhenShiftHeld() {
		var state = presenter.viewState(editor, true);
		assertEquals(5.0, state.stepSeek(), 1e-9);
	}

	@Test
	void seekToClampsToDuration() {
		presenter.seekTo(editor, 999);
		assertEquals(120.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void playStartsClockAndDrive() {
		presenter.play(editor);
		assertTrue(editor.getClock().isPlaying());
		assertTrue(driveStarted.get());
	}

	@Test
	void stopResetsTimeAndDrive() {
		presenter.seekTo(editor, 10);
		driving.set(true);
		presenter.stop(editor);
		assertEquals(0.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
		assertFalse(driving.get());
	}

	@Test
	void jumpToNearbyEventUsesMarkers() {
		timeline.addMarker(new TimelineMarker(2.0, "A"));
		timeline.addMarker(new TimelineMarker(8.0, "B"));
		editor.getClock().seek(5.0);
		presenter.jumpToNearbyEvent(editor, true);
		assertEquals(8.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void addMarkerAtCurrentTimeAppendsMarker() {
		editor.getClock().seek(3.25);
		assertTrue(presenter.addMarkerAtCurrentTime(editor));
		assertEquals(1, timeline.getMarkers().size());
		assertEquals(3.25, timeline.getMarkers().get(0).getTimeSeconds(), 1e-9);
	}

	@Test
	void setLoopInAtAdjustsOutWhenNeeded() {
		toolbarState.setLoopOutSeconds(1.0);
		presenter.setLoopInAt(toolbarState, 5.0, 0.5);
		assertEquals(5.0, toolbarState.getLoopInSeconds(), 1e-9);
		assertTrue(toolbarState.getLoopOutSeconds() > 5.0);
	}
}
