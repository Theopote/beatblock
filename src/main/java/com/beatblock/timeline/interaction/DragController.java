package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.util.SnapSystem;
import com.beatblock.timeline.util.TimeUtils;

/**
 * 拖拽逻辑：事件时间更新、可选吸附。
 */
public final class DragController {

	/**
	 * 将指定事件的时间设为 newTimeSeconds，根据 toolbarState 吸附后再夹到 [0, duration]。
	 *
	 * @param toolbarState 工具栏状态（可为 null，则不吸附）
	 * @param viewState    视图状态（用于计算当前网格步长，可为 null）
	 */
	public static void dragEvent(Timeline timeline, String trackId, String clipId, String eventId,
			double newTimeSeconds, double duration,
			TimelineToolbarState toolbarState, TimelineViewState viewState) {
		if (timeline == null || trackId == null || clipId == null || eventId == null) return;

		double snapped = applySnap(newTimeSeconds, timeline, toolbarState, viewState);
		double t = Math.max(0, Math.min(snapped, duration > 0 ? duration : Double.MAX_VALUE));

		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent e = clip.getEvent(eventId);
		if (e != null) e.setTimeSeconds(t);
	}

	private static double applySnap(double timeSeconds, Timeline timeline,
			TimelineToolbarState toolbarState, TimelineViewState viewState) {
		if (toolbarState == null) return timeSeconds;
		boolean grid = toolbarState.isSnapToGrid();
		boolean beat = toolbarState.isSnapToBeat();
		if (!grid && !beat) return timeSeconds;

		double gridStep = 0;
		if (grid && viewState != null) {
			gridStep = TimeUtils.gridStep(
				viewState.getViewStartTimeSeconds(),
				viewState.getViewEndTimeSeconds(),
				viewState.getZoom());
		}
		return SnapSystem.snap(timeSeconds, timeline, grid, gridStep, beat, timeline.getBpm());
	}
}
