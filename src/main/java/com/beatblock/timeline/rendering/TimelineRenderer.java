package com.beatblock.timeline.rendering;

import com.beatblock.timeline.FrequencyBand;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.SelectionBox;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

/**
 * 时间线渲染入口：按 4 区域绘制（1.时间尺 2.轨道名 3.网格 4.内容/事件/播放头/框选）。
 */
public final class TimelineRenderer {

	private static final int PLAYHEAD_COLOR = 0xFF_FF_66_66;
	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;
	private static final int ROW_SEPARATOR_COLOR = 0x33_88_88_88;
	/** 左侧轨道列表与右侧内容区的竖线分隔 */
	private static final int DIVIDER_COLOR = 0x66_88_88_88;

	private final GridRenderer gridRenderer = new GridRenderer();
	private final TrackRenderer trackRenderer = new TrackRenderer();
	private final EventRenderer eventRenderer = new EventRenderer();
	private final WaveformRenderer waveformRenderer = new WaveformRenderer();

	/** 固定区域：只绘制时间刻度行（左侧「时间」标签 + 标尺），分界线与轨道区对齐，并占位。 */
	public void renderRulerRow(TimelineLayout layout, TimelineViewState viewState) {
		if (viewState == null || layout == null) return;
		ImGui.setCursorPosX(4);
		ImGui.textDisabled("时间");
		gridRenderer.renderRuler(layout.startY, viewState, layout);
		// 分区竖线：时间刻度与轨道区在同一分界处开始
		float divX = layout.trackHeaderLeft + layout.trackHeaderWidth;
		ImGui.getWindowDrawList().addLine(divX, layout.rulerTop, divX, layout.rulerTop + layout.rulerHeight, DIVIDER_COLOR, 1f);
		ImGui.setCursorPosY(layout.startY + TimelineLayout.RULER_HEIGHT);
	}

	/** 可滚动区域：轨道区（左侧轨道列表 + 竖线分隔 + 网格 + 一行一行轨道 + 播放头 + 框选）。 */
	public void renderTrackArea(
		Timeline timeline,
		TimelineViewState viewState,
		SelectionState selectionState,
		TimelineClock clock,
		SelectionBox selectionBox,
		TimelineTrackListState trackListState,
		TimelineLayout layout
	) {
		if (timeline == null || viewState == null || layout == null) return;

		// 预留轨道区总高度，使子窗口滚动范围正确
		ImGui.dummy(0, layout.contentHeight);

		// 左侧轨道列表与右侧内容区的竖线分隔（与标尺分界对齐）
		float divX = layout.trackHeaderLeft + layout.trackHeaderWidth;
		ImGui.getWindowDrawList().addLine(divX, layout.contentTop, divX, layout.contentTop + layout.contentHeight, DIVIDER_COLOR, 1f);

		// 网格竖线（底层）
		gridRenderer.render(viewState, layout, layout.contentHeight);

		// 轨道名 + 内容区，每行后画分隔线，使轨道看起来一行一行
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			float rowY = layout.getRowCursorY(i);
			boolean isGroup = (i == 0 || i == 5);
			String label = rowLabel(i);
			trackRenderer.drawTrackLabel(rowY, i, label, isGroup, trackListState);
			drawRowContent(i, rowY, timeline, viewState, selectionState, layout);
			// 行底分隔线
			float lineY = layout.getRowScreenY(i) + layout.rowHeight;
			float x0 = layout.trackHeaderLeft;
			float x1 = layout.contentLeft + layout.contentWidth;
			ImGui.getWindowDrawList().addLine(x0, lineY, x1, lineY, ROW_SEPARATOR_COLOR, 1f);
		}

		// 播放头（仅限轨道区高度）
		if (clock != null) {
			double currentTime = clock.getCurrentTimeSeconds();
			float playheadX = viewState.timeToScreen(currentTime);
			if (playheadX >= -2 && playheadX <= layout.contentWidth + 2) {
				float px = layout.contentLeft + playheadX;
				float py0 = layout.contentTop;
				float py1 = layout.contentTop + layout.contentHeight;
				ImGui.getWindowDrawList().addLine(px, py0, px, py1, PLAYHEAD_COLOR, 2f);
			}
		}

		// 框选矩形
		if (selectionBox != null && selectionBox.isActive()) {
			ImGui.getWindowDrawList().addRect(selectionBox.getMinX(), selectionBox.getMinY(), selectionBox.getMaxX(), selectionBox.getMaxY(), SELECTED_BORDER_COLOR, 0f, 0, 1.5f);
		}
	}

	private static String rowLabel(int rowIndex) {
		switch (rowIndex) {
			case 0: return "音频";
			case 1: return "波形";
			case 2: return "低频";
			case 3: return "中频";
			case 4: return "高频";
			case 5: return "动画";
			case 6: return "方块动画";
			case 7: return "自动动画";
			case 8: return "关键帧";
			case 9: return "事件";
			default: return "";
		}
	}

	private void drawRowContent(int rowIndex, float rowY, Timeline timeline, TimelineViewState viewState, SelectionState selectionState, TimelineLayout layout) {
		switch (rowIndex) {
			case 1:
				waveformRenderer.render(rowY, timeline, layout, viewState);
				break;
			case 2:
				eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.LOW), layout, viewState);
				break;
			case 3:
				eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.MID), layout, viewState);
				break;
			case 4:
				eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.HIGH), layout, viewState);
				break;
			case 6:
				eventRenderer.renderAnimationEventBlocks(rowY, timeline.getBlockAnimationEvents(), layout, viewState, selectionState);
				break;
			case 7:
				eventRenderer.renderAnimationEventBlocks(rowY, timeline.getAutoAnimationEvents(), layout, viewState, selectionState);
				break;
			case 8:
				eventRenderer.renderCameraKeyframeRow(rowY, timeline.getCameraKeyframes(), layout, viewState);
				break;
			case 9:
				eventRenderer.renderGlobalEventRow(rowY, timeline.getGlobalEvents(), layout, viewState);
				break;
			default:
				break;
		}
	}
}
