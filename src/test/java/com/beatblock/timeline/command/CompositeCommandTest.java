package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeCommandTest {

	@Test
	void executesInOrderAndUndoesInReverse() {
		AtomicInteger counter = new AtomicInteger();
		Command first = new Command() {
			@Override public void execute() { counter.addAndGet(1); }
			@Override public void undo() { counter.addAndGet(-1); }
		};
		Command second = new Command() {
			@Override public void execute() { counter.addAndGet(10); }
			@Override public void undo() { counter.addAndGet(-10); }
		};

		CompositeCommand composite = CompositeCommand.of(first, second);
		composite.execute();
		assertEquals(11, counter.get());
		composite.undo();
		assertEquals(0, counter.get());
	}

	@Test
	void batchesTimelineEdits() {
		Timeline timeline = Timeline.createDefault();
		var eventA = new TimelineAnimationEvent("a", 1.0, 1.0, "build", "stage", 1f, Map.of());
		var eventB = new TimelineAnimationEvent("b", 3.0, 1.0, "pulse", "stage", 1f, Map.of());
		AddTimelineAnimationEventCommand addA = new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, eventA);
		AddTimelineAnimationEventCommand addB = new AddTimelineAnimationEventCommand(
			timeline, Timeline.TRACK_ID_ANIMATION_AUTO, eventB);

		CompositeCommand.of(addA, addB).execute();
		assertEquals(2, timeline.getAutoAnimationEvents().size());

		CompositeCommand.of(addA, addB).undo();
		assertEquals(0, timeline.getAutoAnimationEvents().size());
	}
}
