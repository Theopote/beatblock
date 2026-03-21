package com.beatblock.timeline.interaction;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;

/**
 * 时间线输入：鼠标按下/拖拽/释放，使用 TimelineLayout 四区域做 HitTest，驱动状态与 Clock。
 * 支持：标尺/播放头拖动（SCRUB）、事件拖拽、框选。
 */
public final class TimelineInteraction {

	private static final float DRAG_THRESHOLD_PX = 4f;
	/** 播放头竖线两侧的命中宽度（像素），便于拖动 */
	private static final float PLAYHEAD_HIT_PX = 6f;
	/** 轨道头与内容区分割线可拖动区域宽度（像素） */
	private static final float DIVIDER_HIT_PX = 5f;

	private static final String[] INTERACTIVE_TRACK_IDS = {
		Timeline.TRACK_ID_ANIMATION_BLOCK,
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_CAMERA,
		Timeline.TRACK_ID_GLOBAL
	};

	public void update(
		Timeline timeline,
		TimelineViewState viewState,
		InteractionState interactionState,
		SelectionState selectionState,
		TimelineClock clock,
		SelectionBox selectionBox,
		TimelineTrackListState trackListState,
		TimelineLayout layout,
		TimelineToolbarState toolbarState
	) {
		if (timeline == null || viewState == null || interactionState == null || selectionState == null || layout == null) return;

		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		double duration = timeline.getDurationSeconds();
		if (duration <= 0) duration = 60.0;

		// 分割线拖动：允许在拖动手势中移出子窗口，仍继续调整宽度
		if (trackListState != null) {
			if (interactionState.getMode() == InteractionMode.RESIZE_HEADER) {
				float startW = interactionState.getResizeStartHeaderWidth();
				float delta = mx - interactionState.getMouseStartX();
				trackListState.setTrackHeaderWidth(startW + delta);
				if (ImGui.isMouseReleased(0)) {
					interactionState.setMode(InteractionMode.NONE);
				}
				return;
			}
		}

		if (!ImGui.isWindowHovered()) return;

		// 轨道子窗口内：分割线悬停光标
		if (trackListState != null) {
			boolean overDivider = isMouseOverDivider(mx, my, layout);
			if (overDivider && interactionState.getMode() == InteractionMode.NONE) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
		}

		if (ImGui.isMouseReleased(0)) {
			if (interactionState.getMode() == InteractionMode.DRAG_EVENT && interactionState.getActiveEventId() != null) {
				float dx = mx - interactionState.getMouseStartX();
				float dy = my - interactionState.getMouseStartY();
				if (dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
					selectionState.clearEvents();
					selectionState.selectEvent(interactionState.getActiveEventId());
				}
			}
			if (interactionState.getMode() == InteractionMode.BOX_SELECT) {}
			interactionState.setMode(InteractionMode.NONE);
			interactionState.clearActive();
			if (selectionBox != null) selectionBox.setActive(false);
			return;
		}

		if (ImGui.isMouseDown(0) && interactionState.getMode() != InteractionMode.NONE) {
			if (interactionState.getMode() == InteractionMode.SCRUB_TIME && clock != null) {
				double t = viewState.screenToTime(mx - layout.contentLeft);
				seekClockAndMusic(clock, Math.max(0, Math.min(t, duration)));
				return;
			}
			if (interactionState.getMode() == InteractionMode.DRAG_EVENT && interactionState.getActiveEventId() != null
				&& interactionState.getActiveTrackId() != null && interactionState.getActiveClipId() != null) {
				double t = viewState.screenToTime(mx - layout.contentLeft);
				DragController.dragEvent(timeline, interactionState.getActiveTrackId(), interactionState.getActiveClipId(), interactionState.getActiveEventId(), t, duration, toolbarState, viewState);
				return;
			}
			return;
		}

		if (ImGui.isMouseClicked(0)) {
			boolean ctrl = ImGui.getIO().getKeyCtrl();
			if (trackListState != null && isMouseOverDivider(mx, my, layout)) {
				interactionState.setMode(InteractionMode.RESIZE_HEADER);
				interactionState.setMouseStart(mx, my);
				interactionState.setResizeStartHeaderWidth(trackListState.getTrackHeaderWidth());
				return;
			}
			if (layout.rulerContains(mx, my)) {
				double t = viewState.screenToTime(mx - layout.contentLeft);
				interactionState.setMode(InteractionMode.SCRUB_TIME);
				interactionState.setMouseStart(mx, my);
				if (clock != null) seekClockAndMusic(clock, Math.max(0, Math.min(t, duration)));
				return;
			}
			// 点击播放头竖线也可拖动（与标尺一致的 SCRUB 行为）
			if (clock != null && isMouseOverPlayhead(mx, my, layout, viewState, clock)) {
				double t = viewState.screenToTime(mx - layout.contentLeft);
				interactionState.setMode(InteractionMode.SCRUB_TIME);
				interactionState.setMouseStart(mx, my);
				seekClockAndMusic(clock, Math.max(0, Math.min(t, duration)));
				return;
			}
			for (int i = 0; i < layout.getInteractiveRowCount() && i < INTERACTIVE_TRACK_IDS.length; i++) {
				int logicalRow = TimelineLayout.INTERACTIVE_ROW_INDICES[i];
				if (!layout.isRowVisible(logicalRow)) continue;
				float rowScreenY = layout.getRowScreenY(logicalRow);
				HitResult hit = HitTestSystem.hitTestTrackContent(timeline, INTERACTIVE_TRACK_IDS[i], mx, my,
					layout.contentLeft, rowScreenY, TimelineLayout.ROW_HEIGHT, layout.contentWidth, viewState);
				if (hit.isEmpty()) continue;
				if (hit.getHitType() == HitType.EVENT || hit.getHitType() == HitType.CLIP) {
					interactionState.setMode(InteractionMode.DRAG_EVENT);
					interactionState.setMouseStart(mx, my);
					interactionState.setActiveEventId(hit.getEventId());
					interactionState.setActiveClipId(hit.getClipId());
					interactionState.setActiveTrackId(hit.getTrackId());
					if (!ctrl) selectionState.clearEvents();
					if (hit.getEventId() != null) selectionState.selectEvent(hit.getEventId());
					else if (hit.getClipId() != null) selectionState.selectClip(hit.getClipId());
					return;
				}
			}
			selectionState.clearEvents();
			selectionState.clearClips();
			if (selectionBox != null) {
				selectionBox.setStart(mx, my);
				selectionBox.setEnd(mx, my);
				selectionBox.setActive(true);
			}
			interactionState.setMode(InteractionMode.BOX_SELECT);
			interactionState.setMouseStart(mx, my);
		}
		if (interactionState.getMode() == InteractionMode.BOX_SELECT && ImGui.isMouseDown(0) && selectionBox != null) {
			selectionBox.setEnd(mx, my);
		}
	}

