package com.beatblock.timeline.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitResultTest {

	@Test
	void factoryMethodsSetExpectedTypes() {
		HitResult empty = HitResult.empty();
		assertTrue(empty.isEmpty());

		HitResult header = HitResult.timeHeader(3.5);
		assertEquals(HitType.TIME_HEADER, header.getHitType());
		assertEquals(3.5, header.getTimeSeconds(), 1e-9);

		HitResult event = HitResult.event("track", "clip", "evt", 2.0);
		assertEquals(HitType.EVENT, event.getHitType());
		assertEquals("evt", event.getEventId());

		HitResult clip = HitResult.clip("track", "clip");
		assertEquals(HitType.CLIP, clip.getHitType());

		HitResult track = HitResult.track("track");
		assertEquals(HitType.TRACK, track.getHitType());
	}
}
