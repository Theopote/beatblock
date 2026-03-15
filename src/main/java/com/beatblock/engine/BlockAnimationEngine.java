package com.beatblock.engine;

import java.util.Map;

import net.minecraft.util.math.BlockPos;

/**
 * Block Animation Engine 门面：整合 StageObjectSystem、AnimationLibrary、AnimationPlayer。
 * Timeline / 音频驱动 将事件转为 EngineAnimationInstance 交给 Player，每帧 tick 后渲染层从 getCurrentFrameBlocks 取状态。
 */
public final class BlockAnimationEngine {

	private final StageObjectSystem stageObjectSystem = new StageObjectSystem();
	private final AnimationLibrary animationLibrary = new AnimationLibrary();
	private final AnimationPlayer animationPlayer = new AnimationPlayer();

	public StageObjectSystem getStageObjectSystem() {
		return stageObjectSystem;
	}

	public AnimationLibrary getAnimationLibrary() {
		return animationLibrary;
	}

	public AnimationPlayer getAnimationPlayer() {
		return animationPlayer;
	}

	/**
	 * 每帧调用：根据时间线时间更新动画，移除已结束实例。
	 */
	public void tick(double timelineTimeSeconds) {
		animationPlayer.removeEnded(timelineTimeSeconds);
		animationPlayer.update(timelineTimeSeconds);
	}

	/**
	 * 将 Timeline 动画事件加入播放器（需将 targetObjectId 解析为 StageObject，animationTypeId 解析为 AnimationDefinition）。
	 */
	public void scheduleFromTimelineEvent(String animationTypeId, String targetObjectId, double startTimeSeconds, double durationSeconds, float energy) {
		AnimationDefinition def = animationLibrary.get(animationTypeId);
		StageObject target = stageObjectSystem.get(targetObjectId);
		if (def == null || target == null) return;
		double endTime = startTimeSeconds + Math.max(0.01, durationSeconds);
		animationPlayer.addInstance(new EngineAnimationInstance(def, target, startTimeSeconds, endTime, energy));
	}

	/** 当前帧参与动画的方块及其状态，供渲染层做 Matrix 变换后绘制 */
	public Map<BlockPos, AnimatedBlock> getCurrentFrameBlocks() {
		return animationPlayer.getCurrentFrameBlocks();
	}

	public void clear() {
		animationPlayer.clear();
	}
}
