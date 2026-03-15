package com.beatblock.timeline.rendering;

import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.util.TimeUtils;
import imgui.ImGui;

/**
 * 绘制时间线网格竖线与时间标尺。
 */
public final class GridRenderer {

	private static final int GRID_COLOR = 0x22_88_88_88;
	private static final int RULER_COLOR = 0xFF_88_88_88;

	public void renderRuler(float startY, TimelineViewState view, TimelineLayout layout) {
		if (view == null || layout == null) return;
		double viewStart = view.getViewStartTimeSeconds();
		double viewEnd = view.getViewEndTimeSeconds();
		double range = Math.max(0.1, viewEnd - viewStart);
		double step = range > 60 ? 10 : (range > 20 ? 5 : (range > 5 ? 2 : (range > 1 ? 1 : 0.5)));
		double t0 = Math.floor(viewStart / step) * step;
		for (double t = t0; t <= viewEnd + 0.001; t += step) {
			float x = view.timeToScreen(t);
			if (x >= -2 && x <= layout.contentWidth + 2) {
				ImGui.getWindowDrawList().addLine(layout.rulerLeft + x, layout.rulerTop, layout.rulerLeft + x, layout.rulerTop + layout.rulerHeight, RULER_COLOR, 1f);
				ImGui.getWindowDrawList().addText(layout.rulerLeft + x + 2, layout.rulerTop + 2, RULER_COLOR, String.format("%.1f", t));
			}
		}
	}

	public void render(TimelineViewState view, TimelineLayout layout, float contentHeight) {
		if (view == null || layout == null) return;
		double viewStart = view.getViewStartTimeSeconds();
		double viewEnd = view.getViewEndTimeSeconds();
		double step = TimeUtils.gridStep(viewStart, viewEnd, view.getZoom());
		double t0 = Math.floor(viewStart / step) * step;
		for (double t = t0; t <= viewEnd + 0.001; t += step) {
			float x = view.timeToScreen(t);
			if (x >= 0 && x <= layout.contentWidth) {
				ImGui.getWindowDrawList().addLine(layout.contentLeft + x, layout.contentTop, layout.contentLeft + x, layout.contentTop + contentHeight, GRID_COLOR, 1f);
			}
		}
	}
}
