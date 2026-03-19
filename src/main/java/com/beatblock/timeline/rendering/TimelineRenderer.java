package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
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
	/** 轨道槽交替背景（深色），使轨道行更明显 */
	private static final int ROW_BG_EVEN = 0xFF_28_28_2A;
	private static final int ROW_BG_ODD = 0xFF_1E_1E_20;
	/** 左侧轨道列表与右侧内容区的竖线分隔（ABGR，供面板贯通绘制） */
	public static final int TIMELINE_DIVIDER_COLOR = 0x66_88_88_88;

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
		// 竖向分割线在 TimelinePanel 中自标尺顶贯通画到子窗口底，避免与滚动区重复/断层
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

		// 轨道槽交替背景（仅可见行），轨道与轨道之间靠 ROW_GAP 留白
		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			if (!layout.isRowVisible(i)) continue;
			float rowScreenY = layout.getRowScreenY(i);
			float rowH = layout.rowHeight;
			int vi = layout.getVisibleIndex(i);
			int bg = (vi % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
			ImGui.getWindowDrawList().addRectFilled(x0, rowScreenY, x1, rowScreenY + rowH, bg);
		}

		// 竖向分割线由 TimelinePanel 统一绘制（标尺—轨道区贯通）

		// 网格竖线（仅时间轴方向，不画行间线）
		gridRenderer.render(viewState, layout, layout.contentHeight);

		// 轨道名 + 内容区（仅可见行）；组可折叠，折叠后子轨道不绘制
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			if (!layout.isRowVisible(i)) continue;
			float rowY = layout.getRowCursorY(i);
			boolean isGroup = TimelineTrackMeta.isGroupRow(i);
			String displayName = trackListState != null ? trackListState.getDisplayName(i) : TimelineTrackMeta.getDefaultName(i);
			trackRenderer.drawTrackLabel(rowY, i, displayName, isGroup, trackListState, layout.trackHeaderLeft, layout.trackHeaderWidth);
			drawRowContent(i, rowY, timeline, viewState, selectionState, layout);
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

	private void drawRowContent(int rowIndex, float rowY, Timeline timeline, TimelineViewState viewState, SelectionState selectionState, TimelineLayout layout) {
		switch (rowIndex) {
			case 1: {
				// Audio 轨：支持从「音频解析」面板拖拽资产到此处
				float dropX = layout.contentLeft;
				float dropY = rowY;
				ImGui.setCursorScreenPos(dropX, dropY);
				ImGui.invisibleButton("##AudioTrackDropTarget", layout.contentWidth, layout.rowHeight);
				if (ImGui.beginDragDropTarget()) {
					if (ImGui.acceptDragDropPayload("BB_AUDIO_ASSET_ID") != null) {
						AudioAsset asset = AudioAssetManager.getInstance().getCurrentDragAsset();
						if (asset != null && asset.getFeatureTimeline() != null && BeatBlock.audioAnalysisEngine != null) {
							BeatBlock.audioAnalysisEngine.fillTimelineFromFeature(timeline, asset.getFeatureTimeline(), asset.getSampleRate());
							if (BeatBlock.timelineEditor != null) {
								BeatBlock.timelineEditor.syncClockDuration();
							}
						}
					}
					ImGui.endDragDropTarget();
				}
				waveformRenderer.render(rowY, timeline, layout, viewState);
				break;
			}
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
