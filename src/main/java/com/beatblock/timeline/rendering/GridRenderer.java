package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.util.MusicTimeFormatter;
import com.beatblock.timeline.util.TimeUtils;
import imgui.ImGui;

/**
 * 绘制时间线网格竖线与时间标尺。
 *
 * 标尺分两种模式（自动切换）：
 *  - 时间模式：mm:ss 主刻度 + 副刻度，有 BPM 时叠加小节/拍标记
 *  - 节拍模式：小节为主刻度（有 BPM 且每小节屏幕宽度 ≥ 30px 时启用）
 */
public final class GridRenderer {

	// ── 网格线 ──
	private static final int GRID_COLOR       = 0x22_88_88_88;

	// ── 标尺背景 ──
	private static final int RULER_BG         = 0xFF_1C_1C_1E;

	// ── 时间刻度颜色 ──
	/** 主刻度线：亮灰 */
	private static final int MAJOR_TICK_COLOR = 0xFF_88_88_88;
	/** 副刻度线：暗灰 */
	private static final int MINOR_TICK_COLOR = 0xFF_3D_3D_3D;
	/** 小节边界线：蓝色调 */
	private static final int BAR_TICK_COLOR   = 0xCC_66_AA_FF;
	/** 拍线：半透明蓝 */
	private static final int BEAT_TICK_COLOR  = 0x77_44_88_DD;

	// ── 标签颜色 ──
	/** mm:ss 主标签 */
	private static final int LABEL_TIME_COLOR = 0xFF_CC_CC_CC;
	/** 小节号副标签 */
	private static final int LABEL_BAR_COLOR  = 0xFF_88_BB_FF;
	/** 循环区标签 */
	private static final int LABEL_LOOP_COLOR = 0xFF_FF_DD_88;

	// ── 循环区颜色 ──
	private static final int LOOP_RANGE_FILL  = 0x33_FF_D0_66;
	private static final int LOOP_IN_COLOR    = 0xEE_FF_BB_44;
	private static final int LOOP_OUT_COLOR   = 0xEE_FF_88_66;
	private static final int MARKER_COLOR     = 0xEE_FF_D4_66;
	private static final float MARKER_LABEL_GAP = 6f;
	private static final int MARKER_LABEL_ROWS = 3;

	// ── 刻度高度比例（占 rulerHeight） ──
	private static final float MAJOR_TICK_FRAC = 0.55f;
	private static final float MINOR_TICK_FRAC = 0.27f;
	private static final float BAR_TICK_FRAC   = 0.65f;
	private static final float BEAT_TICK_FRAC  = 0.22f;
	private static final float SUB_TICK_FRAC   = 0.12f;

	// ── 公共入口 ──────────────────────────────────────────────

	/**
	 * 绘制时间刻度标尺（固定顶部行）。
	 *
	 * @param startY 保留参数（兼容旧调用），实际坐标由 layout.rulerTop 决定
	 * @param view   当前视图状态
	 * @param layout 布局（提供标尺区坐标）
	 * @param bpm    来自 Timeline.getBpm()；≤ 0 表示无 BPM 信息，只显示 mm:ss
	 */
	public void renderRuler(float startY, TimelineViewState view, TimelineLayout layout, double bpm, TimelineToolbarState toolbarState, Timeline timeline) {
		if (view == null || layout == null) return;

		float rTop   = layout.rulerTop;
		float rBot   = rTop + layout.rulerHeight;
		float rLeft  = layout.rulerLeft;
		float rRight = rLeft + layout.rulerWidth;

		// 1. 标尺背景
		ImGui.getWindowDrawList().addRectFilled(rLeft, rTop, rRight, rBot, RULER_BG);

		// 2. 选择显示模式
		double secondsPerBeat = 0, secondsPerBar = 0;
		if (bpm > 0) {
			secondsPerBeat = 60.0 / bpm;
			secondsPerBar  = secondsPerBeat * 4;
		}
		boolean beatMode = bpm > 0 && (secondsPerBar * view.getZoom()) >= 30;

		if (beatMode) {
			renderBeatModeRuler(view, layout, bpm, secondsPerBeat, secondsPerBar,
					rTop, rBot, rLeft);
		} else {
			renderTimeModeRuler(view, layout, bpm, secondsPerBeat, secondsPerBar,
					rTop, rBot, rLeft);
		}

		renderMarkers(view, layout, timeline, rTop, rBot, rLeft);
		renderLoopOverlay(view, layout, toolbarState, rTop, rBot, rLeft);

		// 3. 底部分隔线
		ImGui.getWindowDrawList().addLine(rLeft, rBot - 1, rRight, rBot - 1, MAJOR_TICK_COLOR, 1f);
	}

