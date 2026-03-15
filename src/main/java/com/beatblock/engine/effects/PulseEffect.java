package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.AnimationEffect;

/**
 * 脉冲：缩放随进度变化，scale = 1 + (peak - 1) * sin(t * π)
 */
public final class PulseEffect implements AnimationEffect {

	private final float peakScale;

	public PulseEffect(float peakScale) {
		this.peakScale = Math.max(1f, peakScale);
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		float s = (float) (1 + (peakScale - 1) * Math.sin(t * Math.PI) * energy);
		block.setScale(block.getScale() * s);
	}
}
