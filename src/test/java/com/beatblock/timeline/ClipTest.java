package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClipTest {

	@Test
	void eventsStaySortedByTime() {
		Clip clip = new Clip("c1", 0, 10);
		clip.addEvent(new TimelineEvent("e3", 3.0, EventType.ANIMATION, Map.of()));
		clip.addEvent(new TimelineEvent("e1", 1.0, EventType.ANIMATION, Map.of()));
		clip.addEvent(new TimelineEvent("e2", 2.0, EventType.ANIMATION, Map.of()));

		assertEquals("e1", clip.getEvents().get(0).getId());
		assertEquals("e3", clip.getEvents().get(2).getId());
	}

	@Test
	void startEndTimesClampEachOther() {
		Clip clip = new Clip("c1", 5, 10);
		clip.setEndTimeSeconds(3);
		assertEquals(5, clip.getStartTimeSeconds(), 1e-9);
		assertEquals(5, clip.getEndTimeSeconds(), 1e-9);
		assertEquals(0, clip.getDurationSeconds(), 1e-9);
	}

	@Test
	void removeEventReturnsWhetherRemoved() {
		Clip clip = new Clip("c1", 0, 1);
		clip.addEvent(new TimelineEvent("e1", 0, EventType.ANIMATION, Map.of()));
		assertEquals(true, clip.removeEvent("e1"));
		assertNull(clip.getEvent("e1"));
		assertEquals(false, clip.removeEvent("missing"));
	}
}
