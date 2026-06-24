package com.beatblock.timeline.rendering;

import imgui.ImGui;

/** 音频组拖放悬停边框高亮。 */
public final class TimelineAudioGroupDropHighlight {

	private static final int AUDIO_GROUP_DROP_HIGHLIGHT_COLOR = 0x55_7F_77_DD;

	private TimelineAudioGroupDropHighlight() {
	}

	public static void drawIfActive(TimelineLayout layout, boolean active) {
		if (!active || layout == null) return;

		float y0 = -1f;
		float y1 = -1f;
		float groupY = layout.getRowScreenY(TimelineTrackMeta.ROW_AUDIO_GROUP);
		if (groupY >= 0f) {
			y0 = groupY;
			y1 = groupY + layout.getRowHeight(TimelineTrackMeta.ROW_AUDIO_GROUP);
		}
		for (int slot = 0; slot < layout.getActiveAudioSubRowCount(); slot++) {
			int r = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			float ry = layout.getRowScreenY(r);
			if (ry < 0f) continue;
			if (y0 < 0f || ry < y0) y0 = ry;
			float bottom = ry + layout.getRowHeight(r);
			if (bottom > y1) y1 = bottom;
		}
		if (y0 >= 0 && y1 > y0) {
			ImGui.getWindowDrawList().addRect(
				layout.contentLeft, y0,
				layout.contentLeft + layout.contentWidth, y1,
				AUDIO_GROUP_DROP_HIGHLIGHT_COLOR, 3f, 0, 1.5f);
		}
	}
}
