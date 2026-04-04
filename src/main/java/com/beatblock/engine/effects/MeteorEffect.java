package com.beatblock.engine.effects;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.AnimationEffect;
import net.minecraft.util.math.Vec3d;

/**
 * 流星坠落：方块从高空（原位 + height）以重力曲线砸向原始位置。
 *
 * <p>运动曲线使用二次加速（t²），模拟重力加速度：
 * <ul>
 *   <li>t=0：方块在起始高空，带横向入射散射偏移</li>
 *   <li>t=1：方块落回原始位置，无偏移</li>
 * </ul>
 *
 * <p>支持的 extraParams：
 * <ul>
 *   <li>{@code meteorHeight} (double, 默认 12.0) — 起始高度偏移（方块数），覆盖构造参数</li>
 *   <li>{@code meteorScatter} (double, 默认 2.5) — 横向切入散射半径（方块数），覆盖构造参数</li>
 * </ul>
 */
public final class MeteorEffect implements AnimationEffect {

	private final float height;
	private final float scatter;

	public MeteorEffect(float height, float scatter) {
		this.height = Math.max(0f, height);
		this.scatter = Math.max(0f, scatter);
	}

	@Override
	public void apply(AnimatedBlock block, float t, float energy, EffectContext ctx) {
		float h  = (float) ctx.paramDouble("meteorHeight",  height);
		float sc = (float) ctx.paramDouble("meteorScatter", scatter);

		Vec3d pos = block.getPosition();

		// 重力加速下落曲线：剩余高度 = h * (1 - t²)
		// t=0 → 在高处；t→1 → 快速砸向原位
		double fall = h * (1.0 - (double) t * t) * energy;

		// 横向入射偏移：t=0 最大，t=1 归零（线性收束）
		// 用方块原始坐标作哈希种子，使每块切入角度不同
		double xOff = sc * Math.sin(pos.x * 1.9 + pos.z * 0.7) * (1.0 - t) * energy;
		double zOff = sc * Math.cos(pos.z * 1.9 + pos.x * 0.7) * (1.0 - t) * energy;

		block.setPosition(pos.x + xOff, pos.y + fall, pos.z + zOff);

		// 远距离感：高空时方块略小，落地时恢复原始大小
		// scale: 从 (1 - 0.5*energy) 线性增长到 1.0
		float scaleMin = 1f - 0.5f * energy;
		float scaleFactor = scaleMin + (1f - scaleMin) * t;
		block.setScale(block.getScale() * scaleFactor);
	}
}
