package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.audio.BeatBlockRuntime;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.timeline.FrequencyBand;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.SelectionBox;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

import java.nio.charset.StandardCharsets;

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

	/** 音频组高亮颜色（紫色半透明边框，ABGR） */
	private static final int AUDIO_GROUP_DROP_HIGHLIGHT_COLOR = 0x55_7F_77_DD;

	private final GridRenderer gridRenderer = new GridRenderer();
	private final TrackRenderer trackRenderer = new TrackRenderer();
	private final EventRenderer eventRenderer = new EventRenderer();
	private final WaveformRenderer waveformRenderer = new WaveformRenderer();

	/** 当前帧音频组是否有拖拽悬停高亮（任意 row 0~4 悬停且有 audio payload 时置 true） */
	private boolean audioGroupDropHighlight;

	/**
	 * 固定区域：只绘制时间刻度行（左侧「时间」标签 + 标尺），分界线与轨道区对齐，并占位。
	 *
	 * @param bpm 来自 Timeline.getBpm()；传 0 表示无 BPM 信息
	 */
	public void renderRulerRow(TimelineLayout layout, TimelineViewState viewState, double bpm, TimelineToolbarState toolbarState, Timeline timeline) {
		if (viewState == null || layout == null) return;
		ImGui.setCursorPosX(4);
		ImGui.textDisabled("时间");
		gridRenderer.renderRuler(layout.startY, viewState, layout, bpm, toolbarState, timeline);
		// 竖向分割线在 TimelinePanel 中自标尺顶贯通画到子窗口底，避免与滚动区重复/断层
		ImGui.setCursorPosY(layout.startY + TimelineLayout.RULER_HEIGHT);
	}

	public void renderRulerRow(TimelineLayout layout, TimelineViewState viewState, double bpm) {
		renderRulerRow(layout, viewState, bpm, null, null);
	}

	/** 兼容旧调用（无 BPM）。 */
	public void renderRulerRow(TimelineLayout layout, TimelineViewState viewState) {
		renderRulerRow(layout, viewState, 0, null, null);
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

		// 每帧重置音频组拖放高亮标记
		audioGroupDropHighlight = false;

		// 轨道名 + 内容区（仅可见行）；组可折叠，折叠后子轨道不绘制
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			if (!layout.isRowVisible(i)) continue;
			float rowY = layout.getRowCursorY(i);
			boolean isGroup = TimelineTrackMeta.isGroupRow(i);
			String displayName = trackListState != null ? trackListState.getDisplayName(i) : TimelineTrackMeta.getDefaultName(i);

			// 音频组行（0~4）：在轨道头区域加一个拖放目标（先画，让后续交互控件覆盖在上面）
			if (i >= TimelineTrackMeta.ROW_AUDIO_GROUP && i <= TimelineTrackMeta.ROW_FREQ_HIGH) {
				renderAudioTrackHeaderDropTarget(i, timeline, layout);
			}

			trackRenderer.drawTrackLabel(rowY, i, displayName, isGroup, trackListState, layout.trackHeaderLeft, layout.trackHeaderWidth);
			drawRowContent(i, rowY, timeline, viewState, selectionState, layout);
		}

		// 音频组拖放高亮（在所有行内容绘制后叠加边框）
		drawAudioGroupDropHighlight(layout);

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
			case TimelineTrackMeta.ROW_AUDIO_GROUP:
			case TimelineTrackMeta.ROW_WAVEFORM:
			case TimelineTrackMeta.ROW_FREQ_LOW:
			case TimelineTrackMeta.ROW_FREQ_MID:
			case TimelineTrackMeta.ROW_FREQ_HIGH: {
				renderAudioGroupDropTarget(rowIndex, rowY, timeline, layout);
				if (rowIndex == TimelineTrackMeta.ROW_WAVEFORM) {
					waveformRenderer.render(rowY, timeline, layout, viewState);
				}
				if (rowIndex == TimelineTrackMeta.ROW_FREQ_LOW) {
					eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.LOW), layout, viewState);
				}
				if (rowIndex == TimelineTrackMeta.ROW_FREQ_MID) {
					eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.MID), layout, viewState);
				}
				if (rowIndex == TimelineTrackMeta.ROW_FREQ_HIGH) {
					eventRenderer.renderFrequencyDots(rowY, timeline.getFrequencyEventsByBand(FrequencyBand.HIGH), layout, viewState);
				}
				break;
			}
			case TimelineTrackMeta.ROW_ANIM_BLOCK:
				eventRenderer.renderAnimationEventBlocks(rowY, timeline.getBlockAnimationEvents(), layout, viewState, selectionState);
				break;
			case TimelineTrackMeta.ROW_ANIM_AUTO:
				eventRenderer.renderAnimationEventBlocks(rowY, timeline.getAutoAnimationEvents(), layout, viewState, selectionState);
				break;
			case TimelineTrackMeta.ROW_CAMERA:
				eventRenderer.renderCameraKeyframeRow(rowY, timeline.getCameraKeyframes(), layout, viewState);
				break;
			case TimelineTrackMeta.ROW_GLOBAL_EVENT:
				eventRenderer.renderGlobalEventRow(rowY, timeline.getGlobalEvents(), layout, viewState);
				break;
			default:
				break;
		}
	}

	/**
	 * 在指定行放置一个不可见按钮作为拖放目标（内容区），接受音频资产拖放。
	 * 行 0~4（音频组/波形/低中高频）均调用此方法；松手后自动填充整组数据。
	 */
	private void renderAudioGroupDropTarget(int rowIndex, float rowY, Timeline timeline, TimelineLayout layout) {
		float screenY = layout.getRowScreenY(rowIndex);
		if (screenY < 0) return;
		ImGui.setCursorScreenPos(layout.contentLeft, screenY);
		ImGui.invisibleButton("##AudioDropTarget_" + rowIndex, layout.contentWidth, layout.rowHeight);

		if (ImGui.isItemHovered()) {
			audioGroupDropHighlight = true;
		}

		acceptAudioAssetDrop(timeline);
	}

	/**
	 * 在轨道头区域为音频行放置拖放目标。先于 drawTrackLabel 调用，
	 * 使后续交互控件（折叠/改名/可见/锁定）覆盖在上方、优先获取 hover。
	 * 拖拽操作时鼠标不会点击按钮，因此不影响交互，而松手时 ImGui 会
	 * 回退到最底层的 hovered item 作为 drop target。
	 */
	private void renderAudioTrackHeaderDropTarget(int rowIndex, Timeline timeline, TimelineLayout layout) {
		float screenY = layout.getRowScreenY(rowIndex);
		if (screenY < 0) return;
		ImGui.setCursorScreenPos(layout.trackHeaderLeft, screenY);
		ImGui.invisibleButton("##AudioHeaderDrop_" + rowIndex, layout.trackHeaderWidth, layout.rowHeight);

		if (ImGui.isItemHovered()) {
			audioGroupDropHighlight = true;
		}

		acceptAudioAssetDrop(timeline);
	}

	/** 共用的音频资产拖放接受逻辑。在 invisibleButton 之后调用。 */
	private void acceptAudioAssetDrop(Timeline timeline) {
		if (ImGui.beginDragDropTarget()) {
			byte[] payload = ImGui.acceptDragDropPayload("BB_AUDIO_ASSET_ID");
			if (payload != null) {
				String assetId = new String(payload, StandardCharsets.UTF_8).trim();
				AudioAsset asset = AudioAssetManager.getInstance().findById(assetId);
				if (asset == null) {
					asset = AudioAssetManager.getInstance().getCurrentDragAsset();
				}
				if (asset != null && BeatBlock.audioAnalysisEngine != null) {
					if (asset.getBeatmap() != null) {
						BeatBlock.audioAnalysisEngine.fillTimelineFromBeatmap(timeline, asset.getBeatmap());
						BeatBlockRuntime.getInstance().loadBeatmap(asset.getBeatmap());
					} else if (asset.getFeatureTimeline() != null) {
						BeatBlock.audioAnalysisEngine.fillTimelineFromFeature(timeline, asset.getFeatureTimeline(), asset.getSampleRate());
					}
					if (BeatBlock.timelineEditor != null) {
						BeatBlock.timelineEditor.syncClockDuration();
					}
				}
			}
			ImGui.endDragDropTarget();
		}
	}

	/**
	 * 绘制音频组拖放高亮边框（row 0~4 外围），并在帧末重置标记。
	 * 应在所有行内容绘制完毕后调用。
	 */
	private void drawAudioGroupDropHighlight(TimelineLayout layout) {
		if (!audioGroupDropHighlight) return;
		// 寻找组内第一个和最后一个可见行
		float y0 = -1f, y1 = -1f;
		for (int r = TimelineTrackMeta.ROW_AUDIO_GROUP; r <= TimelineTrackMeta.ROW_FREQ_HIGH; r++) {
			float ry = layout.getRowScreenY(r);
			if (ry < 0) continue;
			if (y0 < 0) y0 = ry;
			y1 = ry + layout.rowHeight;
		}
		if (y0 >= 0 && y1 > y0) {
			// 高亮覆盖整行（轨道头 + 内容区），提示整个音频组都是拖放区域
			ImGui.getWindowDrawList().addRect(
				layout.trackHeaderLeft, y0,
				layout.contentLeft + layout.contentWidth, y1,
				AUDIO_GROUP_DROP_HIGHLIGHT_COLOR, 3f, 0, 1.5f);
		}
		audioGroupDropHighlight = false;
	}
}
