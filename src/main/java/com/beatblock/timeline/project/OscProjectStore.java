package com.beatblock.timeline.project;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * .osc 项目文件读写（轻量版）。
 *
 * 当前版本仅存储项目身份与时间线基础信息：
 * - projectId / projectPath
 * - timelineName
 * - audioPath
 */
public final class OscProjectStore {

	private static final int CURRENT_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private OscProjectStore() {}

	public static void save(Path filePath, Timeline timeline) throws IOException {
		if (filePath == null) throw new IOException("保存失败：文件路径为空");
		if (timeline == null) throw new IOException("保存失败：Timeline 为空");

		Path abs = filePath.toAbsolutePath().normalize();
		Path parent = abs.getParent();
		if (parent != null) Files.createDirectories(parent);

		String projectId = stringMeta(timeline, "projectId");
		if (projectId.isBlank()) projectId = UUID.randomUUID().toString();
		String audioPath = stringMeta(timeline, "audioPath");
		String timelineName = timeline.getName() == null ? "" : timeline.getName();

		JsonObject root = new JsonObject();
		root.addProperty("version", CURRENT_VERSION);
		root.addProperty("projectId", projectId);
		root.addProperty("projectPath", abs.toString());
		root.addProperty("timelineName", timelineName);
		root.addProperty("audioPath", audioPath);
		JsonArray markers = new JsonArray();
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null) continue;
			JsonObject markerObj = new JsonObject();
			markerObj.addProperty("timeSeconds", marker.getTimeSeconds());
			markerObj.addProperty("name", marker.getName());
			markers.add(markerObj);
		}
		root.add("markers", markers);

		Files.writeString(abs, GSON.toJson(root), StandardCharsets.UTF_8);

		// 回写到 timeline，确保后续 UI 隔离键稳定。
		timeline.setMetadata("projectId", projectId);
		timeline.setMetadata("projectPath", abs.toString());
		if (!audioPath.isBlank()) timeline.setMetadata("audioPath", audioPath);
	}

	public static LoadedProject load(Path filePath) throws IOException {
		if (filePath == null) throw new IOException("打开失败：文件路径为空");
		Path abs = filePath.toAbsolutePath().normalize();
		if (!Files.exists(abs)) throw new IOException("打开失败：文件不存在 " + abs);

		String json = Files.readString(abs, StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		int version = getInt(root, "version", 1);
		if (version > CURRENT_VERSION) {
			throw new IOException("不支持的 .osc 版本: " + version + " (当前支持 <= " + CURRENT_VERSION + ")");
		}

		String projectId = getString(root, "projectId", "");
		if (projectId.isBlank()) projectId = UUID.randomUUID().toString();

		String projectPath = getString(root, "projectPath", abs.toString());
		String timelineName = getString(root, "timelineName", "");
		String audioPath = getString(root, "audioPath", "");
		List<TimelineMarker> markers = parseMarkers(root);

		return new LoadedProject(projectId, projectPath, timelineName, audioPath, markers);
	}

	private static List<TimelineMarker> parseMarkers(JsonObject root) {
		List<TimelineMarker> markers = new ArrayList<>();
		if (root == null || !root.has("markers") || root.get("markers").isJsonNull()) return markers;
		try {
			JsonArray arr = root.getAsJsonArray("markers");
			for (int i = 0; i < arr.size(); i++) {
				JsonObject obj = arr.get(i).getAsJsonObject();
				double timeSeconds = obj.has("timeSeconds") ? obj.get("timeSeconds").getAsDouble() : 0;
				String name = getString(obj, "name", "");
				markers.add(new TimelineMarker(timeSeconds, name));
			}
		} catch (Exception ignored) {}
		markers.sort(java.util.Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return markers;
	}

	private static int getInt(JsonObject obj, String key, int def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		try {
			return obj.get(key).getAsInt();
		} catch (Exception ignored) {
			return def;
		}
	}

	private static String getString(JsonObject obj, String key, String def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		try {
			return obj.get(key).getAsString();
		} catch (Exception ignored) {
			return def;
		}
	}

	private static String stringMeta(Timeline timeline, String key) {
		Object v = timeline.getMetadata(key);
		if (v == null) return "";
		String s = String.valueOf(v);
		return s == null ? "" : s.trim();
	}

	public static final class LoadedProject {
		private final String projectId;
		private final String projectPath;
		private final String timelineName;
		private final String audioPath;
		private final List<TimelineMarker> markers;

		public LoadedProject(String projectId, String projectPath, String timelineName, String audioPath, List<TimelineMarker> markers) {
			this.projectId = projectId == null ? "" : projectId;
			this.projectPath = projectPath == null ? "" : projectPath;
			this.timelineName = timelineName == null ? "" : timelineName;
			this.audioPath = audioPath == null ? "" : audioPath;
			this.markers = markers != null ? List.copyOf(markers) : List.of();
		}

		public String getProjectId() { return projectId; }
		public String getProjectPath() { return projectPath; }
		public String getTimelineName() { return timelineName; }
		public String getAudioPath() { return audioPath; }
		public List<TimelineMarker> getMarkers() { return markers; }
	}
}
