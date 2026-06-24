package com.beatblock.timeline.interaction;

import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionBox;
import com.beatblock.timeline.editor.SelectionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineBoxSelectHandlerTest {

	@Test
	void beginClearsSelectionAndActivatesBox() {
		SelectionState selection = new SelectionState();
		selection.selectEvent("evt-a");
		selection.selectClip("clip-a");
		SelectionBox box = new SelectionBox();
		InteractionState interaction = new InteractionState();

		TimelineBoxSelectHandler.begin(selection, box, interaction, 10f, 20f);

		assertFalse(selection.isEventSelected("evt-a"));
		assertFalse(selection.isClipSelected("clip-a"));
		assertTrue(box.isActive());
		assertEquals(InteractionMode.BOX_SELECT, interaction.getMode());
		assertEquals(10f, box.getStartX(), 1e-6f);
		assertEquals(10f, box.getEndX(), 1e-6f);
	}

	@Test
	void updateEndMovesBoxCorner() {
		SelectionBox box = new SelectionBox();
		box.setStart(10f, 20f);
		box.setEnd(10f, 20f);

		TimelineBoxSelectHandler.updateEnd(box, 50f, 80f);

		assertEquals(50f, box.getEndX(), 1e-6f);
		assertEquals(80f, box.getEndY(), 1e-6f);
	}
}
