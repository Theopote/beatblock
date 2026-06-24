package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 时间线工具栏配置：Demucs 映射预设/缩放与 Action Rollback 模式读写及 ui.json 持久化。
 */
public final class TimelineToolbarConfigPresenter {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineToolbarConfigPresenter.class);
	private static final Gson UI_CONFIG_GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final String[] DEMUCS_PRESET_LABELS = { "Drive", "Balanced", "Detail" };
	public static final String[] DEMUCS_PRESET_VALUES = { "drive", "balanced", "detail" };
	public static final String[] CLIP_GENERATION_MODE_LABELS = { "Mixed", "Trigger", "Sustain" };
	public static final String[] CLIP_GENERATION_MODE_VALUES = { "mixed", "trigger", "sustain" };
	public static final String[] ACTION_ROLLBACK_LABELS = { "Preview", "Persistent" };
	public static final String[] ACTION_ROLLBACK_VALUES = { "preview", "persistent" };
	public static final String[] DEMUCS_FEATURE_KEYS = {
		"kick", "snare", "hihat", "hihat_open", "snare_hi", "bass", "vocals", "other"
	};
	public static final String[] DEMUCS_FEATURE_LABELS = {
		"Kick", "Snare", "HiHat", "HiHat Open", "Snare Hi", "Bass", "Vocals", "Other"
	};

	public static final double DEMUCS_SCALE_MIN = 0.5;
	public static final double DEMUCS_SCALE_MAX = 2.0;
	public static final double DEMUCS_ENERGY_SCALE_MIN = 0.6;
	public static final double DEMUCS_ENERGY_SCALE_MAX = 1.6;

	public record DemucsAdvancedScales(double durationScale, double energyScale, double gapScale) {}

	public record ActionRollbackViewState(String mode, String statusLabel) {}

	private final Supplier<Timeline> timeline;
	private final Supplier<Path> uiConfigPath;
	private boolean demucsMappingConfigLoaded;
	private boolean actionExecutionConfigLoaded;

	public TimelineToolbarConfigPresenter(Supplier<Timeline> timeline, Supplier<Path> uiConfigPath) {
		this.timeline = timeline;
		this.uiConfigPath = uiConfigPath;
	}

	public boolean isDemucsSeparationActive() {
		Timeline current = timeline.get();
		if (current == null) {
			return false;
		}
		Object separationMode = current.getMetadata("separationMode");
		return separationMode != null && "demucs".equalsIgnoreCase(separationMode.toString().trim());
	}

	public void ensureDemucsMappingConfigLoaded() {
		if (demucsMappingConfigLoaded) {
			return;
		}
		Timeline current = timeline.get();
		if (current == null) {
			return;
		}
		demucsMappingConfigLoaded = true;
		Path configPath = uiConfigPath.get();
		if (configPath == null || !Files.isRegularFile(configPath)) {
			return;
		}
		try {
			String txt = Files.readString(configPath, StandardCharsets.UTF_8);
			if (txt.isBlank()) {
				return;
			}
			JsonObject root = JsonParser.parseString(txt).getAsJsonObject();
			if (!root.has("demucsMapping") || !root.get("demucsMapping").isJsonObject()) {
				return;
			}
			JsonObject dm = root.getAsJsonObject("demucsMapping");

			if (current.getMetadata("demucsMappingPreset") == null && dm.has("preset")) {
				String preset = dm.get("preset").getAsString();
				if (isValidDemucsPreset(preset)) {
					current.setMetadata("demucsMappingPreset", preset.toLowerCase(Locale.ROOT));
				}
			}
			if (current.getMetadata("featureClipGenerationMode") == null && dm.has("clipGenerationMode")) {
				String mode = dm.get("clipGenerationMode").getAsString();
				if (isValidClipGenerationMode(mode)) {
					current.setMetadata("featureClipGenerationMode", mode.toLowerCase(Locale.ROOT));
				}
			}

			applyDefaultScaleFromJson(current, dm, "durationScale", "demucsMapDurationScale", DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
			applyDefaultScaleFromJson(current, dm, "energyScale", "demucsMapEnergyScale", DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX);
			applyDefaultScaleFromJson(current, dm, "gapScale", "demucsMapGapScale", DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);

			if (dm.has("featureScale") && dm.get("featureScale").isJsonObject()) {
				JsonObject featureScale = dm.getAsJsonObject("featureScale");
				for (String featureKey : DEMUCS_FEATURE_KEYS) {
					if (!featureScale.has(featureKey) || !featureScale.get(featureKey).isJsonObject()) {
						continue;
					}
					JsonObject featureObj = featureScale.getAsJsonObject(featureKey);
					applyDefaultScaleFromJson(current, featureObj, "durationScale",
						featureMetadataKey(featureKey, "duration"), DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
					applyDefaultScaleFromJson(current, featureObj, "energyScale",
						featureMetadataKey(featureKey, "energy"), DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX);
					applyDefaultScaleFromJson(current, featureObj, "gapScale",
						featureMetadataKey(featureKey, "gap"), DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
				}
			}
		} catch (Exception e) {
			LOGGER.debug("BeatBlock config: failed to read ui.json demucs mapping reason={}", e.toString());
		}
	}

	public void ensureActionExecutionConfigLoaded() {
		if (actionExecutionConfigLoaded) {
			return;
		}
		Timeline current = timeline.get();
		if (current == null) {
			return;
		}
		actionExecutionConfigLoaded = true;
		if (current.getMetadata("timelineActionRollbackMode") != null) {
			return;
		}
		Path configPath = uiConfigPath.get();
		if (configPath == null || !Files.isRegularFile(configPath)) {
			return;
		}
		try {
			String txt = Files.readString(configPath, StandardCharsets.UTF_8);
			if (txt.isBlank()) {
				return;
			}
			JsonObject root = JsonParser.parseString(txt).getAsJsonObject();
			if (!root.has("timelineActionExecution") || !root.get("timelineActionExecution").isJsonObject()) {
				return;
			}
			JsonObject action = root.getAsJsonObject("timelineActionExecution");
			if (!action.has("rollbackMode")) {
				return;
			}
			writeActionRollbackMode(action.get("rollbackMode").getAsString());
		} catch (Exception e) {
			LOGGER.debug("BeatBlock config: failed to read ui.json action execution reason={}", e.toString());
		}
	}

	public String readDemucsPreset() {
		Timeline current = timeline.get();
		if (current == null) {
			return "balanced";
		}
		Object preset = current.getMetadata("demucsMappingPreset");
		if (preset == null) {
			return "balanced";
		}
		String value = preset.toString().trim().toLowerCase(Locale.ROOT);
		return isValidDemucsPreset(value) ? value : "balanced";
	}

	public void writeDemucsPreset(String preset) {
		Timeline current = timeline.get();
		if (current == null || preset == null) {
			return;
		}
		current.setMetadata("demucsMappingPreset", preset);
		persistDemucsMappingConfig();
	}

	public String readClipGenerationMode() {
		Timeline current = timeline.get();
		if (current == null) {
			return "mixed";
		}
		Object mode = current.getMetadata("featureClipGenerationMode");
		if (mode == null) {
			return "mixed";
		}
		String value = mode.toString().trim().toLowerCase(Locale.ROOT);
		return isValidClipGenerationMode(value) ? value : "mixed";
	}

	public void writeClipGenerationMode(String mode) {
		Timeline current = timeline.get();
		if (current == null) {
			return;
		}
		String normalized = mode == null ? "mixed" : mode.trim().toLowerCase(Locale.ROOT);
		if (!isValidClipGenerationMode(normalized)) {
			normalized = "mixed";
		}
		current.setMetadata("featureClipGenerationMode", normalized);
		persistDemucsMappingConfig();
	}

	public DemucsAdvancedScales readGlobalScales() {
		return new DemucsAdvancedScales(
			readTimelineScale("demucsMapDurationScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX),
			readTimelineScale("demucsMapEnergyScale", 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX),
			readTimelineScale("demucsMapGapScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX)
		);
	}

	public void writeGlobalScales(float durationScale, float energyScale, float gapScale) {
		writeTimelineScale("demucsMapDurationScale", durationScale);
		writeTimelineScale("demucsMapEnergyScale", energyScale);
		writeTimelineScale("demucsMapGapScale", gapScale);
		persistDemucsMappingConfig();
	}

	public void resetGlobalScalesToDefault() {
		writeGlobalScales(1.0f, 1.0f, 1.0f);
	}

	public double readFeatureScale(String featureKey, String metric) {
		return readTimelineScale(featureMetadataKey(featureKey, metric), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
	}

	public double readFeatureEnergyScale(String featureKey) {
		return readTimelineScale(
			featureMetadataKey(featureKey, "energy"),
			1.0,
			DEMUCS_ENERGY_SCALE_MIN,
			DEMUCS_ENERGY_SCALE_MAX
		);
	}

	public void writeFeatureScales(String featureKey, float duration, float energy, float gap) {
		writeTimelineScale(featureMetadataKey(featureKey, "duration"), duration);
		writeTimelineScale(featureMetadataKey(featureKey, "energy"), energy);
		writeTimelineScale(featureMetadataKey(featureKey, "gap"), gap);
		persistDemucsMappingConfig();
	}

	public void resetAllFeatureOverrides() {
		for (String featureKey : DEMUCS_FEATURE_KEYS) {
			writeTimelineScale(featureMetadataKey(featureKey, "duration"), 1.0f);
			writeTimelineScale(featureMetadataKey(featureKey, "energy"), 1.0f);
			writeTimelineScale(featureMetadataKey(featureKey, "gap"), 1.0f);
		}
		persistDemucsMappingConfig();
	}

	public String readActionRollbackMode() {
		Timeline current = timeline.get();
		if (current == null) {
			return "preview";
		}
		Object mode = current.getMetadata("timelineActionRollbackMode");
		if (mode == null) {
			return "preview";
		}
		String value = mode.toString().trim().toLowerCase(Locale.ROOT);
		if ("preview".equals(value) || "persistent".equals(value) || "performance".equals(value)) {
			return "performance".equals(value) ? "persistent" : value;
		}
		return "preview";
	}

	public void writeActionRollbackMode(String mode) {
		Timeline current = timeline.get();
		if (current == null) {
			return;
		}
		String normalized = ("persistent".equalsIgnoreCase(mode) || "performance".equalsIgnoreCase(mode))
			? "persistent"
			: "preview";
		current.setMetadata("timelineActionRollbackMode", normalized);
		persistActionExecutionConfig();
	}

	public ActionRollbackViewState actionRollbackViewState() {
		String mode = readActionRollbackMode();
		String label = "persistent".equalsIgnoreCase(mode) ? "Action: Persistent" : "Action: Preview";
		return new ActionRollbackViewState(mode, label);
	}

	public static int indexOfDemucsPresetValue(String value) {
		if (value == null || value.isBlank()) {
			return 1;
		}
		for (int i = 0; i < DEMUCS_PRESET_VALUES.length; i++) {
			if (DEMUCS_PRESET_VALUES[i].equalsIgnoreCase(value)) {
				return i;
			}
		}
		return 1;
	}

	public static int indexOfClipGenerationMode(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		for (int i = 0; i < CLIP_GENERATION_MODE_VALUES.length; i++) {
			if (CLIP_GENERATION_MODE_VALUES[i].equalsIgnoreCase(value)) {
				return i;
			}
		}
		return 0;
	}

	public static int indexOfActionRollbackValue(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		for (int i = 0; i < ACTION_ROLLBACK_VALUES.length; i++) {
			if (ACTION_ROLLBACK_VALUES[i].equalsIgnoreCase(value)) {
				return i;
			}
		}
		return 0;
	}

	public static String featureMetadataKey(String featureKey, String metric) {
		if (featureKey == null || featureKey.isBlank() || metric == null || metric.isBlank()) {
			return "";
		}
		String normalizedFeature = featureKey.trim().toLowerCase(Locale.ROOT);
		String normalizedMetric = metric.trim().toLowerCase(Locale.ROOT);
		return switch (normalizedMetric) {
			case "duration" -> "demucsFeatDuration_" + normalizedFeature;
			case "energy" -> "demucsFeatEnergy_" + normalizedFeature;
			case "gap" -> "demucsFeatGap_" + normalizedFeature;
			default -> "";
		};
	}

	private void persistDemucsMappingConfig() {
		Timeline current = timeline.get();
		Path configPath = uiConfigPath.get();
		if (current == null || configPath == null) {
			return;
		}
		try {
			JsonObject root = readOrCreateConfigRoot(configPath);
			JsonObject dm = root.has("demucsMapping") && root.get("demucsMapping").isJsonObject()
				? root.getAsJsonObject("demucsMapping")
				: new JsonObject();

			dm.addProperty("preset", readDemucsPreset());
			dm.addProperty("clipGenerationMode", readClipGenerationMode());
			dm.addProperty("durationScale", readTimelineScale("demucsMapDurationScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX));
			dm.addProperty("energyScale", readTimelineScale("demucsMapEnergyScale", 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX));
			dm.addProperty("gapScale", readTimelineScale("demucsMapGapScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX));

			JsonObject featureScale = new JsonObject();
			for (String featureKey : DEMUCS_FEATURE_KEYS) {
				JsonObject featureObj = new JsonObject();
				featureObj.addProperty("durationScale",
					readTimelineScale(featureMetadataKey(featureKey, "duration"), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX));
				featureObj.addProperty("energyScale",
					readTimelineScale(featureMetadataKey(featureKey, "energy"), 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX));
				featureObj.addProperty("gapScale",
					readTimelineScale(featureMetadataKey(featureKey, "gap"), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX));
				featureScale.add(featureKey, featureObj);
			}
			dm.add("featureScale", featureScale);
			root.add("demucsMapping", dm);
			writeConfigRoot(configPath, root);
		} catch (Exception e) {
			LOGGER.debug("BeatBlock config: failed to persist demucs mapping reason={}", e.toString());
		}
	}

	private void persistActionExecutionConfig() {
		Timeline current = timeline.get();
		Path configPath = uiConfigPath.get();
		if (current == null || configPath == null) {
			return;
		}
		try {
			JsonObject root = readOrCreateConfigRoot(configPath);
			JsonObject action = root.has("timelineActionExecution") && root.get("timelineActionExecution").isJsonObject()
				? root.getAsJsonObject("timelineActionExecution")
				: new JsonObject();
			action.addProperty("rollbackMode", readActionRollbackMode());
			root.add("timelineActionExecution", action);
			writeConfigRoot(configPath, root);
		} catch (Exception e) {
			LOGGER.debug("BeatBlock config: failed to persist action execution reason={}", e.toString());
		}
	}

	private double readTimelineScale(String key, double defaultValue, double min, double max) {
		Timeline current = timeline.get();
		if (current == null || key == null || key.isBlank()) {
			return defaultValue;
		}
		Object raw = current.getMetadata(key);
		if (raw == null) {
			return defaultValue;
		}
		double value;
		if (raw instanceof Number n) {
			value = n.doubleValue();
		} else {
			try {
				value = Double.parseDouble(raw.toString().trim());
			} catch (Exception e) {
				return defaultValue;
			}
		}
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return defaultValue;
		}
		return Math.max(min, Math.min(max, value));
	}

	private void writeTimelineScale(String key, float value) {
		Timeline current = timeline.get();
		if (current == null || key == null || key.isBlank()) {
			return;
		}
		current.setMetadata(key, value);
	}

	private static JsonObject readOrCreateConfigRoot(Path configPath) throws Exception {
		JsonObject root = new JsonObject();
		if (Files.isRegularFile(configPath)) {
			String existing = Files.readString(configPath, StandardCharsets.UTF_8);
			if (!existing.isBlank()) {
				root = JsonParser.parseString(existing).getAsJsonObject();
			}
		}
		return root;
	}

	private static void writeConfigRoot(Path configPath, JsonObject root) throws Exception {
		Files.createDirectories(configPath.getParent());
		Files.writeString(configPath, UI_CONFIG_GSON.toJson(root), StandardCharsets.UTF_8);
	}

	private static void applyDefaultScaleFromJson(
		Timeline timeline,
		JsonObject dm,
		String jsonKey,
		String metadataKey,
		double min,
		double max
	) {
		if (timeline.getMetadata(metadataKey) != null) {
			return;
		}
		double value = 1.0;
		if (dm.has(jsonKey)) {
			try {
				value = dm.get(jsonKey).getAsDouble();
			} catch (Exception ignored) {}
		}
		value = Math.max(min, Math.min(max, value));
		timeline.setMetadata(metadataKey, value);
	}

	private static boolean isValidDemucsPreset(String value) {
		return "drive".equals(value) || "detail".equals(value) || "balanced".equals(value);
	}

	private static boolean isValidClipGenerationMode(String value) {
		return "trigger".equals(value) || "sustain".equals(value) || "mixed".equals(value);
	}
}
