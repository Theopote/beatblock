package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineToolbarActionsPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private TimelineToolbarActionsPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		presenter = new TimelineToolbarActionsPresenter(
			() -> timeline,
			() -> editor,
			() -> Vec3d.ZERO
		);
	}

	@Test
	void runBindingMapReturnsOutcomeOnEmptyTimeline() {
		var outcome = presenter.runBindingMap();
		assertTrue(outcome.message().contains("Binding Map"));
	}

	@Test
	void runAutoMapReturnsOutcomeOnEmptyTimeline() {
		var outcome = presenter.runAutoMap();
		assertTrue(outcome.message().contains("Auto Map"));
	}

	@Test
	void runBakeStepReportsNothingToBake() {
		var outcome = presenter.runBakeStepSequences();
		assertFalse(outcome.success());
		assertTrue(outcome.message().contains("Bake STEP"));
	}

	@Test
	void runBindingMapFailsWhenTimelineMissing() {
		var missing = new TimelineToolbarActionsPresenter(() -> null, () -> editor, () -> Vec3d.ZERO);
		var outcome = missing.runBindingMap();
		assertFalse(outcome.success());
	}
}
