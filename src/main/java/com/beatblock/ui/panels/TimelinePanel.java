package com.beatblock.ui.panels;

import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 底部通栏时间线面板：音乐、摄像机、动画事件等轨道。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}
		ImGui.text("时间线");
		ImGui.sameLine();
		ImGui.textDisabled("(音乐 | 摄像机 | 动画事件)");
		ImGui.separator();
		ImGui.textWrapped("轨道与关键帧将在此显示与编辑。");
		ImGui.end();
	}
}
