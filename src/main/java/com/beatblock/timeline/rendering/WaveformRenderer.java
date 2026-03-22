package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.FrequencyEvent;
import com.beatblock.timeline.WaveformData;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 绘制音频波形轨：█████░░░████
 */
public final class WaveformRenderer {

	private static final int WAVEFORM_COLOR = 0xFF_66_AA_FF;
	private static final int BEAT_LINE_COLOR = 0x55_7F_77_DD;
	private static final int BEAT_HEAD_COLOR = 0xBB_7F_77_DD;
	private static final double EPS = 1e-6;

	private WaveformData cachedWaveform;
	private int cachedWaveformSampleCount = -1;
	private double cachedDuration = Double.NaN;
	private double cachedViewStart = Double.NaN;
	private double cachedViewEnd = Double.NaN;
	private float cachedZoom = Float.NaN;
	private float cachedTimelineWidth = Float.NaN;
	private int cachedRenderSamples = -1;
	private float[] cachedXs = new float[0];
	private float[] cachedSamples = new float[0];
	private int cachedCount;

	private static boolean nearlyEqual(double a, double b) {
		return Math.abs(a - b) <= EPS;
	}

	private void ensureCache(Timeline timeline, TimelineLayout layout, TimelineViewState view, WaveformData wf,
			double viewStart, double viewEnd) {
		double dur = timeline.getDurationSeconds();
		int renderSamples = (int) Math.min(layout.timelineWidth, 800);
		boolean cacheValid = cachedWaveform == wf
				&& cachedWaveformSampleCount == wf.getSampleCount()
				&& nearlyEqual(cachedDuration, dur)
				&& nearlyEqual(cachedViewStart, viewStart)
				&& nearlyEqual(cachedViewEnd, viewEnd)
				&& Math.abs(cachedZoom - view.getZoom()) <= EPS
				&& Math.abs(cachedTimelineWidth - layout.timelineWidth) <= EPS
				&& cachedRenderSamples == renderSamples;
		if (cacheValid) return;

		if (cachedXs.length < renderSamples) {
			cachedXs = new float[renderSamples];
			cachedSamples = new float[renderSamples];
		}
		cachedCount = 0;
		for (int i = 0; i < renderSamples; i++) {
			double t = viewStart + (viewEnd - viewStart) * (double) i / renderSamples;
			if (t < 0 || t > dur) continue;
			int idx = wf.timeToIndex(t);
			cachedXs[cachedCount] = view.timeToScreen(t);
			cachedSamples[cachedCount] = wf.getSample(idx);
			cachedCount++;
		}

		cachedWaveform = wf;
		cachedWaveformSampleCount = wf.getSampleCount();
		cachedDuration = dur;
		cachedViewStart = viewStart;
		cachedViewEnd = viewEnd;
		cachedZoom = view.getZoom();
		cachedTimelineWidth = layout.timelineWidth;
		cachedRenderSamples = renderSamples;
	}

	public void render(float rowY, float rowHeight, Timeline timeline, TimelineLayout layout, TimelineViewState view) {
		WaveformData wf = timeline.getWaveform();
		renderWaveform(rowY, rowHeight, wf, WAVEFORM_COLOR, timeline, layout, view, true);
	}

	/**
	 * 渲染指定的波形数据（用于茎波形轨）。
	 */
	public void renderStemWaveform(float rowY, float rowHeight, WaveformData wf, int color,
	                               Timeline timeline, TimelineLayout layout, TimelineViewState view) {
		renderWaveform(rowY, rowHeight, wf, color, timeline, layout, view, false);
	}

	private void renderWaveform(float rowY, float rowHeight, WaveformData wf, int color,
	                            Timeline timeline, TimelineLayout layout, TimelineViewState view,
	                            boolean showBeatOverlay) {
		if (timeline == null || layout == null || view == null) return;
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(layout.trackLabelWidth);
		float minX = ImGui.getCursorScreenPosX();
		float minY = ImGui.getCursorScreenPosY();
		double viewStart = view.getViewStartTimeSeconds();
		double viewEnd = view.getViewEndTimeSeconds();
		if (wf != null && wf.getSampleCount() > 0) {
			ensureCache(timeline, layout, view, wf, viewStart, viewEnd);
			float halfH = rowHeight * 0.42f;
			float centerY = minY + rowHeight * 0.5f;
			for (int i = 0; i < cachedCount; i++) {
				float x = cachedXs[i];
				float s = cachedSamples[i];
				if (x < -1 || x > layout.timelineWidth + 1) continue;
				float y0 = centerY;
				float y1 = y0 - s * halfH;
				ImGui.getWindowDrawList().addLine(minX + x, y0, minX + x, y1, color, 1f);
			}
			if (showBeatOverlay) {
				renderBeatOverlay(minX, minY, rowHeight, timeline, view);
			}
		} else {
			cachedWaveform = null;
			cachedWaveformSampleCount = -1;
			cachedCount = 0;
			ImGui.textDisabled("~~~~ 波形（导入音乐后生成）~~~~");
		}
		ImGui.setCursorPosY(rowY + rowHeight);
	}

	private void renderBeatOverlay(float screenLeft, float rowTopY, float rowHeight, Timeline timeline, TimelineViewState view) {
		double bpm = timeline.getBpm();
		if (bpm <= 0) return;
		List<FrequencyEvent> events = timeline.getFrequencyEvents();
		if (events.isEmpty()) return;

		double beatDur = 60.0 / bpm;
		Set<Long> beatIndices = new TreeSet<>();
		for (FrequencyEvent event : events) {
			long beatIndex = Math.round(event.getTimeSeconds() / beatDur);
			beatIndices.add(beatIndex);
		}

		double viewStart = view.getViewStartTimeSeconds();
		double viewEnd = view.getViewEndTimeSeconds();
		float yStart = rowTopY + 4f;
		float yEnd = rowTopY + rowHeight - 4f;
		for (long beatIndex : beatIndices) {
			double t = beatIndex * beatDur;
			if (t < viewStart || t > viewEnd) continue;
			float x = screenLeft + view.timeToScreen(t);
			for (float y = yStart; y < yEnd; y += 5f) {
				float y2 = Math.min(y + 2f, yEnd);
				ImGui.getWindowDrawList().addLine(x, y, x, y2, BEAT_LINE_COLOR, 1f);
			}
			ImGui.getWindowDrawList().addRectFilled(x - 1f, rowTopY, x + 1f, rowTopY + 3f, BEAT_HEAD_COLOR);
		}
	}
}
