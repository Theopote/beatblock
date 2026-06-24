package com.beatblock.timeline.interaction;

import com.beatblock.BeatBlockClient;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.rendering.*;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseCursor;

import java.util.ArrayList;
import java.util.List;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.CAMERA_EDGE_HIT_PX;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.DRAG_THRESHOLD_PX;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_DELETE_CONFIRM;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_EVENT_CONTEXT;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_MARKER_CONTEXT;
import static com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots.InteractiveTrackSlot;
import static com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots.build;

/**
 * 时间线输入：鼠标按下/拖拽/释放，使用 TimelineLayout 四区域做 HitTest，驱动状态与 Clock。
 * 支持：标尺/播放头拖动（SCRUB）、事件拖拽、框选。
 */
public final class TimelineInteraction implements TimelineInteractionPopupHost {

	private IAudioPlayer audioPlayer;
	private MusicPlayer musicPlayer;
	private TimelineEditor timelineEditor;
	private final List<TimelineInteractionClipboard.ClipboardEvent> clipboardEvents = new ArrayList<>();
	private final TimelineInteractionPopupState popupState = new TimelineInteractionPopupState();

	/** 事件拖动开始时的 timeSeconds */
	private double dragEventInitialTimeSeconds;
	private TimelineClipDragSession clipDragSession;
	/** 摄像机片段缩放开始时的快照（用于 Undo） */
	private ClipDragStateSnapshot resizeClipBeforeSnapshot;

	private TimelineCameraClipResizeHandler.Session cameraResizeSession;

	public void setAudioPlayer(IAudioPlayer audioPlayer) {
		this.audioPlayer = audioPlayer;
	}

	public void setMusicPlayer(MusicPlayer musicPlayer) {
		this.musicPlayer = musicPlayer;
	}

	public void bindTimelineEditor(TimelineEditor timelineEditor) {
		this.timelineEditor = timelineEditor;
	}

	public TimelineInteraction() {
		popupState.contextTimeSeconds = 0;
	}

	@Override
	public TimelineInteractionPopupState popupState() {
		return popupState;
	}

	@Override
	public List<TimelineInteractionClipboard.ClipboardEvent> clipboardEvents() {
		return clipboardEvents;
	}

	@Override
	public TimelineEditor timelineEditor() {
		return timelineEditor;
	}

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
		if (layout.contentWidth > 0) {
			viewState.setViewEndTimeSeconds(viewState.screenToTime(layout.contentWidth));
		}

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

