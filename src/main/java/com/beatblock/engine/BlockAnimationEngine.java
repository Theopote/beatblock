package com.beatblock.engine;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import java.util.Map;

import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block Animation Engine 门面：整合 StageObjectSystem、AnimationLibrary、AnimationPlayer。
 * Timeline / 音频驱动 将事件转为 EngineAnimationInstance 交给 Player，每帧 tick 后渲染层从 getCurrentFrameBlocks 取状态。
 */
public final class BlockAnimationEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger(BlockAnimationEngine.class);

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

	public void scheduleTimelineEvent(TimelineAnimationEvent event) {
		if (event == null) return;
		TimelineAnimationActionMode actionMode = event.getActionMode();
		switch (actionMode) {
			case ANIMATE -> scheduleFromTimelineEvent(
				event.getAnimationTypeId(),
				event.getTargetObjectId(),
				event.getTimeSeconds(),
				event.getDurationSeconds(),
				event.getEnergy()
			);
			case PLACE, CLEAR -> LOGGER.debug(
				"Timeline action mode {} is captured for event {}, but world mutation is not implemented yet.",
				actionMode,
				event.getEventId()
			);
		}
	}

	/** 当前帧参与动画的方块及其状态，供渲染层做 Matrix 变换后绘制 */
	public Map<BlockPos, AnimatedBlock> getCurrentFrameBlocks() {
		return animationPlayer.getCurrentFrameBlocks();
	}

	public void clear() {
		animationPlayer.clear();
	}
}
