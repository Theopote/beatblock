package com.beatblock.timeline.editor;

/**
 * 视图系统：时间线缩放与滚动，时间 ↔ 屏幕坐标转换。
 * screenX = (time - viewStartTime) * zoom，content 区域左上为 (0,0)。
 */
public class TimelineViewState {

	private double viewStartTimeSeconds;
	private double viewEndTimeSeconds;
	private float zoom;       // 像素/秒
	private float scrollY;

	public TimelineViewState() {
		this.viewStartTimeSeconds = 0;
		this.viewEndTimeSeconds = 60;
		this.zoom = 10f;
		this.scrollY = 0;
	}

	public double getViewStartTimeSeconds() {
		return viewStartTimeSeconds;
	}

	public void setViewStartTimeSeconds(double viewStartTimeSeconds) {
		this.viewStartTimeSeconds = Math.max(0, viewStartTimeSeconds);
	}

	public double getViewEndTimeSeconds() {
		return viewEndTimeSeconds;
	}

	public void setViewEndTimeSeconds(double viewEndTimeSeconds) {
		this.viewEndTimeSeconds = Math.max(viewStartTimeSeconds, viewEndTimeSeconds);
	}

	/** 根据可见区域宽度与时长设置 zoom，使整段刚好可见 */
	public void fitToDuration(double durationSeconds, float contentWidthPx) {
		if (durationSeconds <= 0 || contentWidthPx <= 0) return;
		viewStartTimeSeconds = 0;
		viewEndTimeSeconds = durationSeconds;
		zoom = contentWidthPx / (float) durationSeconds;
	}

	/** 设置可见时间范围并推导 zoom（需 contentWidthPx） */
	public void setVisibleRange(double startSeconds, double endSeconds, float contentWidthPx) {
		viewStartTimeSeconds = Math.max(0, startSeconds);
		viewEndTimeSeconds = Math.max(viewStartTimeSeconds, endSeconds);
		if (contentWidthPx > 0 && viewEndTimeSeconds > viewStartTimeSeconds) {
			zoom = contentWidthPx / (float) (viewEndTimeSeconds - viewStartTimeSeconds);
		}
	}

	public float getZoom() {
		return zoom;
	}

	public void setZoom(float zoom) {
		this.zoom = Math.max(0.5f, Math.min(1000f, zoom));
	}

	/** 以鼠标所在时间为锚点缩放，保持该时间在屏幕上的位置不变 */
	public void zoomAt(double anchorTimeSeconds, float anchorScreenX, float newZoom) {
		if (newZoom <= 0) return;
		double prevTimeAtAnchor = screenToTime(anchorScreenX);
		this.zoom = Math.max(0.5f, Math.min(1000f, newZoom));
		viewStartTimeSeconds += prevTimeAtAnchor - screenToTime(anchorScreenX);
		viewStartTimeSeconds = Math.max(0, viewStartTimeSeconds);
	}

	public float getScrollY() {
		return scrollY;
	}

	public void setScrollY(float scrollY) {
		this.scrollY = Math.max(0, scrollY);
	}

	public void addScrollY(float deltaY) {
		this.scrollY = Math.max(0, this.scrollY + deltaY);
	}

	/** 时间 → 屏幕 X（相对 content 区域左侧） */
	public float timeToScreen(double timeSeconds) {
		return (float) (timeSeconds - viewStartTimeSeconds) * zoom;
	}

	/** 屏幕 X → 时间 */
	public double screenToTime(float screenX) {
		return screenX / zoom + viewStartTimeSeconds;
	}

	/** 视图平移：deltaScreenX 为正表示内容向左移 */
	public void pan(float deltaScreenX) {
		viewStartTimeSeconds -= deltaScreenX / zoom;
		viewStartTimeSeconds = Math.max(0, viewStartTimeSeconds);
	}
}
