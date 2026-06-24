package com.beatblock.timeline.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineDenseFeatureApplierTest {

	@Test
	void shutdownIsIdempotent() {
		TimelineDenseFeatureApplier applier = new TimelineDenseFeatureApplier();
		applier.shutdown();
		applier.shutdown();
	}
}
