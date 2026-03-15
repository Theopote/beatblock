package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.*;
import com.beatblock.timeline.editor.TimelineEditor;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/**
 * 底部通栏时间线面板：接入 TimelineEditor（TimeSystem + Viewport + Selection + Interaction）。
 * Step1：轨道 / 网格 / 事件 / 播放头，时间↔屏幕由 ViewState 驱动。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final float TRACK_LABEL_WIDTH = 110f;
	private static final float ROW_HEIGHT = 22f;
	private static final float RULER_HEIGHT = 20f;
	private static final int PLAYHEAD_COLOR = 0xFF_FF_66_66;
	private static final int ZERO_COLOR = 0xFF_88_88_88;
	private static final int GRID_COLOR = 0x22_88_88_88;
	private static final int WAVEFORM_COLOR = 0xFF_66_AA_FF;
	private static final int EVENT_DOT_COLOR = 0xFF_AA_CC_FF;
	private static final int KEYFRAME_COLOR = 0xFF_FF_CC_66;
	private static final int GLOBAL_EVENT_COLOR = 0xFF_AA_FF_AA;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		Timeline model = BeatBlock.timeline;
		TimelineEditor editor = BeatBlock.timelineEditor;
		if (model == null) {
			ImGui.text("时间线（未加载模型）");
			ImGui.end();
			return;
		}

		double duration = getDuration();
		double currentTime = getCurrentTime();
		if (editor != null) {
			editor.syncClockDuration();
			if (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.isPlaying()) {
				editor.getClock().setCurrentTimeSeconds(BeatBlock.musicPlayer.getCurrentTimeSeconds());
			} else {
				editor.getClock().setCurrentTimeSeconds(currentTime);
			}
			currentTime = editor.getClock().getCurrentTimeSeconds();
		}

		ImGui.text("时间线");
		ImGui.sameLine();
		ImGui.textDisabled("(音乐 | 摄像机 | 动画事件)");
		ImGui.sameLine(ImGui.getWindowWidth() - 120);
		ImGui.text(String.format("%.1fs / %.1fs", currentTime, duration));
		ImGui.separator();

		float contentWidth = ImGui.getContentRegionAvailX();
		float startY = ImGui.getCursorPosY();
		float timelineWidth = Math.max(200f, contentWidth - TRACK_LABEL_WIDTH - 20f);

		TimelineViewState viewState = editor != null ? editor.getViewState() : null;
		float zoom = (viewState != null) ? viewState.getZoom() : (duration > 0 ? (timelineWidth / (float) duration) : 10f);
		double viewStart = viewState != null ? viewState.getViewStartTimeSeconds() : 0;
		double viewEnd = viewState != null ? viewState.getViewEndTimeSeconds() : duration;
		// 首次打开（仍为默认 0~60 可见范围）时适配整段时长
		if (viewState != null && duration > 0 && timelineWidth > 0
			&& viewState.getViewEndTimeSeconds() >= 59 && viewState.getViewEndTimeSeconds() <= 61) {
			viewState.fitToDuration(duration, timelineWidth);
			viewStart = viewState.getViewStartTimeSeconds();
			viewEnd = viewState.getViewEndTimeSeconds();
			zoom = viewState.getZoom();
		}

		drawTrackLabel(startY, "", false);
		drawRuler(startY, timelineWidth, viewStart, viewEnd, zoom);
		drawGrid(startY + RULER_HEIGHT, timelineWidth, viewStart, viewEnd, zoom, 260);
		float rowY = startY + RULER_HEIGHT;

		rowY = drawTrackLabel(rowY, "音频", true);
		drawTrackLabel(rowY, "波形", false);
		rowY = drawAudioWaveformRow(rowY, model, timelineWidth, zoom, viewStart, viewEnd);
		drawTrackLabel(rowY, "低频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.LOW), timelineWidth, zoom, viewStart, viewEnd);
		drawTrackLabel(rowY, "中频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.MID), timelineWidth, zoom, viewStart, viewEnd);
		drawTrackLabel(rowY, "高频", false);
		rowY = drawFrequencyDots(rowY, model.getFrequencyEventsByBand(FrequencyBand.HIGH), timelineWidth, zoom, viewStart, viewEnd);

		rowY = drawTrackLabel(rowY, "动画", true);
		drawTrackLabel(rowY, "方块动画", false);
		rowY = drawAnimationEventBlocks(rowY, model.getBlockAnimationEvents(), timelineWidth, zoom, viewStart, viewEnd);
		drawTrackLabel(rowY, "自动动画", false);
		rowY = drawAnimationEventBlocks(rowY, model.getAutoAnimationEvents(), timelineWidth, zoom, viewStart, viewEnd);

		rowY = drawTrackLabel(rowY, "摄像机", false);
		drawTrackLabel(rowY, "关键帧", false);
		rowY = drawCameraKeyframeRow(rowY, model.getCameraKeyframes(), timelineWidth, zoom, viewStart, viewEnd);

		rowY = drawTrackLabel(rowY, "全局事件", false);
		drawTrackLabel(rowY, "事件", false);
		rowY = drawGlobalEventRow(rowY, model.getGlobalEvents(), timelineWidth, zoom, viewStart, viewEnd);

		float playheadX = (float) ((currentTime - viewStart) * zoom);
		if (playheadX >= -2 && playheadX <= timelineWidth + 2) {
			float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
			float py0 = ImGui.getWindowPosY() + startY + ImGui.getScrollY();
			float py1 = ImGui.getWindowPosY() + rowY + ImGui.getScrollY();
			ImGui.getWindowDrawList().addLine(padX + playheadX, py0, padX + playheadX, py1, PLAYHEAD_COLOR, 2f);
		}

		ImGui.end();
	}

	private double getDuration() {
		if (BeatBlock.timeline != null && BeatBlock.timeline.getDurationSeconds() > 0) {
			return BeatBlock.timeline.getDurationSeconds();
		}
		if (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.getDurationSeconds() > 0) {
			return BeatBlock.musicPlayer.getDurationSeconds();
		}
		return 60.0;
	}

	private double getCurrentTime() {
		if (BeatBlock.musicPlayer != null) {
			return BeatBlock.musicPlayer.getCurrentTimeSeconds();
		}
		if (BeatBlock.timelineEditor != null) {
			return BeatBlock.timelineEditor.getClock().getCurrentTimeSeconds();
		}
		return 0;
	}

	private float timeToScreen(double timeSeconds, double viewStart, float zoom) {
		return (float) (timeSeconds - viewStart) * zoom;
	}

	private void drawRuler(float startY, float width, double viewStart, double viewEnd, float zoom) {
		float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
		float screenY = ImGui.getWindowPosY() + startY + ImGui.getScrollY();
		double range = Math.max(0.1, viewEnd - viewStart);
		double step = range > 60 ? 10 : (range > 20 ? 5 : (range > 5 ? 2 : (range > 1 ? 1 : 0.5)));
		double t0 = Math.floor(viewStart / step) * step;
		for (double t = t0; t <= viewEnd + 0.001; t += step) {
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= -2 && x <= width + 2) {
				ImGui.getWindowDrawList().addLine(padX + x, screenY, padX + x, screenY + RULER_HEIGHT, ZERO_COLOR, 1f);
				ImGui.getWindowDrawList().addText(padX + x + 2, screenY + 2, ZERO_COLOR, String.format("%.1f", t));
			}
		}
	}

	/** 时间线网格：可见范围内按步长画竖线 */
	private void drawGrid(float contentTopY, float width, double viewStart, double viewEnd, float zoom, float contentHeight) {
		float padX = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
		float screenY0 = ImGui.getWindowPosY() + contentTopY + ImGui.getScrollY();
		double range = Math.max(0.01, viewEnd - viewStart);
		double step = range > 30 ? 5 : (range > 10 ? 2 : (range > 2 ? 1 : 0.5));
		double t0 = Math.floor(viewStart / step) * step;
		for (double t = t0; t <= viewEnd + 0.001; t += step) {
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= 0 && x <= width) {
				ImGui.getWindowDrawList().addLine(padX + x, screenY0, padX + x, screenY0 + contentHeight, GRID_COLOR, 1f);
			}
		}
	}

	/** 绘制轨道标签。isGroup=true 时占一整行并推进 rowY；否则只画标签不推进（与右侧内容同行）。 */
	private float drawTrackLabel(float rowY, String label, boolean isGroup) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(4);
		if (isGroup) {
			ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.85f, 0.7f, 1f);
		}
		ImGui.text(label);
		if (isGroup) {
			ImGui.popStyleColor();
		}
		return isGroup ? rowY + ROW_HEIGHT : rowY;
	}

	private float drawAudioWaveformRow(float rowY, Timeline model, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float minX = ImGui.getCursorScreenPosX();
		float minY = ImGui.getCursorScreenPosY();
		WaveformData wf = model.getWaveform();
		if (wf != null && wf.getSampleCount() > 0) {
			double dur = model.getDurationSeconds();
			int samples = (int) Math.min(width, 800);
			float halfH = ROW_HEIGHT * 0.4f;
			for (int i = 0; i < samples; i++) {
				double t = viewStart + (viewEnd - viewStart) * (double) i / samples;
				if (t < 0 || t > dur) continue;
				int idx = wf.timeToIndex(t);
				float s = wf.getSample(idx);
				float x = timeToScreen(t, viewStart, zoom);
				if (x < -1 || x > width + 1) continue;
				float y0 = minY + ROW_HEIGHT * 0.5f;
				float y1 = y0 - s * halfH;
				ImGui.getWindowDrawList().addLine(minX + x, y0, minX + x, y1, WAVEFORM_COLOR, 1f);
			}
		} else {
			ImGui.textDisabled("~~~~ 波形（导入音乐后生成）~~~~");
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawFrequencyDots(float rowY, List<FrequencyEvent> events, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (FrequencyEvent e : events) {
			double t = e.getTimeSeconds();
			if (t < viewStart || t > viewEnd) continue;
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= -4 && x <= width + 4) {
				float r = 3f + e.getEnergy() * 3f;
				ImGui.getWindowDrawList().addCircleFilled(baseX + x, baseY, r, EVENT_DOT_COLOR);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawAnimationEventBlocks(float rowY, List<TimelineAnimationEvent> events, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (TimelineAnimationEvent e : events) {
			double t = e.getTimeSeconds();
			double end = e.getEndTimeSeconds();
			if (end < viewStart || t > viewEnd) continue;
			float x = timeToScreen(t, viewStart, zoom);
			float w = (float) (e.getDurationSeconds() * zoom);
			w = Math.max(8f, Math.min(w, width - x + 1));
			if (x + w >= -2 && x <= width + 2) {
				ImGui.getWindowDrawList().addRectFilled(
					baseX + x, baseY - ROW_HEIGHT * 0.35f,
					baseX + x + w, baseY + ROW_HEIGHT * 0.35f,
					KEYFRAME_COLOR, 2f
				);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawCameraKeyframeRow(float rowY, List<CameraKeyframe> keyframes, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (CameraKeyframe k : keyframes) {
			double t = k.getTimeSeconds();
			if (t < viewStart || t > viewEnd) continue;
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= -8 && x <= width + 8) {
				ImGui.getWindowDrawList().addTriangleFilled(
					baseX + x, baseY - 6,
					baseX + x - 5, baseY + 5,
					baseX + x + 5, baseY + 5,
					KEYFRAME_COLOR
				);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}

	private float drawGlobalEventRow(float rowY, List<GlobalEvent> events, float width, float zoom, double viewStart, double viewEnd) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(TRACK_LABEL_WIDTH);
		float baseX = ImGui.getCursorScreenPosX();
		float baseY = ImGui.getCursorScreenPosY() + ROW_HEIGHT * 0.5f;
		for (GlobalEvent e : events) {
			double t = e.getTimeSeconds();
			if (t < viewStart || t > viewEnd) continue;
			float x = timeToScreen(t, viewStart, zoom);
			if (x >= -6 && x <= width + 6) {
				ImGui.getWindowDrawList().addCircleFilled(baseX + x, baseY, 5f, GLOBAL_EVENT_COLOR);
			}
		}
		ImGui.setCursorPosY(rowY + ROW_HEIGHT);
		return rowY + ROW_HEIGHT;
	}
}
