package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.util.SnapSystem;
import com.beatblock.timeline.util.TimeUtils;

/**
 * 拖拽逻辑：事件时间更新、可选吸附。
 */
public final class DragController {

	private DragController() {}

	/**
	 * 将指定事件的时间设为 newTimeSeconds，根据 toolbarState 吸附后再夹到 [0, duration]。
	 */
	public static void dragEvent(Timeline timeline, String trackId, String clipId, String eventId,
			double newTimeSeconds, double duration,
			TimelineToolbarState toolbarState, TimelineViewState viewState,
			InteractionState interactionState) {
		double t = computeEventDragTime(
			newTimeSeconds, eventId, duration, timeline, toolbarState, viewState, interactionState
		);
		if (Double.isNaN(t)) return;

		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent e = clip.getEvent(eventId);
		if (e != null) {
			e.setTimeSeconds(t);
			if (isAnimationTrack(trackId)) {
				timeline.markAnimationEventsDirty(trackId);
			}
		}
	}

	private static boolean isAnimationTrack(String trackId) {
		return Timeline.TRACK_ID_ANIMATION_BLOCK.equals(trackId)
			|| Timeline.TRACK_ID_ANIMATION_AUTO.equals(trackId)
			|| Timeline.TRACK_ID_BUILD_REVERSE.equals(trackId)
			|| Timeline.isBlockAnimationFeatureTrackId(trackId);
	}

	/**
	 * 计算吸附后的目标时间，不修改时间线。
	 */
	public static double computeEventDragTime(
		double newTimeSeconds,
		String eventId,
		double duration,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState,
		InteractionState interactionState
	) {
		if (timeline == null) return Double.NaN;

		SnapSystem.SnapResult snapped = applySnapWithGuides(newTimeSeconds, eventId, timeline, toolbarState, viewState);
		if (interactionState != null) {
			interactionState.setAlignmentGuideTimes(snapped.guideTimes());
		}
		return Math.max(0, Math.min(snapped.timeSeconds(), duration > 0 ? duration : Double.MAX_VALUE));
	}

	/**
	 * 拖动音频片段到新位置，返回实际生效的新起始时间（吸附 + 夹取后）。
	 */
	public static double dragClip(Timeline timeline, String trackId, String clipId,
			double mouseTimeSeconds, double dragInitialMouseTime,
			double dragInitialClipStart, double clipDuration,
			double maxDuration, TimelineToolbarState toolbarState, TimelineViewState viewState,
			InteractionState interactionState) {
		if (timeline == null || trackId == null || clipId == null) return dragInitialClipStart;
		Track track = timeline.getTrack(trackId);
		if (track == null) return dragInitialClipStart;
		Clip clip = track.getClip(clipId);
		if (clip == null) return dragInitialClipStart;

		double rawNewStart = dragInitialClipStart + (mouseTimeSeconds - dragInitialMouseTime);
		SnapSystem.SnapResult snapped = applySnapWithGuides(rawNewStart, null, timeline, toolbarState, viewState);
		if (interactionState != null) {
			interactionState.setAlignmentGuideTimes(snapped.guideTimes());
		}
		double clampedStart = Math.max(0.0, snapped.timeSeconds());

		clip.setStartTimeSeconds(clampedStart);
		clip.setEndTimeSeconds(clampedStart + clipDuration);
		return clampedStart;
	}

	/** 供片段边缘拖拽等复用：与 {@link #dragEvent} 相同的吸附规则。 */
	public static double snapTime(double timeSeconds, String excludeEventId, Timeline timeline,
			TimelineToolbarState toolbarState, TimelineViewState viewState) {
		return snapTime(timeSeconds, excludeEventId, timeline, toolbarState, viewState, null);
	}

	public static double snapTime(double timeSeconds, String excludeEventId, Timeline timeline,
			TimelineToolbarState toolbarState, TimelineViewState viewState,
			InteractionState interactionState) {
		SnapSystem.SnapResult result = applySnapWithGuides(timeSeconds, excludeEventId, timeline, toolbarState, viewState);
		if (interactionState != null) {
			interactionState.setAlignmentGuideTimes(result.guideTimes());
		}
		return result.timeSeconds();
	}

	private static SnapSystem.SnapResult applySnapWithGuides(double timeSeconds, String excludeEventId, Timeline timeline,
			TimelineToolbarState toolbarState, TimelineViewState viewState) {
		if (toolbarState == null) return SnapSystem.SnapResult.unchanged(timeSeconds);
		boolean grid = toolbarState.isSnapToGrid();
		boolean beat = toolbarState.isSnapToBeat();
		boolean magnet = toolbarState.isMagnetSnap();
		if (!grid && !beat && !magnet) return SnapSystem.SnapResult.unchanged(timeSeconds);

		double gridStep = 0;
		if (grid && viewState != null) {
			gridStep = TimeUtils.gridStep(
				viewState.getViewStartTimeSeconds(),
				viewState.getViewEndTimeSeconds(),
				viewState.getZoom());
		}
		return SnapSystem.snapWithGuides(timeSeconds, timeline, grid, gridStep, beat, timeline.getBpm(), magnet, excludeEventId);
	}
}
