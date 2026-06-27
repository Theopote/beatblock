package com.beatblock.timeline.binding;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TrackRegistry;

import org.jspecify.annotations.Nullable;

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
	public static final String TEMPLATE_RHYTHM_PARKOUR = "rhythm_parkour";
	public static final String TEMPLATE_ARCHITECTURAL_SHOW = "architectural_show";

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
				if (!passesSectionFilter(rule, timeline, event)) continue;
				if (!passesThreshold(rule, event)) continue;
				if (!passesProbability(rule, event)) continue;
				if (!passesCooldown(rule, event, lastAcceptedByRule)) continue;

				float energy = clamp01(event.getEnergy() * rule.energyScale());
				Map<String, Object> params = AnimationEventParams.fromBindingRule(
					rule,
					energy,
					TimelineEventOrigin.AUTO_GENERATED
				).withMergedExtensions(rule.extraParams()).toParameterMap();

				TimelineAnimationEvent generated = new TimelineAnimationEvent(
					"",
					event.getTimeSeconds(),
					rule.durationSeconds(),
					rule.animationTypeId(),
					rule.targetObjectId(),
					energy,
					params
				);

				String trackId = toBlockTrack
					? Timeline.blockAnimationFeatureTrackId(rule.sourceFeatureKey())
					: Timeline.TRACK_ID_ANIMATION_AUTO;
				if (toBlockTrack) ensureFeatureTrack(timeline, rule.sourceFeatureKey());
				TimelineDraftWriter.writeEvent(timeline, trackId, generated, TimelineEventOrigin.AUTO_GENERATED);
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

	public static List<AnimationBindingRule> createTemplateRules(Timeline timeline, String templateId) {
		if (timeline == null) return List.of();
		String targetObjectId = resolveDefaultTargetObjectId();
		if (targetObjectId.isBlank()) return List.of();

		String key = templateId != null ? templateId.trim().toLowerCase(Locale.ROOT) : "";
		return switch (key) {
			case TEMPLATE_RHYTHM_PARKOUR -> createRhythmParkourTemplate(timeline, targetObjectId);
			case TEMPLATE_ARCHITECTURAL_SHOW -> createArchitecturalShowTemplate(timeline, targetObjectId);
			default -> createDefaultRules(timeline);
		};
	}

	private static List<AnimationBindingRule> createRhythmParkourTemplate(Timeline timeline, String targetObjectId) {
		String kick = resolveFeatureKey(timeline, "kick", "low");
		String snare = resolveFeatureKey(timeline, "snare", "mid");
		String hihat = resolveFeatureKey(timeline, "hihat", "high", "hihat_open", "snare_hi");
		String bass = resolveFeatureKey(timeline, "bass");
		String vocals = resolveFeatureKey(timeline, "vocals", "other");

		Map<String, Object> waveParams = new HashMap<>();
		waveParams.put("waveAmplitude", 1.1f);
		waveParams.put("wavePhaseOffset", 0.45f);

		List<AnimationBindingRule> rules = new ArrayList<>();
		rules.add(AnimationBindingRule.builder()
			.name("Rhythm Kick Jump")
			.sourceFeatureKey(kick)
			.animationTypeId("BlockJump")
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.28f)
			.energyScale(1.0f)
			.durationSeconds(0.55)
			.cooldownSeconds(0.08)
			.probability(1.0f)
			.spatialMode(SpatialDispatchMode.ALL)
			.sequentialDelaySeconds(0.0)
			.build());
		rules.add(AnimationBindingRule.builder()
			.name("Rhythm Snare Wave")
			.sourceFeatureKey(snare)
			.animationTypeId("WaveMotion")
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.21f)
			.energyScale(1.05f)
			.durationSeconds(0.92)
			.cooldownSeconds(0.12)
			.probability(1.0f)
			.spatialMode(SpatialDispatchMode.SEQUENTIAL)
			.sequentialDelaySeconds(0.03)
			.extraParams(waveParams)
			.build());
		rules.add(AnimationBindingRule.builder()
			.name("Rhythm Hat Pulse")
			.sourceFeatureKey(hihat)
			.animationTypeId("Pulse")
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.17f)
			.energyScale(1.0f)
			.durationSeconds(0.30)
			.cooldownSeconds(0.06)
			.probability(1.0f)
			.spatialMode(SpatialDispatchMode.ALL)
			.sequentialDelaySeconds(0.0)
			.build());
		rules.add(AnimationBindingRule.builder()
			.name("Rhythm Bass Spiral")
			.sourceFeatureKey(bass)
			.animationTypeId("SpiralLift")
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.22f)
			.energyScale(1.0f)
			.durationSeconds(1.35)
			.cooldownSeconds(0.18)
			.probability(0.9f)
			.spatialMode(SpatialDispatchMode.SPIRAL)
			.sequentialDelaySeconds(0.0)
			.build());
		rules.add(AnimationBindingRule.builder()
			.name("Rhythm Vocal Orbit")
			.sourceFeatureKey(vocals)
			.animationTypeId("Orbit")
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.20f)
			.energyScale(0.95f)
			.durationSeconds(1.05)
			.cooldownSeconds(0.20)
			.probability(0.85f)
			.spatialMode(SpatialDispatchMode.RADIAL)
			.sequentialDelaySeconds(0.02)
			.build());
		return rules;
	}

	private static List<AnimationBindingRule> createArchitecturalShowTemplate(Timeline timeline, String targetObjectId) {
		String kick = resolveFeatureKey(timeline, "kick", "low");
		String snare = resolveFeatureKey(timeline, "snare", "mid");
		String bass = resolveFeatureKey(timeline, "bass", "other");
		String hihat = resolveFeatureKey(timeline, "hihat", "high", "hihat_open", "snare_hi");

		Map<String, Object> wallParams = new HashMap<>();
		wallParams.put("buildMode", "WALL");
		wallParams.put("placeBlock", "minecraft:light_gray_concrete");

		Map<String, Object> bridgeParams = new HashMap<>();
		bridgeParams.put("buildMode", "BRIDGE");
		bridgeParams.put("placeBlock", "minecraft:smooth_stone");

		Map<String, Object> towerParams = new HashMap<>();
		towerParams.put("buildMode", "TOWER");
		towerParams.put("placeBlock", "minecraft:quartz_block");

		Map<String, Object> dissolveParams = new HashMap<>();
		dissolveParams.put("buildMode", "DISSOLVE");
		dissolveParams.put("buildDissolve", "true");

		List<AnimationBindingRule> rules = new ArrayList<>();
		rules.add(AnimationBindingRule.builder()
			.name("Build Kick Wall")
			.sourceFeatureKey(kick)
			.animationTypeId("Pulse")
			.actionMode(TimelineAnimationActionMode.BUILD)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.30f)
			.energyScale(1.0f)
			.durationSeconds(1.60)
			.cooldownSeconds(0.28)
			.probability(1.0f)
			.spatialMode(SpatialDispatchMode.ALL)
			.sequentialDelaySeconds(0.0)
			.extraParams(wallParams)
			.build());
		rules.add(AnimationBindingRule.builder()
			.name("Build Snare Bridge")
			.sourceFeatureKey(snare)
			.animationTypeId("Pulse")
			.actionMode(TimelineAnimationActionMode.BUILD)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.24f)
			.energyScale(1.0f)
			.durationSeconds(1.90)
			.cooldownSeconds(0.34)
			.probability(0.85f)
			.spatialMode(SpatialDispatchMode.ALL)
			.sequentialDelaySeconds(0.0)
			.extraParams(bridgeParams)
			.build());
		rules.add(AnimationBindingRule.builder()
			.name("Build Bass Tower")
			.sourceFeatureKey(bass)
			.animationTypeId("Pulse")
			.actionMode(TimelineAnimationActionMode.BUILD)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.20f)
			.energyScale(1.0f)
			.durationSeconds(2.30)
			.cooldownSeconds(0.55)
			.probability(0.75f)
			.spatialMode(SpatialDispatchMode.ALL)
			.sequentialDelaySeconds(0.0)
			.extraParams(towerParams)
			.build());
		rules.add(AnimationBindingRule.builder()
			.name("Build Hat Dissolve")
			.sourceFeatureKey(hihat)
			.animationTypeId("Pulse")
			.actionMode(TimelineAnimationActionMode.BUILD)
			.targetObjectId(targetObjectId)
			.energyThreshold(0.26f)
			.energyScale(1.0f)
			.durationSeconds(1.20)
			.cooldownSeconds(0.40)
			.probability(0.35f)
			.spatialMode(SpatialDispatchMode.ALL)
			.sequentialDelaySeconds(0.0)
			.extraParams(dissolveParams)
			.build());
		return rules;
	}

	private static String resolveFeatureKey(Timeline timeline, String preferred, String... aliases) {
		if (timeline == null || timeline.getFeatureTracks().isEmpty()) {
			return preferred != null ? preferred : "other";
		}
		List<String> keys = new ArrayList<>(timeline.getFeatureTracks().keySet());
		for (String key : keys) {
			if (key != null && key.equalsIgnoreCase(preferred)) return key;
		}
		if (aliases != null) {
			for (String alias : aliases) {
				for (String key : keys) {
					if (key != null && key.equalsIgnoreCase(alias)) return key;
				}
			}
		}
		keys.sort(String.CASE_INSENSITIVE_ORDER);
		return keys.isEmpty() ? (preferred != null ? preferred : "other") : keys.getFirst();
	}

	private static @Nullable AnimationBindingRule defaultRuleForFeature(String featureKey, String targetObjectId) {
		if (featureKey == null || featureKey.isBlank()) return null;
		String key = featureKey.trim().toLowerCase(Locale.ROOT);
		AnimationBindingRule.Builder b = AnimationBindingRule.builder()
			.name("Bind " + key)
			.sourceFeatureKey(key)
			.targetObjectId(targetObjectId)
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.probability(1.0f)
			.energyScale(1.0f);

		switch (key) {
			case "kick", "low" -> b.animationTypeId("BlockJump").energyThreshold(0.30f).durationSeconds(0.55).cooldownSeconds(0.08)
                .spatialMode(SpatialDispatchMode.ALL);
			case "snare", "mid" -> b.animationTypeId("WaveMotion").energyThreshold(0.22f).durationSeconds(0.80).cooldownSeconds(0.12)
                .spatialMode(SpatialDispatchMode.SEQUENTIAL).sequentialDelaySeconds(0.03);
			case "hihat", "hihat_open", "snare_hi", "high" -> b.animationTypeId("Pulse").energyThreshold(0.18f).durationSeconds(0.28).cooldownSeconds(0.06)
                .spatialMode(SpatialDispatchMode.ALL);
			case "bass" -> b.animationTypeId("SpiralLift").energyThreshold(0.24f).durationSeconds(1.20).cooldownSeconds(0.20)
                .spatialMode(SpatialDispatchMode.SPIRAL);
			case "vocals" -> b.animationTypeId("Orbit").energyThreshold(0.20f).durationSeconds(0.95).cooldownSeconds(0.18)
                .spatialMode(SpatialDispatchMode.RADIAL);
			case "other" -> b.animationTypeId("BlockExplosion").energyThreshold(0.26f).durationSeconds(0.72).cooldownSeconds(0.24)
                .spatialMode(SpatialDispatchMode.RANDOM).probability(0.7f);
			default -> b.animationTypeId("Pulse").energyThreshold(0.22f).durationSeconds(0.40).cooldownSeconds(0.10)
                .spatialMode(SpatialDispatchMode.ALL);
		}
		return b.build();
	}

	private static String resolveDefaultTargetObjectId() {
		var engine = BeatBlock.getContext().blockAnimationEngine();
		if (engine == null) {
			return "";
		}
		var all = engine.getStageObjectSystem().getAll();
		if (all.isEmpty()) return "";
		return all.iterator().next().getId();
	}

	private static boolean passesThreshold(AnimationBindingRule rule, FeatureEvent event) {
		return event.getEnergy() + 1e-6f >= rule.energyThreshold();
	}

	private static boolean passesSectionFilter(AnimationBindingRule rule, Timeline timeline, FeatureEvent event) {
		if (rule == null || timeline == null || event == null) return false;
		String wanted = normalizeSectionLabel(rule.sectionFilter());
		if (wanted.isBlank() || "all".equals(wanted) || "any".equals(wanted) || "*".equals(wanted)) return true;
		String current = sectionLabelAtTime(timeline, event.getTimeSeconds());
		if (current.isBlank()) return false;
		return wanted.equals(current);
	}

	private static String sectionLabelAtTime(Timeline timeline, double timeSeconds) {
		if (timeline == null) return "";
		String current = "";
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null || marker.getType() != MarkerType.SECTION) continue;
			if (marker.getTimeSeconds() > timeSeconds) break;
			String label = extractSectionLabel(marker.getName());
			if (!label.isBlank()) current = label;
		}
		return current;
	}

	private static String extractSectionLabel(String markerName) {
		if (markerName == null) return "";
		String raw = markerName.trim();
		if (raw.isBlank()) return "";
		String upper = raw.toUpperCase(Locale.ROOT);
		if (upper.startsWith("SECTION ")) {
			raw = raw.substring("SECTION ".length()).trim();
		}
		return normalizeSectionLabel(raw);
	}

	private static String normalizeSectionLabel(String value) {
		if (value == null) return "";
		String s = value.trim().toLowerCase(Locale.ROOT);
		if (s.isBlank()) return "";
		return s;
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
		h = h * 31 + Long.hashCode(bits);
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
