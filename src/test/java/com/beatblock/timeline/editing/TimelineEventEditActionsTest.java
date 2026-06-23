package com.beatblock.timeline.editing;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.command.CommandManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEventEditActionsTest {

	@Test
	void executeSubmitsUpdateCommand() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		var event = TimelineOperations.addEvent(clip, 0.5, EventType.ANIMATION, java.util.Map.of("energy", 0.5));
		CommandManager commands = new CommandManager();
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(event, clip);
		AnimationEventSnapshot after = new AnimationEventSnapshot(
			1.0, java.util.Map.of("energy", 0.9), 0.0, 2.0
		);

		assertTrue(TimelineEventEditActions.execute(
			timeline, commands, track.getId(), clip.getId(), event.getId(), before, after
		));
		assertTrue(commands.canUndo());
		commands.undo();
		assertTrue(Math.abs(0.5 - event.getTimeSeconds()) < 1e-9);
	}
}
