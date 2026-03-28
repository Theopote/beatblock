package com.beatblock.timeline.binding;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TrackRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将音频特征轨道按照绑定规则转换为动画事件。
 */
public final class AnimationBindingEngine {

	public static final String METADATA_RULES_KEY = "animationBindingRules";
	public static final String GENERATED_BY_MARK = "audio-binding-rule";

	private AnimationBindingEngine() {}

	public static int applyRules(Timeline timeline, int targetRowIndex, boolean createDefaultsIfMissing) {
		if (timeline == null) return 0;
		boolean toBlockTrack = targetRowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK;
		boolean toAutoTrack = targetRowIndex == TimelineTrackMeta.ROW_ANIM_AUTO;
		if (!toBlockTrack && !toAutoTrack) return 0;

		List<AnimationBindingRule> rules = loadRules(timeline);
		if (rules.isEmpty() && createDefaultsIfMissing) {
			rules = createDefaultRules(timeline);
			saveRules(timeline, rules);
		}
		if (rules.isEmpty()) return 0;

		if (toBlockTrack) {
			pruneGeneratedEventsOnFeatureTracks(timeline);
			pruneGeneratedEventsOnTrack(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK);
		} else {
			pruneGeneratedEventsOnTrack(timeline, Timeline.TRACK_ID_ANIMATION_AUTO);
		}

		int added = 0;
		Map<String, Double> lastAcceptedByRule = new HashMap<>();
		for (AnimationBindingRule rule : rules) {
			if (rule == null || !rule.isValid()) continue;
			FeatureTrack sourceTrack = timeline.getFeatureTracks().get(rule.sourceFeatureKey());
			if (sourceTrack == null || sourceTrack.getEvents().isEmpty()) continue;

			for (FeatureEvent event : sourceTrack.getEvents()) {
				if (event == null) continue;
				if (!passesThreshold(rule, event)) continue;
				if (!passesProbability(rule, event)) continue;
				if (!passesCooldown(rule, event, lastAcceptedByRule)) continue;

				Map<String, Object> params = new HashMap<>();
				params.put("generatedBy", GENERATED_BY_MARK);
				params.put("bindingRuleId", rule.id());
				params.put("bindingRuleName", rule.name());
				params.put("sourceFeature", rule.sourceFeatureKey());
				params.put("energyThreshold", rule.energyThreshold());
				params.put("energyScale", rule.energyScale());
				params.put("probability", rule.probability());
				params.put("cooldownSeconds", rule.cooldownSeconds());
				params.put("actionMode", rule.actionMode().name());
				params.put("mode", rule.actionMode().name());
				params.put("spatialMode", rule.spatialMode().name());
				params.put("sequentialDelaySeconds", rule.sequentialDelaySeconds());
				if (!rule.sectionFilter().isBlank()) params.put("sectionFilter", rule.sectionFilter());
				params.putAll(rule.extraParams());

				float energy = clamp01(event.getEnergy() * rule.energyScale());
				TimelineAnimationEvent generated = new TimelineAnimationEvent(
					"",
					event.getTimeSeconds(),
					rule.durationSeconds(),
					rule.animationTypeId(),
					rule.targetObjectId(),
					energy,
					params
				);

				if (toBlockTrack) {
					ensureFeatureTrack(timeline, rule.sourceFeatureKey());
					timeline.addAnimationEvent(Timeline.blockAnimationFeatureTrackId(rule.sourceFeatureKey()), generated);
				} else {
					timeline.addAutoAnimationEvent(generated);
				}
				lastAcceptedByRule.put(rule.id(), event.getTimeSeconds());
				added++;
			}
		}
		timeline.sortAll();
		return added;
	}

