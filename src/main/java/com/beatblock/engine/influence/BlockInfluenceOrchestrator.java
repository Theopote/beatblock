package com.beatblock.engine.influence;

import com.beatblock.engine.AnimationPlayer;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.BuildSequencer;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.EngineAnimationInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 统一帧编排：动画 preset 求值 + APPEARANCE 脉冲 + 建造 EXISTENCE mutation，单入口 apply。
 */
public final class BlockInfluenceOrchestrator {

	private final BlockControlExecutor blockControlExecutor;
	private final BlockInfluenceEvaluator evaluator = new BlockInfluenceEvaluator();
	private final AppearancePulseTracker appearanceTracker = new AppearancePulseTracker();
	private final Map<String, Float> lastProgressByInstance = new HashMap<>();

	private InfluenceFrame lastFrame = new InfluenceFrame();

	public BlockInfluenceOrchestrator(BlockControlExecutor blockControlExecutor) {
		this.blockControlExecutor = blockControlExecutor;
	}

	public InfluenceFrame getLastFrame() {
		return lastFrame;
	}

	public void applyFrame(InfluenceFrame frame, AnimationPlayer animationPlayer, World world) {
		if (animationPlayer != null && frame != null) {
			animationPlayer.replaceCurrentFrameBlocks(frame.getAnimatedBlocks());
		}
		if (world != null && frame != null && blockControlExecutor != null) {
			blockControlExecutor.applyMutations(world, frame.getWorldMutations());
		}
	}

	/**
	 * 计算并应用单帧影响；返回本帧世界 mutation 数量。
	 */
	public int tick(
		double timelineTimeSeconds,
		AnimationPlayer animationPlayer,
		BuildSequencer buildSequencer,
		World world
	) {
		InfluenceFrame frame = new InfluenceFrame();

		if (animationPlayer != null && world != null) {
			for (EngineAnimationInstance instance : animationPlayer.getActiveInstances()) {
				if (instance.isActiveAt(timelineTimeSeconds)) continue;
				if (timelineTimeSeconds <= instance.getEndTimeSeconds()) continue;
				appearanceTracker.revert(InfluenceInstanceKeys.key(instance), frame, world);
			}
		}

		Set<String> endingKeys = new HashSet<>();
		if (animationPlayer != null) {
			for (EngineAnimationInstance instance : animationPlayer.getActiveInstances()) {
				if (!instance.isActiveAt(timelineTimeSeconds)) {
					endingKeys.add(InfluenceInstanceKeys.key(instance));
				}
			}
			animationPlayer.removeEnded(timelineTimeSeconds);
			for (String key : endingKeys) {
				lastProgressByInstance.remove(key);
				appearanceTracker.clearInstance(key);
			}
		}

		if (animationPlayer != null) {
			for (EngineAnimationInstance instance : animationPlayer.getActiveInstances()) {
				if (!instance.isActiveAt(timelineTimeSeconds)) continue;
				contributeAnimation(frame, instance, timelineTimeSeconds, world);
			}
		}
		if (buildSequencer != null && world != null) {
			buildSequencer.contributeExistenceMutations(frame, timelineTimeSeconds, world);
		}

		applyFrame(frame, animationPlayer, world);
		lastFrame = frame;
		return frame.getWorldMutations().size();
	}

	private void contributeAnimation(
		InfluenceFrame frame,
		EngineAnimationInstance instance,
		double timelineTimeSeconds,
		World world
	) {
		if (instance.getTarget() == null || instance.getDefinition() == null) return;
		BlockInfluencePreset preset = instance.getDefinition().getPreset();
		if (preset == null) return;

		String instanceKey = InfluenceInstanceKeys.key(instance);
		float t = instance.getProgress(timelineTimeSeconds);
		float previousT = lastProgressByInstance.getOrDefault(instanceKey, 0f);
		lastProgressByInstance.put(instanceKey, t);

		float energy = instance.getEnergy();
		EffectContext ctx = new EffectContext(instance.getTarget().getCenter(), instance.getExtraParams());

		for (BlockPos pos : instance.getTarget().getBlocks()) {
			var block = frame.animatedBlockFor(pos);
			block.resetToOriginal();
			evaluator.applyPreset(block, preset, t, energy, ctx);
		}

		if (world != null) {
			appearanceTracker.contribute(instanceKey, instance, preset, frame, world, t, previousT, ctx);
		}
	}
}
