package com.beatblock.ui.panels;

import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * 动画库面板，可通过菜单「视图 → 面板」打开/关闭。
 */
public class AnimationLibraryPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			return;
		}
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.ANIMATION_LIBRARY_WINDOW, pOpen, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}
		ImGui.text("动画库");
		ImGui.separator();
		ImGui.textWrapped("预设动画模板（如 bounce、slide、pulse）将在此列出，可拖入时间线。");
		ImGui.end();
	}
}