	public static List<AnimationBindingRule> loadRules(Timeline timeline) {
		if (timeline == null) return List.of();
		Object raw = timeline.getMetadata(METADATA_RULES_KEY);
		if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
		List<AnimationBindingRule> rules = new ArrayList<>();
		for (Object item : list) {
			if (!(item instanceof Map<?, ?> m)) continue;
			Map<String, Object> cast = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : m.entrySet()) {
				if (e.getKey() == null) continue;
				cast.put(String.valueOf(e.getKey()), e.getValue());
			}
			AnimationBindingRule rule = AnimationBindingRule.fromMap(cast);
			if (rule != null) rules.add(rule);
		}
		return List.copyOf(rules);
	}

	public static void saveRules(Timeline timeline, List<AnimationBindingRule> rules) {
		if (timeline == null) return;
		if (rules == null || rules.isEmpty()) {
			timeline.setMetadata(METADATA_RULES_KEY, null);
			return;
		}
		List<Map<String, Object>> encoded = new ArrayList<>();
		for (AnimationBindingRule rule : rules) {
			if (rule != null) encoded.add(rule.toMap());
		}
		timeline.setMetadata(METADATA_RULES_KEY, encoded);
	}

	public static List<AnimationBindingRule> createDefaultRules(Timeline timeline) {
		if (timeline == null || timeline.getFeatureTracks().isEmpty()) return List.of();
		String targetObjectId = resolveDefaultTargetObjectId();
		if (targetObjectId.isBlank()) return List.of();

		List<AnimationBindingRule> rules = new ArrayList<>();
		for (String key : timeline.getFeatureTracks().keySet()) {
			AnimationBindingRule rule = defaultRuleForFeature(key, targetObjectId);
			if (rule != null) rules.add(rule);
		}
		rules.sort(Comparator.comparing(AnimationBindingRule::sourceFeatureKey));
		return rules;
	}

	private static AnimationBindingRule defaultRuleForFeature(String featureKey, String targetObjectId) {
		if (featureKey == null || featureKey.isBlank()) return null;
		String key = featureKey.trim().toLowerCase(Locale.ROOT);
		AnimationBindingRule.Builder b = AnimationBindingRule.builder()
			.name("Bind " + key)
			.sourceFeatureKey(key)
			.targetObjectId(targetObjectId)
			.actionMode(com.beatblock.timeline.TimelineAnimationActionMode.ANIMATE)
			.probability(1.0f)
			.energyScale(1.0f);

		switch (key) {
			case "kick", "low" -> {
				b.animationTypeId("BlockJump").energyThreshold(0.30f).durationSeconds(0.55).cooldownSeconds(0.08)
					.spatialMode(SpatialDispatchMode.ALL);
			}
			case "snare", "mid" -> {
				b.animationTypeId("WaveMotion").energyThreshold(0.22f).durationSeconds(0.80).cooldownSeconds(0.12)
					.spatialMode(SpatialDispatchMode.SEQUENTIAL).sequentialDelaySeconds(0.03);
			}
			case "hihat", "hihat_open", "snare_hi", "high" -> {
				b.animationTypeId("Pulse").energyThreshold(0.18f).durationSeconds(0.28).cooldownSeconds(0.06)
					.spatialMode(SpatialDispatchMode.ALL);
			}
			case "bass" -> {
				b.animationTypeId("SpiralLift").energyThreshold(0.24f).durationSeconds(1.20).cooldownSeconds(0.20)
					.spatialMode(SpatialDispatchMode.SPIRAL);
			}
			case "vocals" -> {
				b.animationTypeId("Orbit").energyThreshold(0.20f).durationSeconds(0.95).cooldownSeconds(0.18)
					.spatialMode(SpatialDispatchMode.RADIAL);
			}
			case "other" -> {
				b.animationTypeId("BlockExplosion").energyThreshold(0.26f).durationSeconds(0.72).cooldownSeconds(0.24)
					.spatialMode(SpatialDispatchMode.RANDOM).probability(0.7f);
			}
			default -> {
				b.animationTypeId("Pulse").energyThreshold(0.22f).durationSeconds(0.40).cooldownSeconds(0.10)
					.spatialMode(SpatialDispatchMode.ALL);
			}
		}
		return b.build();
	}

	private static String resolveDefaultTargetObjectId() {
		if (BeatBlock.blockAnimationEngine == null || BeatBlock.blockAnimationEngine.getStageObjectSystem() == null) {
			return "";
		}
		var all = BeatBlock.blockAnimationEngine.getStageObjectSystem().getAll();
		if (all == null || all.isEmpty()) return "";
		return all.iterator().next().getId();
	}

	private static boolean passesThreshold(AnimationBindingRule rule, FeatureEvent event) {
		return event.getEnergy() + 1e-6f >= rule.energyThreshold();
	}

	private static boolean passesProbability(AnimationBindingRule rule, FeatureEvent event) {
		if (rule.probability() >= 0.999f) return true;
		double seed = hash01(rule.id(), event.getTimeSeconds());
		return seed <= rule.probability();
	}

	private static boolean passesCooldown(AnimationBindingRule rule, FeatureEvent event, Map<String, Double> lastAcceptedByRule) {
		if (rule.cooldownSeconds() <= 0.0) return true;
		Double prev = lastAcceptedByRule.get(rule.id());
		return prev == null || event.getTimeSeconds() >= prev + rule.cooldownSeconds();
	}

	private static double hash01(String ruleId, double timeSeconds) {
		long bits = Double.doubleToLongBits(timeSeconds);
		int h = 17;
		h = h * 31 + (ruleId != null ? ruleId.hashCode() : 0);
		h = h * 31 + (int) (bits ^ (bits >>> 32));
		long normalized = (h & 0x7fffffffL);
		return normalized / (double) Integer.MAX_VALUE;
	}

	private static float clamp01(float value) {
		if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
		return Math.max(0f, Math.min(1f, value));
	}

	private static void ensureFeatureTrack(Timeline timeline, String featureKey) {
		String trackId = Timeline.blockAnimationFeatureTrackId(featureKey);
		if (timeline.getTrack(trackId) != null) return;
		timeline.addTrack(new Track(trackId, TrackRegistry.localizedName(featureKey), TrackType.ANIMATION));
	}

	private static void pruneGeneratedEventsOnFeatureTracks(Timeline timeline) {
		if (timeline == null) return;
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isBlockAnimationFeatureTrackId(track.getId())) continue;
			pruneGeneratedEventsOnTrack(timeline, track.getId());
		}
	}

	private static void pruneGeneratedEventsOnTrack(Timeline timeline, String trackId) {
		if (timeline == null || trackId == null || trackId.isBlank()) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;

		List<String> emptyClipIds = new ArrayList<>();
		boolean changed = false;
		for (var clip : track.getClips()) {
			if (clip == null) continue;
			List<String> removeEventIds = new ArrayList<>();
			for (var event : clip.getEvents()) {
				if (event == null || event.getType() != EventType.ANIMATION) continue;
				Object generatedBy = event.getParameters().get("generatedBy");
				if (generatedBy == null) continue;
				if (GENERATED_BY_MARK.equalsIgnoreCase(String.valueOf(generatedBy).trim())) {
					removeEventIds.add(event.getId());
				}
			}
			for (String eventId : removeEventIds) {
				if (clip.removeEvent(eventId)) changed = true;
			}
			if (clip.getEvents().isEmpty()) emptyClipIds.add(clip.getId());
		}
		for (String clipId : emptyClipIds) {
			if (track.removeClip(clipId)) changed = true;
		}
		if (changed) timeline.markAnimationEventsDirty(trackId);
	}
}