		if (toolbarState != null && interactionState.getMode() == InteractionMode.LOOP_IN_DRAG) {
			double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
			toolbarState.setLoopInSeconds(t);
			if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= t) {
				toolbarState.setLoopOutSeconds(t + 0.1);
			}
			if (clock != null) seekClockAndMusic(clock, t);
			if (ImGui.isMouseReleased(0)) {
				interactionState.setMode(InteractionMode.NONE);
			}
			return;
		}

		if (interactionState.getMode() == InteractionMode.MARKER_DRAG) {
			String markerId = interactionState.getActiveMarkerId();
			int markerIndex = timeline.findMarkerIndexById(markerId);
			if (markerIndex >= 0) {
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
				timeline.updateMarker(markerId, t, marker.getName());
				if (clock != null) seekClockAndMusic(clock, t);
			}
			if (ImGui.isMouseReleased(0)) {
				interactionState.setMode(InteractionMode.NONE);
				interactionState.clearActive();
			}
			return;
		}
		if (toolbarState != null && interactionState.getMode() == InteractionMode.LOOP_OUT_DRAG) {
			double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
			double loopIn = toolbarState.getLoopInSeconds();
			toolbarState.setLoopOutSeconds(Math.max(t, loopIn + 0.1));
			if (clock != null) seekClockAndMusic(clock, toolbarState.getLoopOutSeconds());
			if (ImGui.isMouseReleased(0)) {
				interactionState.setMode(InteractionMode.NONE);
			}
			return;
		}

		// 右键菜单等 popup 必须在 isWindowHovered 检查之前每帧调用，
		// 否则鼠标移入弹出窗口时 isWindowHovered 返回 false 导致 beginPopup 未被调用，ImGui 会关闭弹窗。
		TimelineInteractionPopups.renderAll(timeline, selectionState, trackListState, clock, this);

		if (ImGui.isMouseReleased(0)) {
			if (interactionState.getMode() == InteractionMode.RESIZE_CLIP) {
				TimelineDragCommitSupport.finishResizeClipDrag(
					timeline,
					timelineEditor,
					interactionState,
					mx,
					my,
					cameraResizeSession != null ? cameraResizeSession.undoSnapshot() : null,
					() -> {
						resizeClipBeforeSnapshot = null;
						cameraResizeSession = null;
					}
				);
			}
			if (interactionState.getMode() == InteractionMode.DRAG_CLIP && interactionState.getActiveClipId() != null) {
				TimelineClipDragCoordinator.finishOnMouseRelease(
					timeline, timelineEditor, clipDragSession, interactionState, selectionState, mx, my);
				clipDragSession = null;
			}
			if (interactionState.getMode() == InteractionMode.DRAG_EVENT && interactionState.getActiveEventId() != null) {
				float dx = mx - interactionState.getMouseStartX();
				float dy = my - interactionState.getMouseStartY();
				boolean belowThreshold = dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX;
				if (belowThreshold) {
					selectionState.clearEvents();
					selectionState.selectEvent(interactionState.getActiveEventId());
					revertEventDrag(timeline, interactionState);
				} else {
					commitEventDrag(timeline, interactionState);
				}
				dragEventInitialTimeSeconds = 0.0;
			}
			if (interactionState.getMode() == InteractionMode.BOX_SELECT
					&& selectionBox != null && selectionBox.isActive()) {
				TimelineBoxSelectHandler.applySelection(timeline, layout, viewState, selectionBox, selectionState);
			}
			interactionState.setMode(InteractionMode.NONE);
			interactionState.clearActive();
			if (selectionBox != null) selectionBox.setActive(false);
			return;
		}

		if (!ImGui.isWindowHovered(ImGuiHoveredFlags.AllowWhenBlockedByActiveItem | ImGuiHoveredFlags.AllowWhenBlockedByPopup)) return;
		boolean alt = ImGui.getIO().getKeyAlt();

		if (ImGui.isKeyPressed(ImGuiKey.Delete)
				&& TimelineInteractionDeleteSupport.hasDeletableSelection(timeline, selectionState, trackListState)) {
			ImGui.openPopup(POPUP_DELETE_CONFIRM);
		}
		if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.C)) {
			copySelectedEvents(timeline, selectionState);
		}
		if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.V)) {
			double anchor = popupState.contextTimeSeconds;
			if (layout.contentContains(mx, my)) {
				float localX = Math.max(0, Math.min(mx - layout.contentLeft, layout.contentWidth));
				anchor = viewState.screenToTime(localX);
			}
			pasteClipboardEvents(timeline, selectionState, anchor, trackListState);
		}

		float wheel = ImGui.getIO().getMouseWheel();
		if (ImGui.getIO().getKeyCtrl() && wheel != 0
				&& (layout.contentContains(mx, my) || layout.rulerContains(mx, my))) {
			float anchorX = mx - layout.contentLeft;
			anchorX = Math.max(0, Math.min(anchorX, layout.contentWidth));
			double anchorTime = viewState.screenToTime(anchorX);
			float zoomFactor = (float) Math.pow(1.15, wheel);
			float newZoom = viewState.getZoom() * zoomFactor;
			viewState.zoomAt(anchorTime, anchorX, newZoom);
			if (layout.contentWidth > 0) {
				viewState.setViewEndTimeSeconds(viewState.screenToTime(layout.contentWidth));
			}
		}

		if (trackListState != null && ImGui.getIO().getKeyAlt() && wheel != 0 && layout.contentContains(mx, my)) {
			int hoveredRow = layout.findRowAtScreenY(my);
			if (hoveredRow >= TimelineTrackMeta.ROW_AUDIO_SUBS_START && hoveredRow <= TimelineTrackMeta.ROW_AUDIO_SUBS_END) {
				trackListState.adjustAudioRowHeight(wheel * 2f);
			}
		}
		if (trackListState != null && alt && ImGui.isMouseClicked(2) && layout.contentContains(mx, my)) {
			int hoveredRow = layout.findRowAtScreenY(my);
			if (hoveredRow >= TimelineTrackMeta.ROW_AUDIO_SUBS_START && hoveredRow <= TimelineTrackMeta.ROW_AUDIO_SUBS_END) {
				trackListState.resetAudioRowHeight();
				return;
			}
		}

		// 轨道子窗口内：分割线悬停光标
		if (trackListState != null) {
			boolean overDivider = TimelineRulerHitTest.isMouseOverDivider(mx, my, layout);
			if (overDivider && interactionState.getMode() == InteractionMode.NONE) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
		}
		if (toolbarState != null && layout.rulerContains(mx, my) && interactionState.getMode() == InteractionMode.NONE) {
			if (TimelineRulerHitTest.isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)
				|| TimelineRulerHitTest.isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState)) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
		}
		if (trackListState != null && layout.contentContains(mx, my)
				&& interactionState.getMode() == InteractionMode.NONE
				&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, Timeline.TRACK_ID_CAMERA)) {
			int camRow = layout.findRowAtScreenY(my);
			if (camRow == TimelineTrackMeta.ROW_CAMERA && layout.isRowVisible(camRow)) {
				float rowSy = layout.getRowScreenY(camRow);
				float rowH = layout.getRowHeight(camRow);
				if (CameraTrackHitTest.hitClipEdge(timeline, mx, my, rowSy, rowH,
						layout.contentLeft, layout.contentWidth, viewState, CAMERA_EDGE_HIT_PX) != null) {
					ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
				}
			}
		}

		if (alt && toolbarState != null && layout.rulerContains(mx, my) && ImGui.isMouseClicked(1)) {
			double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
			double loopIn = toolbarState.getLoopInSeconds();
			toolbarState.setLoopOutSeconds(Math.max(t, loopIn + 0.1));
			return;
		}
		if (!alt && layout.rulerContains(mx, my) && ImGui.isMouseClicked(1)) {
			int markerIndex = TimelineRulerHitTest.findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (markerIndex >= 0) {
				popupState.contextMarkerId = timeline.getMarkers().get(markerIndex).getId();
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				popupState.markerNameBuffer.set(marker.getName());
				ImGui.openPopup(POPUP_MARKER_CONTEXT);
			}
		}

		if (!alt && ImGui.isMouseDoubleClicked(0) && layout.rulerContains(mx, my)) {
			boolean overLoopHandle = toolbarState != null
				&& (TimelineRulerHitTest.isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)
					|| TimelineRulerHitTest.isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState));
			int markerIndex = TimelineRulerHitTest.findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (!overLoopHandle && markerIndex < 0) {
				double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
				TimelineRulerHitTest.addMarkerAtTime(timeline, t);
				if (clock != null) seekClockAndMusic(clock, t);
				return;
			}
		}

		if (ImGui.isMouseClicked(1) && layout.contentContains(mx, my)) {
			popupState.contextTimeSeconds = viewState.screenToTime(Math.max(0, Math.min(mx - layout.contentLeft, layout.contentWidth)));
			HitResult hit = TimelineContentHitTest.hitContentAtMouse(timeline, viewState, layout, mx, my);
			popupState.contextTrackId = hit.getTrackId();
			popupState.contextClipId = hit.getClipId();
			BeatBlockClient.LOGGER.info(String.format(
				"[TimelineInteraction.handleMouse] Right-click detected: popupState.contextTrackId=%s, popupState.contextClipId=%s, hitTrackId=%s, hitClipId=%s, hitEventId=%s",
				popupState.contextTrackId, popupState.contextClipId, hit.getTrackId(), hit.getClipId(), hit.getEventId()
			));
			if (Timeline.TRACK_ID_CAMERA.equals(hit.getTrackId())) {
				if (hit.getClipId() != null) {
					popupState.contextCameraShowPath.set(CameraPathMetadata.isPathVisible(timeline, hit.getClipId()));
				}
				if (hit.getEventId() != null) {
					popupState.propertiesEventId = hit.getEventId();
					selectionState.clearEvents();
					selectionState.selectEvent(hit.getEventId());
					if (hit.getClipId() != null) {
						selectionState.selectClip(hit.getClipId());
					}
				} else if (hit.getClipId() != null && !selectionState.isClipSelected(hit.getClipId())) {
					selectionState.clearEvents();
					selectionState.clearClips();
					selectionState.selectClip(hit.getClipId());
				}
			} else {
				// 右键命中片段时自动将其加入选中，确保右键菜单的 Delete 项可用
				if (hit.getClipId() != null && !selectionState.isClipSelected(hit.getClipId())) {
					BeatBlockClient.LOGGER.info(String.format(
						"[TimelineInteraction.handleMouse] Auto-selecting context clip: %s",
						hit.getClipId()
					));
					selectionState.clearEvents();
					selectionState.clearClips();
					selectionState.selectClip(hit.getClipId());
				}
				if (hit.getEventId() != null) {
					popupState.propertiesEventId = hit.getEventId();
				}
			}
			ImGui.openPopup(POPUP_EVENT_CONTEXT);
		}

		if (ImGui.isMouseDown(0) && interactionState.getMode() != InteractionMode.NONE) {
			if (interactionState.getMode() == InteractionMode.SCRUB_TIME && clock != null) {
				double t = viewState.screenToTime(mx - layout.contentLeft);
				seekClockAndMusic(clock, Math.max(0, Math.min(t, duration)));
				return;
			}
			if (interactionState.getMode() == InteractionMode.RESIZE_CLIP
					&& Timeline.TRACK_ID_CAMERA.equals(interactionState.getActiveTrackId())
					&& interactionState.getActiveClipId() != null
					&& cameraResizeSession != null) {
				if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, Timeline.TRACK_ID_CAMERA)) return;
				TimelineCameraClipResizeHandler.applyDuringDrag(
					timeline,
					cameraResizeSession,
					interactionState,
					viewState,
					toolbarState,
					layout,
					mx
				);
				if (clock != null) {
					seekClockAndMusic(clock, clock.getCurrentTimeSeconds());
				}
				return;
			}
			if (interactionState.getMode() == InteractionMode.DRAG_CLIP
					&& interactionState.getActiveClipId() != null && interactionState.getActiveTrackId() != null) {
				TimelineClipDragCoordinator.applyDuringDrag(
					timeline,
					clipDragSession,
					interactionState,
					viewState,
					layout,
					toolbarState,
					trackListState,
					duration,
					mx,
					clock != null ? () -> seekClockAndMusic(clock, clock.getCurrentTimeSeconds()) : null
				);
				return;
			}
			if (interactionState.getMode() == InteractionMode.DRAG_EVENT && interactionState.getActiveEventId() != null
				&& interactionState.getActiveTrackId() != null && interactionState.getActiveClipId() != null) {
				if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, interactionState.getActiveTrackId())) {
					return;
				}
				double t = viewState.screenToTime(mx - layout.contentLeft);
				DragController.dragEvent(timeline, interactionState.getActiveTrackId(), interactionState.getActiveClipId(), interactionState.getActiveEventId(), t, duration, toolbarState, viewState, interactionState);
				return;
			}
			if (interactionState.getMode() == InteractionMode.BOX_SELECT && selectionBox != null) {
				TimelineBoxSelectHandler.updateEnd(selectionBox, mx, my);
				return;
			}
			return;
		}

		if (ImGui.isMouseClicked(0)) {
			boolean ctrl = ImGui.getIO().getKeyCtrl();
			if (trackListState != null && TimelineRulerHitTest.isMouseOverDivider(mx, my, layout)) {
				interactionState.setMode(InteractionMode.RESIZE_HEADER);
				interactionState.setMouseStart(mx, my);
				interactionState.setResizeStartHeaderWidth(trackListState.getTrackHeaderWidth());
				return;
			}
			if (layout.rulerContains(mx, my)) {
				if (toolbarState != null && TimelineRulerHitTest.isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)) {
					interactionState.setMode(InteractionMode.LOOP_IN_DRAG);
					interactionState.setMouseStart(mx, my);
					return;
				}
				if (toolbarState != null && TimelineRulerHitTest.isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState)) {
					interactionState.setMode(InteractionMode.LOOP_OUT_DRAG);
					interactionState.setMouseStart(mx, my);
					return;
				}
				int markerIndex = TimelineRulerHitTest.findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
				if (!alt && markerIndex >= 0 && clock != null) {
					TimelineMarker marker = timeline.getMarkers().get(markerIndex);
					seekClockAndMusic(clock, marker.getTimeSeconds());
					interactionState.setMode(InteractionMode.MARKER_DRAG);
					interactionState.setMouseStart(mx, my);
					interactionState.setActiveMarkerId(marker.getId());
					return;
				}
				if (alt && toolbarState != null) {
					double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
					toolbarState.setLoopInSeconds(t);
					if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= t) {
						toolbarState.setLoopOutSeconds(t + 0.1);
					}
					return;
				}
				double t = viewState.screenToTime(mx - layout.contentLeft);
				interactionState.setMode(InteractionMode.SCRUB_TIME);
				interactionState.setMouseStart(mx, my);
				if (clock != null) seekClockAndMusic(clock, Math.max(0, Math.min(t, duration)));
				return;
			}
			if (layout.contentContains(mx, my) && trackListState != null
					&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, Timeline.TRACK_ID_CAMERA)) {
				TimelineCameraClipResizeHandler.Session session = TimelineCameraClipResizeHandler.tryBeginOnMouseClick(
					timeline, interactionState, layout, viewState, trackListState, mx, my);
				if (session != null) {
					cameraResizeSession = session;
					resizeClipBeforeSnapshot = session.undoSnapshot();
					return;
				}
			}
			for (InteractiveTrackSlot slot : build(timeline)) {
				int logicalRow = slot.rowIndex();
				if (!layout.isRowVisible(logicalRow)) continue;
				if (trackListState != null && trackListState.isLocked(logicalRow)) continue;
				float rowScreenY = layout.getRowScreenY(logicalRow);
				float rowH = layout.getRowHeight(logicalRow);
				HitResult hit = HitTestSystem.hitTestTrackContent(timeline, slot.trackId(), mx, my,
					layout.contentLeft, rowScreenY, rowH, layout.contentWidth, viewState);
				if (hit.isEmpty()) continue;
				// 音频/摄像机轨上的纯片段命中 → DRAG_CLIP
				if (hit.getHitType() == HitType.CLIP && hit.getEventId() == null
						&& (Timeline.TRACK_ID_AUDIO.equals(hit.getTrackId())
						|| Timeline.TRACK_ID_CAMERA.equals(hit.getTrackId()))) {
					Track hitTrack = timeline.getTrack(hit.getTrackId());
					Clip hitClip = hitTrack != null ? hitTrack.getClip(hit.getClipId()) : null;
					if (hitClip != null) {
						clipDragSession = TimelineClipDragCoordinator.tryBeginFromClipHit(
							timeline, hit, hitClip, interactionState, viewState, layout, mx, my);
						if (clipDragSession != null) {
							if (!ctrl) selectionState.clearClips();
							selectionState.selectClip(hit.getClipId());
						}
					}
					return;
				}
				if (hit.getHitType() == HitType.EVENT || hit.getHitType() == HitType.CLIP) {
					interactionState.setMode(InteractionMode.DRAG_EVENT);
					interactionState.setMouseStart(mx, my);
					interactionState.setActiveEventId(hit.getEventId());
					interactionState.setActiveClipId(hit.getClipId());
					interactionState.setActiveTrackId(hit.getTrackId());
					dragEventInitialTimeSeconds = 0.0;
					if (hit.getEventId() != null) {
						TimelineEventRef dragRef = TimelineEventRefs.find(timeline, hit.getEventId());
						if (dragRef != null && dragRef.event() != null) {
							dragEventInitialTimeSeconds = dragRef.event().getTimeSeconds();
						}
					}
					if (!ctrl) selectionState.clearEvents();
					if (hit.getEventId() != null) selectionState.selectEvent(hit.getEventId());
					else if (hit.getClipId() != null) selectionState.selectClip(hit.getClipId());
					return;
				}
			}
			// 点击播放头竖线也可拖动（与标尺一致的 SCRUB 行为）
			// 放在片段命中之后，避免音频片段与播放头重叠时误触发播放头拖动。
			if (TimelineRulerHitTest.isMouseOverPlayhead(mx, my, layout, viewState, clock)) {
				double t = viewState.screenToTime(mx - layout.contentLeft);
				interactionState.setMode(InteractionMode.SCRUB_TIME);
				interactionState.setMouseStart(mx, my);
				seekClockAndMusic(clock, Math.max(0, Math.min(t, duration)));
				return;
			}
			if (!layout.contentContains(mx, my)) {
				return;
			}
			TimelineBoxSelectHandler.begin(selectionState, selectionBox, interactionState, mx, my);
		}
		if (interactionState.getMode() == InteractionMode.BOX_SELECT && ImGui.isMouseDown(0) && selectionBox != null) {
			TimelineBoxSelectHandler.updateEnd(selectionBox, mx, my);
		}
	}

	/**
	 * 仅处理固定标尺区域交互（主窗口上下文）：Scrub、Loop 句柄、Marker 点击/拖拽/右键菜单。
	 * 不处理轨道内容区命中与框选，避免与子窗口的完整交互逻辑相互干扰。
	 */
	public void updateRulerOnly(
		Timeline timeline,
		TimelineViewState viewState,
		InteractionState interactionState,
		SelectionState selectionState,
		TimelineClock clock,
		TimelineLayout layout,
		TimelineToolbarState toolbarState
	) {
		if (timeline == null || viewState == null || interactionState == null || selectionState == null || layout == null) return;

		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		double duration = timeline.getDurationSeconds();
		if (duration <= 0) duration = 60.0;
		if (layout.contentWidth > 0) {
			viewState.setViewEndTimeSeconds(viewState.screenToTime(layout.contentWidth));
		}

		if (toolbarState != null && interactionState.getMode() == InteractionMode.LOOP_IN_DRAG) {
			double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
			toolbarState.setLoopInSeconds(t);
			if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= t) {
				toolbarState.setLoopOutSeconds(t + 0.1);
			}
			if (clock != null) seekClockAndMusic(clock, t);
			if (ImGui.isMouseReleased(0)) {
				interactionState.setMode(InteractionMode.NONE);
			}
			return;
		}

		if (interactionState.getMode() == InteractionMode.MARKER_DRAG) {
			String markerId = interactionState.getActiveMarkerId();
			int markerIndex = timeline.findMarkerIndexById(markerId);
			if (markerIndex >= 0) {
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
				timeline.updateMarker(markerId, t, marker.getName());
				if (clock != null) seekClockAndMusic(clock, t);
			}
			if (ImGui.isMouseReleased(0)) {
				interactionState.setMode(InteractionMode.NONE);
				interactionState.clearActive();
			}
			return;
		}

		if (toolbarState != null && interactionState.getMode() == InteractionMode.LOOP_OUT_DRAG) {
			double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
			double loopIn = toolbarState.getLoopInSeconds();
			toolbarState.setLoopOutSeconds(Math.max(t, loopIn + 0.1));
			if (clock != null) seekClockAndMusic(clock, toolbarState.getLoopOutSeconds());
			if (ImGui.isMouseReleased(0)) {
				interactionState.setMode(InteractionMode.NONE);
			}
			return;
		}

		if (ImGui.isMouseReleased(0) && interactionState.getMode() == InteractionMode.SCRUB_TIME) {
			interactionState.setMode(InteractionMode.NONE);
			interactionState.clearActive();
			return;
		}

		if (!ImGui.isWindowHovered(ImGuiHoveredFlags.AllowWhenBlockedByActiveItem | ImGuiHoveredFlags.AllowWhenBlockedByPopup)) return;
		boolean alt = ImGui.getIO().getKeyAlt();

		float wheel = ImGui.getIO().getMouseWheel();
		if (ImGui.getIO().getKeyCtrl() && wheel != 0 && layout.rulerContains(mx, my)) {
			float anchorX = mx - layout.contentLeft;
			anchorX = Math.max(0, Math.min(anchorX, layout.contentWidth));
			double anchorTime = viewState.screenToTime(anchorX);
			float zoomFactor = (float) Math.pow(1.15, wheel);
			float newZoom = viewState.getZoom() * zoomFactor;
			viewState.zoomAt(anchorTime, anchorX, newZoom);
			if (layout.contentWidth > 0) {
				viewState.setViewEndTimeSeconds(viewState.screenToTime(layout.contentWidth));
			}
		}

		if (toolbarState != null && layout.rulerContains(mx, my) && interactionState.getMode() == InteractionMode.NONE) {
			if (TimelineRulerHitTest.isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)
				|| TimelineRulerHitTest.isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState)) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
		}

		if (alt && toolbarState != null && layout.rulerContains(mx, my) && ImGui.isMouseClicked(1)) {
			double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
			double loopIn = toolbarState.getLoopInSeconds();
			toolbarState.setLoopOutSeconds(Math.max(t, loopIn + 0.1));
			return;
		}
		if (!alt && layout.rulerContains(mx, my) && ImGui.isMouseClicked(1)) {
			int markerIndex = TimelineRulerHitTest.findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (markerIndex >= 0) {
				popupState.contextMarkerId = timeline.getMarkers().get(markerIndex).getId();
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				popupState.markerNameBuffer.set(marker.getName());
				ImGui.openPopup(POPUP_MARKER_CONTEXT);
			}
		}

		if (!alt && ImGui.isMouseDoubleClicked(0) && layout.rulerContains(mx, my)) {
			boolean overLoopHandle = toolbarState != null
				&& (TimelineRulerHitTest.isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)
					|| TimelineRulerHitTest.isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState));
			int markerIndex = TimelineRulerHitTest.findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (!overLoopHandle && markerIndex < 0) {
				double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
				TimelineRulerHitTest.addMarkerAtTime(timeline, t);
				if (clock != null) seekClockAndMusic(clock, t);
				return;
			}
		}

		TimelineInteractionPopups.renderMarkerOnly(timeline, clock, this);

		if (ImGui.isMouseDown(0) && interactionState.getMode() == InteractionMode.SCRUB_TIME) {
			if (clock != null) {
				double t = viewState.screenToTime(mx - layout.contentLeft);
				seekClockAndMusic(clock, Math.max(0, Math.min(t, duration)));
			}
			return;
		}

		if (ImGui.isMouseClicked(0)) {
			if (!layout.rulerContains(mx, my)) return;
			if (toolbarState != null && TimelineRulerHitTest.isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)) {
				interactionState.setMode(InteractionMode.LOOP_IN_DRAG);
				interactionState.setMouseStart(mx, my);
				return;
			}
			if (toolbarState != null && TimelineRulerHitTest.isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState)) {
				interactionState.setMode(InteractionMode.LOOP_OUT_DRAG);
				interactionState.setMouseStart(mx, my);
				return;
			}
			int markerIndex = TimelineRulerHitTest.findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (!alt && markerIndex >= 0 && clock != null) {
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				seekClockAndMusic(clock, marker.getTimeSeconds());
				interactionState.setMode(InteractionMode.MARKER_DRAG);
				interactionState.setMouseStart(mx, my);
				interactionState.setActiveMarkerId(marker.getId());
				return;
			}
			if (alt && toolbarState != null) {
				double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
				toolbarState.setLoopInSeconds(t);
				if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= t) {
					toolbarState.setLoopOutSeconds(t + 0.1);
				}
				return;
			}
			double t = viewState.screenToTime(mx - layout.contentLeft);
			interactionState.setMode(InteractionMode.SCRUB_TIME);
			interactionState.setMouseStart(mx, my);
			if (clock != null) seekClockAndMusic(clock, Math.max(0, Math.min(t, duration)));
		}
	}

	/** 拖动/点击标尺或播放头时，同时更新时钟和音乐进度 */
	@Override
	public void seekClockAndMusic(TimelineClock clock, double timeSeconds) {
		TimelinePlaybackSeeker.seek(
			clock,
			timeSeconds,
			audioPlayer,
			musicPlayer,
			timelineEditor != null ? timelineEditor.getTimeline() : null
		);
	}

	@Override
	public TimelineEventRef resolvePropertiesEventRef(Timeline timeline, SelectionState selectionState) {
		return TimelineEventRefs.resolveForProperties(
			timeline,
			selectionState,
			popupState.propertiesEventId,
			id -> popupState.propertiesEventId = id
		);
	}

	@Override
	public boolean canDeleteContextClip(Timeline timeline, TimelineTrackListState trackListState) {
		return TimelineInteractionDeleteSupport.canDeleteContextClip(
			timeline, trackListState, popupState.contextTrackId, popupState.contextClipId);
	}

	@Override
	public void copySelectedEvents(Timeline timeline, SelectionState selectionState) {
		TimelineInteractionClipboard.copy(clipboardEvents, timeline, selectionState);
	}

	@Override
	public void pasteClipboardEvents(Timeline timeline, SelectionState selectionState, double anchorTimeSeconds,
			TimelineTrackListState trackListState) {
		TimelineInteractionClipboard.paste(new TimelineInteractionClipboard.PasteRequest(
			timeline,
			selectionState,
			clipboardEvents,
			anchorTimeSeconds,
			popupState.contextTrackId,
			popupState.contextClipId,
			trackListState
		));
	}

	@Override
	public void deleteSelectedEntries(Timeline timeline, SelectionState selectionState, TimelineTrackListState trackListState) {
		TimelineInteractionDeleteSupport.deleteSelectedEntries(timeline, selectionState, trackListState);
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
		if (!TimelineRulerHitTest.isMouseOverRulerDivider(mx, my, parentLayout)) return;
		if (interactionState.getMode() == InteractionMode.NONE) {
			ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
		}
		if (ImGui.isMouseClicked(0) && interactionState.getMode() == InteractionMode.NONE) {
			interactionState.setMode(InteractionMode.RESIZE_HEADER);
			interactionState.setMouseStart(mx, my);
			interactionState.setResizeStartHeaderWidth(trackListState.getTrackHeaderWidth());
		}
	}

	private void commitEventDrag(Timeline timeline, InteractionState interactionState) {
		TimelineDragCommitSupport.commitEventDrag(timeline, timelineEditor, interactionState, dragEventInitialTimeSeconds);
	}

	private void revertEventDrag(Timeline timeline, InteractionState interactionState) {
		TimelineDragCommitSupport.revertEventDrag(timeline, interactionState, dragEventInitialTimeSeconds);
	}
}
