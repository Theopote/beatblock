package com.beatblock.timeline.rendering;

/**
 * 轨道列表左侧状态：每行的「可见」「锁定」等，与 TimelineLayout.CONTENT_ROW_COUNT 对应。
 */
public final class TimelineTrackListState {

	private final boolean[] visible = new boolean[TimelineLayout.CONTENT_ROW_COUNT];
	private final boolean[] locked = new boolean[TimelineLayout.CONTENT_ROW_COUNT];

	public TimelineTrackListState() {
		for (int i = 0; i < visible.length; i++) {
			visible[i] = true;
		}
	}

	public boolean isVisible(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= visible.length) return true;
		return visible[rowIndex];
	}

	public void setVisible(int rowIndex, boolean v) {
		if (rowIndex >= 0 && rowIndex < visible.length) visible[rowIndex] = v;
	}

	public void toggleVisible(int rowIndex) {
		if (rowIndex >= 0 && rowIndex < visible.length) visible[rowIndex] = !visible[rowIndex];
	}

	public boolean isLocked(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= locked.length) return false;
		return locked[rowIndex];
	}

	public void setLocked(int rowIndex, boolean v) {
		if (rowIndex >= 0 && rowIndex < locked.length) locked[rowIndex] = v;
	}

	public void toggleLocked(int rowIndex) {
		if (rowIndex >= 0 && rowIndex < locked.length) locked[rowIndex] = !locked[rowIndex];
	}
}
