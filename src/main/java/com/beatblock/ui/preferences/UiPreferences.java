package com.beatblock.ui.preferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import imgui.flag.ImGuiCol;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** UI 偏好：主题与快捷键，持久化到 config/beatblock/ui.json。 */
public final class UiPreferences {

	private static final Logger LOGGER = LoggerFactory.getLogger(UiPreferences.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String THEME_KEY = "uiTheme";
	private static final String SHORTCUTS_KEY = "shortcuts";

	private static UiTheme theme = UiTheme.DARK;
	private static final EnumMap<BeatBlockShortcutId, String> shortcuts = new EnumMap<>(BeatBlockShortcutId.class);
	private static boolean loaded;

	private UiPreferences() {
	}

	public static UiTheme theme() {
		ensureLoaded();
		return theme;
	}

	public static void setTheme(UiTheme value) {
		ensureLoaded();
		theme = value != null ? value : UiTheme.DARK;
		save();
	}

	public static String shortcut(BeatBlockShortcutId id) {
		ensureLoaded();
		return shortcuts.getOrDefault(id, id.defaultChord());
	}

	public static void setShortcut(BeatBlockShortcutId id, String chord) {
		if (id == null) {
			return;
		}
		ensureLoaded();
		if (chord == null || chord.isBlank() || chord.equalsIgnoreCase(id.defaultChord())) {
			shortcuts.remove(id);
		} else {
			shortcuts.put(id, chord.trim());
		}
		save();
	}

	public static void resetShortcuts() {
		ensureLoaded();
		shortcuts.clear();
		save();
	}

	public static Map<BeatBlockShortcutId, String> allShortcuts() {
		ensureLoaded();
		Map<BeatBlockShortcutId, String> out = new EnumMap<>(BeatBlockShortcutId.class);
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			out.put(id, shortcut(id));
		}
		return out;
	}

	public static void pushPanelThemeColors() {
		UiThemeColors colors = UiThemeColors.forTheme(theme());
		pushColor(ImGuiCol.WindowBg, colors.windowBg());
		pushColor(ImGuiCol.Text, colors.text());
		pushColor(ImGuiCol.TitleBg, colors.titleBg());
		pushColor(ImGuiCol.TitleBgActive, colors.titleBgActive());
		pushColor(ImGuiCol.TitleBgCollapsed, colors.titleBgCollapsed());
	}

	private static void pushColor(int colorIndex, float[] rgba) {
		imgui.ImGui.pushStyleColor(colorIndex, rgba[0], rgba[1], rgba[2], rgba[3]);
	}

	public static void popPanelThemeColors() {
		imgui.ImGui.popStyleColor(5);
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = uiConfigPath();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			if (root.has(THEME_KEY)) {
				theme = UiTheme.fromId(root.get(THEME_KEY).getAsString());
			}
			if (root.has(SHORTCUTS_KEY) && root.get(SHORTCUTS_KEY).isJsonObject()) {
				JsonObject map = root.getAsJsonObject(SHORTCUTS_KEY);
				for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
					if (map.has(id.id()) && map.get(id.id()).isJsonPrimitive()) {
						shortcuts.put(id, map.get(id.id()).getAsString());
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to load UI preferences from {}", path, e);
		}
	}

	private static void save() {
		Path path = uiConfigPath();
		try {
			JsonObject root;
			if (Files.isRegularFile(path)) {
				root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			} else {
				root = new JsonObject();
				Files.createDirectories(path.getParent());
			}
			root.addProperty(THEME_KEY, theme.id());
			JsonObject map = new JsonObject();
			for (Map.Entry<BeatBlockShortcutId, String> entry : shortcuts.entrySet()) {
				map.addProperty(entry.getKey().id(), entry.getValue());
			}
			root.add(SHORTCUTS_KEY, map);
			Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Failed to save UI preferences to {}", path, e);
		}
	}

	static Path uiConfigPath() {
		return FabricLoader.getInstance().getGameDir()
			.resolve("config").resolve("beatblock").resolve("ui.json");
	}
}
