package com.beatblock.timeline.command;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteEventCommandTest {

	@Test
	void executeRemovesEventAndUndoRestores() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_BLOCK);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of("animationType", "build"));
		String eventId = event.getId();

		DeleteEventCommand command = new DeleteEventCommand(timeline, track.getId(), clip.getId(), event);
		command.execute();
		assertNull(clip.getEvent(eventId));
		assertTrue(clip.getEvents().isEmpty());

		command.undo();
		assertEquals(1, clip.getEvents().size());
		assertEquals(eventId, clip.getEvents().getFirst().getId());
		assertEquals("build", clip.getEvents().getFirst().getParameter("animationType"));
	}
}
