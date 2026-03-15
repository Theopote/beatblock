package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.FrequencyBands;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动粒子：根据高频能量峰值生成粒子事件（Spark / Dust / Flash）。
 */
public final class ParticleDirector {

	private static final float HIGH_ENERGY_THRESHOLD = 0.2f;
	private static final double MIN_PARTICLE_GAP = 0.15;

	/**
	 * 从频段序列中取“高频”能量，超过阈值且局部峰值处插入粒子事件。
	 */
	public static List<ParticleEvent> generate(List<FrequencyBands> bands, boolean enabled) {
		List<ParticleEvent> out = new ArrayList<>();
		if (!enabled || bands == null || bands.size() < 3) return out;
		double lastTime = -MIN_PARTICLE_GAP - 1;
		for (int i = 1; i < bands.size() - 1; i++) {
			FrequencyBands prev = bands.get(i - 1);
			FrequencyBands cur = bands.get(i);
			FrequencyBands next = bands.get(i + 1);
			float high = cur.getHigh();
			if (high < HIGH_ENERGY_THRESHOLD) continue;
			if (high < prev.getHigh() || high < next.getHigh()) continue;
			double t = cur.getTimeSeconds();
			if (t < lastTime + MIN_PARTICLE_GAP) continue;
			ParticleType type = high > 0.6f ? ParticleType.FLASH : (high > 0.35f ? ParticleType.SPARK : ParticleType.DUST);
			out.add(new ParticleEvent(t, type));
			lastTime = t;
		}
		return out;
	}
}
