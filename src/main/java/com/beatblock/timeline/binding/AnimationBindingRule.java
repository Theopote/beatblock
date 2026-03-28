package com.beatblock.timeline.binding;

import com.beatblock.timeline.TimelineAnimationActionMode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 音频特征到动画事件的绑定规则。
 */
public final class AnimationBindingRule {

	private final String id;
	private final String name;
	private final boolean enabled;
	private final String sourceFeatureKey;
	private final String animationTypeId;
	private final TimelineAnimationActionMode actionMode;
	private final String targetObjectId;
	private final float energyThreshold;
	private final float energyScale;
	private final double durationSeconds;
	private final double cooldownSeconds;
	private final float probability;
	private final SpatialDispatchMode spatialMode;
	private final double sequentialDelaySeconds;
	private final String sectionFilter;
	private final Map<String, Object> extraParams;

	private AnimationBindingRule(Builder builder) {
		this.id = safe(builder.id, UUID.randomUUID().toString().replace("-", ""));
		this.name = safe(builder.name, "rule-" + this.id.substring(0, Math.min(6, this.id.length())));
		this.enabled = builder.enabled;
		this.sourceFeatureKey = safe(builder.sourceFeatureKey, "").toLowerCase(Locale.ROOT);
		this.animationTypeId = normalizeAnimationTypeId(builder.animationTypeId);
		this.actionMode = builder.actionMode != null ? builder.actionMode : TimelineAnimationActionMode.ANIMATE;
		this.targetObjectId = safe(builder.targetObjectId, "");
		this.energyThreshold = clamp01(builder.energyThreshold);
		this.energyScale = Math.max(0f, builder.energyScale);
		this.durationSeconds = Math.max(0.01, builder.durationSeconds);
		this.cooldownSeconds = Math.max(0.0, builder.cooldownSeconds);
		this.probability = clamp01(builder.probability);
		this.spatialMode = builder.spatialMode != null ? builder.spatialMode : SpatialDispatchMode.ALL;
		this.sequentialDelaySeconds = Math.max(0.0, builder.sequentialDelaySeconds);
		this.sectionFilter = safe(builder.sectionFilter, "");
		this.extraParams = builder.extraParams == null
			? Collections.emptyMap()
			: Map.copyOf(builder.extraParams);
	}

	public String id() { return id; }
	public String name() { return name; }
	public boolean enabled() { return enabled; }
	public String sourceFeatureKey() { return sourceFeatureKey; }
	public String animationTypeId() { return animationTypeId; }
	public TimelineAnimationActionMode actionMode() { return actionMode; }
	public String targetObjectId() { return targetObjectId; }
	public float energyThreshold() { return energyThreshold; }
	public float energyScale() { return energyScale; }
	public double durationSeconds() { return durationSeconds; }
	public double cooldownSeconds() { return cooldownSeconds; }
	public float probability() { return probability; }
	public SpatialDispatchMode spatialMode() { return spatialMode; }
	public double sequentialDelaySeconds() { return sequentialDelaySeconds; }
	public String sectionFilter() { return sectionFilter; }
	public Map<String, Object> extraParams() { return extraParams; }

	public boolean isValid() {
		return enabled && !sourceFeatureKey.isBlank() && !animationTypeId.isBlank() && !targetObjectId.isBlank();
	}

	public Map<String, Object> toMap() {
		Map<String, Object> out = new HashMap<>();
		out.put("id", id);
		out.put("name", name);
		out.put("enabled", enabled);
		out.put("sourceFeatureKey", sourceFeatureKey);
		out.put("animationTypeId", animationTypeId);
		out.put("actionMode", actionMode.name());
		out.put("targetObjectId", targetObjectId);
		out.put("energyThreshold", energyThreshold);
		out.put("energyScale", energyScale);
		out.put("durationSeconds", durationSeconds);
		out.put("cooldownSeconds", cooldownSeconds);
		out.put("probability", probability);
		out.put("spatialMode", spatialMode.name());
		out.put("sequentialDelaySeconds", sequentialDelaySeconds);
		if (!sectionFilter.isBlank()) out.put("sectionFilter", sectionFilter);
		if (!extraParams.isEmpty()) out.put("extraParams", extraParams);
		return out;
	}

