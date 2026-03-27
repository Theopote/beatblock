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
	public static final float RULER_HEIGHT = 28f;

	/** 内容区最大行数（含所有音频子轨槽位）：1组+MAX_AUDIO_SUB_ROWS子轨+1动画组+2动画子轨+摄像机+全局 */
	public static final int CONTENT_ROW_COUNT = TimelineTrackMeta.ROW_GLOBAL_EVENT + 1;

	/** 可交互轨道在内容行中的索引（0-based）：方块动画、自动动画、摄像机、全局 */
	public static final int[] INTERACTIVE_ROW_INDICES = {
		TimelineTrackMeta.ROW_ANIM_BLOCK,
		TimelineTrackMeta.ROW_ANIM_AUTO,
		TimelineTrackMeta.ROW_CAMERA,
		TimelineTrackMeta.ROW_GLOBAL_EVENT
	};

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
	/** 每行顶部 Y（窗口本地坐标），不可见行为 -1 */
	private final float[] rowCursorY = new float[CONTENT_ROW_COUNT];
	/** 每行高度，默认 ROW_HEIGHT。 */
	private final float[] rowHeights = new float[CONTENT_ROW_COUNT];
	/** 逻辑行对应的可见行序号（0-based），不可见为 -1 */
	private final int[] logicalToVisibleIndex = new int[CONTENT_ROW_COUNT];
	/** 可见行序号（0-based）对应的逻辑行索引，不可用槽位为 -1 */
	private final int[] visibleToLogicalRow = new int[CONTENT_ROW_COUNT];
	/** 当前可见行数（考虑折叠） */
	private int visibleRowCount;

	/**
	 * 本帧实际活跃的音频子轨道数（由 TimelineRenderer 每帧根据 TrackRegistry 设置）。
	 * 超出此数量的子轨槽位（{@link TimelineTrackMeta#ROW_AUDIO_SUBS_START} + activeAudioSubRowCount ~ END）不参与渲染。
	 * 默认值 3 与旧的低/中/高三频段行为一致。
	 */
	private int activeAudioSubRowCount = 3;
	private int activeAnimationSubRowCount = 0;

	/** 时间线区域在窗口内的起始 Y（用于 setCursorPosY） */
	public float startY;

	/**
	 * 设置本帧活跃的音频子轨道数，必须在 {@link #attachTrackAreaContext} 之前调用。
	 * 值被钳制到 [0, MAX_AUDIO_SUB_ROWS]。
	 */
	public void setActiveAudioSubRowCount(int count) {
		activeAudioSubRowCount = Math.max(0, Math.min(count, TimelineTrackMeta.MAX_AUDIO_SUB_ROWS));
	}

	public int getActiveAudioSubRowCount() { return activeAudioSubRowCount; }

	public void setActiveAnimationSubRowCount(int count) {
		activeAnimationSubRowCount = Math.max(0, Math.min(count, TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS));
	}

	public int getActiveAnimationSubRowCount() { return activeAnimationSubRowCount; }

	/**
	 * 采样固定标尺区域的锚点与共享宽度。本帧只调用一次。
	 * @param trackHeaderWidthPx 轨道头区域宽度（可拖动分割线调整），若 <= 0 则用 TRACK_LABEL_WIDTH。
	 */
	public void beginFrame(float trackHeaderWidthPx) {
		float headerW = trackHeaderWidthPx > 0 ? trackHeaderWidthPx : TRACK_LABEL_WIDTH;

		float backupX = ImGui.getCursorPosX();
		float backupY = ImGui.getCursorPosY();
		startY = backupY;
		float availX = ImGui.getContentRegionAvailX();
		float rightPad = Math.max(8f, ImGui.getStyle().getScrollbarSize() * 0.5f + 4f);
		contentWidth = Math.max(200f, availX - headerW - rightPad);
		trackHeaderWidth = headerW;
		rowHeight = ROW_HEIGHT;
		timelineWidth = contentWidth;
		trackLabelWidth = headerW;
		for (int i = 0; i < CONTENT_ROW_COUNT; i++) {
			rowHeights[i] = ROW_HEIGHT;
			rowCursorY[i] = -1f;
			rowScreenY[i] = -1f;
			logicalToVisibleIndex[i] = -1;
			visibleToLogicalRow[i] = -1;
		}

		ImGui.setCursorPos(backupX, startY);
		rulerTop = ImGui.getCursorScreenPosY();
		trackHeaderLeft = ImGui.getCursorScreenPosX();
		rulerLeft = trackHeaderLeft + headerW;
		rulerWidth = contentWidth;

		ImGui.setCursorPos(backupX, backupY);
	}

	/**
	 * 在轨道子窗口顶部采样滚动后的内容锚点，并仅在这里计算一次行坐标。
	 */
	public void attachTrackAreaContext(TimelineTrackListState trackListState) {
		float backupX = ImGui.getCursorPosX();
		float backupY = ImGui.getCursorPosY();
		ImGui.setCursorPos(backupX, backupY);
		trackHeaderTop = ImGui.getCursorScreenPosY();
		trackHeaderLeft = ImGui.getCursorScreenPosX();
		contentLeft = trackHeaderLeft + trackHeaderWidth;
		contentTop = trackHeaderTop;
		ImGui.setCursorPos(backupX, backupY);

		int v = 0;
		float cursorY = 0f;
		for (int i = 0; i < CONTENT_ROW_COUNT; i++) {
			boolean visible = isRowVisible(i, trackListState, activeAudioSubRowCount, activeAnimationSubRowCount);
			float h = resolveRowHeight(i, trackListState);
			rowHeights[i] = h;
			if (visible) {
				logicalToVisibleIndex[i] = v;
				visibleToLogicalRow[v] = i;
				rowCursorY[i] = cursorY;
				rowScreenY[i] = trackHeaderTop + cursorY;
				cursorY += h + ROW_GAP;
				v++;
			} else {
				logicalToVisibleIndex[i] = -1;
				rowScreenY[i] = -1f;
				rowCursorY[i] = -1f;
			}
		}
		visibleRowCount = v;
		trackHeaderHeight = visibleRowCount > 0 ? Math.max(0f, cursorY - ROW_GAP) : 0f;
		contentHeight = trackHeaderHeight;
	}

	private static float resolveRowHeight(int rowIndex, TimelineTrackListState state) {
		if (state == null) return ROW_HEIGHT;
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			return state.getAudioRowHeight();
		}
		return ROW_HEIGHT;
	}

	private static boolean isRowVisible(int rowIndex, TimelineTrackListState state, int activeAudioSubRowCount, int activeAnimationSubRowCount) {
		// 超出活跃音频子轨数量的槽位始终不可见
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
			if (slot >= activeAudioSubRowCount) return false;
		}
		if (TimelineTrackMeta.isAnimationFeatureSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.animationFeatureSubRowSlot(rowIndex);
			if (slot >= activeAnimationSubRowCount) return false;
		}
		if (state == null) return true;
		int parent = TimelineTrackMeta.getParentRowIndex(rowIndex);
		if (parent == TimelineTrackMeta.NO_PARENT) return true;
		return !state.isGroupCollapsed(parent);
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
		return rowCursorY[rowIndex];
	}

	/** 第 i 行高度（像素）。 */
	public float getRowHeight(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= CONTENT_ROW_COUNT) return ROW_HEIGHT;
		return rowHeights[rowIndex] > 0 ? rowHeights[rowIndex] : ROW_HEIGHT;
	}

	/** 根据屏幕 Y 命中可见行，未命中返回 -1。 */
	public int findRowAtScreenY(float screenY) {
		if (visibleRowCount <= 0) return -1;
		int lo = 0;
		int hi = visibleRowCount - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			int logicalRow = visibleToLogicalRow[mid];
			if (logicalRow < 0) return -1;
			float y = rowScreenY[logicalRow];
			float h = getRowHeight(logicalRow);
			if (screenY < y) {
				hi = mid - 1;
			} else if (screenY > y + h) {
				lo = mid + 1;
			} else {
				return logicalRow;
			}
		}
		return -1;
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