	public void renderRuler(float startY, TimelineViewState view, TimelineLayout layout, double bpm) {
		renderRuler(startY, view, layout, bpm, null, null);
	}

	/** 兼容旧调用（无 BPM）。 */
	public void renderRuler(float startY, TimelineViewState view, TimelineLayout layout) {
		renderRuler(startY, view, layout, 0, null, null);
	}

	/**
	 * 绘制轨道区时间网格竖线（不含标尺）。
	 */
	public void render(TimelineViewState view, TimelineLayout layout, float contentHeight) {
		if (view == null || layout == null) return;
		double viewStart = view.getViewStartTimeSeconds();
		double viewEnd   = view.getViewEndTimeSeconds();
		double step = TimeUtils.gridStep(viewStart, viewEnd, view.getZoom());
		double t0 = Math.floor(viewStart / step) * step;
		for (double t = t0; t <= viewEnd + 0.001; t += step) {
			float x = view.timeToScreen(t);
			if (x >= 0 && x <= layout.contentWidth) {
				ImGui.getWindowDrawList().addLine(
						layout.contentLeft + x, layout.contentTop,
						layout.contentLeft + x, layout.contentTop + contentHeight,
						GRID_COLOR, 1f);
			}
		}
	}

	// ── 私有实现 ─────────────────────────────────────────────

	/**
	 * 时间模式：mm:ss 主/副刻度 + BPM 小节/拍叠加。
	 * 当 bpm 可用时，在标尺底部叠加小节线（蓝色）与拍线（半透明蓝）。
	 */
	private void renderTimeModeRuler(TimelineViewState view, TimelineLayout layout,
			double bpm, double secondsPerBeat, double secondsPerBar,
			float rTop, float rBot, float rLeft) {

		double viewStart = view.getViewStartTimeSeconds();
		double viewEnd   = view.getViewEndTimeSeconds();
		double range     = Math.max(0.1, viewEnd - viewStart);

		// 自适应主副步长
		double majorStep, minorStep;
		if      (range > 600) { majorStep = 120; minorStep = 30; }
		else if (range > 300) { majorStep =  60; minorStep = 10; }
		else if (range > 120) { majorStep =  30; minorStep =  5; }
		else if (range >  60) { majorStep =  15; minorStep =  5; }
		else if (range >  20) { majorStep =  10; minorStep =  2; }
		else if (range >  10) { majorStep =   5; minorStep =  1; }
		else if (range >   5) { majorStep =   2; minorStep =  0.5; }
		else if (range >   2) { majorStep =   1; minorStep =  0.25; }
		else                  { majorStep = 0.5; minorStep =  0.1; }

		float majorTickH = layout.rulerHeight * MAJOR_TICK_FRAC;
		float minorTickH = layout.rulerHeight * MINOR_TICK_FRAC;
		float textY      = rTop + 2;

		// 副刻度（跳过与主刻度重合的点）
		double t0m = Math.floor(viewStart / minorStep) * minorStep;
		for (double t = t0m; t <= viewEnd + 0.001; t += minorStep) {
			if (Math.abs(t % majorStep) < minorStep * 0.01) continue;
			float xOff = view.timeToScreen(t);
			if (xOff < -2 || xOff > layout.contentWidth + 2) continue;
			float sx = rLeft + xOff;
			ImGui.getWindowDrawList().addLine(sx, rBot - minorTickH, sx, rBot, MINOR_TICK_COLOR, 1f);
		}

		// 主刻度 + mm:ss 标签
		double t0 = Math.floor(viewStart / majorStep) * majorStep;
		for (double t = t0; t <= viewEnd + 0.001; t += majorStep) {
			float xOff = view.timeToScreen(t);
			if (xOff < -2 || xOff > layout.contentWidth + 2) continue;
			float sx = rLeft + xOff;
			ImGui.getWindowDrawList().addLine(sx, rBot - majorTickH, sx, rBot, MAJOR_TICK_COLOR, 1f);
			ImGui.getWindowDrawList().addText(sx + 3, textY, LABEL_TIME_COLOR,
					MusicTimeFormatter.formatMmSs(t));
		}

		// BPM 叠加：小节线 + 拍线（底部较小的刻度层）
		if (bpm > 0 && secondsPerBar > 0) {
			float barPx  = (float) (secondsPerBar  * view.getZoom());
			float beatPx = (float) (secondsPerBeat * view.getZoom());
			float barTickH  = layout.rulerHeight * BAR_TICK_FRAC;
			float beatTickH = layout.rulerHeight * BEAT_TICK_FRAC;

			// 小节线（brighter，底部贯穿）
			if (barPx >= 10) {
				double t0b = Math.floor(viewStart / secondsPerBar) * secondsPerBar;
				for (double t = t0b; t <= viewEnd + 0.001; t += secondsPerBar) {
					float xOff = view.timeToScreen(t);
					if (xOff < -2 || xOff > layout.contentWidth + 2) continue;
					float sx = rLeft + xOff;
					ImGui.getWindowDrawList().addLine(sx, rBot - barTickH, sx, rBot, BAR_TICK_COLOR, 1.5f);
					// 小节号标签（靠近底部，仅宽度足够时显示）
					if (barPx >= 30) {
						int barNum = MusicTimeFormatter.barNumber(t, bpm);
						ImGui.getWindowDrawList().addText(sx + 2, rBot - 13, LABEL_BAR_COLOR,
								"B" + barNum);
					}
				}
			}

			// 拍线（排除小节边界）
			if (beatPx >= 8) {
				double t0bt = Math.floor(viewStart / secondsPerBeat) * secondsPerBeat;
				for (double t = t0bt; t <= viewEnd + 0.001; t += secondsPerBeat) {
					if (Math.abs(t % secondsPerBar) < secondsPerBeat * 0.01) continue;
					float xOff = view.timeToScreen(t);
					if (xOff < -2 || xOff > layout.contentWidth + 2) continue;
					float sx = rLeft + xOff;
					ImGui.getWindowDrawList().addLine(sx, rBot - beatTickH, sx, rBot, BEAT_TICK_COLOR, 1f);
				}
			}
		}
	}

