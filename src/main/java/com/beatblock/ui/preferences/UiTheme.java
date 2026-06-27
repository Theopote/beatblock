package com.beatblock.ui.preferences;

/** UI 主题预设。 */
public enum UiTheme {
	DARK("dark"),
	LIGHT("light"),
	HIGH_CONTRAST("high_contrast");

	private final String id;

	UiTheme(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static UiTheme fromId(String raw) {
		if (raw == null || raw.isBlank()) {
			return DARK;
		}
		for (UiTheme theme : values()) {
			if (theme.id.equalsIgnoreCase(raw.trim())) {
				return theme;
			}
		}
		return DARK;
	}
}
