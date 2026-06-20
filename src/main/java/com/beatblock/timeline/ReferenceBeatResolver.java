package com.beatblock.timeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从第 1 层参考轨解析 STEP {@code NEXT_BEAT} 用的节拍时刻。
 * <p>
 * 优先 {@code kick}，其次 {@code low}、{@code drums}；与绑定规则的主节奏轨一致。
 */
public final class ReferenceBeatResolver {

	private static final String[] PRIMARY_RHYTHM_PREFERRED = {"kick", "low", "drums"};
	private static final double MERGE_EPSILON_SECONDS = 1e-4;

	private ReferenceBeatResolver() {}

	public static double[] resolveBeatTimesSeconds(Timeline timeline) {
		if (timeline == null || !timeline.hasFeatureTracks()) {
			return new double[0];
		}
		String key = resolvePrimaryRhythmKey(timeline);
		List<FeatureEvent> events = timeline.getFeatureEvents(key);
		if (events.isEmpty()) {
			return new double[0];
		}
		List<Double> times = new ArrayList<>(events.size());
		for (FeatureEvent event : events) {
			if (event == null) continue;
			times.add(event.getTimeSeconds());
		}
		times.sort(Comparator.naturalOrder());
		List<Double> merged = new ArrayList<>(times.size());
		for (double t : times) {
			if (merged.isEmpty() || t - merged.getLast() > MERGE_EPSILON_SECONDS) {
				merged.add(t);
			}
		}
		double[] out = new double[merged.size()];
		for (int i = 0; i < merged.size(); i++) {
			out[i] = merged.get(i);
		}
		return out;
	}

	private static String resolvePrimaryRhythmKey(Timeline timeline) {
		List<String> keys = new ArrayList<>(timeline.getFeatureTracks().keySet());
		for (String preferred : PRIMARY_RHYTHM_PREFERRED) {
			for (String key : keys) {
				if (key != null && key.equalsIgnoreCase(preferred)) {
					return key;
				}
			}
		}
		keys.sort(String.CASE_INSENSITIVE_ORDER);
		return keys.isEmpty() ? "kick" : keys.getFirst();
	}

	public static String describePrimaryRhythmKey(Timeline timeline) {
		if (timeline == null || !timeline.hasFeatureTracks()) {
			return "";
		}
		return resolvePrimaryRhythmKey(timeline);
	}
}
