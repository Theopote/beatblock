package com.beatblock.timeline.binding;

import com.beatblock.timeline.TimelineAnimationActionMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationBindingRuleTest {

	@Test
	void isValidRequiresEnabledSourceAnimationAndTarget() {
		AnimationBindingRule valid = AnimationBindingRule.builder()
			.sourceFeatureKey("kick")
			.animationTypeId("Pulse")
			.targetObjectId("stage-a")
			.build();
		assertTrue(valid.isValid());

		AnimationBindingRule missingTarget = AnimationBindingRule.builder()
			.sourceFeatureKey("kick")
			.animationTypeId("Pulse")
			.targetObjectId("")
			.build();
		assertFalse(missingTarget.isValid());
	}

	@Test
	void fromMapNormalizesLegacyAnimationAliases() {
		AnimationBindingRule rule = AnimationBindingRule.fromMap(Map.of(
			"sourceFeatureKey", "snare",
			"animationTypeId", "slide",
			"targetObjectId", "stage-b",
			"actionMode", "BUILD"
		));
		assertEquals("Orbit", rule.animationTypeId());
		assertEquals(TimelineAnimationActionMode.BUILD, rule.actionMode());
		assertEquals("snare", rule.sourceFeatureKey());
	}

	@Test
	void toMapRoundTripsCoreFields() {
		AnimationBindingRule original = AnimationBindingRule.builder()
			.id("rule-1")
			.name("Kick Rule")
			.sourceFeatureKey("kick")
			.animationTypeId("BlockJump")
			.targetObjectId("stage-a")
			.energyThreshold(0.4f)
			.spatialMode(SpatialDispatchMode.SEQUENTIAL)
			.build();

		AnimationBindingRule restored = AnimationBindingRule.fromMap(original.toMap());
		assertEquals("rule-1", restored.id());
		assertEquals("Kick Rule", restored.name());
		assertEquals(SpatialDispatchMode.SEQUENTIAL, restored.spatialMode());
		assertEquals(0.4f, restored.energyThreshold(), 1e-6);
	}
}
