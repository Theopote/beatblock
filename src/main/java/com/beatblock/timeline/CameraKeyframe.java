package com.beatblock.timeline;

/**
 * 第 2 层 — 相机事件时间点（概念上的 CameraEvent 锚点）。
 * <p>
 * 具体姿态/路径由摄像机轨 {@link com.beatblock.timeline.Track} 上的
 * {@link com.beatblock.timeline.TimelineEvent} 与 clip 参数解析；
 * 第 3 层由 {@link com.beatblock.client.camera.TimelineCameraEvaluator} 采样应用。
 */
public final class CameraKeyframe {

	private final double timeSeconds;

	public CameraKeyframe(double timeSeconds) {
		this.timeSeconds = Math.max(0, timeSeconds);
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}
}
