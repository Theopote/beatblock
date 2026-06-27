package com.beatblock.video;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 视频导出默认项，持久化到 config/beatblock/export.json。 */
public final class VideoExportPreferences {

	private static final Logger LOGGER = LoggerFactory.getLogger(VideoExportPreferences.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static int resolutionPresetIndex = 1;
	private static int fpsPresetIndex = 1;
	private static boolean includeAudio = true;
	private static String lastOutputDirectory = "";
	private static boolean loaded;

	private VideoExportPreferences() {}

	public static int resolutionPresetIndex() {
		ensureLoaded();
		return resolutionPresetIndex;
	}

	public static void setResolutionPresetIndex(int index) {
		ensureLoaded();
		resolutionPresetIndex = Math.max(0, index);
		save();
	}

	public static int fpsPresetIndex() {
		ensureLoaded();
		return fpsPresetIndex;
	}

	public static void setFpsPresetIndex(int index) {
		ensureLoaded();
		fpsPresetIndex = Math.max(0, index);
		save();
	}

	public static boolean includeAudio() {
		ensureLoaded();
		return includeAudio;
	}

	public static void setIncludeAudio(boolean value) {
		ensureLoaded();
		includeAudio = value;
		save();
	}

	public static String lastOutputDirectory() {
		ensureLoaded();
		return lastOutputDirectory;
	}

	public static void setLastOutputDirectory(String directory) {
		ensureLoaded();
		lastOutputDirectory = directory != null ? directory.trim() : "";
		save();
	}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("beatblock/export.json");
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = configPath();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			if (root.has("resolutionPresetIndex")) {
				resolutionPresetIndex = root.get("resolutionPresetIndex").getAsInt();
			}
			if (root.has("fpsPresetIndex")) {
				fpsPresetIndex = root.get("fpsPresetIndex").getAsInt();
			}
			if (root.has("includeAudio")) {
				includeAudio = root.get("includeAudio").getAsBoolean();
			}
			if (root.has("lastOutputDirectory")) {
				lastOutputDirectory = root.get("lastOutputDirectory").getAsString();
			}
		} catch (Exception e) {
			LOGGER.warn("Unable to load export preferences from {}", path, e);
		}
	}

	private static void save() {
		try {
			Path path = configPath();
			Files.createDirectories(path.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("resolutionPresetIndex", resolutionPresetIndex);
			root.addProperty("fpsPresetIndex", fpsPresetIndex);
			root.addProperty("includeAudio", includeAudio);
			root.addProperty("lastOutputDirectory", lastOutputDirectory);
			Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Unable to save export preferences", e);
		}
	}
}
