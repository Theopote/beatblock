package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.AnimationEffect;
import net.minecraft.util.math.Vec3d;

/**
 * 方块升起：y += t * height * energy
 */
public final class RiseEffect implements AnimationEffect {

	private final float height;

	public RiseEffect(float height) {
		this.height = Math.max(0f, height);
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		float y = t * height * energy;
		Vec3d pos = block.getPosition();
		block.setPosition(pos.x, pos.y + y, pos.z);
	}
}
