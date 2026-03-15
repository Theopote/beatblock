package com.beatblock.automap.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 节奏模式生成：根据段落类型与复杂度决定是否采纳某节奏事件、最小间隔等。
 */
public final class PatternGenerator {

	/**
	 * 根据复杂度得到最小事件间隔（秒）。Low 更稀疏，Extreme 更密。
	 */
	public static double getMinGapSeconds(Complexity complexity) {
		if (complexity == null) return 0.12;
		return switch (complexity) {
			case LOW -> 0.25;
			case MEDIUM -> 0.12;
			case HIGH -> 0.06;
			case EXTREME -> 0.04;
		};
	}

	/**
	 * 根据复杂度得到能量阈值，低于则丢弃。Low 只保留强拍，Extreme 几乎全留。
	 */
	public static float getEnergyThreshold(Complexity complexity) {
		if (complexity == null) return 0.2f;
		return switch (complexity) {
			case LOW -> 0.5f;
			case MEDIUM -> 0.25f;
			case HIGH -> 0.15f;
			case EXTREME -> 0.05f;
		};
	}

	/**
	 * 按间隔与能量阈值过滤节奏事件。
	 */
	public static List<RhythmEvent> filter(List<RhythmEvent> events, Complexity complexity) {
		if (events == null) return List.of();
		double minGap = getMinGapSeconds(complexity);
		float minEnergy = getEnergyThreshold(complexity);
		List<RhythmEvent> out = new ArrayList<>();
		double lastTime = -minGap - 1;
		for (RhythmEvent e : events) {
			if (e.getEnergy() < minEnergy) continue;
			if (e.getTimeSeconds() < lastTime + minGap) continue;
			out.add(e);
			lastTime = e.getTimeSeconds();
		}
		return out;
	}
}
