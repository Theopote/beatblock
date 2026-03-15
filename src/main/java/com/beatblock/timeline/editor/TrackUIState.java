package com.beatblock.timeline.editor;

/**
 * 轨道 UI 状态：折叠、行高。
 */
public class TrackUIState {

	private boolean collapsed;
	private float heightPx = 22f;

	public boolean isCollapsed() {
		return collapsed;
	}

	public void setCollapsed(boolean collapsed) {
		this.collapsed = collapsed;
	}

	public void toggleCollapsed() {
		collapsed = !collapsed;
	}

	public float getHeightPx() {
		return heightPx;
	}

	public void setHeightPx(float heightPx) {
		this.heightPx = Math.max(8f, heightPx);
	}
}
