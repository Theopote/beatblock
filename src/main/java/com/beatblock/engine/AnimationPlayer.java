package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责播放动画：维护当前活跃实例，每帧根据时间线时间更新并对方块应用效果。
 */
public final class AnimationPlayer {

	private final List<EngineAnimationInstance> activeInstances = new ArrayList<>();
	/** 当前帧每块（按 BlockPos 去重）的动画状态，供渲染读取；每帧由 applyAnimation 填充 */
	private final Map<BlockPos, AnimatedBlock> currentFrameBlocks = new HashMap<>();

	public List<EngineAnimationInstance> getActiveInstances() {
		return new ArrayList<>(activeInstances);
	}

	public void addInstance(EngineAnimationInstance instance) {
		if (instance != null) activeInstances.add(instance);
	}

	public void removeEnded(double timelineTimeSeconds) {
		activeInstances.removeIf(inst -> !inst.isActiveAt(timelineTimeSeconds));
	}

	/**
	 * 每帧调用：根据时间线时间更新所有活跃实例，对目标方块的 AnimatedBlock 应用效果。
	 * 执行后可通过 getCurrentFrameBlocks() 取得当前帧每块的状态用于渲染。
	 */
	public void update(double timelineTimeSeconds) {
		currentFrameBlocks.clear();
		for (EngineAnimationInstance anim : activeInstances) {
			if (!anim.isActiveAt(timelineTimeSeconds)) continue;
			applyAnimation(anim, anim.getProgress(timelineTimeSeconds));
		}
	}

	/**
	 * 对单条实例应用动画：遍历目标方块，每块先取或创建 AnimatedBlock 并重置，再依次应用 definition 的 effects。
	 */
	void applyAnimation(EngineAnimationInstance anim, float t) {
		StageObject target = anim.getTarget();
		if (target == null) return;
		float energy = anim.getEnergy();
		EffectContext ctx = new EffectContext(target.getCenter());
		for (BlockPos pos : target.getBlocks()) {
			AnimatedBlock block = currentFrameBlocks.computeIfAbsent(pos.toImmutable(), p -> new AnimatedBlock(p));
			block.resetToOriginal();
			for (AnimationEffect effect : anim.getDefinition().getEffects()) {
				effect.apply(block, t, energy, ctx);
			}
		}
	}

	/** 当前帧参与动画的方块及其状态（只读），渲染层可据此做 Matrix 变换后绘制 */
	public Map<BlockPos, AnimatedBlock> getCurrentFrameBlocks() {
		return currentFrameBlocks;
	}

	public void clear() {
		activeInstances.clear();
		currentFrameBlocks.clear();
	}
}
