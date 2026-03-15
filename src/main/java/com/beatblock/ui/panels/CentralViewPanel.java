package com.beatblock.ui.panels;

import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 中央区域：留空以便看到 Minecraft 场景。
 * 使用 NoBackground 等使中间不遮挡；Dockspace 使用 PassthruCentralNode 使鼠标可穿透。
 */
public class CentralViewPanel {

	private static final int WINDOW_FLAGS =
		ImGuiWindowFlags.NoCollapse
			| ImGuiWindowFlags.NoBackground
			| ImGuiWindowFlags.NoScrollbar
			| ImGuiWindowFlags.NoInputs;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.CENTRAL_VIEW_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}
		// 不绘制内容，仅占位；场景由 Minecraft 在底层渲染
		ImGui.end();
	}
}
