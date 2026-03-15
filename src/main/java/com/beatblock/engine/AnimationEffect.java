package com.beatblock.engine;

/**
 * 动画由多个 Effect 组合而成；每个 Effect 根据时间与能量修改 AnimatedBlock 的状态。
 * time: 0 → 1（动画进度），energy: 音乐能量 0～1，影响高度/速度/粒子等。
 */
public interface AnimationEffect {

	/**
	 * 对单块应用本效果（可修改 block 的 position、velocity、rotation、scale）。
	 *
	 * @param block 当前块的动画状态（每帧会先 resetToOriginal 再依次应用各 effect）
	 * @param time  动画进度 0～1
	 * @param energy 音乐能量 0～1
	 * @param ctx   上下文（如舞台中心，供 Explosion/Spiral/Orbit 使用）
	 */
	void apply(AnimatedBlock block, float time, float energy, EffectContext ctx);
}
