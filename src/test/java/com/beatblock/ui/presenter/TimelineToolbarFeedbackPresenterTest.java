package com.beatblock.ui.presenter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineToolbarFeedbackPresenterTest {

	private AtomicLong clock;
	private TimelineToolbarFeedbackPresenter presenter;

	@BeforeEach
	void setUp() {
		clock = new AtomicLong(1_000_000L);
		presenter = new TimelineToolbarFeedbackPresenter(clock::get);
	}

	@Test
	void emptyWhenNothingSet() {
		assertFalse(presenter.viewToolActionFeedback().visible());
		assertFalse(presenter.viewTemplateApplyFeedback().visible());
	}

	@Test
	void toolActionFeedbackVisibleDuringHold() {
		presenter.setToolActionFeedback("Auto Map generated 3 events", true);
		clock.set(1_000_500L);
		var state = presenter.viewToolActionFeedback();
		assertTrue(state.visible());
		assertTrue(state.success());
		assertEquals("Auto Map generated 3 events", state.message());
		assertEquals(1.0f, state.alpha(), 1e-6f);
	}

	@Test
	void toolActionFeedbackFadesAfterHold() {
		presenter.setToolActionFeedback("Bake STEP: nothing baked", false);
		clock.set(1_000_000L + TimelineToolbarFeedbackPresenter.HOLD_MS + 650L);
		var state = presenter.viewToolActionFeedback();
		assertTrue(state.visible());
		assertFalse(state.success());
		assertTrue(state.alpha() > 0f && state.alpha() < 1f);
	}

	@Test
	void toolActionFeedbackClearsAfterTtl() {
		presenter.setToolActionFeedback("done", true);
		clock.set(1_000_000L + TimelineToolbarFeedbackPresenter.HOLD_MS
			+ TimelineToolbarFeedbackPresenter.FADE_MS);
		assertFalse(presenter.viewToolActionFeedback().visible());
		assertFalse(presenter.viewToolActionFeedback().visible());
	}

	@Test
	void templateApplyFeedbackIsIndependentFromToolAction() {
		presenter.setToolActionFeedback("tool", true);
		presenter.setTemplateApplyFeedback("template", false);

		assertTrue(presenter.viewToolActionFeedback().success());
		assertFalse(presenter.viewTemplateApplyFeedback().success());
		assertEquals("tool", presenter.viewToolActionFeedback().message());
		assertEquals("template", presenter.viewTemplateApplyFeedback().message());
	}
}