	/**
	 * 节拍模式（zoom 够高，每小节 ≥ 30px）：小节为主刻度，拍为副刻度。
	 * 同时在顶部显示 mm:ss，底部显示 "B1" "B2" 小节号。
	 */
	private void renderBeatModeRuler(TimelineViewState view, TimelineLayout layout,
			double bpm, double secondsPerBeat, double secondsPerBar,
			float rTop, float rBot, float rLeft) {

		double viewStart = view.getViewStartTimeSeconds();
		double viewEnd   = view.getViewEndTimeSeconds();

		float barTickH  = layout.rulerHeight * BAR_TICK_FRAC;
		float beatTickH = layout.rulerHeight * BEAT_TICK_FRAC;
		float subTickH  = layout.rulerHeight * SUB_TICK_FRAC;

		float textY    = rTop + 2;                          // mm:ss 标签
		float barTextY = rBot - 13;                         // 小节号标签

		float beatPx = (float) (secondsPerBeat * view.getZoom());
		float barPx  = (float) (secondsPerBar  * view.getZoom());

		// 小节主刻度：蓝色线 + mm:ss 标签 + 小节号
		double t0bar = Math.floor(viewStart / secondsPerBar) * secondsPerBar;
		for (double t = t0bar; t <= viewEnd + 0.001; t += secondsPerBar) {
			float xOff = view.timeToScreen(t);
			if (xOff < -2 || xOff > layout.contentWidth + 2) continue;
			float sx = rLeft + xOff;
			ImGui.getWindowDrawList().addLine(sx, rBot - barTickH, sx, rBot, BAR_TICK_COLOR, 1.5f);
			ImGui.getWindowDrawList().addText(sx + 3, textY, LABEL_TIME_COLOR,
					MusicTimeFormatter.formatMmSs(t));
			int barNum = MusicTimeFormatter.barNumber(t, bpm);
			ImGui.getWindowDrawList().addText(sx + 3, barTextY, LABEL_BAR_COLOR, "B" + barNum);
		}

		// 拍副刻度（排除小节边界）
		if (beatPx >= 8) {
			double t0bt = Math.floor(viewStart / secondsPerBeat) * secondsPerBeat;
			for (double t = t0bt; t <= viewEnd + 0.001; t += secondsPerBeat) {
				if (Math.abs(t % secondsPerBar) < secondsPerBeat * 0.01) continue;
				float xOff = view.timeToScreen(t);
				if (xOff < -2 || xOff > layout.contentWidth + 2) continue;
				float sx = rLeft + xOff;
				ImGui.getWindowDrawList().addLine(sx, rBot - beatTickH, sx, rBot, BEAT_TICK_COLOR, 1f);
				// 拍号（仅当拍宽够大）
				if (beatPx >= 20) {
					int beatNum = MusicTimeFormatter.beatNumber(t, bpm);
					ImGui.getWindowDrawList().addText(sx + 2, barTextY, BEAT_TICK_COLOR,
							String.valueOf(beatNum));
				}
			}
		}

		// 1/4 拍细分（拍宽 ≥ 60px）
		if (beatPx >= 60) {
			double subStep = secondsPerBeat / 4.0;
			double t0sub   = Math.floor(viewStart / subStep) * subStep;
			for (double t = t0sub; t <= viewEnd + 0.001; t += subStep) {
				if (Math.abs(t % secondsPerBeat) < subStep * 0.01) continue;
				float xOff = view.timeToScreen(t);
				if (xOff < -2 || xOff > layout.contentWidth + 2) continue;
				float sx = rLeft + xOff;
				ImGui.getWindowDrawList().addLine(sx, rBot - subTickH, sx, rBot, MINOR_TICK_COLOR, 1f);
			}
		}
	}

