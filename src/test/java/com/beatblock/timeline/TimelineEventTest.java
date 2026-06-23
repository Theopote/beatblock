package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimelineEventTest {

	@Test
	void parametersAreImmutableView() {
		TimelineEvent event = new TimelineEvent("e1", 1.0, EventType.ANIMATION, Map.of("key", "value"));
		assertEquals("value", event.getParameter("key"));
		event.setParameter("added", 42);
		assertEquals(42, event.getParameter("added"));
		event.removeParameter("key");
		assertNull(event.getParameter("key"));
	}

	@Test
	void clampsNegativeTimeToZero() {
		TimelineEvent event = new TimelineEvent("e1", -5.0, EventType.GLOBAL, Map.of());
		assertEquals(0.0, event.getTimeSeconds(), 1e-9);
	}
}
