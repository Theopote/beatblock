package com.beatblock.timeline.interaction;

import com.beatblock.BeatBlockClient;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editing.AnimationEventSnapshot;
import com.beatblock.timeline.editing.GenericEventPropertiesEditor;
import com.beatblock.timeline.editing.TimelineEventEditActions;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.PARAM_INPUT_BUFFER_SIZE;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_DELETE_CONFIRM;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_EVENT_CONTEXT;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_EVENT_PROPERTIES;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_MARKER_CONTEXT;

/** 时间线 ImGui 右键菜单与属性/删除/标记弹窗。 */
public final class TimelineInteractionPopups {

	private TimelineInteractionPopups() {}

	public static void renderMarkerOnly(
		Timeline timeline,
		TimelineClock clock,
		TimelineInteractionPopupHost host
	) {
		renderMarkerContextPopup(timeline, clock, host);
	}

	public static void renderAll(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		TimelineClock clock,
		TimelineInteractionPopupHost host
	) {
		renderContextMenu(timeline, selectionState, trackListState, host);
		renderPropertiesPopup(timeline, trackListState, host);
		renderMarkerContextPopup(timeline, clock, host);
		renderDeleteConfirmPopup(timeline, selectionState, trackListState, host);
	}

	public static void openPropertiesPopup(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		TimelineInteractionPopupHost host
	) {
		TimelineEventRef ref = host.resolvePropertiesEventRef(timeline, selectionState);
		if (ref == null || ref.event() == null) return;
		if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, ref.track().getId())) return;
		TimelineInteractionPopupState state = host.popupState();
		state.propertiesEventId = ref.event().getId();
		state.propertiesTimeBuffer.set(String.format(java.util.Locale.ROOT, "%.6f", ref.event().getTimeSeconds()));
		loadPropertiesParameterBuffers(state, ref.event());
		state.propertiesError = null;
		ImGui.openPopup(POPUP_EVENT_PROPERTIES);
	}

	private static void renderContextMenu(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		TimelineInteractionPopupHost host
	) {
		if (!ImGui.beginPopup(POPUP_EVENT_CONTEXT)) return;
		TimelineInteractionPopupState state = host.popupState();
		boolean requestDeleteConfirmPopup = false;
		boolean hasSelection = selectionState != null
			&& (!selectionState.getSelectedEvents().isEmpty() || !selectionState.getSelectedClips().isEmpty());
		boolean canDeleteSelection = TimelineInteractionDeleteSupport.hasDeletableSelection(timeline, selectionState, trackListState);
		boolean canDeleteContextClip = host.canDeleteContextClip(timeline, trackListState);
		BeatBlockClient.LOGGER.info(String.format(
			"[TimelineInteraction.renderContextMenu] Menu opened: contextClipId=%s, contextTrackId=%s, canDeleteSelection=%s, canDeleteContextClip=%s",
			state.contextClipId, state.contextTrackId, canDeleteSelection, canDeleteContextClip
		));
		boolean canDeleteAny = canDeleteSelection || canDeleteContextClip;
		boolean hasClipboard = !host.clipboardEvents().isEmpty();
		TimelineEventRef propertiesRef = host.resolvePropertiesEventRef(timeline, selectionState);
		boolean canOpenProperties = propertiesRef != null
			&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, propertiesRef.track().getId());
		if (ImGui.menuItem("Copy", "Ctrl+C", false, hasSelection)) {
			host.copySelectedEvents(timeline, selectionState);
		}
		if (ImGui.menuItem("Paste", "Ctrl+V", false, hasClipboard)) {
			host.pasteClipboardEvents(timeline, selectionState, state.contextTimeSeconds, trackListState);
		}
		if (timeline != null && Timeline.TRACK_ID_CAMERA.equals(state.contextTrackId)
				&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, state.contextTrackId)) {
			if (state.contextClipId != null) {
				if (ImGui.checkbox("显示路径##camCtxPathVis", state.contextCameraShowPath)) {
					CameraPathMetadata.setPathVisible(timeline, state.contextClipId, state.contextCameraShowPath.get());
				}
			}
			TimelineEventRef ctxEv = state.propertiesEventId != null
				? TimelineEventRefs.find(timeline, state.propertiesEventId) : null;
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
			if (state.contextClipId != null) {
				Track camT = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
				Clip ctxClip = camT != null ? camT.getClip(state.contextClipId) : null;
				if (ctxClip != null) {
					TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(ctxClip);
					CameraSegmentKind k = seg != null
						? CameraSegmentKind.fromParam(seg.getParameters().get("kind"))
						: CameraSegmentKind.PATH;
					canAddPathKf = k == CameraSegmentKind.PATH;
				}
			}
			if (ImGui.menuItem("添加路径关键帧（当前位置）##camAddKfCtx", null, false, canAddPathKf)) {
				CameraKeyframeActions.addKeyframeAtTime(timeline, state.contextTimeSeconds);
			}
			if (ImGui.beginMenu("添加镜头片段")) {
				double[] a = TimelineContentHitTest.readCameraAnchorFive();
				if (ImGui.menuItem("自定义路径（关键帧）")) {
					CameraTrackFactory.addPathSegment(timeline, state.contextTimeSeconds, a[0], a[1], a[2], a[3], a[4]);
				}
				if (ImGui.menuItem("推进（Dolly）")) {
					CameraTrackFactory.addDollySegment(timeline, state.contextTimeSeconds, a[0], a[1], a[2], a[3], 8.0);
				}
				if (ImGui.menuItem("环绕（Orbit）")) {
					double[] o = TimelineContentHitTest.readOrbitParamsFromView();
					CameraTrackFactory.addOrbitSegment(timeline, state.contextTimeSeconds,
						o[0], o[1], o[2], o[3], o[4], o[5], o[6]);
				}
				if (ImGui.menuItem("升降（Crane）")) {
					CameraTrackFactory.addCraneSegment(timeline, state.contextTimeSeconds, a[0], a[1], a[2], a[3], a[4], 6.0);
				}
				if (ImGui.menuItem("节拍震动（Shake）")) {
					CameraTrackFactory.addShakeSegment(timeline, state.contextTimeSeconds, a[0], a[1], a[2], a[3], a[4]);
				}
				ImGui.endMenu();
			}
		}
		String deleteLabel = canDeleteAny ? "Delete" : "Delete (Locked)";
		if (ImGui.menuItem(deleteLabel, "Del", false, canDeleteAny)) {
			if (selectionState != null && canDeleteContextClip && state.contextClipId != null) {
				selectionState.clearEvents();
				selectionState.clearClips();
				selectionState.selectClip(state.contextClipId);
			} else if (selectionState != null && !hasSelection && state.contextClipId != null) {
				selectionState.clearEvents();
				selectionState.clearClips();
				selectionState.selectClip(state.contextClipId);
			}
			requestDeleteConfirmPopup = true;
			ImGui.closeCurrentPopup();
		}
		ImGui.separator();
		String propertiesLabel = propertiesRef != null && !canOpenProperties
			? "Properties (Locked)"
			: "Properties";
		if (ImGui.menuItem(propertiesLabel, null, false, canOpenProperties)) {
			openPropertiesPopup(timeline, selectionState, trackListState, host);
		}
		ImGui.endPopup();
		if (requestDeleteConfirmPopup) {
			ImGui.openPopup(POPUP_DELETE_CONFIRM);
		}
	}

	private static void renderDeleteConfirmPopup(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		TimelineInteractionPopupHost host
	) {
		if (!ImGui.beginPopupModal(POPUP_DELETE_CONFIRM)) return;
		int selectedEventCount = selectionState != null ? selectionState.getSelectedEvents().size() : 0;
		int selectedClipCount = selectionState != null ? selectionState.getSelectedClips().size() : 0;
		boolean hasDeletable = TimelineInteractionDeleteSupport.hasDeletableSelection(timeline, selectionState, trackListState);
		boolean canDeleteCtxClip = host.canDeleteContextClip(timeline, trackListState);
		boolean canDelete = hasDeletable || canDeleteCtxClip;

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
			if (canDelete) {
				host.deleteSelectedEntries(timeline, selectionState, trackListState);
			}
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button("Cancel##timelineDeleteCancel", 120f, 0f)) {
			ImGui.closeCurrentPopup();
		}
		ImGui.endPopup();
	}

	private static void renderMarkerContextPopup(
		Timeline timeline,
		TimelineClock clock,
		TimelineInteractionPopupHost host
	) {
		if (!ImGui.beginPopup(POPUP_MARKER_CONTEXT)) return;
		TimelineInteractionPopupState state = host.popupState();
		int markerIndex = timeline != null ? timeline.findMarkerIndexById(state.contextMarkerId) : -1;
		if (timeline == null || markerIndex < 0 || markerIndex >= timeline.getMarkers().size()) {
			state.contextMarkerId = null;
			ImGui.textDisabled("Marker no longer exists.");
			if (ImGui.button("Close##markerPopupClose")) ImGui.closeCurrentPopup();
			ImGui.endPopup();
			return;
		}

		TimelineMarker marker = timeline.getMarkers().get(markerIndex);
		ImGui.text("Marker");
		ImGui.textDisabled(String.format(java.util.Locale.ROOT, "%.3fs", marker.getTimeSeconds()));
		ImGui.setNextItemWidth(180f);
		ImGui.inputText("Name##markerRename", state.markerNameBuffer);

		if (ImGui.button("Jump##markerJump")) {
			if (clock != null) host.seekClockAndMusic(clock, marker.getTimeSeconds());
		}
		ImGui.sameLine();
		if (ImGui.button("Rename##markerApply")) {
			String newName = state.markerNameBuffer.get() == null ? "" : state.markerNameBuffer.get().trim();
			timeline.updateMarker(state.contextMarkerId, marker.getTimeSeconds(), newName);
			state.contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button("Delete##markerDelete")) {
			timeline.removeMarker(state.contextMarkerId);
			state.contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button("Close##markerClose")) {
			state.contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.endPopup();
	}

	private static void renderPropertiesPopup(
		Timeline timeline,
		TimelineTrackListState trackListState,
		TimelineInteractionPopupHost host
	) {
		if (!ImGui.beginPopup(POPUP_EVENT_PROPERTIES)) return;
		TimelineInteractionPopupState state = host.popupState();
		TimelineEventRef ref = TimelineEventRefs.find(timeline, state.propertiesEventId);
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
			ImGui.beginDisabled();
		}
		ImGui.separator();
		ImGui.text("Time (seconds)");
		ImGui.setNextItemWidth(170f);
		ImGui.inputText("##eventTime", state.propertiesTimeBuffer);
		if (state.propertiesError != null) {
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, state.propertiesError);
		}

		if (!ref.event().getParameters().isEmpty()) {
			ImGui.separator();
			ImGui.text("Parameters");
			List<String> keys = new ArrayList<>(ref.event().getParameters().keySet());
			keys.sort(String::compareTo);
			List<String> removedKeys = new ArrayList<>();
			for (String key : keys) {
				ImString buf = state.propertiesParamBuffers.computeIfAbsent(key, k -> new ImString(256));
				Boolean asNumber = state.propertiesParamAsNumber.computeIfAbsent(key,
					k -> ref.event().getParameters().get(k) instanceof Number);
				ImGui.text(key);
				ImGui.sameLine();
				ImGui.setNextItemWidth(130f);
				ImGui.inputText("##param_" + key, buf);
				ImGui.sameLine();
				boolean numberFlag = asNumber;
				if (ImGui.checkbox("Number##param_type_" + key, numberFlag)) {
					state.propertiesParamAsNumber.put(key, !numberFlag);
				}
				ImGui.sameLine();
				if (ImGui.smallButton("X##param_remove_" + key)) {
					removedKeys.add(key);
				}
			}
			for (String key : removedKeys) {
				state.propertiesParamBuffers.remove(key);
				state.propertiesParamAsNumber.remove(key);
			}
		}

		ImGui.separator();
		ImGui.text("Add Parameter");
		ImGui.setNextItemWidth(120f);
		ImGui.inputText("Key##param_add_key", state.propertiesNewParamKey);
		ImGui.sameLine();
		ImGui.setNextItemWidth(120f);
		ImGui.inputText("Value##param_add_value", state.propertiesNewParamValue);
		ImGui.sameLine();
		if (ImGui.checkbox("Number##param_add_type", state.propertiesNewParamAsNumber)) {
			state.propertiesNewParamAsNumber = !state.propertiesNewParamAsNumber;
		}
		ImGui.sameLine();
		if (ImGui.button("Add/Update##param_add")) {
			String key = state.propertiesNewParamKey.get() != null ? state.propertiesNewParamKey.get().trim() : "";
			if (key.isEmpty()) {
				state.propertiesError = "Parameter key cannot be empty";
			} else {
				ImString valueBuf = state.propertiesParamBuffers.computeIfAbsent(key, k -> new ImString(PARAM_INPUT_BUFFER_SIZE));
				valueBuf.set(state.propertiesNewParamValue.get() == null ? "" : state.propertiesNewParamValue.get());
				state.propertiesParamAsNumber.put(key, state.propertiesNewParamAsNumber);
				state.propertiesError = null;
			}
		}

		if (ImGui.button("Apply") || applyRequested) {
			try {
				double t = Double.parseDouble(state.propertiesTimeBuffer.get().trim());
				Map<String, String> paramRaw = new HashMap<>();
				for (Map.Entry<String, ImString> entry : state.propertiesParamBuffers.entrySet()) {
					paramRaw.put(entry.getKey(), entry.getValue().get());
				}
				var result = GenericEventPropertiesEditor.buildUpdatedSnapshot(
					t,
					paramRaw,
					state.propertiesParamAsNumber,
					ref.clip().getStartTimeSeconds(),
					ref.clip().getEndTimeSeconds()
				);
				if (result instanceof GenericEventPropertiesEditor.Result.Err err) {
					state.propertiesError = err.message();
				} else {
					AnimationEventSnapshot after = ((GenericEventPropertiesEditor.Result.Ok) result).snapshot();
					AnimationEventSnapshot before = AnimationEventSnapshot.capture(ref.event(), ref.clip());
					TimelineEditor editor = host.timelineEditor();
					if (editor != null && TimelineEventEditActions.execute(
						timeline,
						editor.getCommandManager(),
						ref.track().getId(),
						ref.clip().getId(),
						ref.event().getId(),
						before,
						after
					)) {
						state.propertiesOriginalTime = String.format(java.util.Locale.ROOT, "%.6f", ref.event().getTimeSeconds());
						for (Map.Entry<String, ImString> entry : state.propertiesParamBuffers.entrySet()) {
							String key = entry.getKey();
							state.propertiesOriginalParamValues.put(key, entry.getValue().get());
							state.propertiesOriginalParamAsNumber.put(key, state.propertiesParamAsNumber.getOrDefault(key, false));
						}
						state.propertiesError = null;
					}
				}
			} catch (NumberFormatException ex) {
				state.propertiesError = "Invalid number in time/parameter";
			}
		}
		ImGui.sameLine();
		if (ImGui.button("Reset")) {
			resetPropertiesBuffers(state);
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

	private static void loadPropertiesParameterBuffers(TimelineInteractionPopupState state, TimelineEvent event) {
		state.propertiesParamBuffers.clear();
		state.propertiesParamAsNumber.clear();
		state.propertiesOriginalParamValues.clear();
		state.propertiesOriginalParamAsNumber.clear();
		state.propertiesNewParamKey.set("");
		state.propertiesNewParamValue.set("");
		state.propertiesNewParamAsNumber = false;
		if (event == null) return;
		state.propertiesOriginalTime = String.format(java.util.Locale.ROOT, "%.6f", event.getTimeSeconds());
		state.propertiesTimeBuffer.set(state.propertiesOriginalTime);
		for (Map.Entry<String, Object> entry : event.getParameters().entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			String text = value == null ? "" : String.valueOf(value);
			boolean asNumber = value instanceof Number;
			ImString buf = new ImString(256);
			buf.set(text);
			state.propertiesParamBuffers.put(key, buf);
			state.propertiesParamAsNumber.put(key, asNumber);
			state.propertiesOriginalParamValues.put(key, text);
			state.propertiesOriginalParamAsNumber.put(key, asNumber);
		}
	}

	private static void resetPropertiesBuffers(TimelineInteractionPopupState state) {
		state.propertiesTimeBuffer.set(state.propertiesOriginalTime != null ? state.propertiesOriginalTime : "0");
		for (Map.Entry<String, ImString> entry : state.propertiesParamBuffers.entrySet()) {
			String key = entry.getKey();
			entry.getValue().set(state.propertiesOriginalParamValues.getOrDefault(key, ""));
			state.propertiesParamAsNumber.put(key, state.propertiesOriginalParamAsNumber.getOrDefault(key, false));
		}
		state.propertiesError = null;
	}

	private static boolean containsSelectedAudioTrackClip(Timeline timeline, SelectionState selectionState) {
		if (timeline == null || selectionState == null || selectionState.getSelectedClips().isEmpty()) return false;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) return false;
		for (String clipId : selectionState.getSelectedClips()) {
			if (clipId != null && audioTrack.getClip(clipId) != null) return true;
		}
		return false;
	}
}
