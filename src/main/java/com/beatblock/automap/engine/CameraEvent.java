package com.beatblock.automap.engine;

/**
 * 自动镜头事件：时间点 + 动作，用于在时间线插入摄像机关键帧。
 */
public final class CameraEvent {

	private final double timeSeconds;
	private final CameraAction action;

	public CameraEvent(double timeSeconds, CameraAction action) {
		this.timeSeconds = Math.max(0, timeSeconds);
		this.action = action != null ? action : CameraAction.HOLD;
	}

	public double getTimeSeconds() { return timeSeconds; }
	public CameraAction getAction() { return action; }
}
