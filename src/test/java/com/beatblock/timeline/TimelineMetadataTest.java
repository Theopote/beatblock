package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineMetadataTest {

	@Test
	void getBpmReadsNumericMetadata() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("bpm", 128.0);
		assertEquals(128.0, timeline.getBpm(), 1e-9);
	}

	@Test
	void getBpmReturnsZeroWhenUnset() {
		assertEquals(0.0, Timeline.createDefault().getBpm(), 1e-9);
	}

	@Test
	void blockAnimationFeatureTrackHelpers() {
		String trackId = Timeline.blockAnimationFeatureTrackId("kick");
		assertTrue(Timeline.isBlockAnimationFeatureTrackId(trackId));
		assertEquals("kick", Timeline.blockAnimationFeatureKeyFromTrackId(trackId));
	}
}
