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

		double snapped = applySnap(newTimeSeconds, eventId, timeline, toolbarState, viewState);
		double t = Math.max(0, Math.min(snapped, duration > 0 ? duration : Double.MAX_VALUE));

		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent e = clip.getEvent(eventId);
		if (e != null) {
			e.setTimeSeconds(t);
			// 事件时间变更后使动画缓存失效，避免渲染读取旧排序结果。
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	/**
	 * 拖动音频片段到新位置，返回实际生效的新起始时间（吸附 + 夹取后）。
	 * 调用方可用 (返回值 - dragInitialClipStart) 计算实际 delta，然后同步其他轨道上的关联事件。
	 *
	 * @param mouseTimeSeconds     当前鼠标对应的时间轴时间
	 * @param dragInitialMouseTime 拖拽开始时鼠标对应的时间轴时间
	 * @param dragInitialClipStart 拖拽开始时该片段的 startTimeSeconds
	 * @param clipDuration         片段时长（固定，拖拽中不变）
	 * @param maxDuration          时间轴总时长上限
	 */
	public static double dragClip(Timeline timeline, String trackId, String clipId,
			double mouseTimeSeconds, double dragInitialMouseTime,
			double dragInitialClipStart, double clipDuration,
			double maxDuration, TimelineToolbarState toolbarState, TimelineViewState viewState) {
		if (timeline == null || trackId == null || clipId == null) return dragInitialClipStart;
		Track track = timeline.getTrack(trackId);
		if (track == null) return dragInitialClipStart;
		Clip clip = track.getClip(clipId);
		if (clip == null) return dragInitialClipStart;

		double rawNewStart = dragInitialClipStart + (mouseTimeSeconds - dragInitialMouseTime);
		double snappedStart = applySnap(rawNewStart, null, timeline, toolbarState, viewState);
		double clampedStart = Math.max(0.0,
			Math.min(snappedStart, maxDuration > 0 ? maxDuration - clipDuration : rawNewStart));

		clip.setStartTimeSeconds(clampedStart);
		clip.setEndTimeSeconds(clampedStart + clipDuration);
		return clampedStart;
	}

	private static double applySnap(double timeSeconds, String excludeEventId, Timeline timeline,
			TimelineToolbarState toolbarState, TimelineViewState viewState) {
		if (toolbarState == null) return timeSeconds;
		boolean grid = toolbarState.isSnapToGrid();
		boolean beat = toolbarState.isSnapToBeat();
		boolean magnet = toolbarState.isMagnetSnap();
		if (!grid && !beat && !magnet) return timeSeconds;

		double gridStep = 0;
		if (grid && viewState != null) {
			gridStep = TimeUtils.gridStep(
				viewState.getViewStartTimeSeconds(),
				viewState.getViewEndTimeSeconds(),
				viewState.getZoom());
		}
		return SnapSystem.snap(timeSeconds, timeline, grid, gridStep, beat, timeline.getBpm(), magnet, excludeEventId);
	}
}