	public static AnimationBindingRule fromMap(Map<String, Object> raw) {
		if (raw == null) return null;
		Builder b = builder();
		b.id(stringValue(raw.get("id"), ""));
		b.name(stringValue(raw.get("name"), ""));
		b.enabled(booleanValue(raw.get("enabled"), true));
		b.sourceFeatureKey(stringValue(raw.get("sourceFeatureKey"), ""));
		b.animationTypeId(stringValue(raw.get("animationTypeId"), ""));
		b.actionMode(TimelineAnimationActionMode.fromValue(raw.get("actionMode")));
		b.targetObjectId(stringValue(raw.get("targetObjectId"), ""));
		b.energyThreshold((float) numberValue(raw.get("energyThreshold"), 0.2));
		b.energyScale((float) numberValue(raw.get("energyScale"), 1.0));
		b.durationSeconds(numberValue(raw.get("durationSeconds"), 0.4));
		b.cooldownSeconds(numberValue(raw.get("cooldownSeconds"), 0.08));
		b.probability((float) numberValue(raw.get("probability"), 1.0));
		b.spatialMode(SpatialDispatchMode.fromValue(raw.get("spatialMode")));
		b.sequentialDelaySeconds(numberValue(raw.get("sequentialDelaySeconds"), 0.0));
		b.sectionFilter(stringValue(raw.get("sectionFilter"), ""));
		Object extra = raw.get("extraParams");
		if (extra instanceof Map<?, ?> m) {
			Map<String, Object> copied = new HashMap<>();
			for (Map.Entry<?, ?> e : m.entrySet()) {
				if (e.getKey() != null) copied.put(String.valueOf(e.getKey()), e.getValue());
			}
			b.extraParams(copied);
		}
		return b.build();
	}

	private static String normalizeAnimationTypeId(String raw) {
		String v = safe(raw, "Pulse").trim();
		if (v.isEmpty()) return "Pulse";
		String u = v.toUpperCase(Locale.ROOT);
		return switch (u) {
			case "EJECT" -> "BlockJump";
			case "FALL", "METEOR" -> "BlockDrop";
			case "PULSE" -> "Pulse";
			case "SLIDE" -> "Orbit";
			case "SPIRAL" -> "SpiralLift";
			case "WAVE" -> "WaveMotion";
			case "IMPACT" -> "BlockExplosion";
            default -> v;
		};
	}

	private static float clamp01(float value) {
		if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
		return Math.max(0f, Math.min(1f, value));
	}

	private static String safe(String value, String fallback) {
		if (value == null) return fallback;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? fallback : trimmed;
	}

	private static String stringValue(Object value, String fallback) {
		if (value == null) return fallback;
		String s = String.valueOf(value).trim();
		return s.isEmpty() ? fallback : s;
	}

	private static boolean booleanValue(Object value, boolean fallback) {
		if (value instanceof Boolean b) return b;
		if (value instanceof Number n) return n.intValue() != 0;
		if (value == null) return fallback;
		String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
		if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
		if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
		return fallback;
	}

	private static double numberValue(Object value, double fallback) {
		if (value instanceof Number n) return n.doubleValue();
		if (value == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(value).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String id;
		private String name;
		private boolean enabled = true;
		private String sourceFeatureKey;
		private String animationTypeId = "Pulse";
		private TimelineAnimationActionMode actionMode = TimelineAnimationActionMode.ANIMATE;
		private String targetObjectId;
		private float energyThreshold = 0.2f;
		private float energyScale = 1.0f;
		private double durationSeconds = 0.4;
		private double cooldownSeconds = 0.08;
		private float probability = 1.0f;
		private SpatialDispatchMode spatialMode = SpatialDispatchMode.ALL;
		private double sequentialDelaySeconds = 0.0;
		private String sectionFilter = "";
		private Map<String, Object> extraParams = Map.of();

		public Builder id(String value) { this.id = value; return this; }
		public Builder name(String value) { this.name = value; return this; }
		public Builder enabled(boolean value) { this.enabled = value; return this; }
		public Builder sourceFeatureKey(String value) { this.sourceFeatureKey = value; return this; }
		public Builder animationTypeId(String value) { this.animationTypeId = value; return this; }
		public Builder actionMode(TimelineAnimationActionMode value) { this.actionMode = value; return this; }
		public Builder targetObjectId(String value) { this.targetObjectId = value; return this; }
		public Builder energyThreshold(float value) { this.energyThreshold = value; return this; }
		public Builder energyScale(float value) { this.energyScale = value; return this; }
		public Builder durationSeconds(double value) { this.durationSeconds = value; return this; }
		public Builder cooldownSeconds(double value) { this.cooldownSeconds = value; return this; }
		public Builder probability(float value) { this.probability = value; return this; }
		public Builder spatialMode(SpatialDispatchMode value) { this.spatialMode = value; return this; }
		public Builder sequentialDelaySeconds(double value) { this.sequentialDelaySeconds = value; return this; }
		public Builder sectionFilter(String value) { this.sectionFilter = value; return this; }
		public Builder extraParams(Map<String, Object> value) { this.extraParams = value; return this; }

		public AnimationBindingRule build() {
			return new AnimationBindingRule(this);
		}
	}
}
