package com.beatblock.timeline.rendering;

import com.beatblock.timeline.*;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 绘制时间线事件：动画块、关键帧、全局事件、频段点。
 * 所有渲染方法假定传入列表已按 timeSeconds 升序，使用二分查找做视口裁剪，
 * 仅绘制 [viewStart, viewEnd] 范围内的事件，避免 O(n) 全量遍历。
 */
public final class EventRenderer {

	private static final int EVENT_DOT_COLOR    = 0xFF_AA_CC_FF;
	private static final int KEYFRAME_COLOR     = 0xFF_FF_CC_66;
	private static final int GLOBAL_EVENT_COLOR = 0xFF_AA_FF_AA;
	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;
	private static final float MIN_BAR_HALF_WIDTH = 1.25f;

	private static int withAlpha(int abgr, int alpha) {
		return (abgr & 0x00FF_FFFF) | ((alpha & 0xFF) << 24);
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	/**
	 * 在时间排序列表中返回第一个满足 key(item) >= target 的索引（若全部小于则返回 size）。
	 */
	private static <E> int lowerBound(List<E> list, ToDoubleFunction<E> key, double target) {
		int lo = 0, hi = list.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (key.applyAsDouble(list.get(mid)) < target) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	public void renderFrequencyDots(float rowY, List<FrequencyEvent> events, TimelineLayout layout, TimelineViewState view) {
		if (view == null || layout == null || events.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		int start = lowerBound(events, FrequencyEvent::getTimeSeconds, vs);
		for (int i = start; i < events.size(); i++) {
			FrequencyEvent e = events.get(i);
			double t = e.getTimeSeconds();
			if (t > ve) break;
			float r = 3f + e.getEnergy() * 3f;
			ImGui.getWindowDrawList().addCircleFilled(baseX + view.timeToScreen(t), baseY, r, EVENT_DOT_COLOR);
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	/**
	 * 频段事件柱状渲染：柱体 + 底部锚点，便于在低行高下仍能感知节奏。
	 */
	public void renderFrequencyBars(
		float rowY,
		float rowHeight,
		List<FrequencyEvent> events,
		TimelineLayout layout,
		TimelineViewState view,
		int color,
		double bpm,
		float widthRatio,
		float dotScale
	) {
		if (view == null || layout == null || events.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY();
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();

		float pxPerSecond = (float) (view.timeToScreen(1.0) - view.timeToScreen(0.0));
		double beatDur = bpm > 1e-6 ? (60.0 / bpm) : 0.5;
		float beatWidthPx = (float) (pxPerSecond * beatDur);
		float barHalfW = Math.max(MIN_BAR_HALF_WIDTH, beatWidthPx * Math.max(0.08f, widthRatio) * 0.5f);

		float bottomY = baseY + rowHeight - 2f;
		float maxBarH = Math.max(6f, rowHeight - 6f);
		int barColor = withAlpha(color, 0x66);
		int fillColor = withAlpha(color, 0x24);

		int start = lowerBound(events, FrequencyEvent::getTimeSeconds, vs);
		for (int i = start; i < events.size(); i++) {
			FrequencyEvent e = events.get(i);
			double t = e.getTimeSeconds();
			if (t > ve) break;
			float x = baseX + view.timeToScreen(t);
			float energy = clamp01(e.getEnergy());
			float barH = 3f + energy * (maxBarH - 3f);
			float y0 = bottomY - barH;

			ImGui.getWindowDrawList().addRectFilled(x - barHalfW, y0, x + barHalfW, bottomY, barColor, 1.5f);
			ImGui.getWindowDrawList().addRectFilled(x - barHalfW, bottomY - maxBarH, x + barHalfW, bottomY, fillColor, 1f);

			float dotR = Math.max(1.2f, (1.2f + energy * 2.8f) * Math.max(0.7f, dotScale));
			ImGui.getWindowDrawList().addCircleFilled(x, bottomY, dotR, color);
		}

		ImGui.setCursorPosY(rowY + rowHeight);
	}

	/**
	 * Feature-track bar rendering (kick/snare/hihat or any named track).
	 * Mirrors renderFrequencyBars but consumes {@link FeatureEvent} lists.
	 */
	public void renderFeatureBars(
		float rowY,
		float rowHeight,
		List<FeatureEvent> events,
		TimelineLayout layout,
		TimelineViewState view,
		int color,
		double bpm,
		float widthRatio,
		float dotScale
	) {
		if (view == null || layout == null || events == null || events.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY();
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();

		float pxPerSecond = (float) (view.timeToScreen(1.0) - view.timeToScreen(0.0));
		double beatDur = bpm > 1e-6 ? (60.0 / bpm) : 0.5;
		float beatWidthPx = (float) (pxPerSecond * beatDur);
		float barHalfW = Math.max(MIN_BAR_HALF_WIDTH, beatWidthPx * Math.max(0.08f, widthRatio) * 0.5f);

		float bottomY = baseY + rowHeight - 2f;
		float maxBarH = Math.max(6f, rowHeight - 6f);
		int barColor = withAlpha(color, 0x66);
		int fillColor = withAlpha(color, 0x24);

		int start = lowerBound(events, FeatureEvent::getTimeSeconds, vs);
		for (int i = start; i < events.size(); i++) {
			FeatureEvent e = events.get(i);
			double t = e.getTimeSeconds();
			if (t > ve) break;
			float x = baseX + view.timeToScreen(t);
			float energy = clamp01(e.getEnergy());
			float barH = 3f + energy * (maxBarH - 3f);
			float y0 = bottomY - barH;

			ImGui.getWindowDrawList().addRectFilled(x - barHalfW, y0, x + barHalfW, bottomY, barColor, 1.5f);
			ImGui.getWindowDrawList().addRectFilled(x - barHalfW, bottomY - maxBarH, x + barHalfW, bottomY, fillColor, 1f);

			float dotR = Math.max(1.2f, (1.2f + energy * 2.8f) * Math.max(0.7f, dotScale));
			ImGui.getWindowDrawList().addCircleFilled(x, bottomY, dotR, color);
		}

		ImGui.setCursorPosY(rowY + rowHeight);
	}

	public void renderAnimationEventBlocks(float rowY, List<TimelineAnimationEvent> events, TimelineLayout layout, TimelineViewState view, SelectionState selection) {
		renderAnimationEventBlocks(rowY, events, layout, view, selection, KEYFRAME_COLOR);
	}

	public void renderAnimationEventBlocks(float rowY, List<TimelineAnimationEvent> events, TimelineLayout layout, TimelineViewState view, SelectionState selection, int fillColor) {
		if (view == null || layout == null || events.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		// 动画块有时长：起点 < vs 的块可能仍与视口重叠，不能跳过，下界从 0 开始；
		// 起点 > ve 的块之后全部不可见（列表有序），可以 break 提前退出。
		for (int i = 0; i < events.size(); i++) {
			TimelineAnimationEvent e = events.get(i);
			double t = e.getTimeSeconds();
			if (t > ve) break;
			double end = e.getEndTimeSeconds();
			if (end < vs) continue;
			float x = view.timeToScreen(t);
			float w = (float) (e.getDurationSeconds() * view.getZoom());
			w = Math.max(8f, Math.min(w, layout.timelineWidth - x + 1));
			float y0 = baseY - layout.rowHeight * 0.35f;
			float y1 = baseY + layout.rowHeight * 0.35f;
			ImGui.getWindowDrawList().addRectFilled(baseX + x, y0, baseX + x + w, y1, fillColor, 2f);
			if (selection != null && selection.isEventSelected(e.getEventId())) {
				ImGui.getWindowDrawList().addRect(baseX + x, y0, baseX + x + w, y1, SELECTED_BORDER_COLOR, 0f, 0, 2f);
			}
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	public void renderCameraKeyframeRow(float rowY, List<CameraKeyframe> keyframes, TimelineLayout layout, TimelineViewState view) {
		if (view == null || layout == null || keyframes.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		int start = lowerBound(keyframes, CameraKeyframe::getTimeSeconds, vs);
		for (int i = start; i < keyframes.size(); i++) {
			CameraKeyframe k = keyframes.get(i);
			double t = k.getTimeSeconds();
			if (t > ve) break;
			float x = view.timeToScreen(t);
			ImGui.getWindowDrawList().addTriangleFilled(
				baseX + x, baseY - 6,
				baseX + x - 5, baseY + 5,
				baseX + x + 5, baseY + 5,
				KEYFRAME_COLOR
			);
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	public void renderGlobalEventRow(float rowY, List<GlobalEvent> events, TimelineLayout layout, TimelineViewState view) {
		if (view == null || layout == null || events.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		int start = lowerBound(events, GlobalEvent::getTimeSeconds, vs);
		for (int i = start; i < events.size(); i++) {
			GlobalEvent e = events.get(i);
			double t = e.getTimeSeconds();
			if (t > ve) break;
			ImGui.getWindowDrawList().addCircleFilled(baseX + view.timeToScreen(t), baseY, 5f, GLOBAL_EVENT_COLOR);
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}
}
