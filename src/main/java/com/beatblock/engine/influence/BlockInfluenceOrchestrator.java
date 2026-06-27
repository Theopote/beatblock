package com.beatblock.engine.influence;

import com.beatblock.engine.AnimationPlayer;
import com.beatblock.engine.BuildSequencer;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.EngineAnimationInstance;
import com.beatblock.engine.WorldMutationSink;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 统一帧编排：动画 preset 求值 + APPEARANCE 脉冲 + VFX 命中触发 + 建造 EXISTENCE mutation，单入口 apply。
 */
public final class BlockInfluenceOrchestrator {

	private final BlockInfluenceEvaluator evaluator = new BlockInfluenceEvaluator();
	private final AppearancePulseTracker appearanceTracker = new AppearancePulseTracker();
	private final ImpactVfxTracker impactVfxTracker = new ImpactVfxTracker();
	private final Map<String, Float> lastProgressByInstance = new HashMap<>();

	private InfluenceFrame lastFrame = new InfluenceFrame();
	private Vec3d runtimeCameraPosition;
	private Vec3d runtimeCameraForward;

	public BlockInfluenceOrchestrator() {
	}

	public void setRuntimeCamera(Vec3d cameraPosition, Vec3d cameraForward) {
		this.runtimeCameraPosition = cameraPosition;
		this.runtimeCameraForward = cameraForward;
	}

	public InfluenceFrame getLastFrame() {
		return lastFrame;
	}

	/**
	 * @param sink 真正的世界写入出口；world 仅用于读取当前方块状态，不在这里被写入。
	 *             由调用方决定写到哪个权威世界、在哪个线程写（见 BeatBlockAuthoritativeWorldMutator）。
	 */
	public void applyFrame(InfluenceFrame frame, AnimationPlayer animationPlayer, WorldMutationSink sink) {
		if (animationPlayer != null && frame != null) {
			animationPlayer.replaceCurrentFrameBlocks(frame.getAnimatedBlocks());
		}
		if (frame != null && sink != null && !frame.getWorldMutations().isEmpty()) {
			sink.apply(frame.getWorldMutations());
		}
	}

	/**
	 * 计算并应用单帧影响；返回本帧世界 mutation 数量。
	 * world 仅用于读取（isChunkLoaded/getBlockState 等），真正的写入通过 sink 完成。
	 */
	public int tick(
		double timelineTimeSeconds,
		AnimationPlayer animationPlayer,
		BuildSequencer buildSequencer,
		World world,
		WorldMutationSink sink
	) {
		InfluenceFrame frame = new InfluenceFrame();

		// 实例结束（或尚未开始而被移除）时统一清理：lastProgressByInstance 与
		// appearanceTracker 的闪烁状态都不再需要任何世界写入——闪烁纯粹是渲染层外观覆盖，
		// 停止设置覆盖、对应 AnimatedBlock 不再出现在下一帧即等于「自动还原」。
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
				impactVfxTracker.clearInstance(key);
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

		applyFrame(frame, animationPlayer, sink);
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

        String instanceKey = InfluenceInstanceKeys.key(instance);
		float t = instance.getProgress(timelineTimeSeconds);
		float previousT = lastProgressByInstance.getOrDefault(instanceKey, 0f);
		lastProgressByInstance.put(instanceKey, t);

		float energy = instance.getEnergy();
		EffectContext ctx = new EffectContext(
			instance.getTarget().getCenter(),
			instance.getExtraParams(),
			runtimeCameraPosition,
			runtimeCameraForward
		);

		for (BlockPos pos : instance.getTarget().getBlocks()) {
			var block = frame.animatedBlockFor(pos);
			block.resetToOriginal();
			evaluator.applyPreset(block, preset, t, energy, ctx);
		}

		if (world != null) {
			appearanceTracker.contribute(instanceKey, instance, preset, frame, world, t, previousT, ctx);
		}
		impactVfxTracker.contribute(instanceKey, instance, preset, frame, t, previousT, ctx);
	}
}
