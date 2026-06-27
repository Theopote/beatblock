package com.beatblock.ui.eventlibrary;

import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventTemplateTest {

	@Test
	void roundTripPreservesAnimationFieldsWithoutTimeOrTarget() {
		TimelineAnimationEvent source = new TimelineAnimationEvent(
			"evt-1",
			12.5,
			0.8,
			"bounce",
			"obj_a",
			0.6f,
			Map.of("actionMode", "ANIMATE", "generatedBy", "test")
		);
		EventTemplate template = EventTemplate.fromAnimationEvent(source, "My Bounce");
		TimelineAnimationEvent applied = template.toTimelineEvent(3.0, "obj_b");

		assertEquals("My Bounce", template.name());
		assertEquals("bounce", applied.getAnimationTypeId());
		assertEquals(3.0, applied.getTimeSeconds(), 1e-6);
		assertEquals("obj_b", applied.getTargetObjectId());
		assertEquals(0.8, applied.getDurationSeconds(), 1e-6);
		assertEquals(0.6f, applied.getEnergy(), 1e-6);
		assertEquals("ANIMATE", applied.getParameters().get("actionMode"));
	}
}
