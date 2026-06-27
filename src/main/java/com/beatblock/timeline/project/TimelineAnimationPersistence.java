package com.beatblock.timeline.project;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间线 clips/events ↔ JSON（.osc 持久化：动画 / 摄像机 / 全局事件轨）。
 */
public final class TimelineAnimationPersistence {

	private static final List<String> CORE_TRACK_IDS = List.of(
		Timeline.TRACK_ID_ANIMATION_BLOCK,
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_BUILD_REVERSE,
		Timeline.TRACK_ID_CAMERA,
		Timeline.TRACK_ID_GLOBAL
	);

	private TimelineAnimationPersistence() {}

	public static JsonArray toJson(Timeline timeline) {
		JsonArray tracksArr = new JsonArray();
		if (timeline == null) return tracksArr;
		for (String trackId : collectPersistedTrackIds(timeline)) {
			Track track = timeline.getTrack(trackId);
			if (track == null || track.getClips().isEmpty()) continue;
			tracksArr.add(trackToJson(trackId, track));
		}
		return tracksArr;
	}

	public static void loadInto(Timeline timeline, JsonArray arr) {
		if (timeline == null) return;
		for (String trackId : collectPersistedTrackIds(timeline)) {
			clearTrack(timeline, trackId);
		}
		if (arr == null) return;
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			trackFromJson(timeline, arr.get(i).getAsJsonObject());
		}
		timeline.markAnimationEventsDirty();
	}

	private static List<String> collectPersistedTrackIds(Timeline timeline) {
		java.util.ArrayList<String> ids = new java.util.ArrayList<>(CORE_TRACK_IDS);
		if (timeline == null) return ids;
		for (Track track : timeline.getTracks()) {
			if (Timeline.isBlockAnimationFeatureTrackId(track.getId())) {
				ids.add(track.getId());
			}
		}
		return ids;
	}

	private static void clearTrack(Timeline timeline, String trackId) {
		if (Timeline.TRACK_ID_CAMERA.equals(trackId)) {
			timeline.clearCameraKeyframes();
		} else if (Timeline.TRACK_ID_GLOBAL.equals(trackId)) {
			timeline.clearGlobalEvents();
		} else {
			timeline.clearAnimationTrack(trackId);
		}
	}

	private static JsonObject trackToJson(String trackId, Track track) {
		JsonObject root = new JsonObject();
		root.addProperty("trackId", trackId);
		JsonArray clipsArr = new JsonArray();
		for (Clip clip : track.getClips()) {
			clipsArr.add(clipToJson(clip));
		}
		root.add("clips", clipsArr);
		return root;
	}

	private static JsonObject clipToJson(Clip clip) {
		JsonObject root = new JsonObject();
		root.addProperty("id", clip.getId());
		root.addProperty("startTimeSeconds", clip.getStartTimeSeconds());
		root.addProperty("endTimeSeconds", clip.getEndTimeSeconds());
		JsonArray eventsArr = new JsonArray();
		for (TimelineEvent event : clip.getEvents()) {
			eventsArr.add(eventToJson(event));
		}
		root.add("events", eventsArr);
		return root;
	}

	private static JsonObject eventToJson(TimelineEvent event) {
		JsonObject root = new JsonObject();
		root.addProperty("id", event.getId());
		root.addProperty("timeSeconds", event.getTimeSeconds());
		root.addProperty("type", event.getType().name());
		root.add("parameters", paramsToJson(event.getParameters()));
		return root;
	}

	private static void trackFromJson(Timeline timeline, JsonObject root) {
		if (root == null || !root.has("trackId")) return;
		String trackId = root.get("trackId").getAsString();
		Track track = ensureTrack(timeline, trackId);
		if (track == null || !root.has("clips") || !root.get("clips").isJsonArray()) return;
		JsonArray clipsArr = root.getAsJsonArray("clips");
		for (int i = 0; i < clipsArr.size(); i++) {
			if (!clipsArr.get(i).isJsonObject()) continue;
			clipFromJson(track, clipsArr.get(i).getAsJsonObject());
		}
		timeline.markAnimationEventsDirty(trackId);
	}

	private static Track ensureTrack(Timeline timeline, String trackId) {
		if (timeline == null || trackId == null) return null;
		Track existing = timeline.getTrack(trackId);
		if (existing != null) return existing;
		if (Timeline.isBlockAnimationFeatureTrackId(trackId)) {
			String featureKey = Timeline.blockAnimationFeatureKeyFromTrackId(trackId);
			Track track = new Track(trackId, featureKey, com.beatblock.timeline.TrackType.ANIMATION);
			timeline.addTrack(track);
			return track;
		}
		return null;
	}

	private static void clipFromJson(Track track, JsonObject root) {
		if (track == null || root == null) return;
		String id = root.has("id") ? root.get("id").getAsString() : "";
		double start = root.has("startTimeSeconds") ? root.get("startTimeSeconds").getAsDouble() : 0;
		double end = root.has("endTimeSeconds") ? root.get("endTimeSeconds").getAsDouble() : start;
		Clip clip = new Clip(id, start, end);
		track.addClip(clip);
		if (!root.has("events") || !root.get("events").isJsonArray()) return;
		JsonArray eventsArr = root.getAsJsonArray("events");
		for (int i = 0; i < eventsArr.size(); i++) {
			if (!eventsArr.get(i).isJsonObject()) continue;
			TimelineEvent event = eventFromJson(eventsArr.get(i).getAsJsonObject());
			if (event != null) clip.addEvent(event);
		}
	}

	private static TimelineEvent eventFromJson(JsonObject root) {
		if (root == null) return null;
		String id = root.has("id") ? root.get("id").getAsString() : "";
		double time = root.has("timeSeconds") ? root.get("timeSeconds").getAsDouble() : 0;
		EventType type = EventType.ANIMATION;
		if (root.has("type")) {
			try {
				type = EventType.valueOf(root.get("type").getAsString());
			} catch (IllegalArgumentException e) {
				BeatBlock.LOGGER.debug("Unknown event type in .osc, using ANIMATION", e);
			}
		}
		Map<String, Object> params = paramsFromJson(root.has("parameters") ? root.get("parameters") : null);
		return new TimelineEvent(id, time, type, params);
	}

	private static JsonObject paramsToJson(Map<String, Object> params) {
		JsonObject root = new JsonObject();
		if (params == null) return root;
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) continue;
			Object value = entry.getValue();
			if (value instanceof Number number) {
				root.addProperty(entry.getKey(), number);
			} else if (value instanceof Boolean bool) {
				root.addProperty(entry.getKey(), bool);
			} else {
				root.addProperty(entry.getKey(), String.valueOf(value));
			}
		}
		return root;
	}

	private static Map<String, Object> paramsFromJson(JsonElement element) {
		Map<String, Object> out = new HashMap<>();
		if (element == null || element.isJsonNull() || !element.isJsonObject()) return out;
		JsonObject obj = element.getAsJsonObject();
		for (String key : obj.keySet()) {
			JsonElement value = obj.get(key);
			if (value == null || value.isJsonNull()) continue;
			if (value.isJsonPrimitive()) {
				var prim = value.getAsJsonPrimitive();
				if (prim.isBoolean()) out.put(key, prim.getAsBoolean());
				else if (prim.isNumber()) out.put(key, prim.getAsDouble());
				else out.put(key, prim.getAsString());
			} else {
				out.put(key, value.toString());
			}
		}
		return out;
	}
}
