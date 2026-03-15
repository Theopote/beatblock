package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.rendering.TimelineToolbar;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 底部通栏时间线面板：顶部工具栏 + 标题/时间 + 时间线主体（由 TimelineEditor 渲染）。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private final TimelineToolbar toolbar = new TimelineToolbar();

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

		// ----- 顶部工具栏（右侧显示当前时间/总时长） -----
		if (editor != null) {
			toolbar.render(editor, editor.getToolbarState());
		}
		double duration = editor != null && BeatBlock.timeline.getDurationSeconds() > 0
			? BeatBlock.timeline.getDurationSeconds()
			: (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.getDurationSeconds() > 0 ? BeatBlock.musicPlayer.getDurationSeconds() : 60.0);
		double currentTime = editor != null ? editor.getClock().getCurrentTimeSeconds() : 0;
		ImGui.sameLine(ImGui.getWindowWidth() - 100);
		ImGui.textDisabled(String.format("%.1fs / %.1fs", currentTime, duration));

		// ----- 紧接工具栏下方：时间刻度（标尺） -----
		ImGui.separator();
		if (editor != null) {
			editor.render();
		}

		ImGui.end();
	}
}
