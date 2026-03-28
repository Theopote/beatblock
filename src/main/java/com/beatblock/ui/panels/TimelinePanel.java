package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.rendering.TimelineToolbar;
import com.beatblock.timeline.util.MusicTimeFormatter;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * 底部通栏时间线面板：固定工具栏 + 固定时间刻度，轨道区在可滚动子窗口中一行一行显示。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private final TimelineToolbar toolbar = new TimelineToolbar();

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			if (BeatBlock.timeline == null) {
				ImGui.text("时间线（未加载模型）");
				return;
			}

			TimelineEditor editor = BeatBlock.timelineEditor;

			if (editor != null) {
				toolbar.render(editor, editor.getToolbarState());
			}
			double duration = editor != null && BeatBlock.timeline.getDurationSeconds() > 0
				? BeatBlock.timeline.getDurationSeconds()
				: (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.getDurationSeconds() > 0 ? BeatBlock.musicPlayer.getDurationSeconds() : 60.0);
			double currentTime = editor != null ? editor.getClock().getCurrentTimeSeconds() : 0;
			double bpm = BeatBlock.timeline != null ? BeatBlock.timeline.getBpm() : 0;
			String timeDisplay = MusicTimeFormatter.formatPositionDisplay(currentTime, duration, bpm);
			ImVec2 timeSize = ImGui.calcTextSize(timeDisplay);
			float rightPadding = 12f;
			float targetX = Math.max(ImGui.getCursorPosX(), ImGui.getWindowContentRegionMaxX() - timeSize.x - rightPadding);
			ImGui.sameLine(targetX);
			ImGui.textDisabled(timeDisplay);

			ImGui.separator();
			if (editor != null) {
				editor.beginFrameLayout();
				editor.renderRulerOnly();
				editor.handleRulerInteraction();
				editor.tryBeginTimelineDividerDragOnRuler();
			}

			if (ImGui.beginChild("##TimelineTracks", 0, -1, false)) {
				if (editor != null) {
					editor.renderTrackArea();
				}
			}
			ImGui.endChild();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW);
		}
	}
}
