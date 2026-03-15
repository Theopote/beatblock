package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.timeline.TimelineEditor;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 底部通栏时间线面板：只负责窗口与标题区，实际绘制与交互由 TimelineEditor（rendering + interaction）完成。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		if (BeatBlock.timeline == null) {
			ImGui.text("时间线（未加载模型）");
			ImGui.end();
			return;
		}

		TimelineEditor editor = BeatBlock.timelineEditor;
		double duration = editor != null && BeatBlock.timeline.getDurationSeconds() > 0
			? BeatBlock.timeline.getDurationSeconds()
			: (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.getDurationSeconds() > 0 ? BeatBlock.musicPlayer.getDurationSeconds() : 60.0);
		double currentTime = editor != null ? editor.getClock().getCurrentTimeSeconds() : 0;

		ImGui.text("时间线");
		ImGui.sameLine();
		ImGui.textDisabled("(音乐 | 摄像机 | 动画事件)");
		ImGui.sameLine(ImGui.getWindowWidth() - 120);
		ImGui.text(String.format("%.1fs / %.1fs", currentTime, duration));
		ImGui.separator();

		if (editor != null) {
			editor.render();
		}

		ImGui.end();
	}
}
