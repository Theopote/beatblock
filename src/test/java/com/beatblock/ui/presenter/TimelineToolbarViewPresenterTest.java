package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineToolbarViewPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(90.0);
		editor = new TimelineEditor(timeline);
	}

	@Test
	void indexOfClosestZoomFindsNearestPreset() {
		assertEquals(2, TimelineToolbarViewPresenter.indexOfClosestZoom(10f));
		assertEquals(3, TimelineToolbarViewPresenter.indexOfClosestZoom(19f));
	}

	@Test
	void indexOfClosestSpeedFindsNearestPreset() {
		assertEquals(2, TimelineToolbarViewPresenter.indexOfClosestSpeed(1.0));
		assertEquals(4, TimelineToolbarViewPresenter.indexOfClosestSpeed(1.4));
	}

	@Test
	void applyZoomPresetUpdatesViewState() {
		assertTrue(TimelineToolbarViewPresenter.applyZoomPreset(editor, 4));
		assertEquals(TimelineToolbarViewPresenter.ZOOM_PRESET_VALUES[4], editor.getViewState().getZoom(), 1e-6f);
	}

	@Test
	void applySpeedPresetUpdatesClock() {
		TimelineTransportPresenter transport = new TimelineTransportPresenter(
			() -> editor, () -> timeline, () -> null, () -> null, null);
		assertTrue(TimelineToolbarViewPresenter.applySpeedPreset(editor, transport, 5));
		assertEquals(2.0, editor.getClock().getPlaybackSpeed(), 1e-9);
	}

	@Test
	void fitToDurationUsesTimelineDuration() {
		assertTrue(TimelineToolbarViewPresenter.fitToDuration(editor, timeline, 400f));
		assertTrue(editor.getViewState().getZoom() > 0f);
	}

	@Test
	void applyZoomPresetRejectsInvalidIndex() {
		assertFalse(TimelineToolbarViewPresenter.applyZoomPreset(editor, -1));
	}

	@Test
	void trackHeightViewStateReflectsEditorTrackList() {
		var state = TimelineToolbarViewPresenter.trackHeightViewState(editor);
		assertEquals(editor.getTrackListState().getAudioRowHeightMin(), state.min(), 1e-6f);
		assertEquals(editor.getTrackListState().getAudioRowHeightMax(), state.max(), 1e-6f);
		assertEquals(editor.getTrackListState().getAudioRowHeight(), state.current(), 1e-6f);
	}

	@Test
	void setTrackHeightClampsToRange() {
		float max = editor.getTrackListState().getAudioRowHeightMax();
		assertTrue(TimelineToolbarViewPresenter.setTrackHeight(editor, max + 100f));
		assertEquals(max, editor.getTrackListState().getAudioRowHeight(), 1e-6f);
	}

	@Test
	void resetTrackHeightRestoresDefault() {
		assertTrue(TimelineToolbarViewPresenter.setTrackHeight(editor, 48f));
		assertTrue(TimelineToolbarViewPresenter.resetTrackHeight(editor));
		assertTrue(editor.getTrackListState().isAudioRowHeightDefault());
	}
}
