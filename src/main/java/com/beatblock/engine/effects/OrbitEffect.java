package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.AnimationEffect;
import net.minecraft.util.math.Vec3d;

/**
 * 绕中心水平环绕：保持 y，x/z 绕 center 旋转。若 centerOverride 为 ZERO 则用 ctx.getStageCenter()。
 */
public final class OrbitEffect implements AnimationEffect {

	private final Vec3d centerOverride;
	private final float radius;

	public OrbitEffect(Vec3d centerOverride, float radius) {
		this.centerOverride = centerOverride != null ? centerOverride : Vec3d.ZERO;
		this.radius = Math.max(0f, radius);
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		Vec3d center = centerOverride.lengthSquared() > 1e-6 ? centerOverride : ctx.getStageCenter();
		double angle = t * Math.PI * 2 * energy;
		double x = center.x + Math.cos(angle) * radius;
		double z = center.z + Math.sin(angle) * radius;
		Vec3d pos = block.getPosition();
		block.setPosition(x, pos.y, z);
	}
}
