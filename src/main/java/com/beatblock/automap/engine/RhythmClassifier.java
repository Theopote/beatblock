package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.FrequencyBands;

import java.util.ArrayList;
import java.util.List;

/**
 * 节奏分类：将 DetectedBeat 与对应时刻的频段能量结合，分类为 Kick / Snare / HiHat。
 * 低频主导→Kick，中频→Snare，高频→HiHat。
 */
public final class RhythmClassifier {

	private static final double BAND_MATCH_TIME_TOLERANCE = 0.1;

	/**
	 * 对每个 beat 找到最近的 FrequencyBands，取主导频段映射为 RhythmType。
	 */
	public static List<RhythmEvent> classify(List<DetectedBeat> beats, List<FrequencyBands> bands) {
		List<RhythmEvent> out = new ArrayList<>();
		if (beats == null || beats.isEmpty()) return out;
		if (bands == null || bands.isEmpty()) {
			for (DetectedBeat b : beats) out.add(new RhythmEvent(b.getTimeSeconds(), RhythmType.KICK, b.getStrength()));
			return out;
		}
		for (DetectedBeat beat : beats) {
			double t = beat.getTimeSeconds();
			FrequencyBands fb = findClosestBands(bands, t);
			float low = fb != null ? fb.getLow() : 0.33f;
			float mid = fb != null ? fb.getMid() : 0.33f;
			float high = fb != null ? fb.getHigh() : 0.34f;
			float sum = low + mid + high;
			if (sum < 1e-6f) { low = mid = high = 1f / 3f; sum = 1f; } else { low /= sum; mid /= sum; high /= sum; }
			RhythmType type;
			if (low >= mid && low >= high) type = RhythmType.KICK;
			else if (mid >= low && mid >= high) type = RhythmType.SNARE;
			else type = RhythmType.HIHAT;
			out.add(new RhythmEvent(t, type, beat.getStrength()));
		}
		return out;
	}

	private static FrequencyBands findClosestBands(List<FrequencyBands> bands, double time) {
		FrequencyBands best = null;
		double bestDiff = Double.POSITIVE_INFINITY;
		for (FrequencyBands b : bands) {
			double d = Math.abs(b.getTimeSeconds() - time);
			if (d < bestDiff) { bestDiff = d; best = b; }
		}
		return best;
	}
}
