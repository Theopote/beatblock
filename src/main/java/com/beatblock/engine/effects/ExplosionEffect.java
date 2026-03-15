package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.AnimationEffect;
import net.minecraft.util.math.Vec3d;

/**
 * 爆炸效果：从中心向外扩，沿 dir = normalize(position - center) 移动。
 * 若构造时 center 为 ZERO，apply 时使用 ctx.getStageCenter()。
 */
public final class ExplosionEffect implements AnimationEffect {

	private final Vec3d centerOverride;
	private final float strength;

	public ExplosionEffect(Vec3d centerOverride, float strength) {
		this.centerOverride = centerOverride != null ? centerOverride : Vec3d.ZERO;
		this.strength = Math.max(0f, strength);
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		Vec3d center = centerOverride.lengthSquared() > 1e-6 ? centerOverride : ctx.getStageCenter();
		Vec3d pos = block.getPosition();
		Vec3d dir = pos.subtract(center);
		double len = dir.length();
		if (len < 1e-6) return;
		dir = dir.multiply(1.0 / len);
		double move = strength * t * energy;
		Vec3d delta = dir.multiply(move);
		block.setPosition(pos.add(delta));
	}
}
