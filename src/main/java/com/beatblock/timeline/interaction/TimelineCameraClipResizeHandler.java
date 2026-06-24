package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.editor.TimelineViewState;

import java.util.HashMap;
import java.util.Map;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.CAMERA_EDGE_HIT_PX;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.CAMERA_MIN_CLIP_DURATION;

/** 摄像机轨道片段边缘缩放：按下、拖动、Undo 快照。 */
public final class TimelineCameraClipResizeHandler {

	public record Session(
		double initialStart,
		double initialEnd,
		Map<String, Double> eventOrigTimes,
		ClipDragStateSnapshot undoSnapshot
	) {}

	private TimelineCameraClipResizeHandler() {}

	/**
	 * 在摄像机行上检测边缘命中并进入 {@code RESIZE_CLIP} 模式。
	 *
	 * @return 若已开始缩放则返回 session，否则 {@code null}
	 */
	public static Session tryBeginOnMouseClick(
		Timeline timeline,
		InteractionState interactionState,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineTrackListState trackListState,
		float mx,
		float my
	) {
		if (timeline == null || interactionState == null || layout == null || viewState == null) return null;
		if (!layout.contentContains(mx, my) || trackListState == null) return null;
		if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, Timeline.TRACK_ID_CAMERA)) return null;

		int camRow = layout.findRowAtScreenY(my);
		if (camRow != com.beatblock.timeline.rendering.TimelineTrackMeta.ROW_CAMERA || !layout.isRowVisible(camRow)) {
			return null;
		}
		float rowSy = layout.getRowScreenY(camRow);
		float rowH = layout.getRowHeight(camRow);
		CameraTrackHitTest.EdgeHit edge = CameraTrackHitTest.hitClipEdge(
			timeline, mx, my, rowSy, rowH, layout.contentLeft, layout.contentWidth, viewState, CAMERA_EDGE_HIT_PX);
		if (edge == null) return null;

		Track ct = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		Clip c = ct != null ? ct.getClip(edge.clipId()) : null;
		if (c == null) return null;

		Session session = beginSession(timeline, c, edge.clipId());
		interactionState.setMode(com.beatblock.timeline.editor.InteractionMode.RESIZE_CLIP);
		interactionState.setMouseStart(mx, my);
		interactionState.setActiveClipId(edge.clipId());
		interactionState.setActiveTrackId(Timeline.TRACK_ID_CAMERA);
		interactionState.setResizeLeft(edge.leftEdge());
		return session;
	}

	public static Session beginSession(Timeline timeline, Clip clip, String clipId) {
		double start = clip.getStartTimeSeconds();
		double end = clip.getEndTimeSeconds();
		Map<String, Double> origTimes = new HashMap<>();
		for (TimelineEvent se : clip.getEvents()) {
			origTimes.put(se.getId(), se.getTimeSeconds());
		}
		ClipDragStateSnapshot snapshot = ClipDragStateSnapshot.capture(
			timeline,
			Timeline.TRACK_ID_CAMERA,
			clipId,
			origTimes,
			Map.of()
		);
		return new Session(start, end, origTimes, snapshot);
	}

	public static void applyDuringDrag(
		Timeline timeline,
		Session session,
		InteractionState interactionState,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState,
		TimelineLayout layout,
		float mx
	) {
		if (timeline == null || session == null || interactionState == null || viewState == null || layout == null) {
			return;
		}
		Track ct = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		Clip c = ct != null ? ct.getClip(interactionState.getActiveClipId()) : null;
		if (c == null) return;

		double mouseT = viewState.screenToTime(mx - layout.contentLeft);
		double snapped = DragController.snapTime(mouseT, null, timeline, toolbarState, viewState, interactionState);
		if (interactionState.isResizeLeft()) {
			double newStart = Math.max(0.0, Math.min(snapped, session.initialEnd() - CAMERA_MIN_CLIP_DURATION));
			double delta = newStart - session.initialStart();
			c.setStartTimeSeconds(newStart);
			for (TimelineEvent se : c.getEvents()) {
				Double o = session.eventOrigTimes().get(se.getId());
				if (o != null) {
					se.setTimeSeconds(o + delta);
				}
			}
		} else {
			double newEnd = Math.max(session.initialStart() + CAMERA_MIN_CLIP_DURATION, snapped);
			c.setEndTimeSeconds(newEnd);
			for (TimelineEvent se : c.getEvents()) {
				if (se.getTimeSeconds() > newEnd) {
					se.setTimeSeconds(newEnd);
				}
			}
		}
		timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), c.getEndTimeSeconds()));
	}
}
