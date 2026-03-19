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

	/** 左侧轨道列表区默认宽度（可拖动分割线改变，见 TimelineTrackListState） */
	public static final float TRACK_LABEL_WIDTH = 220f;
	/** 单行轨道内容高度 */
	public static final float ROW_HEIGHT = 22f;
	/** 轨道与轨道之间的间距（不用线，用留白） */
	public static final float ROW_GAP = 3f;
	/** 单行占位总高 = ROW_HEIGHT + ROW_GAP */
	public static final float ROW_STRIDE = ROW_HEIGHT + ROW_GAP;
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

	/** 每行顶部 Y（屏幕），不可见行为 -1 */
	private final float[] rowScreenY = new float[CONTENT_ROW_COUNT];
	/** 逻辑行对应的可见行序号（0-based），不可见为 -1 */
	private final int[] logicalToVisibleIndex = new int[CONTENT_ROW_COUNT];
	/** 当前可见行数（考虑折叠） */
	private int visibleRowCount;

	/** 时间线区域在窗口内的起始 Y（用于 setCursorPosY） */
	public float startY;

	/** 是否在「仅轨道区」子窗口中构建（无标尺行，startY 即轨道区顶部）。 */
	private boolean trackAreaOnly;

	/**
	 * 根据当前 ImGui 窗口状态填充布局（在 begin 之后、绘制前调用）。
	 * @param trackAreaOnly 若为 true，表示在可滚动的轨道区子窗口内，无标尺行，行从 0 开始。
	 * @param trackHeaderWidthPx 轨道头区域宽度（可拖动分割线调整），若 &lt;= 0 则用 TRACK_LABEL_WIDTH。
	 * @param trackListState 若非 null，则根据组折叠状态计算可见行；为 null 时全部可见。
	 */
	public void build(boolean trackAreaOnly, float trackHeaderWidthPx, TimelineTrackListState trackListState) {
		this.trackAreaOnly = trackAreaOnly;
		float headerW = trackHeaderWidthPx > 0 ? trackHeaderWidthPx : TRACK_LABEL_WIDTH;

		// 用「内容区 (0,·) 的屏幕坐标」锚定，避免 winPos+scroll 与 ImGui 实际 padding/滚动不一致导致
		// 主竖线、标尺起点、轨道内容区、分割线命中区错位。
		float backupX = ImGui.getCursorPosX();
		float backupY = ImGui.getCursorPosY();
		startY = backupY;
		float availX = ImGui.getContentRegionAvailX();
		float rightPad = Math.max(8f, ImGui.getStyle().getScrollbarSize() * 0.5f + 4f);
		contentWidth = Math.max(200f, availX - headerW - rightPad);

		if (trackAreaOnly) {
			ImGui.setCursorPos(0f, startY);
			trackHeaderLeft = ImGui.getCursorScreenPosX();
			trackHeaderTop = ImGui.getCursorScreenPosY();
			rulerLeft = trackHeaderLeft + headerW;
			rulerTop = trackHeaderTop;
			rulerWidth = contentWidth;
		} else {
			ImGui.setCursorPos(0f, startY);
			rulerTop = ImGui.getCursorScreenPosY();
			ImGui.setCursorPos(0f, startY + RULER_HEIGHT);
			trackHeaderLeft = ImGui.getCursorScreenPosX();
			trackHeaderTop = ImGui.getCursorScreenPosY();
			rulerLeft = trackHeaderLeft + headerW;
			rulerWidth = contentWidth;
		}

		ImGui.setCursorPos(backupX, backupY);

		trackHeaderWidth = headerW;
		rowHeight = ROW_HEIGHT;

		int v = 0;
		for (int i = 0; i < CONTENT_ROW_COUNT; i++) {
			boolean visible = isRowVisible(i, trackListState);
			if (visible) {
				logicalToVisibleIndex[i] = v;
				rowScreenY[i] = trackHeaderTop + v * ROW_STRIDE;
				v++;
			} else {
				logicalToVisibleIndex[i] = -1;
				rowScreenY[i] = -1f;
			}
		}
		visibleRowCount = v;
		trackHeaderHeight = visibleRowCount * ROW_STRIDE;

		contentLeft = rulerLeft;
		contentTop = trackHeaderTop;
		this.contentWidth = contentWidth;
		contentHeight = trackHeaderHeight;
		timelineWidth = contentWidth;
		trackLabelWidth = headerW;
	}

	private static boolean isRowVisible(int rowIndex, TimelineTrackListState state) {
		if (state == null) return true;
		int parent = TimelineTrackMeta.getParentRowIndex(rowIndex);
		if (parent == TimelineTrackMeta.NO_PARENT) return true;
		return !state.isGroupCollapsed(parent);
	}

	/** 兼容旧调用：不传 state，全部行可见。 */
	public void build(boolean trackAreaOnly, float trackHeaderWidthPx) {
		build(trackAreaOnly, trackHeaderWidthPx, null);
	}

	/** 兼容旧调用：按「含标尺」、默认轨道头宽度构建。 */
	public void build(boolean trackAreaOnly) {
		build(trackAreaOnly, TRACK_LABEL_WIDTH);
	}

	/** 兼容旧调用：按「含标尺」模式构建。 */
	public void build() {
		build(false);
	}

	/** 第 i 行是否可见（未折叠或其父组未折叠） */
	public boolean isRowVisible(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= CONTENT_ROW_COUNT) return false;
		return logicalToVisibleIndex[rowIndex] >= 0;
	}

	/** 第 i 行在可见行中的序号（0-based），不可见返回 -1 */
	public int getVisibleIndex(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= CONTENT_ROW_COUNT) return -1;
		return logicalToVisibleIndex[rowIndex];
	}

	public int getVisibleRowCount() { return visibleRowCount; }

	/** 第 i 行内容区的屏幕 Y（行顶），不可见返回 -1 */
	public float getRowScreenY(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= CONTENT_ROW_COUNT) return -1f;
		return rowScreenY[rowIndex];
	}

	/** 第 i 行内容区的光标 Y（用于 ImGui.setCursorPosY），不可见返回 -1 */
	public float getRowCursorY(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= CONTENT_ROW_COUNT) return -1f;
		int vi = logicalToVisibleIndex[rowIndex];
		if (vi < 0) return -1f;
		float rulerOffset = trackAreaOnly ? 0f : RULER_HEIGHT;
		return startY + rulerOffset + vi * ROW_STRIDE;
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
