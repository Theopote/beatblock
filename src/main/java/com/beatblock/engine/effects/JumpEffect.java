package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.AnimationEffect;
import net.minecraft.util.math.Vec3d;

/**
 * 方块跳起：y += sin(t * π) * height * energy
 */
public final class JumpEffect implements AnimationEffect {

	private final float height;

	public JumpEffect(float height) {
		this.height = Math.max(0f, height);
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		float h = height * energy;
		float y = (float) Math.sin(t * Math.PI) * h;
		Vec3d pos = block.getPosition();
		block.setPosition(pos.x, pos.y + y, pos.z);
	}
}
