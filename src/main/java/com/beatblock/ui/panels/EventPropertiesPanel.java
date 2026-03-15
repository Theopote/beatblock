package com.beatblock.ui.panels;

import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 右侧事件属性面板。
 */
public class EventPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.EVENT_PROPERTIES_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}
		ImGui.text("事件属性");
		ImGui.separator();
		ImGui.textWrapped("选中时间线上的事件后，可在此编辑属性。");
		ImGui.end();
	}
}