	private void renderLoopOverlay(
		TimelineViewState view,
		TimelineLayout layout,
		TimelineToolbarState toolbarState,
		float rTop,
		float rBot,
		float rLeft
	) {
		if (view == null || layout == null || toolbarState == null || !toolbarState.hasLoopRange()) return;

		double loopIn = toolbarState.getLoopInSeconds();
		double loopOut = toolbarState.getLoopOutSeconds();
		if (loopOut <= loopIn) return;

		float xIn = rLeft + view.timeToScreen(loopIn);
		float xOut = rLeft + view.timeToScreen(loopOut);
		float clipLeft = rLeft;
		float clipRight = rLeft + layout.rulerWidth;
		float fillLeft = Math.max(clipLeft, Math.min(xIn, xOut));
		float fillRight = Math.min(clipRight, Math.max(xIn, xOut));
		if (fillRight > fillLeft) {
			ImGui.getWindowDrawList().addRectFilled(fillLeft, rTop + 1, fillRight, rBot - 1, LOOP_RANGE_FILL);
		}

		if (xIn >= clipLeft - 2 && xIn <= clipRight + 2) {
			ImGui.getWindowDrawList().addLine(xIn, rTop, xIn, rBot, LOOP_IN_COLOR, 2f);
			ImGui.getWindowDrawList().addText(xIn + 3, rTop + 2, LABEL_LOOP_COLOR, "IN");
		}
		if (xOut >= clipLeft - 2 && xOut <= clipRight + 2) {
			ImGui.getWindowDrawList().addLine(xOut, rTop, xOut, rBot, LOOP_OUT_COLOR, 2f);
			ImGui.getWindowDrawList().addText(xOut + 3, rTop + 12, LABEL_LOOP_COLOR, "OUT");
		}
	}

	private void renderMarkers(
		TimelineViewState view,
		TimelineLayout layout,
		Timeline timeline,
		float rTop,
		float rBot,
		float rLeft
	) {
		if (view == null || layout == null || timeline == null || timeline.getMarkers().isEmpty()) return;
		float clipLeft = rLeft;
		float clipRight = rLeft + layout.rulerWidth;
		float[] rowRightEdges = new float[MARKER_LABEL_ROWS];
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null) continue;
			float x = rLeft + view.timeToScreen(marker.getTimeSeconds());
			if (x < clipLeft - 6 || x > clipRight + 6) continue;
			ImGui.getWindowDrawList().addLine(x, rTop + 2, x, rBot - 2, MARKER_COLOR, 1.5f);
			ImGui.getWindowDrawList().addTriangleFilled(
				x - 4, rTop + 2,
				x + 4, rTop + 2,
				x, rTop + 8,
				MARKER_COLOR
			);
			String name = marker.getName();
			if (name != null && !name.isBlank()) {
				float labelX = x + 4;
				float labelWidth = ImGui.calcTextSize(name).x;
				float labelRight = labelX + labelWidth;
				if (labelX < clipRight) {
					int rowIndex = -1;
					for (int i = 0; i < MARKER_LABEL_ROWS; i++) {
						if (labelX >= rowRightEdges[i] + MARKER_LABEL_GAP) {
							rowIndex = i;
							break;
						}
					}
					if (rowIndex >= 0) {
						float textY = rTop + 2 + rowIndex * 8f;
						ImGui.getWindowDrawList().addText(labelX, textY, MARKER_COLOR, name);
						rowRightEdges[rowIndex] = Math.min(labelRight, clipRight);
					}
				}
			}
		}
	}
}
