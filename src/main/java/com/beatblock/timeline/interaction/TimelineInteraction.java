package com.beatblock.timeline.interaction;

import com.beatblock.BeatBlock;
import com.beatblock.BeatBlockClient;
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
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
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

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * 时间线输入：鼠标按下/拖拽/释放，使用 TimelineLayout 四区域做 HitTest，驱动状态与 Clock。
 * 支持：标尺/播放头拖动（SCRUB）、事件拖拽、框选。
 */
public final class TimelineInteraction {

	private static final float DRAG_THRESHOLD_PX = 4f;
	/** 播放头竖线两侧的命中宽度（像素），便于拖动 */
	private static final float PLAYHEAD_HIT_PX = 6f;
	/** 循环 In/Out 竖线命中宽度（像素） */
	private static final float LOOP_HANDLE_HIT_PX = 6f;
	/** 轨道头与内容区分割线可拖动区域宽度（像素） */
	private static final float DIVIDER_HIT_PX = 5f;
	private static final String POPUP_EVENT_CONTEXT = "##TimelineEventContextPopup";
	private static final String POPUP_EVENT_PROPERTIES = "##TimelineEventPropertiesPopup";
	private static final String POPUP_MARKER_CONTEXT = "##TimelineMarkerContextPopup";
	private static final String POPUP_DELETE_CONFIRM = "##TimelineDeleteConfirmPopup";
	private static final int TIME_INPUT_BUFFER_SIZE = 64;
	private static final int PARAM_INPUT_BUFFER_SIZE = 256;
	private static final int MARKER_NAME_BUFFER_SIZE = 128;
	private static final class ClipboardEvent {
		final String trackId;
		final String clipId;
		final double timeSeconds;
		final com.beatblock.timeline.EventType type;
		final Map<String, Object> parameters;

		ClipboardEvent(String trackId, String clipId, double timeSeconds,
				com.beatblock.timeline.EventType type, Map<String, Object> parameters) {
			this.trackId = trackId;
			this.clipId = clipId;
			this.timeSeconds = timeSeconds;
			this.type = type;
			this.parameters = parameters;
		}
	}

	private static final class EventRef {
		final Track track;
		final Clip clip;
		final TimelineEvent event;

		EventRef(Track track, Clip clip, TimelineEvent event) {
			this.track = track;
			this.clip = clip;
			this.event = event;
		}
	}

	private IAudioPlayer audioPlayer;
	private final List<ClipboardEvent> clipboardEvents = new ArrayList<>();
	private String contextTrackId;
	private String contextClipId;
	private double contextTimeSeconds;
	private String propertiesEventId;
	private final ImString propertiesTimeBuffer = new ImString(TIME_INPUT_BUFFER_SIZE);
	private String propertiesOriginalTime = "0";
	private final Map<String, ImString> propertiesParamBuffers = new HashMap<>();
	private final Map<String, Boolean> propertiesParamAsNumber = new HashMap<>();
	private final Map<String, String> propertiesOriginalParamValues = new HashMap<>();
	private final Map<String, Boolean> propertiesOriginalParamAsNumber = new HashMap<>();
	private final ImString propertiesNewParamKey = new ImString(PARAM_INPUT_BUFFER_SIZE);
	private final ImString propertiesNewParamValue = new ImString(PARAM_INPUT_BUFFER_SIZE);
	private boolean propertiesNewParamAsNumber;
	private String propertiesError;
	private String contextMarkerId;
	private final ImString markerNameBuffer = new ImString(MARKER_NAME_BUFFER_SIZE);

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

	private static final float CAMERA_EDGE_HIT_PX = 6f;
	private static final double CAMERA_MIN_CLIP_DURATION = 0.05;
	private double cameraResizeInitialStart;
	private double cameraResizeInitialEnd;
	private final Map<String, Double> cameraResizeEventOrigTimes = new HashMap<>();
	private final ImBoolean contextCameraShowPath = new ImBoolean(true);

	public void setAudioPlayer(IAudioPlayer audioPlayer) {
		this.audioPlayer = audioPlayer;
	}

	public TimelineInteraction() {
		contextTimeSeconds = 0;
	}

	private static final String[] INTERACTIVE_TRACK_IDS = {
		Timeline.TRACK_ID_AUDIO,
		Timeline.TRACK_ID_ANIMATION_BLOCK,
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_CAMERA,
		Timeline.TRACK_ID_GLOBAL
	};

	private static final int[] INTERACTIVE_ROW_INDICES = {
		TimelineTrackMeta.ROW_AUDIO_GROUP,
		TimelineTrackMeta.ROW_ANIM_BLOCK,
		TimelineTrackMeta.ROW_ANIM_AUTO,
		TimelineTrackMeta.ROW_CAMERA,
		TimelineTrackMeta.ROW_GLOBAL_EVENT
	};

