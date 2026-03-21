package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.rendering.TimelineRenderer;
import com.beatblock.timeline.rendering.TimelineToolbar;
import com.beatblock.timeline.util.MusicTimeFormatter;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 底部通栏时间线面板：固定工具栏 + 固定时间刻度，轨道区在可滚动子窗口中一行一行显示。
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

		// ----- 固定区域：工具栏（不随滚动条滚动） -----
		if (editor != null) {
			toolbar.render(editor, editor.getToolbarState());
		}
		double duration = editor != null && BeatBlock.timeline.getDurationSeconds() > 0
			? BeatBlock.timeline.getDurationSeconds()
			: (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.getDurationSeconds() > 0 ? BeatBlock.musicPlayer.getDurationSeconds() : 60.0);
		double currentTime = editor != null ? editor.getClock().getCurrentTimeSeconds() : 0;
		double bpm = BeatBlock.timeline != null ? BeatBlock.timeline.getBpm() : 0;
		String timeDisplay = MusicTimeFormatter.formatPositionDisplay(currentTime, duration, bpm);
		ImGui.sameLine(ImGui.getWindowWidth() - 240);
		ImGui.textDisabled(timeDisplay);

		// ----- 固定区域：时间刻度（标尺），紧接工具栏下方 -----
		ImGui.separator();
		if (editor != null) {
			editor.renderRulerOnly();
			editor.handleRulerInteraction();
			editor.tryBeginTimelineDividerDragOnRuler();
		}

		// ----- 可滚动区域：轨道区（无外边框），仅此处出现滚动条；高度取剩余空间 -----
		if (ImGui.beginChild("##TimelineTracks", 0, -1, false)) {
			if (editor != null) {
				editor.renderTrackArea();
			}
		}
		ImGui.endChild();

		// 轨道头 / 时间轴 竖向分割线：自标尺顶贯通到子窗口底（与拖动宽度一致）
		if (editor != null) {
			float divX = editor.getCachedDividerScreenX();
			float y0 = editor.getCachedDividerTopScreenY();
			float y1 = editor.getCachedDividerContentBottomScreenY();
			if (y1 > y0) {
				ImGui.getWindowDrawList().addLine(divX, y0, divX, y1, TimelineRenderer.TIMELINE_DIVIDER_COLOR, 1f);
			}
		}

		ImGui.end();
	}
}
