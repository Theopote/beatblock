package com.beatblock.automap;

import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMapGeneratorTest {

	@Test
	void returnsZeroWhenTimelineNull() {
		assertEquals(0, AutoMapGenerator.generate(null, AutoMapConfig.createDefault(), false));
	}

	@Test
	void returnsZeroWhenNoFeatureTracks() {
		Timeline timeline = Timeline.createDefault();
		assertEquals(0, AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false));
		assertTrue(timeline.getAutoAnimationEvents().isEmpty());
	}

	@Test
	void mapsKickFeatureKeyToLowRuleAnimation() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.5f));

		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		assertEquals(1, count);
		TimelineAnimationEvent ev = timeline.getAutoAnimationEvents().getFirst();
		assertEquals("bounce", ev.getAnimationTypeId());
		assertEquals(1.0, ev.getTimeSeconds(), 1e-6);
		assertEquals(AutoMapGenerator.DEFAULT_TARGET_ID, ev.getTargetObjectId());
	}

	@Test
	void skipsEventsBelowMinEnergy() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("low", new FeatureEvent(1.0, 0.05f));

		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		assertEquals(0, count);
	}

	@Test
	void enforcesMinGapBetweenGeneratedEvents() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("mid", new FeatureEvent(1.0, 0.5f));
		timeline.addFeatureEvent("mid", new FeatureEvent(1.05, 0.5f));
		timeline.addFeatureEvent("mid", new FeatureEvent(1.20, 0.5f));

		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		assertEquals(2, count);
		assertEquals(1.0, timeline.getAutoAnimationEvents().get(0).getTimeSeconds(), 1e-6);
		assertEquals(1.20, timeline.getAutoAnimationEvents().get(1).getTimeSeconds(), 1e-6);
	}

	@Test
	void replaceClearsPreviousAutoEvents() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("high", new FeatureEvent(2.0, 0.8f));
		AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);
		assertEquals(1, timeline.getAutoAnimationEvents().size());

		timeline.clearFeatureTracks();
		timeline.addFeatureEvent("low", new FeatureEvent(5.0, 0.9f));
		int count = AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), true);

		assertEquals(1, count);
		assertEquals(1, timeline.getAutoAnimationEvents().size());
		assertEquals("bounce", timeline.getAutoAnimationEvents().getFirst().getAnimationTypeId());
		assertEquals(5.0, timeline.getAutoAnimationEvents().getFirst().getTimeSeconds(), 1e-6);
	}

	@Test
	void mapsEnergyToHeightWhenRuleUsesHeight() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("mid", new FeatureEvent(3.0, 0.5f));

		AutoMapGenerator.generate(timeline, AutoMapConfig.createDefault(), false);

		Object height = timeline.getAutoAnimationEvents().getFirst().getParameters().get("height");
		assertTrue(height instanceof Number);
		assertEquals(1.5f, ((Number) height).floatValue(), 1e-3f);
	}
}
