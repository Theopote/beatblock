package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.AnimationEffect;
import net.minecraft.util.math.Vec3d;

/**
 * 波浪：y += sin(block.x * frequency + t * 6) * amplitude * energy
 */
public final class WaveEffect implements AnimationEffect {

	private final float amplitude;
	private final float frequency;

	public WaveEffect(float amplitude, float frequency) {
		this.amplitude = amplitude;
		this.frequency = frequency;
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		Vec3d pos = block.getPosition();
		double wave = Math.sin(pos.x * frequency + t * 6) * amplitude * energy;
		block.setPosition(pos.x, pos.y + wave, pos.z);
	}
}
