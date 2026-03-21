package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private static final String POPUP_EVENT_CONTEXT = "##TimelineEventContextPopup";
	private static final String POPUP_EVENT_PROPERTIES = "##TimelineEventPropertiesPopup";
	private static final int TIME_INPUT_BUFFER_SIZE = 64;
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
	private String propertiesError;

	public void setAudioPlayer(IAudioPlayer audioPlayer) {
		this.audioPlayer = audioPlayer;
	}

	public TimelineInteraction() {
		contextTimeSeconds = 0;
	}

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

		if (!ImGui.isWindowHovered()) return;

		if (ImGui.isKeyPressed(ImGuiKey.Delete)) {
			deleteSelectedEvents(timeline, selectionState);
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
			pasteClipboardEvents(timeline, selectionState, anchor);
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

		// 轨道子窗口内：分割线悬停光标
		if (trackListState != null) {
			boolean overDivider = isMouseOverDivider(mx, my, layout);
			if (overDivider && interactionState.getMode() == InteractionMode.NONE) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
		}

		if (ImGui.isMouseClicked(1) && layout.contentContains(mx, my)) {
			contextTimeSeconds = viewState.screenToTime(Math.max(0, Math.min(mx - layout.contentLeft, layout.contentWidth)));
			HitResult hit = hitContentAtMouse(timeline, viewState, layout, mx, my);
			contextTrackId = hit != null ? hit.getTrackId() : null;
			contextClipId = hit != null ? hit.getClipId() : null;
			if (hit != null && hit.getEventId() != null) {
				propertiesEventId = hit.getEventId();
			}
			ImGui.openPopup(POPUP_EVENT_CONTEXT);
		}
		renderContextMenu(timeline, selectionState);
		renderPropertiesPopup(timeline);

		if (ImGui.isMouseReleased(0)) {
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
				for (int i = 0; i < layout.getInteractiveRowCount() && i < INTERACTIVE_TRACK_IDS.length; i++) {
					int logicalRow = TimelineLayout.INTERACTIVE_ROW_INDICES[i];
					if (!layout.isRowVisible(logicalRow)) continue;
					float rowTopY = layout.getRowScreenY(logicalRow);
					float rowBotY = rowTopY + TimelineLayout.ROW_HEIGHT;
					if (rowBotY < boxMinY || rowTopY > boxMaxY) continue;
					Track track = timeline.getTrack(INTERACTIVE_TRACK_IDS[i]);
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
	private void seekClockAndMusic(TimelineClock clock, double timeSeconds) {
		clock.seek(timeSeconds);
		if (audioPlayer != null) {
			audioPlayer.setCurrentTimeSeconds(clock.getCurrentTimeSeconds());
		}
	}

	private void renderContextMenu(Timeline timeline, SelectionState selectionState) {
		if (!ImGui.beginPopup(POPUP_EVENT_CONTEXT)) return;
		boolean hasSelection = selectionState != null && !selectionState.getSelectedEvents().isEmpty();
		boolean hasClipboard = !clipboardEvents.isEmpty();
		boolean canOpenProperties = resolvePropertiesEventRef(timeline, selectionState) != null;
		if (ImGui.menuItem("Copy", "Ctrl+C", false, hasSelection)) {
			copySelectedEvents(timeline, selectionState);
		}
		if (ImGui.menuItem("Paste", "Ctrl+V", false, hasClipboard)) {
			pasteClipboardEvents(timeline, selectionState, contextTimeSeconds);
		}
		if (ImGui.menuItem("Delete", "Del", false, hasSelection)) {
			deleteSelectedEvents(timeline, selectionState);
		}
		ImGui.separator();
		if (ImGui.menuItem("Properties", null, false, canOpenProperties)) {
			openPropertiesPopup(timeline, selectionState);
		}
		ImGui.endPopup();
	}

	private void openPropertiesPopup(Timeline timeline, SelectionState selectionState) {
		EventRef ref = resolvePropertiesEventRef(timeline, selectionState);
		if (ref == null || ref.event == null) return;
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

	private void renderPropertiesPopup(Timeline timeline) {
		if (!ImGui.beginPopup(POPUP_EVENT_PROPERTIES)) return;
		EventRef ref = findEventRef(timeline, propertiesEventId);
		if (ref == null || ref.event == null) {
			ImGui.textDisabled("Event no longer exists.");
			if (ImGui.button("Close")) ImGui.closeCurrentPopup();
			ImGui.endPopup();
			return;
		}

		ImGui.text("Event ID: " + ref.event.getId());
		ImGui.text("Type: " + ref.event.getType().name());
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
			for (String key : keys) {
				ImString buf = propertiesParamBuffers.computeIfAbsent(key, k -> new ImString(256));
				Boolean asNumber = propertiesParamAsNumber.computeIfAbsent(key,
					k -> ref.event.getParameters().get(k) instanceof Number);
				ImGui.text(key);
				ImGui.sameLine();
				ImGui.setNextItemWidth(170f);
				ImGui.inputText("##param_" + key, buf);
				ImGui.sameLine();
				boolean numberFlag = asNumber != null && asNumber;
				if (ImGui.checkbox("Number##param_type_" + key, numberFlag)) {
					propertiesParamAsNumber.put(key, !numberFlag);
				}
			}
		}

		if (ImGui.button("Apply")) {
			String raw = propertiesTimeBuffer.get();
			try {
				double t = Math.max(0, Double.parseDouble(raw.trim()));
				ref.event.setTimeSeconds(t);
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
		ImGui.sameLine();
		if (ImGui.button("Close")) {
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

	private static HitResult hitContentAtMouse(Timeline timeline, TimelineViewState viewState,
			TimelineLayout layout, float mx, float my) {
		for (int i = 0; i < layout.getInteractiveRowCount() && i < INTERACTIVE_TRACK_IDS.length; i++) {
			int logicalRow = TimelineLayout.INTERACTIVE_ROW_INDICES[i];
			if (!layout.isRowVisible(logicalRow)) continue;
			float rowScreenY = layout.getRowScreenY(logicalRow);
			HitResult hit = HitTestSystem.hitTestTrackContent(
				timeline,
				INTERACTIVE_TRACK_IDS[i],
				mx,
				my,
				layout.contentLeft,
				rowScreenY,
				TimelineLayout.ROW_HEIGHT,
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

	private void pasteClipboardEvents(Timeline timeline, SelectionState selectionState, double anchorTimeSeconds) {
		if (timeline == null || selectionState == null) return;
		if (clipboardEvents.isEmpty()) return;

		double baseTime = clipboardEvents.get(0).timeSeconds;
		double maxTime = clipboardEvents.get(clipboardEvents.size() - 1).timeSeconds;
		double span = Math.max(0.2, maxTime - baseTime);
		selectionState.clearEvents();
		Set<String> dirtyTracks = new HashSet<>();
		Map<String, Clip> targetClipsByTrack = new HashMap<>();

		for (ClipboardEvent src : clipboardEvents) {
			double newTime = Math.max(0, anchorTimeSeconds + (src.timeSeconds - baseTime));
			Track targetTrack = resolvePasteTargetTrack(timeline, src);
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

	private Track resolvePasteTargetTrack(Timeline timeline, ClipboardEvent src) {
		if (contextTrackId != null) {
			Track t = timeline.getTrack(contextTrackId);
			if (t != null) return t;
		}
		return timeline.getTrack(src.trackId);
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

	private static void deleteSelectedEvents(Timeline timeline, SelectionState selectionState) {
		if (timeline == null || selectionState == null) return;
		if (selectionState.getSelectedEvents().isEmpty()) return;

		List<String> eventIds = new ArrayList<>(selectionState.getSelectedEvents());
		for (Track track : timeline.getTracks()) {
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
