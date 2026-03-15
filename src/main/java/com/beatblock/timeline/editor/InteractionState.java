package com.beatblock.timeline.editor;

/**
 * 交互状态：当前模式、按下时的鼠标位置、正在操作的对象 ID。
 */
public class InteractionState {

	private InteractionMode mode = InteractionMode.NONE;
	private float mouseStartX;
	private float mouseStartY;
	private String activeEventId;
	private String activeClipId;
	private String activeTrackId;
	private boolean resizeLeft; // RESIZE_CLIP 时 true=左边缘

	public InteractionMode getMode() {
		return mode;
	}

	public void setMode(InteractionMode mode) {
		this.mode = mode != null ? mode : InteractionMode.NONE;
	}

	public float getMouseStartX() { return mouseStartX; }
	public float getMouseStartY() { return mouseStartY; }
	public void setMouseStart(float x, float y) {
		mouseStartX = x;
		mouseStartY = y;
	}

	public String getActiveEventId() { return activeEventId; }
	public void setActiveEventId(String id) { activeEventId = id; }
	public String getActiveClipId() { return activeClipId; }
	public void setActiveClipId(String id) { activeClipId = id; }
	public String getActiveTrackId() { return activeTrackId; }
	public void setActiveTrackId(String id) { activeTrackId = id; }

	public boolean isResizeLeft() { return resizeLeft; }
	public void setResizeLeft(boolean left) { resizeLeft = left; }

	public void clearActive() {
		activeEventId = null;
		activeClipId = null;
		activeTrackId = null;
	}
}
