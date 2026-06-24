package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.HitType;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import java.util.List;
import java.util.Map;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.DRAG_THRESHOLD_PX;

/** 音频/摄像机片段拖动：按下、拖动、释放提交。 */
public final class TimelineClipDragCoordinator {

	private TimelineClipDragCoordinator() {}

	/**
	 * 纯片段命中时开始拖动。返回非 null 表示已接管交互。
	 */
	public static TimelineClipDragSession tryBeginFromClipHit(
		Timeline timeline,
		HitResult hit,
		Clip hitClip,
		InteractionState interactionState,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mx,
		float my
	) {
		if (timeline == null || hit == null || hitClip == null || hit.getEventId() != null) return null;
		if (hit.getHitType() != HitType.CLIP) return null;

		if (Timeline.TRACK_ID_AUDIO.equals(hit.getTrackId())) {
			return TimelineClipDragSession.beginAudioClipDrag(
				timeline, hit.getTrackId(), hit.getClipId(), hitClip,
				interactionState, viewState, layout, mx, my);
		}
		if (Timeline.TRACK_ID_CAMERA.equals(hit.getTrackId())) {
			return TimelineClipDragSession.beginCameraClipDrag(
				timeline, hit.getTrackId(), hit.getClipId(), hitClip,
				interactionState, viewState, layout, mx, my);
		}
		return null;
	}

	public static void applyDuringDrag(
		Timeline timeline,
		TimelineClipDragSession session,
		InteractionState interactionState,
		TimelineViewState viewState,
		TimelineLayout layout,
		TimelineToolbarState toolbarState,
		TimelineTrackListState trackListState,
		double duration,
		float mx,
		Runnable seekPlayback
	) {
		if (timeline == null || session == null || interactionState == null || viewState == null || layout == null) {
			return;
		}
		if (interactionState.getActiveClipId() == null || interactionState.getActiveTrackId() == null) return;
		if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, interactionState.getActiveTrackId())) {
			return;
		}

		double mouseTime = viewState.screenToTime(mx - layout.contentLeft);
		double clipDuration = session.initialEnd() - session.initialStart();
		double newStart = DragController.dragClip(
			timeline,
			interactionState.getActiveTrackId(),
			interactionState.getActiveClipId(),
			mouseTime,
			session.initialMouseTime(),
			session.initialStart(),
			clipDuration,
			duration,
			toolbarState,
			viewState,
			interactionState
		);
		double actualDelta = newStart - session.initialStart();
		timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), newStart + clipDuration));

		if (Timeline.TRACK_ID_CAMERA.equals(interactionState.getActiveTrackId())) {
			Track cameraTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
			Clip cameraClip = cameraTrack != null ? cameraTrack.getClip(interactionState.getActiveClipId()) : null;
			if (cameraClip != null) {
				for (TimelineEvent se : cameraClip.getEvents()) {
					Double orig = session.cameraClipEventOriginalTimes().get(se.getId());
					if (orig != null) {
						se.setTimeSeconds(Math.max(0.0, orig + actualDelta));
					}
				}
			}
			if (seekPlayback != null) seekPlayback.run();
			return;
		}

		for (Track track : timeline.getTracks()) {
			if (Timeline.TRACK_ID_AUDIO.equals(track.getId())) continue;
			boolean dirtied = false;
			for (Clip clip : track.getClips()) {
				for (TimelineEvent se : clip.getEvents()) {
					Double orig = session.linkedEventOriginalTimes().get(se.getId());
					if (orig != null) {
						se.setTimeSeconds(Math.max(0.0, orig + actualDelta));
						dirtied = true;
					}
				}
			}
			if (dirtied) timeline.markAnimationEventsDirty(track.getId());
		}

		if (!session.featureEventSnapshot().isEmpty()) {
			double featureMoveStart = session.initialStart();
			double featureMoveEnd = session.initialEnd();
			for (Map.Entry<String, FeatureTrack> entry : timeline.getFeatureTracks().entrySet()) {
				List<double[]> snap = session.featureEventSnapshot().get(entry.getKey());
				if (snap == null) continue;
				FeatureTrack featureTrack = entry.getValue();
				featureTrack.clear();
				for (double[] pair : snap) {
					double originalTime = pair[0];
					double shiftedTime = originalTime;
					if (originalTime >= featureMoveStart && originalTime <= featureMoveEnd) {
						shiftedTime = Math.max(0.0, originalTime + actualDelta);
					}
					featureTrack.addEvent(new FeatureEvent(shiftedTime, (float) pair[1]));
				}
			}
		}

		if (seekPlayback != null) seekPlayback.run();
	}

	public static void finishOnMouseRelease(
		Timeline timeline,
		TimelineEditor editor,
		TimelineClipDragSession session,
		InteractionState interactionState,
		SelectionState selectionState,
		float mx,
		float my
	) {
		if (session == null || interactionState == null || interactionState.getActiveClipId() == null) return;

		float dx = mx - interactionState.getMouseStartX();
		float dy = my - interactionState.getMouseStartY();
		boolean belowThreshold = dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX;
		if (belowThreshold) {
			if (selectionState != null) {
				selectionState.clearClips();
				selectionState.selectClip(interactionState.getActiveClipId());
			}
			TimelineDragCommitSupport.revertClipDrag(timeline, session.undoSnapshot());
		} else {
			TimelineDragCommitSupport.commitClipDrag(timeline, editor, session.undoSnapshot());
		}
		session.clear();
	}
}
