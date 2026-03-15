package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.EnergyFrame;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐结构分析：从能量曲线 Energy(t) 划分段落（Intro / Verse / Build / Drop / Break / Outro）。
 * 平缓→Intro；逐渐增强→Build；爆发→Drop；降低→Break；结尾→Outro。
 */
public final class MusicStructureAnalyzer {

	private static final double SECTION_MIN_DURATION = 4.0;
	private static final double SMOOTH_WINDOW = 2.0;

	/**
	 * 根据能量帧与总时长划分段落。
	 */
	public static List<MusicSection> analyze(List<EnergyFrame> energyFrames, double durationSeconds) {
		List<MusicSection> sections = new ArrayList<>();
		if (energyFrames == null || energyFrames.isEmpty() || durationSeconds <= 0) {
			sections.add(new MusicSection(0, durationSeconds, SectionType.VERSE));
			return sections;
		}
		float[] energies = new float[energyFrames.size()];
		double[] times = new double[energyFrames.size()];
		for (int i = 0; i < energyFrames.size(); i++) {
			EnergyFrame f = energyFrames.get(i);
			times[i] = f.getTimeSeconds();
			energies[i] = f.getEnergy();
		}
		float maxE = 0;
		for (float e : energies) if (e > maxE) maxE = e;
		if (maxE < 1e-6f) maxE = 1f;
		// 归一化
		for (int i = 0; i < energies.length; i++) energies[i] /= maxE;

		double t = 0;
		int idx = 0;
		while (t < durationSeconds && idx < times.length) {
			double segmentEnd = Math.min(t + SECTION_MIN_DURATION, durationSeconds);
			float avg = averageIn(energies, times, t, segmentEnd);
			float trend = trendIn(energies, times, t, segmentEnd);
			SectionType type = classifySegment(avg, trend, t, durationSeconds);
			sections.add(new MusicSection(t, segmentEnd, type));
			t = segmentEnd;
			if (t >= durationSeconds) break;
			idx++;
		}
		mergeAdjacentSameType(sections);
		return sections;
	}

	private static float averageIn(float[] e, double[] t, double from, double to) {
		float sum = 0;
		int n = 0;
		for (int i = 0; i < t.length; i++) {
			if (t[i] >= from && t[i] <= to) { sum += e[i]; n++; }
		}
		return n > 0 ? sum / n : 0;
	}

	private static float trendIn(float[] e, double[] t, double from, double to) {
		int first = -1, last = -1;
		for (int i = 0; i < t.length; i++) {
			if (t[i] >= from && t[i] <= to) {
				if (first < 0) first = i;
				last = i;
			}
		}
		if (first < 0 || last <= first) return 0;
		return (e[last] - e[first]) / (last - first + 1e-6f);
	}

	private static SectionType classifySegment(float avg, float trend, double segmentStart, double totalDuration) {
		double progress = segmentStart / Math.max(0.01, totalDuration);
		if (progress < 0.08) return SectionType.INTRO;
		if (progress > 0.92) return SectionType.OUTRO;
		if (trend > 0.05f && avg < 0.6f) return SectionType.BUILD;
		if (avg > 0.7f) return SectionType.DROP;
		if (avg < 0.25f) return SectionType.BREAK;
		return SectionType.VERSE;
	}

	private static void mergeAdjacentSameType(List<MusicSection> sections) {
		for (int i = sections.size() - 1; i > 0; i--) {
			MusicSection cur = sections.get(i);
			MusicSection prev = sections.get(i - 1);
			if (prev.getType() == cur.getType() && Math.abs(prev.getEndSeconds() - cur.getStartSeconds()) < 0.5) {
				sections.set(i - 1, new MusicSection(prev.getStartSeconds(), cur.getEndSeconds(), prev.getType()));
				sections.remove(i);
			}
		}
	}
}
