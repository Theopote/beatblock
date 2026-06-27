package com.beatblock.ui.eventlibrary;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 事件模板持久化：config/beatblock/event_templates.json */
public final class EventTemplateStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(EventTemplateStore.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type PARAM_MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

	private static final LinkedHashMap<String, EventTemplate> templates = new LinkedHashMap<>();
	private static boolean loaded;

	private EventTemplateStore() {
	}

	public static List<EventTemplate> all() {
		ensureLoaded();
		return List.copyOf(templates.values());
	}

	public static Optional<EventTemplate> find(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		ensureLoaded();
		return Optional.ofNullable(templates.get(id));
	}

	public static void add(EventTemplate template) {
		if (template == null) {
			return;
		}
		ensureLoaded();
		templates.put(template.id(), template);
		save();
	}

	public static boolean remove(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		ensureLoaded();
		if (templates.remove(id) == null) {
			return false;
		}
		save();
		return true;
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = storePath();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			JsonArray array = JsonParser.parseString(json).getAsJsonArray();
			templates.clear();
			for (JsonElement element : array) {
				EventTemplate template = parseTemplate(element.getAsJsonObject());
				if (template != null) {
					templates.put(template.id(), template);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to load event templates from {}", path, e);
		}
	}

	private static EventTemplate parseTemplate(JsonObject obj) {
		if (obj == null) {
			return null;
		}
		String id = stringOrEmpty(obj, "id");
		String name = stringOrEmpty(obj, "name");
		String animationTypeId = stringOrEmpty(obj, "animationTypeId");
		double duration = obj.has("durationSeconds") ? obj.get("durationSeconds").getAsDouble() : 0.5;
		float energy = obj.has("energy") ? obj.get("energy").getAsFloat() : 0.7f;
		Map<String, Object> params = obj.has("parameters")
			? GSON.fromJson(obj.get("parameters"), PARAM_MAP_TYPE)
			: Map.of();
		return new EventTemplate(id, name, animationTypeId, duration, energy, params);
	}

	private static String stringOrEmpty(JsonObject obj, String key) {
		return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : "";
	}

	private static void save() {
		Path path = storePath();
		try {
			JsonArray array = new JsonArray();
			for (EventTemplate template : templates.values()) {
				JsonObject obj = new JsonObject();
				obj.addProperty("id", template.id());
				obj.addProperty("name", template.name());
				obj.addProperty("animationTypeId", template.animationTypeId());
				obj.addProperty("durationSeconds", template.durationSeconds());
				obj.addProperty("energy", template.energy());
				obj.add("parameters", GSON.toJsonTree(template.parameters()));
				array.add(obj);
			}
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(array), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Failed to save event templates to {}", path, e);
		}
	}

	private static Path storePath() {
		return FabricLoader.getInstance().getGameDir()
			.resolve("config").resolve("beatblock").resolve("event_templates.json");
	}
}
