package com.beatblock.timeline.editor;

import com.beatblock.timeline.Timeline;

import java.util.List;

/**
 * 吸附：靠近 Beat / Event / Grid / Clip 边缘时对齐。
 */
public final class SnapHelper {

	public static final double SNAP_THRESHOLD_SECONDS = 0.08;

	public enum SnapTarget {
		BEAT,
		EVENT,
		GRID,
		CLIP_EDGE,
		NONE
	}

	/**
	 * 若 time 靠近某个吸附点则返回吸附后时间，否则返回原值。
	 */
	public static double snap(double timeSeconds, Timeline timeline, double bpm, boolean snapToGrid, double gridStepSeconds) {
		if (timeline == null) return timeSeconds;
		double best = timeSeconds;
		double bestDist = SNAP_THRESHOLD_SECONDS;
		// Grid
		if (snapToGrid && gridStepSeconds > 0) {
			double grid = Math.round(timeSeconds / gridStepSeconds) * gridStepSeconds;
			double d = Math.abs(timeSeconds - grid);
			if (d < bestDist) { bestDist = d; best = grid; }
		}
		// Beat（简单按 BPM 的拍点）
		if (bpm > 0) {
			double beatDuration = 60.0 / bpm;
			double beat = Math.round(timeSeconds / beatDuration) * beatDuration;
			double d = Math.abs(timeSeconds - beat);
			if (d < bestDist) { bestDist = d; best = beat; }
		}
		return best;
	}
}
