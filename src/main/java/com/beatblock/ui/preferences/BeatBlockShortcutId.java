package com.beatblock.ui.preferences;

/** BeatBlock 可配置快捷键。 */
public enum BeatBlockShortcutId {
	UNDO("undo", "Ctrl+Z"),
	REDO("redo", "Ctrl+Y"),
	COPY("copy", "Ctrl+C"),
	PASTE("paste", "Ctrl+V"),
	DELETE("delete", "Delete"),
	IMPORT_MUSIC("import_music", "Ctrl+O"),
	SAVE_PROJECT("save_project", "Ctrl+S"),
	OPEN_PROJECT("open_project", "Ctrl+Shift+O");

	private final String id;
	private final String defaultChord;

	BeatBlockShortcutId(String id, String defaultChord) {
		this.id = id;
		this.defaultChord = defaultChord;
	}

	public String id() {
		return id;
	}

	public String defaultChord() {
		return defaultChord;
	}
}
