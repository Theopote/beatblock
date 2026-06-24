package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.rendering.TimelineTrackListState;

/**
 * 时间线工具栏视图状态：Zoom/Speed 预设与 Fit 缩放。
 */
public final class TimelineToolbarViewPresenter {

	public static final String[] ZOOM_PRESET_LABELS = { "0.25x", "0.5x", "1x", "2x", "3x", "4x" };
	private static final float ZOOM_BASE = 10f;
	public static final float[] ZOOM_PRESET_VALUES = {
		0.25f * ZOOM_BASE, 0.5f * ZOOM_BASE, ZOOM_BASE, 2f * ZOOM_BASE, 3f * ZOOM_BASE, 4f * ZOOM_BASE
	};
	public static final String[] SPEED_LABELS = { "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x" };
	public static final double[] SPEED_VALUES = { 0.5, 0.75, 1.0, 1.25, 1.5, 2.0 };

	public record TrackHeightViewState(float min, float max, float current) {}

	private TimelineToolbarViewPresenter() {}

	public static int indexOfClosestZoom(float zoom) {
		int best = 0;
		float bestDiff = Math.abs(zoom - ZOOM_PRESET_VALUES[0]);
		for (int i = 1; i < ZOOM_PRESET_VALUES.length; i++) {
			float diff = Math.abs(zoom - ZOOM_PRESET_VALUES[i]);
			if (diff < bestDiff) {
				bestDiff = diff;
				best = i;
			}
		}
		return best;
	}

	public static int indexOfClosestSpeed(double speed) {
		int best = 0;
		double bestDiff = Math.abs(speed - SPEED_VALUES[0]);
		for (int i = 1; i < SPEED_VALUES.length; i++) {
			double diff = Math.abs(speed - SPEED_VALUES[i]);
			if (diff < bestDiff) {
				bestDiff = diff;
				best = i;
			}
		}
		return best;
	}

	public static boolean applyZoomPreset(TimelineEditor editor, int presetIndex) {
		if (editor == null || presetIndex < 0 || presetIndex >= ZOOM_PRESET_VALUES.length) {
			return false;
		}
		editor.getViewState().setZoom(ZOOM_PRESET_VALUES[presetIndex]);
		return true;
	}

	public static boolean applySpeedPreset(
		TimelineEditor editor,
		TimelineTransportPresenter transport,
		int presetIndex
	) {
		if (presetIndex < 0 || presetIndex >= SPEED_VALUES.length) {
			return false;
		}
		if (transport != null) {
			transport.setPlaybackSpeed(editor, SPEED_VALUES[presetIndex]);
		} else if (editor != null) {
			editor.getClock().setPlaybackSpeed(SPEED_VALUES[presetIndex]);
		}
		return true;
	}

	public static boolean fitToDuration(TimelineEditor editor, Timeline timeline, float availableWidth) {
		if (editor == null || availableWidth <= 0) {
			return false;
		}
		double duration = timeline != null ? timeline.getDurationSeconds() : 0;
		if (duration <= 0) {
			duration = 60;
		}
		editor.getViewState().fitToDuration(duration, availableWidth);
		return true;
	}

	public static TrackHeightViewState trackHeightViewState(TimelineEditor editor) {
		if (editor == null) {
			return new TrackHeightViewState(0f, 0f, 0f);
		}
		TimelineTrackListState trackState = editor.getTrackListState();
		return new TrackHeightViewState(
			trackState.getAudioRowHeightMin(),
			trackState.getAudioRowHeightMax(),
			trackState.getAudioRowHeight()
		);
	}

	public static boolean setTrackHeight(TimelineEditor editor, float height) {
		if (editor == null) {
			return false;
		}
		editor.getTrackListState().setAudioRowHeight(height);
		return true;
	}

	public static boolean resetTrackHeight(TimelineEditor editor) {
		if (editor == null) {
			return false;
		}
		editor.getTrackListState().resetAudioRowHeight();
		return true;
	}
}
