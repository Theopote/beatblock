package com.beatblock.ui.panels;

import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 左侧工具面板。
 */
public class ToolPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}
		ImGui.text("工具");
		ImGui.separator();
		ImGui.textWrapped("选择、画笔、橡皮等工具将在此列出。");
		ImGui.end();
	}
}
