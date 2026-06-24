package com.beatblock.timeline.interaction;

import com.beatblock.BeatBlockClient;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.AnimationEventSnapshot;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;
import com.beatblock.timeline.editing.GenericEventPropertiesEditor;
import com.beatblock.timeline.editing.TimelineEventEditActions;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.rendering.*;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.CAMERA_EDGE_HIT_PX;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.CAMERA_MIN_CLIP_DURATION;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.DRAG_THRESHOLD_PX;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.MARKER_NAME_BUFFER_SIZE;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.PARAM_INPUT_BUFFER_SIZE;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_DELETE_CONFIRM;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_EVENT_CONTEXT;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_EVENT_PROPERTIES;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_MARKER_CONTEXT;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.TIME_INPUT_BUFFER_SIZE;
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

	// ── 音频片段拖拽快照（DRAG_CLIP 模式使用） ────────────────────────────────
	/** 拖拽开始时片段的 startTimeSeconds */
	private double dragClipInitialStart;
	/** 拖拽开始时片段的 endTimeSeconds */
	private double dragClipInitialEnd;
	/** 拖拽开始时鼠标对应的时间轴时间 */
	private double dragClipInitialMouseTime;
	/** 其他轨道上需要联动的事件：eventId → 拖拽开始时的 timeSeconds */
	private final Map<String, Double> dragLinkedEventOriginalTimes = new HashMap<>();
	/** 拖拽开始时特征轨道快照：key → [{timeSeconds, energy}, ...] */
	private final Map<String, List<double[]>> dragFeatureEventSnapshot = new HashMap<>();
	/** 摄像机片段整体拖动：片内事件的原始时间 */
	private final Map<String, Double> dragCameraClipEventOriginalTimes = new HashMap<>();
	/** 事件拖动开始时的 timeSeconds */
	private double dragEventInitialTimeSeconds;
	/** 片段拖动开始时的快照（用于 Undo） */
	private ClipDragStateSnapshot dragClipBeforeSnapshot;
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
		renderContextMenu(timeline, selectionState, trackListState);
		renderPropertiesPopup(timeline, trackListState);
		renderMarkerContextPopup(timeline, clock);
		renderDeleteConfirmPopup(timeline, selectionState, trackListState);

		if (ImGui.isMouseReleased(0)) {
			if (interactionState.getMode() == InteractionMode.RESIZE_CLIP) {
				finishResizeClipDrag(timeline, interactionState, mx, my);
				cameraResizeEventOrigTimes.clear();
			}
			if (interactionState.getMode() == InteractionMode.DRAG_CLIP && interactionState.getActiveClipId() != null) {
				float dx = mx - interactionState.getMouseStartX();
				float dy = my - interactionState.getMouseStartY();
				boolean belowThreshold = dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX;
				if (belowThreshold) {
					selectionState.clearClips();
					selectionState.selectClip(interactionState.getActiveClipId());
					revertClipDrag(timeline, dragClipBeforeSnapshot);
				} else {
					commitClipDrag(timeline, dragClipBeforeSnapshot);
				}
				dragLinkedEventOriginalTimes.clear();
				dragFeatureEventSnapshot.clear();
				dragCameraClipEventOriginalTimes.clear();
				dragClipBeforeSnapshot = null;
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
				float boxMinX = selectionBox.getMinX();
				float boxMaxX = selectionBox.getMaxX();
				float boxMinY = selectionBox.getMinY();
				float boxMaxY = selectionBox.getMaxY();
				for (InteractiveTrackSlot slot : build(timeline)) {
					int logicalRow = slot.rowIndex();
					if (!layout.isRowVisible(logicalRow)) continue;
					float rowTopY = layout.getRowScreenY(logicalRow);
					float rowBotY = rowTopY + layout.getRowHeight(logicalRow);
					if (rowBotY < boxMinY || rowTopY > boxMaxY) continue;
					Track track = timeline.getTrack(slot.trackId());
					if (track == null) continue;
					for (Clip clip : track.getClips()) {
						for (TimelineEvent e : clip.getEvents()) {
							float screenX = layout.contentLeft + viewState.timeToScreen(e.getTimeSeconds());
							if (screenX >= boxMinX && screenX <= boxMaxX) {
								selectionState.selectEvent(e.getId());
							}
						}
					}
				}
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
			double anchor = contextTimeSeconds;
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
				contextMarkerId = timeline.getMarkers().get(markerIndex).getId();
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				markerNameBuffer.set(marker.getName());
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
			contextTimeSeconds = viewState.screenToTime(Math.max(0, Math.min(mx - layout.contentLeft, layout.contentWidth)));
			HitResult hit = TimelineContentHitTest.hitContentAtMouse(timeline, viewState, layout, mx, my);
			contextTrackId = hit.getTrackId();
			contextClipId = hit.getClipId();
			BeatBlockClient.LOGGER.info(String.format(
				"[TimelineInteraction.handleMouse] Right-click detected: contextTrackId=%s, contextClipId=%s, hitTrackId=%s, hitClipId=%s, hitEventId=%s",
				contextTrackId, contextClipId, hit.getTrackId(), hit.getClipId(), hit.getEventId()
			));
			if (Timeline.TRACK_ID_CAMERA.equals(hit.getTrackId())) {
				if (hit.getClipId() != null) {
					contextCameraShowPath.set(CameraPathMetadata.isPathVisible(timeline, hit.getClipId()));
				}
				if (hit.getEventId() != null) {
					propertiesEventId = hit.getEventId();
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
					propertiesEventId = hit.getEventId();
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
					&& interactionState.getActiveClipId() != null) {
				if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, Timeline.TRACK_ID_CAMERA)) return;
				Track ct = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
				Clip c = ct != null ? ct.getClip(interactionState.getActiveClipId()) : null;
				if (c != null) {
					double mouseT = viewState.screenToTime(mx - layout.contentLeft);
					double snapped = DragController.snapTime(mouseT, null, timeline, toolbarState, viewState, interactionState);
					if (interactionState.isResizeLeft()) {
						double newStart = Math.max(0.0, Math.min(snapped, cameraResizeInitialEnd - CAMERA_MIN_CLIP_DURATION));
						double delta = newStart - cameraResizeInitialStart;
						c.setStartTimeSeconds(newStart);
						for (TimelineEvent se : c.getEvents()) {
							Double o = cameraResizeEventOrigTimes.get(se.getId());
							if (o != null) {
								se.setTimeSeconds(o + delta);
							}
						}
					} else {
						double newEnd = Math.max(cameraResizeInitialStart + CAMERA_MIN_CLIP_DURATION, snapped);
						c.setEndTimeSeconds(newEnd);
						for (TimelineEvent se : c.getEvents()) {
							if (se.getTimeSeconds() > newEnd) {
								se.setTimeSeconds(newEnd);
							}
						}
					}
					timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), c.getEndTimeSeconds()));
					if (clock != null) {
						seekClockAndMusic(clock, clock.getCurrentTimeSeconds());
					}
				}
				return;
			}
			if (interactionState.getMode() == InteractionMode.DRAG_CLIP
					&& interactionState.getActiveClipId() != null && interactionState.getActiveTrackId() != null) {
				if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, interactionState.getActiveTrackId())) return;
				double mouseTime = viewState.screenToTime(mx - layout.contentLeft);
				double clipDuration = dragClipInitialEnd - dragClipInitialStart;
				double newStart = DragController.dragClip(timeline, interactionState.getActiveTrackId(),
					interactionState.getActiveClipId(), mouseTime, dragClipInitialMouseTime,
					dragClipInitialStart, clipDuration, duration, toolbarState, viewState, interactionState);
				double actualDelta = newStart - dragClipInitialStart;
				// 允许片段右移时自动扩展时间线总时长。
				timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), newStart + clipDuration));
				if (Timeline.TRACK_ID_CAMERA.equals(interactionState.getActiveTrackId())) {
					Track ct = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
					Clip cc = ct != null ? ct.getClip(interactionState.getActiveClipId()) : null;
					if (cc != null) {
						for (TimelineEvent se : cc.getEvents()) {
							Double orig = dragCameraClipEventOriginalTimes.get(se.getId());
							if (orig != null) {
								se.setTimeSeconds(Math.max(0.0, orig + actualDelta));
							}
						}
					}
					if (clock != null) {
						seekClockAndMusic(clock, clock.getCurrentTimeSeconds());
					}
					return;
				}
				// 联动：将其他轨道上快照的事件按同样 delta 移动
				for (Track st : timeline.getTracks()) {
					if (Timeline.TRACK_ID_AUDIO.equals(st.getId())) continue;
                    boolean dirtied = false;
					for (Clip sc : st.getClips()) {
						for (TimelineEvent se : sc.getEvents()) {
							Double orig = dragLinkedEventOriginalTimes.get(se.getId());
							if (orig != null) {
								se.setTimeSeconds(Math.max(0.0, orig + actualDelta));
								dirtied = true;
							}
						}
					}
					if (dirtied) timeline.markAnimationEventsDirty(st.getId());
				}
				// 联动：特征轨道事件跟随片段移动
				if (!dragFeatureEventSnapshot.isEmpty()) {
					double featureMoveStart = dragClipInitialStart;
					double featureMoveEnd = dragClipInitialEnd;
					for (Map.Entry<String, FeatureTrack> entry : timeline.getFeatureTracks().entrySet()) {
						List<double[]> snap = dragFeatureEventSnapshot.get(entry.getKey());
						if (snap == null) continue;
						FeatureTrack ft = entry.getValue();
						ft.clear();
						for (double[] pair : snap) {
							double originalTime = pair[0];
							double shiftedTime = originalTime;
							if (originalTime >= featureMoveStart && originalTime <= featureMoveEnd) {
								shiftedTime = Math.max(0.0, originalTime + actualDelta);
							}
							ft.addEvent(new FeatureEvent(shiftedTime, (float) pair[1]));
						}
					}
				}
				// 拖拽期间同步一次音频定位：若播放头已不在任何音频片段内，会在 seek 逻辑中静音/暂停。
				if (clock != null) {
					seekClockAndMusic(clock, clock.getCurrentTimeSeconds());
				}
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
				selectionBox.setEnd(mx, my);
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
				int camRow = layout.findRowAtScreenY(my);
				if (camRow == TimelineTrackMeta.ROW_CAMERA && layout.isRowVisible(camRow)) {
					float rowSy = layout.getRowScreenY(camRow);
					float rowH = layout.getRowHeight(camRow);
					CameraTrackHitTest.EdgeHit edge = CameraTrackHitTest.hitClipEdge(timeline, mx, my,
						rowSy, rowH, layout.contentLeft, layout.contentWidth, viewState, CAMERA_EDGE_HIT_PX);
					if (edge != null) {
						Track ct = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
						Clip c = ct != null ? ct.getClip(edge.clipId()) : null;
						if (c != null) {
							interactionState.setMode(InteractionMode.RESIZE_CLIP);
							interactionState.setMouseStart(mx, my);
							interactionState.setActiveClipId(edge.clipId());
							interactionState.setActiveTrackId(Timeline.TRACK_ID_CAMERA);
							interactionState.setResizeLeft(edge.leftEdge());
							cameraResizeInitialStart = c.getStartTimeSeconds();
							cameraResizeInitialEnd = c.getEndTimeSeconds();
							cameraResizeEventOrigTimes.clear();
							for (TimelineEvent se : c.getEvents()) {
								cameraResizeEventOrigTimes.put(se.getId(), se.getTimeSeconds());
							}
							resizeClipBeforeSnapshot = ClipDragStateSnapshot.capture(
								timeline,
								Timeline.TRACK_ID_CAMERA,
								edge.clipId(),
								cameraResizeEventOrigTimes,
								Map.of()
							);
							return;
						}
					}
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
				// 音频轨上的纯片段命中 → 进入 DRAG_CLIP 模式（可左右拖动片段并联动其他轨道事件）
				if (hit.getHitType() == HitType.CLIP && hit.getEventId() == null
						&& Timeline.TRACK_ID_AUDIO.equals(hit.getTrackId())) {
					Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
					Clip hitClip = audioTrack != null ? audioTrack.getClip(hit.getClipId()) : null;
					if (hitClip != null) {
						interactionState.setMode(InteractionMode.DRAG_CLIP);
						interactionState.setMouseStart(mx, my);
						interactionState.setActiveClipId(hit.getClipId());
						interactionState.setActiveTrackId(hit.getTrackId());
						dragClipInitialStart = hitClip.getStartTimeSeconds();
						dragClipInitialEnd = hitClip.getEndTimeSeconds();
						dragClipInitialMouseTime = viewState.screenToTime(mx - layout.contentLeft);
						// 快照其他轨道上位于该片段时间范围内的所有事件的原始时间
						dragLinkedEventOriginalTimes.clear();
						dragFeatureEventSnapshot.clear();
						dragCameraClipEventOriginalTimes.clear();
						double cs = dragClipInitialStart;
						double ce = dragClipInitialEnd;
						for (Track st : timeline.getTracks()) {
							if (Timeline.TRACK_ID_AUDIO.equals(st.getId())) continue;
                            for (Clip sc : st.getClips()) {
								for (TimelineEvent se : sc.getEvents()) {
									double et = se.getTimeSeconds();
									if (et >= cs && et <= ce) {
										dragLinkedEventOriginalTimes.put(se.getId(), et);
									}
								}
							}
						}
						// 快照特征轨道事件（第一次拖拽时登记）
						for (Map.Entry<String, FeatureTrack> entry : timeline.getFeatureTracks().entrySet()) {
							List<FeatureEvent> evts = entry.getValue().getEvents();
							List<double[]> snap = new ArrayList<>(evts.size());
							for (FeatureEvent fe : evts) snap.add(new double[]{fe.getTimeSeconds(), fe.getEnergy()});
							dragFeatureEventSnapshot.put(entry.getKey(), snap);
						}
						dragClipBeforeSnapshot = ClipDragStateSnapshot.capture(
							timeline,
							hit.getTrackId(),
							hit.getClipId(),
							dragLinkedEventOriginalTimes,
							dragFeatureEventSnapshot
						);
						if (!ctrl) selectionState.clearClips();
						selectionState.selectClip(hit.getClipId());
					}
					return;
				}
				if (hit.getHitType() == HitType.CLIP && hit.getEventId() == null
						&& Timeline.TRACK_ID_CAMERA.equals(hit.getTrackId())) {
					Track cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
					Clip hitClip = cameraTrack != null ? cameraTrack.getClip(hit.getClipId()) : null;
					if (hitClip != null) {
						interactionState.setMode(InteractionMode.DRAG_CLIP);
						interactionState.setMouseStart(mx, my);
						interactionState.setActiveClipId(hit.getClipId());
						interactionState.setActiveTrackId(hit.getTrackId());
						dragClipInitialStart = hitClip.getStartTimeSeconds();
						dragClipInitialEnd = hitClip.getEndTimeSeconds();
						dragClipInitialMouseTime = viewState.screenToTime(mx - layout.contentLeft);
						dragLinkedEventOriginalTimes.clear();
						dragFeatureEventSnapshot.clear();
						dragCameraClipEventOriginalTimes.clear();
						for (TimelineEvent se : hitClip.getEvents()) {
							dragCameraClipEventOriginalTimes.put(se.getId(), se.getTimeSeconds());
						}
						dragClipBeforeSnapshot = ClipDragStateSnapshot.capture(
							timeline,
							hit.getTrackId(),
							hit.getClipId(),
							dragCameraClipEventOriginalTimes,
							Map.of()
						);
						if (!ctrl) selectionState.clearClips();
						selectionState.selectClip(hit.getClipId());
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
				contextMarkerId = timeline.getMarkers().get(markerIndex).getId();
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				markerNameBuffer.set(marker.getName());
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

		renderMarkerContextPopup(timeline, clock);

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
	private void seekClockAndMusic(TimelineClock clock, double timeSeconds) {
		TimelinePlaybackSeeker.seek(
			clock,
			timeSeconds,
			audioPlayer,
			musicPlayer,
			timelineEditor != null ? timelineEditor.getTimeline() : null
		);
	}

	private void renderContextMenu(Timeline timeline, SelectionState selectionState,
			TimelineTrackListState trackListState) {
		if (!ImGui.beginPopup(POPUP_EVENT_CONTEXT)) return;
		boolean requestDeleteConfirmPopup = false;
		boolean hasSelection = selectionState != null
			&& (!selectionState.getSelectedEvents().isEmpty() || !selectionState.getSelectedClips().isEmpty());
		boolean canDeleteSelection = TimelineInteractionDeleteSupport.hasDeletableSelection(timeline, selectionState, trackListState);
		boolean canDeleteContextClip = canDeleteContextClip(timeline, trackListState);
		BeatBlockClient.LOGGER.info(String.format(
			"[TimelineInteraction.renderContextMenu] Menu opened: contextClipId=%s, contextTrackId=%s, canDeleteSelection=%s, canDeleteContextClip=%s",
			contextClipId, contextTrackId, canDeleteSelection, canDeleteContextClip
		));
		boolean canDeleteAny = canDeleteSelection || canDeleteContextClip;
		boolean hasClipboard = !clipboardEvents.isEmpty();
		TimelineEventRef propertiesRef = resolvePropertiesEventRef(timeline, selectionState);
		boolean canOpenProperties = propertiesRef != null && !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, propertiesRef.track().getId());
		BeatBlockClient.LOGGER.info(String.format(
			"[TimelineInteraction.renderContextMenu] About to render menu items: hasSelection=%s, hasClipboard=%s, canDeleteAny=%s",
			hasSelection, hasClipboard, canDeleteAny
		));
		if (ImGui.menuItem("Copy", "Ctrl+C", false, hasSelection)) {
			BeatBlockClient.LOGGER.info("[TimelineInteraction] Copy clicked");
			copySelectedEvents(timeline, selectionState);
		}
		if (ImGui.menuItem("Paste", "Ctrl+V", false, hasClipboard)) {
			BeatBlockClient.LOGGER.info("[TimelineInteraction] Paste clicked");
			pasteClipboardEvents(timeline, selectionState, contextTimeSeconds, trackListState);
		}
		if (timeline != null && Timeline.TRACK_ID_CAMERA.equals(contextTrackId)
				&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, contextTrackId)) {
			if (contextClipId != null) {
				if (ImGui.checkbox("显示路径##camCtxPathVis", contextCameraShowPath)) {
					CameraPathMetadata.setPathVisible(timeline, contextClipId, contextCameraShowPath.get());
				}
			}
			TimelineEventRef ctxEv = propertiesEventId != null ? TimelineEventRefs.find(timeline, propertiesEventId) : null;
			if (ctxEv != null && ctxEv.event() != null && ctxEv.event().getType() == EventType.CAMERA_KEYFRAME) {
				if (ImGui.menuItem("删除关键帧##camDelKf")) {
					TimelineOperations.removeEvent(ctxEv.clip(), ctxEv.event().getId());
					if (selectionState != null) {
						selectionState.deselectEvent(ctxEv.event().getId());
					}
					ImGui.closeCurrentPopup();
				}
			}
			boolean canAddPathKf = false;
			if (contextClipId != null) {
				Track camT = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
				Clip ctxClip = camT != null ? camT.getClip(contextClipId) : null;
				if (ctxClip != null) {
					TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(ctxClip);
					CameraSegmentKind k = seg != null
						? CameraSegmentKind.fromParam(seg.getParameters().get("kind"))
						: CameraSegmentKind.PATH;
					canAddPathKf = k == CameraSegmentKind.PATH;
				}
			}
			if (ImGui.menuItem("添加路径关键帧（当前位置）##camAddKfCtx", null, false, canAddPathKf)) {
				CameraKeyframeActions.addKeyframeAtTime(timeline, contextTimeSeconds);
			}
			if (ImGui.beginMenu("添加镜头片段")) {
				double[] a = TimelineContentHitTest.readCameraAnchorFive();
				if (ImGui.menuItem("自定义路径（关键帧）")) {
					CameraTrackFactory.addPathSegment(timeline, contextTimeSeconds, a[0], a[1], a[2], a[3], a[4]);
				}
				if (ImGui.menuItem("推进（Dolly）")) {
					CameraTrackFactory.addDollySegment(timeline, contextTimeSeconds, a[0], a[1], a[2], a[3], 8.0);
				}
				if (ImGui.menuItem("环绕（Orbit）")) {
					double[] o = TimelineContentHitTest.readOrbitParamsFromView();
					CameraTrackFactory.addOrbitSegment(timeline, contextTimeSeconds,
						o[0], o[1], o[2], o[3], o[4], o[5], o[6]);
				}
				if (ImGui.menuItem("升降（Crane）")) {
					CameraTrackFactory.addCraneSegment(timeline, contextTimeSeconds, a[0], a[1], a[2], a[3], a[4], 6.0);
				}
				if (ImGui.menuItem("节拍震动（Shake）")) {
					CameraTrackFactory.addShakeSegment(timeline, contextTimeSeconds, a[0], a[1], a[2], a[3], a[4]);
				}
				ImGui.endMenu();
			}
		}
		String deleteLabel = canDeleteAny ? "Delete" : "Delete (Locked)";
		BeatBlockClient.LOGGER.debug(String.format(
			"[TimelineInteraction.renderContextMenu] About to render Delete menu item: label=%s, enabled=%s",
			deleteLabel, canDeleteAny
		));
		if (ImGui.menuItem(deleteLabel, "Del", false, canDeleteAny)) {
			BeatBlockClient.LOGGER.info("[TimelineInteraction] *** DELETE MENU ITEM CLICKED! ***");
			if (selectionState != null && canDeleteContextClip && contextClipId != null) {
				// 右键删除应以当前命中的片段为准，避免旧选中项导致“点了删除却没删到当前片段”。
				selectionState.clearEvents();
				selectionState.clearClips();
				selectionState.selectClip(contextClipId);
			} else if (selectionState != null && !hasSelection && contextClipId != null) {
				selectionState.clearEvents();
				selectionState.clearClips();
				selectionState.selectClip(contextClipId);
			}
			requestDeleteConfirmPopup = true;
			ImGui.closeCurrentPopup();
		}
		ImGui.separator();
		String propertiesLabel = propertiesRef != null && !canOpenProperties
			? "Properties (Locked)"
			: "Properties";
		if (ImGui.menuItem(propertiesLabel, null, false, canOpenProperties)) {
			openPropertiesPopup(timeline, selectionState, trackListState);
		}
		ImGui.endPopup();
		if (requestDeleteConfirmPopup) {
			ImGui.openPopup(POPUP_DELETE_CONFIRM);
		}
	}

	private void renderDeleteConfirmPopup(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState
	) {
		if (!ImGui.beginPopupModal(POPUP_DELETE_CONFIRM)) {
			BeatBlockClient.LOGGER.debug("[TimelineInteraction.renderDeleteConfirmPopup] Popup not open this frame");
			return;
		}
		BeatBlockClient.LOGGER.info("[TimelineInteraction.renderDeleteConfirmPopup] Delete confirmation dialog is rendering!");
		int selectedEventCount = selectionState != null ? selectionState.getSelectedEvents().size() : 0;
		int selectedClipCount = selectionState != null ? selectionState.getSelectedClips().size() : 0;
		boolean hasDeletable = TimelineInteractionDeleteSupport.hasDeletableSelection(timeline, selectionState, trackListState);
		boolean canDeleteCtxClip = canDeleteContextClip(timeline, trackListState);
		boolean canDelete = hasDeletable || canDeleteCtxClip;
		BeatBlockClient.LOGGER.info(String.format(
			"[TimelineInteraction.renderDeleteConfirmPopup] Dialog state: selectedClips=%d, selectedEvents=%d, hasDeletable=%s, canDeleteCtxClip=%s, canDelete=%s",
			selectedClipCount, selectedEventCount, hasDeletable, canDeleteCtxClip, canDelete
		));

		ImGui.text("Delete Confirmation");
		ImGui.separator();
		ImGui.textWrapped(String.format(java.util.Locale.ROOT,
			"将删除选中的内容：%d 个片段，%d 个事件。",
			selectedClipCount,
			selectedEventCount));

		if (containsSelectedAudioTrackClip(timeline, selectionState)) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f,
				"警告：本次删除包含顶部音频片段。若删除后音频轨为空，将同步清理音频波形与分析数据。");
		}

		ImGui.spacing();
		if (ImGui.button("Confirm Delete##timelineDeleteConfirm", 150f, 0f)) {
			BeatBlockClient.LOGGER.info(String.format(
				"[TimelineInteraction.renderDeleteConfirmPopup] CONFIRM DELETE BUTTON CLICKED! canDelete=%s, selectedClips=%d, selectedEvents=%d",
				canDelete, selectedClipCount, selectedEventCount
			));
			if (canDelete) {
				BeatBlockClient.LOGGER.info("[TimelineInteraction.renderDeleteConfirmPopup] Calling deleteSelectedEntries()...");
				deleteSelectedEntries(timeline, selectionState, trackListState);
			} else {
				BeatBlockClient.LOGGER.warn("[TimelineInteraction.renderDeleteConfirmPopup] Cannot delete: canDelete is false");
			}
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button("Cancel##timelineDeleteCancel", 120f, 0f)) {
			ImGui.closeCurrentPopup();
		}

		ImGui.endPopup();
	}

	private boolean containsSelectedAudioTrackClip(Timeline timeline, SelectionState selectionState) {
		if (timeline == null || selectionState == null || selectionState.getSelectedClips().isEmpty()) return false;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) return false;
		for (String clipId : selectionState.getSelectedClips()) {
			if (clipId != null && audioTrack.getClip(clipId) != null) return true;
		}
		return false;
	}

	private void renderMarkerContextPopup(Timeline timeline, TimelineClock clock) {
		if (!ImGui.beginPopup(POPUP_MARKER_CONTEXT)) return;
		int markerIndex = timeline != null ? timeline.findMarkerIndexById(contextMarkerId) : -1;
		if (timeline == null || markerIndex < 0 || markerIndex >= timeline.getMarkers().size()) {
			contextMarkerId = null;
			ImGui.textDisabled("Marker no longer exists.");
			if (ImGui.button("Close##markerPopupClose")) ImGui.closeCurrentPopup();
			ImGui.endPopup();
			return;
		}

		TimelineMarker marker = timeline.getMarkers().get(markerIndex);
		ImGui.text("Marker");
		ImGui.textDisabled(String.format(java.util.Locale.ROOT, "%.3fs", marker.getTimeSeconds()));
		ImGui.setNextItemWidth(180f);
		ImGui.inputText("Name##markerRename", markerNameBuffer);

		if (ImGui.button("Jump##markerJump")) {
			if (clock != null) seekClockAndMusic(clock, marker.getTimeSeconds());
		}
		ImGui.sameLine();
		if (ImGui.button("Rename##markerApply")) {
			String newName = markerNameBuffer.get() == null ? "" : markerNameBuffer.get().trim();
			timeline.updateMarker(contextMarkerId, marker.getTimeSeconds(), newName);
			contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button("Delete##markerDelete")) {
			timeline.removeMarker(contextMarkerId);
			contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button("Close##markerClose")) {
			contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}

		ImGui.endPopup();
	}

	private void openPropertiesPopup(Timeline timeline, SelectionState selectionState,
			TimelineTrackListState trackListState) {
		TimelineEventRef ref = resolvePropertiesEventRef(timeline, selectionState);
		if (ref == null || ref.event() == null) return;
		if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, ref.track().getId())) return;
		propertiesEventId = ref.event().getId();
		propertiesTimeBuffer.set(String.format(java.util.Locale.ROOT, "%.6f", ref.event().getTimeSeconds()));
		loadPropertiesParameterBuffers(ref.event());
		propertiesError = null;
		ImGui.openPopup(POPUP_EVENT_PROPERTIES);
	}

	private void loadPropertiesParameterBuffers(TimelineEvent event) {
		propertiesParamBuffers.clear();
		propertiesParamAsNumber.clear();
		propertiesOriginalParamValues.clear();
		propertiesOriginalParamAsNumber.clear();
		propertiesNewParamKey.set("");
		propertiesNewParamValue.set("");
		propertiesNewParamAsNumber = false;
		if (event == null) return;
		propertiesOriginalTime = String.format(java.util.Locale.ROOT, "%.6f", event.getTimeSeconds());
		propertiesTimeBuffer.set(propertiesOriginalTime);
		for (Map.Entry<String, Object> entry : event.getParameters().entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			String text = value == null ? "" : String.valueOf(value);
			boolean asNumber = value instanceof Number;
			ImString buf = new ImString(256);
			buf.set(text);
			propertiesParamBuffers.put(key, buf);
			propertiesParamAsNumber.put(key, asNumber);
			propertiesOriginalParamValues.put(key, text);
			propertiesOriginalParamAsNumber.put(key, asNumber);
		}
	}

	private void resetPropertiesBuffers() {
		propertiesTimeBuffer.set(propertiesOriginalTime != null ? propertiesOriginalTime : "0");
		for (Map.Entry<String, ImString> entry : propertiesParamBuffers.entrySet()) {
			String key = entry.getKey();
			entry.getValue().set(propertiesOriginalParamValues.getOrDefault(key, ""));
			propertiesParamAsNumber.put(key, propertiesOriginalParamAsNumber.getOrDefault(key, false));
		}
		propertiesError = null;
	}

	private void renderPropertiesPopup(Timeline timeline, TimelineTrackListState trackListState) {
		if (!ImGui.beginPopup(POPUP_EVENT_PROPERTIES)) return;
		TimelineEventRef ref = TimelineEventRefs.find(timeline, propertiesEventId);
		if (ref == null || ref.event() == null) {
			ImGui.textDisabled("Event no longer exists.");
			if (ImGui.button("Close")) ImGui.closeCurrentPopup();
			ImGui.endPopup();
			return;
		}
		boolean trackLocked = TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, ref.track().getId());
		boolean applyRequested = !trackLocked && ImGui.isKeyPressed(ImGuiKey.Enter);
		boolean closeRequested = ImGui.isKeyPressed(ImGuiKey.Escape);

		ImGui.text("Event ID: " + ref.event().getId());
		ImGui.text("Type: " + ref.event().getType().name());
		if (trackLocked) {
			ImGui.textDisabled("Track is locked. Editing is disabled.");
		}
		if (trackLocked) {
			ImGui.beginDisabled();
		}
		ImGui.separator();
		ImGui.text("Time (seconds)");
		ImGui.setNextItemWidth(170f);
		ImGui.inputText("##eventTime", propertiesTimeBuffer);
		if (propertiesError != null) {
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, propertiesError);
		}

		if (!ref.event().getParameters().isEmpty()) {
			ImGui.separator();
			ImGui.text("Parameters");
			List<String> keys = new ArrayList<>(ref.event().getParameters().keySet());
			keys.sort(String::compareTo);
			List<String> removedKeys = new ArrayList<>();
			for (String key : keys) {
				ImString buf = propertiesParamBuffers.computeIfAbsent(key, k -> new ImString(256));
				Boolean asNumber = propertiesParamAsNumber.computeIfAbsent(key,
					k -> ref.event().getParameters().get(k) instanceof Number);
				ImGui.text(key);
				ImGui.sameLine();
				ImGui.setNextItemWidth(130f);
				ImGui.inputText("##param_" + key, buf);
				ImGui.sameLine();
				boolean numberFlag = asNumber;
				if (ImGui.checkbox("Number##param_type_" + key, numberFlag)) {
					propertiesParamAsNumber.put(key, !numberFlag);
				}
				ImGui.sameLine();
				if (ImGui.smallButton("X##param_remove_" + key)) {
					removedKeys.add(key);
				}
			}
			for (String key : removedKeys) {
				propertiesParamBuffers.remove(key);
				propertiesParamAsNumber.remove(key);
			}
		}

		ImGui.separator();
		ImGui.text("Add Parameter");
		ImGui.setNextItemWidth(120f);
		ImGui.inputText("Key##param_add_key", propertiesNewParamKey);
		ImGui.sameLine();
		ImGui.setNextItemWidth(120f);
		ImGui.inputText("Value##param_add_value", propertiesNewParamValue);
		ImGui.sameLine();
		if (ImGui.checkbox("Number##param_add_type", propertiesNewParamAsNumber)) {
			propertiesNewParamAsNumber = !propertiesNewParamAsNumber;
		}
		ImGui.sameLine();
		if (ImGui.button("Add/Update##param_add")) {
			String key = propertiesNewParamKey.get() != null ? propertiesNewParamKey.get().trim() : "";
			if (key.isEmpty()) {
				propertiesError = "Parameter key cannot be empty";
			} else {
				ImString valueBuf = propertiesParamBuffers.computeIfAbsent(key, k -> new ImString(PARAM_INPUT_BUFFER_SIZE));
				valueBuf.set(propertiesNewParamValue.get() == null ? "" : propertiesNewParamValue.get());
				propertiesParamAsNumber.put(key, propertiesNewParamAsNumber);
				propertiesError = null;
			}
		}

		if (ImGui.button("Apply") || applyRequested) {
			try {
				double t = Double.parseDouble(propertiesTimeBuffer.get().trim());
				Map<String, String> paramRaw = new HashMap<>();
				for (Map.Entry<String, ImString> entry : propertiesParamBuffers.entrySet()) {
					paramRaw.put(entry.getKey(), entry.getValue().get());
				}
				var result = GenericEventPropertiesEditor.buildUpdatedSnapshot(
					t,
					paramRaw,
					propertiesParamAsNumber,
					ref.clip().getStartTimeSeconds(),
					ref.clip().getEndTimeSeconds()
				);
				if (result instanceof GenericEventPropertiesEditor.Result.Err err) {
					propertiesError = err.message();
				} else {
					AnimationEventSnapshot after = ((GenericEventPropertiesEditor.Result.Ok) result).snapshot();
					AnimationEventSnapshot before = AnimationEventSnapshot.capture(ref.event(), ref.clip());
					TimelineEditor editor = timelineEditor;
					if (editor != null && TimelineEventEditActions.execute(
						timeline,
						editor.getCommandManager(),
						ref.track().getId(),
						ref.clip().getId(),
						ref.event().getId(),
						before,
						after
					)) {
						propertiesOriginalTime = String.format(java.util.Locale.ROOT, "%.6f", ref.event().getTimeSeconds());
						for (Map.Entry<String, ImString> entry : propertiesParamBuffers.entrySet()) {
							String key = entry.getKey();
							propertiesOriginalParamValues.put(key, entry.getValue().get());
							propertiesOriginalParamAsNumber.put(key, propertiesParamAsNumber.getOrDefault(key, false));
						}
						propertiesError = null;
					}
				}
			} catch (Exception ex) {
				propertiesError = "Invalid number in time/parameter";
			}
		}
		ImGui.sameLine();
		if (ImGui.button("Reset")) {
			resetPropertiesBuffers();
		}

		if (trackLocked) {
			ImGui.endDisabled();
		}

		ImGui.sameLine();
		if (ImGui.button("Close") || closeRequested) {
			ImGui.closeCurrentPopup();
		}
		ImGui.endPopup();
	}

	private TimelineEventRef resolvePropertiesEventRef(Timeline timeline, SelectionState selectionState) {
		return TimelineEventRefs.resolveForProperties(
			timeline,
			selectionState,
			propertiesEventId,
			id -> propertiesEventId = id
		);
	}

	private boolean canDeleteContextClip(Timeline timeline, TimelineTrackListState trackListState) {
		return TimelineInteractionDeleteSupport.canDeleteContextClip(
			timeline, trackListState, contextTrackId, contextClipId);
	}

	private void copySelectedEvents(Timeline timeline, SelectionState selectionState) {
		TimelineInteractionClipboard.copy(clipboardEvents, timeline, selectionState);
	}

	private void pasteClipboardEvents(Timeline timeline, SelectionState selectionState, double anchorTimeSeconds,
			TimelineTrackListState trackListState) {
		TimelineInteractionClipboard.paste(new TimelineInteractionClipboard.PasteRequest(
			timeline,
			selectionState,
			clipboardEvents,
			anchorTimeSeconds,
			contextTrackId,
			contextClipId,
			trackListState
		));
	}

	private void deleteSelectedEntries(Timeline timeline, SelectionState selectionState, TimelineTrackListState trackListState) {
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

	private void commitClipDrag(Timeline timeline, ClipDragStateSnapshot before) {
		TimelineDragCommitSupport.commitClipDrag(timeline, timelineEditor, before);
	}

	private void revertClipDrag(Timeline timeline, ClipDragStateSnapshot before) {
		TimelineDragCommitSupport.revertClipDrag(timeline, before);
	}

	private void finishResizeClipDrag(Timeline timeline, InteractionState interactionState, float mx, float my) {
		TimelineDragCommitSupport.finishResizeClipDrag(
			timeline,
			timelineEditor,
			interactionState,
			mx,
			my,
			resizeClipBeforeSnapshot,
			() -> resizeClipBeforeSnapshot = null
		);
	}
}