	/** 拖动/点击标尺或播放头时，同时更新时钟和音乐进度 */
	private static void seekClockAndMusic(TimelineClock clock, double timeSeconds) {
		clock.seek(timeSeconds);
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setCurrentTimeSeconds(clock.getCurrentTimeSeconds());
		}
	}

	/** 鼠标是否在播放头竖线附近（轨道内容区 Y 内） */
	private static boolean isMouseOverPlayhead(float mouseX, float mouseY, TimelineLayout layout,
		TimelineViewState viewState, TimelineClock clock) {
		if (clock == null) return false;
		float playheadX = layout.contentLeft + viewState.timeToScreen(clock.getCurrentTimeSeconds());
		if (mouseX < playheadX - PLAYHEAD_HIT_PX || mouseX > playheadX + PLAYHEAD_HIT_PX) return false;
		return mouseY >= layout.contentTop && mouseY < layout.contentTop + layout.contentHeight;
	}

	/** 鼠标是否在轨道头与内容区之间的分割线上（可拖动） */
	private static boolean isMouseOverDivider(float mouseX, float mouseY, TimelineLayout layout) {
		float divX = layout.trackHeaderLeft + layout.trackHeaderWidth;
		if (mouseX < divX - DIVIDER_HIT_PX || mouseX > divX + DIVIDER_HIT_PX) return false;
		return mouseY >= layout.contentTop && mouseY < layout.contentTop + layout.contentHeight;
	}

	private static boolean isMouseOverRulerDivider(float mouseX, float mouseY, TimelineLayout parentLayout) {
		float divX = parentLayout.trackHeaderLeft + parentLayout.trackHeaderWidth;
		if (mouseX < divX - DIVIDER_HIT_PX || mouseX > divX + DIVIDER_HIT_PX) return false;
		return mouseY >= parentLayout.rulerTop && mouseY < parentLayout.rulerTop + parentLayout.rulerHeight;
	}

	/**
	 * 时间线主窗口内、标尺行上的分割线：按下开始拖动轨道头宽度。应在 {@code renderRulerOnly} 之后、{@code beginChild} 之前调用。
	 */
	public void tryBeginDividerDragOnRuler(
			TimelineTrackListState trackListState,
			InteractionState interactionState,
			TimelineLayout parentLayout) {
		if (trackListState == null || interactionState == null || parentLayout == null) return;
		if (!ImGui.isWindowHovered()) return;
		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		if (!isMouseOverRulerDivider(mx, my, parentLayout)) return;
		if (interactionState.getMode() == InteractionMode.NONE) {
			ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
		}
		if (ImGui.isMouseClicked(0) && interactionState.getMode() == InteractionMode.NONE) {
			interactionState.setMode(InteractionMode.RESIZE_HEADER);
			interactionState.setMouseStart(mx, my);
			interactionState.setResizeStartHeaderWidth(trackListState.getTrackHeaderWidth());
		}
	}
}