	private record InteractiveTrackSlot(String trackId, int rowIndex) {}

	private static List<InteractiveTrackSlot> interactiveTrackSlots(Timeline timeline) {
		List<InteractiveTrackSlot> slots = new ArrayList<>();
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_AUDIO, TimelineTrackMeta.ROW_AUDIO_GROUP));
		if (timeline != null) {
			List<TrackDefinition> defs = TrackRegistry.buildBlockAnimationControlTracks(timeline);
			for (int i = 0; i < defs.size() && i < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; i++) {
				slots.add(new InteractiveTrackSlot(defs.get(i).getKey(), TimelineTrackMeta.ROW_ANIM_FEATURES_START + i));
			}
		}
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_ANIMATION_AUTO, TimelineTrackMeta.ROW_ANIM_AUTO));
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_CAMERA, TimelineTrackMeta.ROW_CAMERA));
		slots.add(new InteractiveTrackSlot(Timeline.TRACK_ID_GLOBAL, TimelineTrackMeta.ROW_GLOBAL_EVENT));
		return slots;
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
				cameraResizeEventOrigTimes.clear();
			}
			if (interactionState.getMode() == InteractionMode.DRAG_CLIP && interactionState.getActiveClipId() != null) {
				float dx = mx - interactionState.getMouseStartX();
				float dy = my - interactionState.getMouseStartY();
				if (dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
					selectionState.clearClips();
					selectionState.selectClip(interactionState.getActiveClipId());
				}
				dragLinkedEventOriginalTimes.clear();
				dragFeatureEventSnapshot.clear();
				dragCameraClipEventOriginalTimes.clear();
			}
			if (interactionState.getMode() == InteractionMode.DRAG_EVENT && interactionState.getActiveEventId() != null) {
				float dx = mx - interactionState.getMouseStartX();
				float dy = my - interactionState.getMouseStartY();
				if (dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
					selectionState.clearEvents();
					selectionState.selectEvent(interactionState.getActiveEventId());
				}
			}
			if (interactionState.getMode() == InteractionMode.BOX_SELECT
					&& selectionBox != null && selectionBox.isActive()) {
				float boxMinX = selectionBox.getMinX();
				float boxMaxX = selectionBox.getMaxX();
				float boxMinY = selectionBox.getMinY();
				float boxMaxY = selectionBox.getMaxY();
				for (InteractiveTrackSlot slot : interactiveTrackSlots(timeline)) {
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
				&& hasDeletableSelection(timeline, selectionState, trackListState)) {
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
			boolean overDivider = isMouseOverDivider(mx, my, layout);
			if (overDivider && interactionState.getMode() == InteractionMode.NONE) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
		}
		if (toolbarState != null && layout.rulerContains(mx, my) && interactionState.getMode() == InteractionMode.NONE) {
			if (isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)
				|| isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState)) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
		}
		if (trackListState != null && layout.contentContains(mx, my)
				&& interactionState.getMode() == InteractionMode.NONE
				&& !isTrackLocked(timeline, trackListState, Timeline.TRACK_ID_CAMERA)) {
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
			int markerIndex = findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (markerIndex >= 0) {
				contextMarkerId = timeline.getMarkers().get(markerIndex).getId();
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				markerNameBuffer.set(marker.getName());
				ImGui.openPopup(POPUP_MARKER_CONTEXT);
			}
		}

		if (!alt && ImGui.isMouseDoubleClicked(0) && layout.rulerContains(mx, my)) {
			boolean overLoopHandle = toolbarState != null
				&& (isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)
					|| isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState));
			int markerIndex = findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (!overLoopHandle && markerIndex < 0) {
				double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
				addMarkerAtTime(timeline, t);
				if (clock != null) seekClockAndMusic(clock, t);
				return;
			}
		}

		if (ImGui.isMouseClicked(1) && layout.contentContains(mx, my)) {
			contextTimeSeconds = viewState.screenToTime(Math.max(0, Math.min(mx - layout.contentLeft, layout.contentWidth)));
			HitResult hit = hitContentAtMouse(timeline, viewState, layout, mx, my);
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
				if (isTrackLocked(timeline, trackListState, Timeline.TRACK_ID_CAMERA)) return;
				Track ct = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
				Clip c = ct != null ? ct.getClip(interactionState.getActiveClipId()) : null;
				if (c != null) {
					double mouseT = viewState.screenToTime(mx - layout.contentLeft);
					double snapped = DragController.snapTime(mouseT, null, timeline, toolbarState, viewState);
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
				if (isTrackLocked(timeline, trackListState, interactionState.getActiveTrackId())) return;
				double mouseTime = viewState.screenToTime(mx - layout.contentLeft);
				double clipDuration = dragClipInitialEnd - dragClipInitialStart;
				double newStart = DragController.dragClip(timeline, interactionState.getActiveTrackId(),
					interactionState.getActiveClipId(), mouseTime, dragClipInitialMouseTime,
					dragClipInitialStart, clipDuration, duration, toolbarState, viewState);
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
					if (st == null) continue;
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
				if (isTrackLocked(timeline, trackListState, interactionState.getActiveTrackId())) {
					return;
				}
				double t = viewState.screenToTime(mx - layout.contentLeft);
				DragController.dragEvent(timeline, interactionState.getActiveTrackId(), interactionState.getActiveClipId(), interactionState.getActiveEventId(), t, duration, toolbarState, viewState);
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
			if (trackListState != null && isMouseOverDivider(mx, my, layout)) {
				interactionState.setMode(InteractionMode.RESIZE_HEADER);
				interactionState.setMouseStart(mx, my);
				interactionState.setResizeStartHeaderWidth(trackListState.getTrackHeaderWidth());
				return;
			}
			if (layout.rulerContains(mx, my)) {
				if (toolbarState != null && isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)) {
					interactionState.setMode(InteractionMode.LOOP_IN_DRAG);
					interactionState.setMouseStart(mx, my);
					return;
				}
				if (toolbarState != null && isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState)) {
					interactionState.setMode(InteractionMode.LOOP_OUT_DRAG);
					interactionState.setMouseStart(mx, my);
					return;
				}
				int markerIndex = findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
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
					&& !isTrackLocked(timeline, trackListState, Timeline.TRACK_ID_CAMERA)) {
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
							return;
						}
					}
				}
			}
			for (InteractiveTrackSlot slot : interactiveTrackSlots(timeline)) {
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
							if (st == null) continue;
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
					if (!ctrl) selectionState.clearEvents();
					if (hit.getEventId() != null) selectionState.selectEvent(hit.getEventId());
					else if (hit.getClipId() != null) selectionState.selectClip(hit.getClipId());
					return;
				}
			}
			// 点击播放头竖线也可拖动（与标尺一致的 SCRUB 行为）
			// 放在片段命中之后，避免音频片段与播放头重叠时误触发播放头拖动。
			if (isMouseOverPlayhead(mx, my, layout, viewState, clock)) {
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
			if (isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)
				|| isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState)) {
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
			int markerIndex = findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (markerIndex >= 0) {
				contextMarkerId = timeline.getMarkers().get(markerIndex).getId();
				TimelineMarker marker = timeline.getMarkers().get(markerIndex);
				markerNameBuffer.set(marker.getName());
				ImGui.openPopup(POPUP_MARKER_CONTEXT);
			}
		}

		if (!alt && ImGui.isMouseDoubleClicked(0) && layout.rulerContains(mx, my)) {
			boolean overLoopHandle = toolbarState != null
				&& (isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)
					|| isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState));
			int markerIndex = findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
			if (!overLoopHandle && markerIndex < 0) {
				double t = Math.max(0, Math.min(viewState.screenToTime(mx - layout.contentLeft), duration));
				addMarkerAtTime(timeline, t);
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
			if (toolbarState != null && isMouseOverLoopInHandle(mx, my, layout, viewState, toolbarState)) {
				interactionState.setMode(InteractionMode.LOOP_IN_DRAG);
				interactionState.setMouseStart(mx, my);
				return;
			}
			if (toolbarState != null && isMouseOverLoopOutHandle(mx, my, layout, viewState, toolbarState)) {
				interactionState.setMode(InteractionMode.LOOP_OUT_DRAG);
				interactionState.setMouseStart(mx, my);
				return;
			}
			int markerIndex = findMarkerIndexAtMouse(timeline, viewState, layout, mx, my);
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
		clock.seek(timeSeconds);
		try {
			if (audioPlayer == null) return;

			Timeline timeline = BeatBlock.timeline;
			if (timeline == null || BeatBlock.musicPlayer == null || audioPlayer != BeatBlock.musicPlayer) {
				audioPlayer.setCurrentTimeSeconds(clock.getCurrentTimeSeconds());
				return;
			}

			Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
			if (audioTrack == null || audioTrack.getClips().isEmpty()) {
				audioPlayer.setCurrentTimeSeconds(clock.getCurrentTimeSeconds());
				return;
			}

			boolean segmentedTimeline = hasSegmentedClipAudio(timeline, audioTrack);

			Clip targetClip = null;
			double t = clock.getCurrentTimeSeconds();
			for (Clip c : audioTrack.getClips()) {
				if (c == null) continue;
				if (t >= c.getStartTimeSeconds() && t <= c.getEndTimeSeconds()) {
					targetClip = c;
					break;
				}
			}
			if (targetClip == null) {
				if (segmentedTimeline) {
					timeline.setMetadata("activeAudioClipId", null);
					if (audioPlayer.isPlaying()) {
						audioPlayer.pause();
					}
					return;
				}
				audioPlayer.setCurrentTimeSeconds(t);
				return;
			}

			Object pathObj = timeline.getMetadata("clipAudioPath_" + targetClip.getId());
			if (pathObj != null) {
				String path = pathObj.toString();
				String loadedPath = BeatBlock.musicPlayer.getLoadedAudioPath();
				if (loadedPath == null || !loadedPath.equals(path)) {
					boolean wasPlaying = BeatBlock.musicPlayer.isPlaying();
					BeatBlock.musicPlayer.loadAudio(path);
					if (wasPlaying) BeatBlock.musicPlayer.play();
				}
				double localTime = Math.max(0.0,
					Math.min(t - targetClip.getStartTimeSeconds(), targetClip.getDurationSeconds()));
				audioPlayer.setCurrentTimeSeconds(localTime);
				timeline.setMetadata("activeAudioClipId", targetClip.getId());
				return;
			}

			audioPlayer.setCurrentTimeSeconds(t);
		} finally {
			com.beatblock.client.camera.TimelineCameraController.getInstance().tick();
		}
	}

	private static boolean hasSegmentedClipAudio(Timeline timeline, Track audioTrack) {
		if (timeline == null || audioTrack == null) return false;
		for (Clip c : audioTrack.getClips()) {
			if (c == null) continue;
			Object pathObj = timeline.getMetadata("clipAudioPath_" + c.getId());
			if (pathObj != null && !pathObj.toString().isBlank()) return true;
		}
		return false;
	}

	private static boolean isMouseOverLoopInHandle(
		float mx,
		float my,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState
	) {
		if (layout == null || viewState == null || toolbarState == null) return false;
		float x = layout.rulerLeft + viewState.timeToScreen(toolbarState.getLoopInSeconds());
		return layout.rulerContains(mx, my) && Math.abs(mx - x) <= LOOP_HANDLE_HIT_PX;
	}

	private static boolean isMouseOverLoopOutHandle(
		float mx,
		float my,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState
	) {
		if (layout == null || viewState == null || toolbarState == null || !toolbarState.hasLoopRange()) return false;
		float x = layout.rulerLeft + viewState.timeToScreen(toolbarState.getLoopOutSeconds());
		return layout.rulerContains(mx, my) && Math.abs(mx - x) <= LOOP_HANDLE_HIT_PX;
	}

	private static int findMarkerIndexAtMouse(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mx,
		float my
	) {
		if (timeline == null || viewState == null || layout == null || !layout.rulerContains(mx, my)) return -1;
		List<TimelineMarker> markers = timeline.getMarkers();
		for (int i = 0; i < markers.size(); i++) {
			TimelineMarker marker = markers.get(i);
			if (marker == null) continue;
			float x = layout.rulerLeft + viewState.timeToScreen(marker.getTimeSeconds());
			if (Math.abs(mx - x) <= LOOP_HANDLE_HIT_PX) return i;
		}
		return -1;
	}

	private static void addMarkerAtTime(Timeline timeline, double timeSeconds) {
		if (timeline == null) return;
		int markerIndex = timeline.getMarkers().size() + 1;
		timeline.addMarker(new TimelineMarker(timeSeconds, "Marker " + markerIndex));
	}

	private void renderContextMenu(Timeline timeline, SelectionState selectionState,
			TimelineTrackListState trackListState) {
		if (!ImGui.beginPopup(POPUP_EVENT_CONTEXT)) return;
		boolean requestDeleteConfirmPopup = false;
		boolean hasSelection = selectionState != null
			&& (!selectionState.getSelectedEvents().isEmpty() || !selectionState.getSelectedClips().isEmpty());
		boolean canDeleteSelection = hasDeletableSelection(timeline, selectionState, trackListState);
		boolean canDeleteContextClip = canDeleteContextClip(timeline, trackListState);
		BeatBlockClient.LOGGER.info(String.format(
			"[TimelineInteraction.renderContextMenu] Menu opened: contextClipId=%s, contextTrackId=%s, canDeleteSelection=%s, canDeleteContextClip=%s",
			contextClipId, contextTrackId, canDeleteSelection, canDeleteContextClip
		));
		boolean canDeleteAny = canDeleteSelection || canDeleteContextClip;
		boolean hasClipboard = !clipboardEvents.isEmpty();
		EventRef propertiesRef = resolvePropertiesEventRef(timeline, selectionState);
		boolean canOpenProperties = propertiesRef != null && !isTrackLocked(timeline, trackListState, propertiesRef.track.getId());
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
				&& !isTrackLocked(timeline, trackListState, contextTrackId)) {
			if (contextClipId != null) {
				if (ImGui.checkbox("显示路径##camCtxPathVis", contextCameraShowPath)) {
					CameraPathMetadata.setPathVisible(timeline, contextClipId, contextCameraShowPath.get());
				}
			}
			EventRef ctxEv = propertiesEventId != null ? findEventRef(timeline, propertiesEventId) : null;
			if (ctxEv != null && ctxEv.event != null && ctxEv.event.getType() == EventType.CAMERA_KEYFRAME) {
				if (ImGui.menuItem("删除关键帧##camDelKf")) {
					TimelineOperations.removeEvent(ctxEv.clip, ctxEv.event.getId());
					if (selectionState != null) {
						selectionState.deselectEvent(ctxEv.event.getId());
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
				double[] a = readCameraAnchorFive();
				if (ImGui.menuItem("正常路径（关键帧）")) {
					CameraTrackFactory.addPathSegment(timeline, contextTimeSeconds, a[0], a[1], a[2], a[3], a[4]);
				}
				if (ImGui.menuItem("推进（Dolly）")) {
					CameraTrackFactory.addDollySegment(timeline, contextTimeSeconds, a[0], a[1], a[2], a[3], 8.0);
				}
				if (ImGui.menuItem("环绕（Orbit）")) {
					double[] o = readOrbitParamsFromView();
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
		boolean hasDeletable = hasDeletableSelection(timeline, selectionState, trackListState);
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

	private boolean canDeleteContextClip(Timeline timeline, TimelineTrackListState trackListState) {
		if (timeline == null || contextClipId == null) {
			BeatBlockClient.LOGGER.debug(String.format(
				"[TimelineInteraction.canDeleteContextClip] Early return: timeline=%s, contextClipId=%s",
				timeline != null, contextClipId
			));
			return false;
		}
		if (contextTrackId != null && !contextTrackId.isBlank()) {
			Track track = timeline.getTrack(contextTrackId);
			boolean trackExists = track != null;
			boolean clipExists = trackExists && track.getClip(contextClipId) != null;
			boolean trackNotLocked = !isTrackLocked(timeline, trackListState, contextTrackId);
			boolean result = trackExists && clipExists && trackNotLocked;
			BeatBlockClient.LOGGER.debug(String.format(
				"[TimelineInteraction.canDeleteContextClip] With contextTrackId: trackExists=%s, clipExists=%s, trackNotLocked=%s, result=%s",
				trackExists, clipExists, trackNotLocked, result
			));
			return result;
		}
		// Search through all tracks
		for (Track track : timeline.getTracks()) {
			Clip clip = track.getClip(contextClipId);
			if (clip != null) {
				boolean trackNotLocked = !isTrackLocked(timeline, trackListState, track.getId());
				BeatBlockClient.LOGGER.debug(String.format(
					"[TimelineInteraction.canDeleteContextClip] Found clip in track %s: trackNotLocked=%s",
					track.getId(), trackNotLocked
				));
				return trackNotLocked;
			}
		}
		BeatBlockClient.LOGGER.debug("[TimelineInteraction.canDeleteContextClip] Clip not found in any track");
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
		EventRef ref = resolvePropertiesEventRef(timeline, selectionState);
		if (ref == null || ref.event == null) return;
		if (isTrackLocked(timeline, trackListState, ref.track.getId())) return;
		propertiesEventId = ref.event.getId();
		propertiesTimeBuffer.set(String.format(java.util.Locale.ROOT, "%.6f", ref.event.getTimeSeconds()));
		loadPropertiesParameterBuffers(ref.event);
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
		EventRef ref = findEventRef(timeline, propertiesEventId);
		if (ref == null || ref.event == null) {
			ImGui.textDisabled("Event no longer exists.");
			if (ImGui.button("Close")) ImGui.closeCurrentPopup();
			ImGui.endPopup();
			return;
		}
		boolean trackLocked = isTrackLocked(timeline, trackListState, ref.track.getId());
		boolean applyRequested = !trackLocked && ImGui.isKeyPressed(ImGuiKey.Enter);
		boolean closeRequested = ImGui.isKeyPressed(ImGuiKey.Escape);

		ImGui.text("Event ID: " + ref.event.getId());
		ImGui.text("Type: " + ref.event.getType().name());
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

		if (!ref.event.getParameters().isEmpty()) {
			ImGui.separator();
			ImGui.text("Parameters");
			List<String> keys = new ArrayList<>(ref.event.getParameters().keySet());
			keys.sort(String::compareTo);
			List<String> removedKeys = new ArrayList<>();
			for (String key : keys) {
				ImString buf = propertiesParamBuffers.computeIfAbsent(key, k -> new ImString(256));
				Boolean asNumber = propertiesParamAsNumber.computeIfAbsent(key,
					k -> ref.event.getParameters().get(k) instanceof Number);
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
			String raw = propertiesTimeBuffer.get();
			try {
				double t = Math.max(0, Double.parseDouble(raw.trim()));
				ref.event.setTimeSeconds(t);
				Set<String> existing = new HashSet<>(ref.event.getParameters().keySet());
				for (String key : existing) {
					if (!propertiesParamBuffers.containsKey(key)) {
						ref.event.removeParameter(key);
					}
				}
				for (Map.Entry<String, ImString> entry : propertiesParamBuffers.entrySet()) {
					String key = entry.getKey();
					String valueRaw = entry.getValue().get();
					boolean asNumber = propertiesParamAsNumber.getOrDefault(key, false);
					if (asNumber) {
						ref.event.setParameter(key, Double.parseDouble(valueRaw.trim()));
					} else {
						ref.event.setParameter(key, valueRaw);
					}
				}
				timeline.markAnimationEventsDirty(ref.track.getId());
				propertiesOriginalTime = String.format(java.util.Locale.ROOT, "%.6f", ref.event.getTimeSeconds());
				for (Map.Entry<String, ImString> entry : propertiesParamBuffers.entrySet()) {
					String key = entry.getKey();
					propertiesOriginalParamValues.put(key, entry.getValue().get());
					propertiesOriginalParamAsNumber.put(key, propertiesParamAsNumber.getOrDefault(key, false));
				}
				propertiesError = null;
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

	private EventRef resolvePropertiesEventRef(Timeline timeline, SelectionState selectionState) {
		EventRef byContext = findEventRef(timeline, propertiesEventId);
		if (byContext != null) return byContext;
		if (selectionState != null && !selectionState.getSelectedEvents().isEmpty()) {
			for (String eventId : selectionState.getSelectedEvents()) {
				EventRef bySelection = findEventRef(timeline, eventId);
				if (bySelection != null) {
					propertiesEventId = eventId;
					return bySelection;
				}
			}
		}
		return null;
	}

	private static EventRef findEventRef(Timeline timeline, String eventId) {
		if (timeline == null || eventId == null || eventId.isBlank()) return null;
		for (Track track : timeline.getTracks()) {
			for (Clip clip : track.getClips()) {
				TimelineEvent e = clip.getEvent(eventId);
				if (e != null) return new EventRef(track, clip, e);
			}
		}
		return null;
	}

	private HitResult hitContentAtMouse(Timeline timeline, TimelineViewState viewState,
			TimelineLayout layout, float mx, float my) {
		for (InteractiveTrackSlot slot : interactiveTrackSlots(timeline)) {
			int logicalRow = slot.rowIndex();
			if (!layout.isRowVisible(logicalRow)) continue;
			float rowScreenY = layout.getRowScreenY(logicalRow);
			float rowH = layout.getRowHeight(logicalRow);
			HitResult hit = HitTestSystem.hitTestTrackContent(
				timeline,
				slot.trackId(),
				mx,
				my,
				layout.contentLeft,
				rowScreenY,
				rowH,
				layout.contentWidth,
				viewState);
			if (!hit.isEmpty()) return hit;
		}
		return HitResult.empty();
	}

	private void copySelectedEvents(Timeline timeline, SelectionState selectionState) {
		clipboardEvents.clear();
		if (timeline == null || selectionState == null) return;
		if (selectionState.getSelectedEvents().isEmpty()) return;
		Set<String> selected = new HashSet<>(selectionState.getSelectedEvents());
		for (Track track : timeline.getTracks()) {
			for (Clip clip : track.getClips()) {
				for (TimelineEvent e : clip.getEvents()) {
					if (!selected.contains(e.getId())) continue;
					clipboardEvents.add(new ClipboardEvent(
						track.getId(),
						clip.getId(),
						e.getTimeSeconds(),
						e.getType(),
						new HashMap<>(e.getParameters())
					));
				}
			}
		}
		clipboardEvents.sort(Comparator.comparingDouble(a -> a.timeSeconds));
	}

	private void pasteClipboardEvents(Timeline timeline, SelectionState selectionState, double anchorTimeSeconds,
			TimelineTrackListState trackListState) {
		if (timeline == null || selectionState == null) return;
		if (clipboardEvents.isEmpty()) return;

		double baseTime = clipboardEvents.getFirst().timeSeconds;
		double maxTime = clipboardEvents.getLast().timeSeconds;
		double span = Math.max(0.2, maxTime - baseTime);
		selectionState.clearEvents();
		Set<String> dirtyTracks = new HashSet<>();
		Map<String, Clip> targetClipsByTrack = new HashMap<>();

		for (ClipboardEvent src : clipboardEvents) {
			double newTime = Math.max(0, anchorTimeSeconds + (src.timeSeconds - baseTime));
			Track targetTrack = resolvePasteTargetTrack(timeline, src, trackListState);
			if (targetTrack == null) continue;
			Clip targetClip = resolveOrCreatePasteTargetClip(
				timeline,
				targetTrack,
				newTime,
				anchorTimeSeconds,
				span,
				targetClipsByTrack);
			if (targetClip == null) continue;
			TimelineEvent added = TimelineOperations.addEvent(targetClip, newTime, src.type, new HashMap<>(src.parameters));
			if (added != null) {
				selectionState.selectEvent(added.getId());
				dirtyTracks.add(targetTrack.getId());
			}
		}

		for (String trackId : dirtyTracks) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	private Track resolvePasteTargetTrack(Timeline timeline, ClipboardEvent src, TimelineTrackListState trackListState) {
		if (contextTrackId != null) {
			Track t = timeline.getTrack(contextTrackId);
			if (t != null && !isTrackLocked(timeline, trackListState, t.getId())) return t;
		}
		Track fallback = timeline.getTrack(src.trackId);
		if (fallback == null) return null;
		return isTrackLocked(timeline, trackListState, fallback.getId()) ? null : fallback;
	}

	private Clip resolveOrCreatePasteTargetClip(
			Timeline timeline,
			Track targetTrack,
			double eventTime,
			double anchorTime,
			double span,
			Map<String, Clip> targetClipsByTrack) {
		Clip cached = targetClipsByTrack.get(targetTrack.getId());
		if (cached != null) return cached;

		if (contextTrackId != null && contextTrackId.equals(targetTrack.getId()) && contextClipId != null) {
			Clip contextClip = targetTrack.getClip(contextClipId);
			if (contextClip != null) {
				targetClipsByTrack.put(targetTrack.getId(), contextClip);
				return contextClip;
			}
		}

		for (Clip clip : targetTrack.getClips()) {
			if (eventTime >= clip.getStartTimeSeconds() && eventTime <= clip.getEndTimeSeconds()) {
				targetClipsByTrack.put(targetTrack.getId(), clip);
				return clip;
			}
		}

		double start = Math.max(0, anchorTime - 0.05);
		double end = Math.max(start + 0.2, start + span + 0.1);
		Clip created = TimelineOperations.addClip(targetTrack, start, end);
		if (created != null) {
			targetClipsByTrack.put(targetTrack.getId(), created);
		}
		return created;
	}

	private void deleteSelectedEntries(Timeline timeline, SelectionState selectionState, TimelineTrackListState trackListState) {
		if (timeline == null || selectionState == null) return;
		if (selectionState.getSelectedEvents().isEmpty() && selectionState.getSelectedClips().isEmpty()) {
			BeatBlockClient.LOGGER.warn("[TimelineInteraction.deleteSelectedEntries] No clips or events to delete");
			return;
		}

		List<String> clipIds = new ArrayList<>(selectionState.getSelectedClips());
		BeatBlockClient.LOGGER.info(String.format(
			"[TimelineInteraction.deleteSelectedEntries] Starting: clipIds=%s, eventIds=%s",
			clipIds, selectionState.getSelectedEvents()
		));
		if (!clipIds.isEmpty()) {
			for (Track track : timeline.getTracks()) {
				if (isTrackLocked(timeline, trackListState, track.getId())) {
					BeatBlockClient.LOGGER.debug(String.format("[TimelineInteraction.deleteSelectedEntries] Track locked: %s", track.getId()));
					continue;
				}
				for (String clipId : clipIds) {
					if (clipId == null) continue;
					Clip clip = track.getClip(clipId);
					if (clip != null) {
						BeatBlockClient.LOGGER.info(String.format("[TimelineInteraction.deleteSelectedEntries] Removing clip %s from track %s", clipId, track.getId()));
						if (track.removeClip(clipId)) {
							BeatBlockClient.LOGGER.info(String.format("[TimelineInteraction.deleteSelectedEntries] Clip removed successfully: %s", clipId));
							selectionState.deselectClip(clipId);
							timeline.markAnimationEventsDirty(track.getId());
							if (Timeline.TRACK_ID_AUDIO.equals(track.getId())) {
								onAudioRootClipDeleted(timeline, clipId);
							}
						} else {
							BeatBlockClient.LOGGER.warn(String.format("[TimelineInteraction.deleteSelectedEntries] Failed to remove clip: %s", clipId));
						}
					} else {
						BeatBlockClient.LOGGER.debug(String.format("[TimelineInteraction.deleteSelectedEntries] Clip not found in track %s: %s", track.getId(), clipId));
					}
				}
			}
		}

		List<String> eventIds = new ArrayList<>(selectionState.getSelectedEvents());
		for (Track track : timeline.getTracks()) {
			if (isTrackLocked(timeline, trackListState, track.getId())) continue;
			for (Clip clip : track.getClips()) {
				for (String eventId : eventIds) {
					if (eventId == null) continue;
					if (TimelineOperations.removeEvent(clip, eventId)) {
						selectionState.deselectEvent(eventId);
						timeline.markAnimationEventsDirty(track.getId());
					}
				}
			}
		}
	}

	private boolean hasDeletableSelection(Timeline timeline, SelectionState selectionState,
			TimelineTrackListState trackListState) {
		if (timeline == null || selectionState == null) return false;

		if (!selectionState.getSelectedClips().isEmpty()) {
			for (String clipId : selectionState.getSelectedClips()) {
				if (clipId == null) continue;
				for (Track track : timeline.getTracks()) {
					if (track.getClip(clipId) != null && !isTrackLocked(timeline, trackListState, track.getId())) {
						return true;
					}
				}
			}
		}

		if (selectionState.getSelectedEvents().isEmpty()) return false;
		for (String eventId : selectionState.getSelectedEvents()) {
			EventRef ref = findEventRef(timeline, eventId);
			if (ref != null && !isTrackLocked(timeline, trackListState, ref.track.getId())) {
				return true;
			}
		}
		return false;
	}

	private boolean isTrackLocked(Timeline timeline, TimelineTrackListState trackListState, String trackId) {
		if (trackListState == null || trackId == null || trackId.isBlank()) return false;
		int logicalRow = logicalRowForTrackId(timeline, trackId);
		if (logicalRow < 0) return false;
		return trackListState.isLocked(logicalRow);
	}

	private int logicalRowForTrackId(Timeline timeline, String trackId) {
		if (trackId == null || trackId.isBlank()) return -1;
		for (InteractiveTrackSlot slot : interactiveTrackSlots(timeline)) {
			if (trackId.equals(slot.trackId())) {
				return slot.rowIndex();
			}
		}
		return -1;
	}

	private static void onAudioRootClipDeleted(Timeline timeline, String deletedClipId) {
		if (timeline == null || deletedClipId == null || deletedClipId.isBlank()) return;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) return;

		// 多段音频模式：仅在音频轨已无任何片段时，才清理全局音频分析/波形数据。
		if (!audioTrack.getClips().isEmpty()) return;

		if (audioTrack.getAudioData() != null) {
			audioTrack.getAudioData().setWaveform(null);
			audioTrack.getAudioData().clearAll();
			audioTrack.getAudioData().clearStemWaveforms();
		}

		timeline.setMetadata("audioRootClipId", null);
		timeline.setMetadata("audioAssetId", null);
		timeline.setMetadata("audioPath", null);
		timeline.setMetadata("awaitingAnalyzedBeatmap", null);
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

	/** x, y, z, yawDeg, pitchDeg — 无玩家时为原点与水平朝向 */
	private static double[] readCameraAnchorFive() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && mc.player != null) {
			Vec3d eye = mc.player.getEyePos();
			return new double[]{
				eye.x,
				eye.y,
				eye.z,
				mc.player.getYaw(),
				mc.player.getPitch()
			};
		}
		return new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
	}

	/**
	 * 环绕片段默认值：目标为准星方块命中点（否则为视线前方约 10m），半径/高度/起始角由当前眼点相对目标拟合，弧长默认 270°。
	 */
	private static double[] readOrbitParamsFromView() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null) {
			return new double[]{0.0, 0.0, 0.0, 10.0, 4.0, 0.0, 270.0};
		}
		Vec3d eye = mc.player.getEyePos();
		Vec3d target;
		net.minecraft.util.hit.HitResult ch = mc.crosshairTarget;
		if (ch instanceof BlockHitResult bhr && ch.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
			target = bhr.getPos();
		} else {
			Vec3d dir = mc.player.getRotationVec(1f);
			target = eye.add(dir.multiply(10.0));
		}
		double tx = target.x;
		double ty = target.y;
		double tz = target.z;
		double dx = eye.x - tx;
		double dz = eye.z - tz;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		double radius = Math.max(0.75, horiz);
		double height = eye.y - ty;
		double yawStartDeg = Math.toDegrees(Math.atan2(-dx, dz));
		double yawEndDeg = yawStartDeg + 270.0;
		return new double[]{tx, ty, tz, radius, height, yawStartDeg, yawEndDeg};
	}
}
