package com.beatblock.timeline.editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineViewStateTest {

	private TimelineViewState view;

	@BeforeEach
	void setUp() {
		view = new TimelineViewState();
	}

	@Test
	void timeAndScreenConvertSymmetrically() {
		view.setViewStartTimeSeconds(10.0);
		view.setZoom(20f);
		assertEquals(30f, view.timeToScreen(11.5), 1e-3);
		assertEquals(11.5, view.screenToTime(30f), 1e-9);
	}

	@Test
	void fitToDurationSetsZoomFromWidth() {
		view.fitToDuration(120.0, 600f);
		assertEquals(0.0, view.getViewStartTimeSeconds(), 1e-9);
		assertEquals(120.0, view.getViewEndTimeSeconds(), 1e-9);
		assertEquals(5f, view.getZoom(), 1e-3);
	}

	@Test
	void zoomAtKeepsAnchorTimeStable() {
		view.setViewStartTimeSeconds(0);
		view.setZoom(10f);
		double anchorTime = view.screenToTime(50f);
		view.zoomAt(anchorTime, 50f, 20f);
		assertEquals(anchorTime, view.screenToTime(50f), 1e-6);
	}

	@Test
	void panShiftsViewStart() {
		view.setViewStartTimeSeconds(5.0);
		view.setZoom(10f);
		view.pan(20f);
		assertEquals(3.0, view.getViewStartTimeSeconds(), 1e-9);
	}
}
