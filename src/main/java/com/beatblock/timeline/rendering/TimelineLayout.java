package com.beatblock.timeline.rendering;

import imgui.ImGui;

/**
 * 时间线 UI 布局：4 个独立区域 + 行位置，渲染与交互共用，避免坐标不一致。
 *
 * 区域：
 * 1. TimeRuler   - 时间标尺（顶部横条）
 * 2. TrackHeaders - 左侧轨道名
 * 3. ContentArea  - 轨道内容（波形/事件等）
 * 4. GridArea    - 网格竖线（与 ContentArea 同范围）
 */
public final class TimelineLayout {

	public static final float TRACK_LABEL_WIDTH = 110f;
	public static final float ROW_HEIGHT = 22f;
	public static final float RULER_HEIGHT = 20f;

	/** 内容区行数（不含标尺）：音频组+波形+低中高频+动画组+方块/自动+摄像机+全局 */
	public static final int CONTENT_ROW_COUNT = 10;

	/** 可交互轨道在内容行中的索引（0-based）：方块动画、自动动画、摄像机、全局 */
	public static final int[] INTERACTIVE_ROW_INDICES = { 6, 7, 8, 9 };

	// ----- 区域边界（屏幕坐标） -----

	/** 1. 时间标尺：左侧 X */
	public float rulerLeft;
	/** 1. 时间标尺：顶部 Y */
	public float rulerTop;
	/** 1. 时间标尺：宽度 */
	public float rulerWidth;
	/** 1. 时间标尺：高度 */
	public float rulerHeight = RULER_HEIGHT;

	/** 2. 轨道名区域：左侧 X */
	public float trackHeaderLeft;
	/** 2. 轨道名区域：顶部 Y */
	public float trackHeaderTop;
	/** 2. 轨道名区域：宽度 */
	public float trackHeaderWidth = TRACK_LABEL_WIDTH;
	/** 2. 轨道名区域：总高度 */
	public float trackHeaderHeight;

	/** 3. 内容区：左侧 X */
	public float contentLeft;
	/** 3. 内容区：顶部 Y */
	public float contentTop;
	/** 3. 内容区：宽度 */
	public float contentWidth;
	/** 3. 内容区：总高度 */
	public float contentHeight;

	/** 4. 网格区与内容区同范围，仅逻辑区分 */

	/** 兼容子渲染器：与 contentWidth / TRACK_LABEL_WIDTH / ROW_HEIGHT 一致 */
	public float timelineWidth;
	public float trackLabelWidth;
	public float rowHeight = ROW_HEIGHT;

	// ----- 行位置（供渲染与 HitTest 共用） -----

	/** 每行顶部 Y（屏幕），index 0 = 第一行内容（波形行），共 CONTENT_ROW_COUNT 个 */
	private final float[] rowScreenY = new float[CONTENT_ROW_COUNT];

	/** 时间线区域在窗口内的起始 Y（用于 setCursorPosY） */
	public float startY;

	/**
	 * 根据当前 ImGui 窗口状态填充布局（在 begin 之后、绘制前调用）。
	 */
	public void build() {
		float winX = ImGui.getWindowPosX();
		float scrollX = ImGui.getScrollX();
		float scrollY = ImGui.getScrollY();
		float winY = ImGui.getWindowPosY();
		startY = ImGui.getCursorPosY();
		float availX = ImGui.getContentRegionAvailX();
		contentWidth = Math.max(200f, availX - TRACK_LABEL_WIDTH - 20f);

		rulerLeft = winX + scrollX + TRACK_LABEL_WIDTH;
		rulerTop = winY + scrollY + startY;
		rulerWidth = contentWidth;

		trackHeaderLeft = winX + scrollX;
		trackHeaderTop = winY + scrollY + startY + RULER_HEIGHT;
		trackHeaderWidth = TRACK_LABEL_WIDTH;
		trackHeaderHeight = CONTENT_ROW_COUNT * ROW_HEIGHT;

		contentLeft = rulerLeft;
		contentTop = trackHeaderTop;
		this.contentWidth = contentWidth;
		contentHeight = trackHeaderHeight;
		timelineWidth = contentWidth;
		trackLabelWidth = TRACK_LABEL_WIDTH;
		rowHeight = ROW_HEIGHT;

		for (int i = 0; i < CONTENT_ROW_COUNT; i++) {
			rowScreenY[i] = contentTop + i * ROW_HEIGHT;
		}
	}

	/** 第 i 行内容区的屏幕 Y（行顶），i 从 0 到 CONTENT_ROW_COUNT-1 */
	public float getRowScreenY(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= CONTENT_ROW_COUNT) return contentTop;
		return rowScreenY[rowIndex];
	}

	/** 第 i 行内容区的光标 Y（用于 ImGui.setCursorPosY），i 从 0 到 CONTENT_ROW_COUNT-1 */
	public float getRowCursorY(int rowIndex) {
		return startY + RULER_HEIGHT + rowIndex * ROW_HEIGHT;
	}

	/** 第 i 个可交互轨道的屏幕 Y（与 INTERACTIVE_TRACK_IDS[i] 对应） */
	public float getInteractiveRowScreenY(int interactiveIndex) {
		if (interactiveIndex < 0 || interactiveIndex >= INTERACTIVE_ROW_INDICES.length) return contentTop;
		return getRowScreenY(INTERACTIVE_ROW_INDICES[interactiveIndex]);
	}

	public int getInteractiveRowCount() {
		return INTERACTIVE_ROW_INDICES.length;
	}

	/** 时间标尺是否包含点 (screenX, screenY) */
	public boolean rulerContains(float screenX, float screenY) {
		return screenX >= rulerLeft && screenX <= rulerLeft + rulerWidth
			&& screenY >= rulerTop && screenY < rulerTop + rulerHeight;
	}

	/** 内容区是否包含点 (screenX, screenY) */
	public boolean contentContains(float screenX, float screenY) {
		return screenX >= contentLeft && screenX <= contentLeft + contentWidth
			&& screenY >= contentTop && screenY < contentTop + contentHeight;
	}
}
