package com.beatblock.timeline.editor;

/**
 * 框选矩形：屏幕坐标 start → end。
 */
public class SelectionBox {

	private boolean active;
	private float startX;
	private float startY;
	private float endX;
	private float endY;

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public float getStartX() { return startX; }
	public float getStartY() { return startY; }
	public float getEndX() { return endX; }
	public float getEndY() { return endY; }

	public void setStart(float x, float y) {
		startX = x;
		startY = y;
		endX = x;
		endY = y;
	}

	public void setEnd(float x, float y) {
		endX = x;
		endY = y;
	}

	public float getMinX() {
		return Math.min(startX, endX);
	}

	public float getMaxX() {
		return Math.max(startX, endX);
	}

	public float getMinY() {
		return Math.min(startY, endY);
	}

	public float getMaxY() {
		return Math.max(startY, endY);
	}

	public boolean contains(float x, float y) {
		float minX = getMinX(), maxX = getMaxX(), minY = getMinY(), maxY = getMaxY();
		return x >= minX && x <= maxX && y >= minY && y <= maxY;
	}
}
