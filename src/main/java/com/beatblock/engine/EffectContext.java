package com.beatblock.engine;

import net.minecraft.util.math.Vec3d;

/**
 * 应用动画效果时的上下文，如舞台对象中心（用于爆炸、螺旋、环绕等）。
 */
public final class EffectContext {

	private final Vec3d stageCenter;

	public EffectContext(Vec3d stageCenter) {
		this.stageCenter = stageCenter != null ? stageCenter : Vec3d.ZERO;
	}

	public Vec3d getStageCenter() {
		return stageCenter;
	}
}
