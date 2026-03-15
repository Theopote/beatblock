package com.beatblock.timeline.editor;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

/**
 * 统一 HitTest：根据屏幕坐标返回 HitResult（EVENT / CLIP / TRACK / TIME_HEADER / EMPTY）。
 */
public final class TimelineHitTest {

	/** 时间标尺区域判定：contentLeftX 为内容区左侧，rulerHeight 为标尺高度 */
	public static HitResult hitTestTimeRuler(float mouseX, float mouseY, float contentLeftX, float contentTopY, float rulerHeight, float contentWidth, TimelineViewState viewState) {
		if (mouseX < contentLeftX || mouseX > contentLeftX + contentWidth) return HitResult.empty();
		if (mouseY >= contentTopY && mouseY < contentTopY + rulerHeight) {
			double t = viewState.screenToTime(mouseX - contentLeftX);
			return HitResult.timeHeader(t);
		}
		return HitResult.empty();
	}

	/**
	 * 在轨道内容区检测：是否点到某条轨道的某个 Event 或 Clip。
	 * rowY 为当前行 Y，rowHeight 为行高，contentLeftX 与 contentWidth 为内容区。
	 */
	public static HitResult hitTestTrackContent(Timeline timeline, String trackId, float mouseX, float mouseY, float contentLeftX, float rowY, float rowHeight, float contentWidth, TimelineViewState viewState) {
		if (timeline == null || trackId == null) return HitResult.empty();
		if (mouseY < rowY || mouseY > rowY + rowHeight || mouseX < contentLeftX) return HitResult.empty();
		Track track = timeline.getTrack(trackId);
		if (track == null) return HitResult.track(trackId);
		float localX = mouseX - contentLeftX;
		double timeAtMouse = viewState.screenToTime(localX);
		for (Clip clip : track.getClips()) {
			double start = clip.getStartTimeSeconds();
			double end = clip.getEndTimeSeconds();
			float x0 = viewState.timeToScreen(start);
			float x1 = viewState.timeToScreen(end);
			if (localX >= x0 && localX <= x1) {
				for (TimelineEvent e : clip.getEvents()) {
					float ex = viewState.timeToScreen(e.getTimeSeconds());
					if (Math.abs(localX - ex) <= 8f) {
						return HitResult.event(trackId, clip.getId(), e.getId(), e.getTimeSeconds());
					}
				}
				return HitResult.clip(trackId, clip.getId());
			}
		}
		return HitResult.track(trackId);
	}
}
