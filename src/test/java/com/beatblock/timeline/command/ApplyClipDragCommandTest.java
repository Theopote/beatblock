package com.beatblock.timeline.command;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyClipDragCommandTest {

	@Test
	void executeAndUndoRestoreClipAndLinkedEventTimes() {
		Timeline timeline = Timeline.createDefault();
		var audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		var animTrack = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var audioClip = TimelineOperations.addClip(audioTrack, 1.0, 5.0);
		var animClip = TimelineOperations.addClip(animTrack, 0.0, 10.0);
		var linkedEvent = TimelineOperations.addEvent(animClip, 2.0, EventType.ANIMATION, Map.of());

		ClipDragStateSnapshot before = ClipDragStateSnapshot.capture(
			timeline,
			audioTrack.getId(),
			audioClip.getId(),
			Map.of(linkedEvent.getId(), 2.0),
			Map.of()
		);

		audioClip.setStartTimeSeconds(3.0);
		audioClip.setEndTimeSeconds(7.0);
		linkedEvent.setTimeSeconds(4.0);
		ClipDragStateSnapshot after = before.captureCurrent(timeline);

		ApplyClipDragCommand command = new ApplyClipDragCommand(timeline, before, after);
		command.undo();
		assertEquals(1.0, audioClip.getStartTimeSeconds(), 1e-9);
		assertEquals(5.0, audioClip.getEndTimeSeconds(), 1e-9);
		assertEquals(2.0, linkedEvent.getTimeSeconds(), 1e-9);

		command.execute();
		assertEquals(3.0, audioClip.getStartTimeSeconds(), 1e-9);
		assertEquals(7.0, audioClip.getEndTimeSeconds(), 1e-9);
		assertEquals(4.0, linkedEvent.getTimeSeconds(), 1e-9);
	}

	@Test
	void commitViaTimelineEventEditActionsSkipsIdenticalSnapshots() {
		Timeline timeline = Timeline.createDefault();
		var track = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		var clip = TimelineOperations.addClip(track, 0.0, 2.0);
		ClipDragStateSnapshot snapshot = ClipDragStateSnapshot.capture(
			timeline, track.getId(), clip.getId(), Map.of(), Map.of()
		);
		CommandManager commands = new CommandManager();
		assertTrue(!com.beatblock.timeline.editing.TimelineEventEditActions.commitClipDrag(
			timeline, commands, snapshot, snapshot
		));
		assertTrue(!commands.canUndo());
	}
}
