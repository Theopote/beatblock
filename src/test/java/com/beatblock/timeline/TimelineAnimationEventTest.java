package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineAnimationEventTest {

	@Test
	void clampsDurationAndEnergy() {
		var event = new TimelineAnimationEvent(
			"ev", 1.0, 0.001, "pulse", "stage", 2.5f, Map.of());
		assertEquals(0.01, event.getDurationSeconds(), 1e-9);
		assertEquals(1.0f, event.getEnergy(), 1e-6);
		assertEquals(1.01, event.getEndTimeSeconds(), 1e-9);
	}

	@Test
	void resolvesActionModeFromParameters() {
		var build = new TimelineAnimationEvent(
			"ev", 0, 1, "build", "s", 1f, Map.of("actionMode", "BUILD"));
		assertEquals(TimelineAnimationActionMode.BUILD, build.getActionMode());

		var animate = new TimelineAnimationEvent(
			"ev", 0, 1, "pulse", "s", 1f, Map.of("mode", "ANIMATE"));
		assertEquals(TimelineAnimationActionMode.ANIMATE, animate.getActionMode());
	}

	@Test
	void resolvesEventOrigin() {
		var auto = new TimelineAnimationEvent(
			"ev", 0, 1, "build", "s", 1f,
			Map.of("eventOrigin", TimelineEventOrigin.AUTO_GENERATED.name()));
		assertEquals(TimelineEventOrigin.AUTO_GENERATED, auto.getEventOrigin());
	}
}
