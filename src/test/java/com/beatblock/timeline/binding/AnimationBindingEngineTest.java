package com.beatblock.timeline.binding;

import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationBindingEngineTest {

	@Test
	void applyRulesGeneratesAutoTrackEventFromFeature() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("kick", new FeatureEvent(2.0, 0.6f));
		AnimationBindingRule rule = AnimationBindingRule.builder()
			.name("Kick Bounce")
			.sourceFeatureKey("kick")
			.animationTypeId("BlockJump")
			.targetObjectId("stage-a")
			.energyThreshold(0.3f)
			.probability(1.0f)
			.cooldownSeconds(0.0)
			.durationSeconds(0.4)
			.build();
		AnimationBindingEngine.saveRules(timeline, List.of(rule));

		int added = AnimationBindingEngine.applyRules(timeline, TimelineTrackMeta.ROW_ANIM_AUTO, false);

		assertEquals(1, added);
		TimelineAnimationEvent ev = timeline.getAutoAnimationEvents().getFirst();
		assertEquals("BlockJump", ev.getAnimationTypeId());
		assertEquals(2.0, ev.getTimeSeconds(), 1e-6);
		assertEquals("stage-a", ev.getTargetObjectId());
	}

	@Test
	void skipsFeatureEventsBelowEnergyThreshold() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("snare", new FeatureEvent(1.0, 0.1f));
		AnimationBindingRule rule = AnimationBindingRule.builder()
			.sourceFeatureKey("snare")
			.animationTypeId("slide")
			.targetObjectId("stage-a")
			.energyThreshold(0.5f)
			.probability(1.0f)
			.build();
		AnimationBindingEngine.saveRules(timeline, List.of(rule));

		assertEquals(0, AnimationBindingEngine.applyRules(timeline, TimelineTrackMeta.ROW_ANIM_AUTO, false));
	}

	@Test
	void respectsCooldownBetweenEventsForSameRule() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("hihat", new FeatureEvent(1.0, 0.9f));
		timeline.addFeatureEvent("hihat", new FeatureEvent(1.1, 0.9f));
		timeline.addFeatureEvent("hihat", new FeatureEvent(1.5, 0.9f));
		AnimationBindingRule rule = AnimationBindingRule.builder()
			.sourceFeatureKey("hihat")
			.animationTypeId("pulse")
			.targetObjectId("stage-a")
			.energyThreshold(0.1f)
			.probability(1.0f)
			.cooldownSeconds(0.25)
			.build();
		AnimationBindingEngine.saveRules(timeline, List.of(rule));

		int added = AnimationBindingEngine.applyRules(timeline, TimelineTrackMeta.ROW_ANIM_AUTO, false);

		assertEquals(2, added);
		assertEquals(1.0, timeline.getAutoAnimationEvents().get(0).getTimeSeconds(), 1e-6);
		assertEquals(1.5, timeline.getAutoAnimationEvents().get(1).getTimeSeconds(), 1e-6);
	}

	@Test
	void writesBlockFeatureTrackWhenTargetRowIsBlockTrack() {
		Timeline timeline = Timeline.createDefault();
		timeline.addFeatureEvent("bass", new FeatureEvent(0.5, 0.7f));
		AnimationBindingRule rule = AnimationBindingRule.builder()
			.sourceFeatureKey("bass")
			.animationTypeId("bounce")
			.targetObjectId("stage-a")
			.energyThreshold(0.2f)
			.probability(1.0f)
			.build();
		AnimationBindingEngine.saveRules(timeline, List.of(rule));

		int added = AnimationBindingEngine.applyRules(timeline, TimelineTrackMeta.ROW_ANIM_BLOCK, false);

		assertEquals(1, added);
		assertTrue(timeline.getTrack(Timeline.blockAnimationFeatureTrackId("bass")) != null);
		assertEquals(1, timeline.getAnimationEvents(Timeline.blockAnimationFeatureTrackId("bass")).size());
	}

	@Test
	void sectionFilterLimitsEventsToMatchingMarkerRegion() {
		Timeline timeline = Timeline.createDefault();
		timeline.addMarker(new TimelineMarker("m1", 0.0, "SECTION intro", MarkerType.SECTION));
		timeline.addMarker(new TimelineMarker("m2", 4.0, "SECTION drop", MarkerType.SECTION));
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.8f));
		timeline.addFeatureEvent("kick", new FeatureEvent(5.0, 0.8f));
		AnimationBindingRule introOnly = AnimationBindingRule.builder()
			.sourceFeatureKey("kick")
			.animationTypeId("BlockJump")
			.targetObjectId("stage-a")
			.energyThreshold(0.1f)
			.probability(1.0f)
			.sectionFilter("intro")
			.build();
		AnimationBindingEngine.saveRules(timeline, List.of(introOnly));

		int added = AnimationBindingEngine.applyRules(timeline, TimelineTrackMeta.ROW_ANIM_AUTO, false);

		assertEquals(1, added);
		assertEquals(1.0, timeline.getAutoAnimationEvents().getFirst().getTimeSeconds(), 1e-6);
	}
}
