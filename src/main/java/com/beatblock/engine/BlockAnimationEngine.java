package com.beatblock.engine;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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
	private final BlockControlExecutor blockControlExecutor = new BlockControlExecutor(stageObjectSystem);
	private final BuildSequencer buildSequencer = new BuildSequencer(stageObjectSystem);

	public StageObjectSystem getStageObjectSystem() {
		return stageObjectSystem;
	}

	public AnimationLibrary getAnimationLibrary() {
		return animationLibrary;
	}

	public AnimationPlayer getAnimationPlayer() {
		return animationPlayer;
	}

	public BlockControlExecutor getBlockControlExecutor() {
		return blockControlExecutor;
	}

	public BuildSequencer getBuildSequencer() {
		return buildSequencer;
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
		if (actionMode == TimelineAnimationActionMode.ANIMATE) {
			scheduleFromTimelineEventWithSpatial(event);
		}
	}

	private void scheduleFromTimelineEventWithSpatial(TimelineAnimationEvent event) {
		if (event == null) return;
		AnimationDefinition def = animationLibrary.get(event.getAnimationTypeId());
		StageObject target = stageObjectSystem.get(event.getTargetObjectId());
		if (def == null || target == null) return;

		Map<String, Object> params = event.getParameters();
		SpatialDispatchMode spatialMode = SpatialDispatchMode.fromValue(params.get("spatialMode"));
		double stepDelay = resolveSpatialStepDelay(params, spatialMode, event.getDurationSeconds(), target.getBlocks().size());
		if (spatialMode == SpatialDispatchMode.ALL || stepDelay <= 0.0 || target.getBlocks().size() <= 1) {
			double endTime = event.getTimeSeconds() + Math.max(0.01, event.getDurationSeconds());
			animationPlayer.addInstance(new EngineAnimationInstance(
				def, target, event.getTimeSeconds(), endTime, event.getEnergy(), params));
			return;
		}

		List<BlockPos> ordered = sortBlocksForSpatialMode(target, spatialMode, event);
		Vec3d center = target.getCenter();
		double baseStart = event.getTimeSeconds();
		double duration = Math.max(0.01, event.getDurationSeconds());
		float energy = event.getEnergy();
		for (int i = 0; i < ordered.size(); i++) {
			BlockPos block = ordered.get(i);
			double start = baseStart + i * stepDelay;
			double end = start + duration;
			StageObject perBlockTarget = new StageObject(
				target.getId() + "#" + i,
				target.getName(),
				List.of(block),
				center
			);
			animationPlayer.addInstance(new EngineAnimationInstance(def, perBlockTarget, start, end, energy, params));
		}
	}

	private static double resolveSpatialStepDelay(Map<String, Object> params, SpatialDispatchMode mode, double durationSeconds, int blockCount) {
		if (mode == null || mode == SpatialDispatchMode.ALL || blockCount <= 1) return 0.0;
		Object raw = params != null ? params.get("sequentialDelaySeconds") : null;
		double explicit = readDouble(raw, -1.0);
		if (explicit >= 0.0) return explicit;

		double duration = Math.max(0.05, durationSeconds);
		double byDuration = duration / Math.max(2.0, Math.min(28.0, blockCount * 0.6));
		return Math.max(0.01, Math.min(0.06, byDuration));
	}

	private List<BlockPos> sortBlocksForSpatialMode(StageObject target, SpatialDispatchMode mode, TimelineAnimationEvent event) {
		List<BlockPos> blocks = new ArrayList<>(target.getBlocks());
		if (blocks.size() <= 1 || mode == SpatialDispatchMode.ALL) return blocks;
		Vec3d center = target.getCenter();
		long seed = spatialSeed(event, target);

		switch (mode) {
			case SEQUENTIAL -> blocks.sort(Comparator
				.comparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getZ)
				.thenComparingInt(BlockPos::getY));
			case RADIAL -> blocks.sort(Comparator
				.comparingDouble((BlockPos p) -> distanceSqToCenter(p, center))
				.thenComparingInt(BlockPos::getY));
			case SPIRAL -> blocks.sort(Comparator
				.comparingDouble((BlockPos p) -> angleAroundCenter(p, center))
				.thenComparingDouble(p -> distanceSqToCenter(p, center)));
			case RANDOM -> blocks.sort(Comparator.comparingLong(p -> mixedHash(seed, p)));
			case ALL -> {
				return blocks;
			}
		}
		return blocks;
	}

	private static double distanceSqToCenter(BlockPos pos, Vec3d center) {
		double dx = (pos.getX() + 0.5) - center.x;
		double dy = pos.getY() - center.y;
		double dz = (pos.getZ() + 0.5) - center.z;
		return dx * dx + dy * dy + dz * dz;
	}

	private static double angleAroundCenter(BlockPos pos, Vec3d center) {
		double dx = (pos.getX() + 0.5) - center.x;
		double dz = (pos.getZ() + 0.5) - center.z;
		double angle = Math.atan2(dz, dx);
		if (angle < 0) angle += Math.PI * 2.0;
		return angle;
	}

	private static long spatialSeed(TimelineAnimationEvent event, StageObject target) {
		long t = Double.doubleToLongBits(event.getTimeSeconds());
		long id = Objects.hashCode(event.getEventId());
		long targetId = Objects.hashCode(target.getId());
		return (t ^ (id * 31L) ^ (targetId * 131L));
	}

	private static long mixedHash(long seed, BlockPos p) {
		long h = seed;
		h ^= ((long) p.getX()) * 0x9E3779B185EBCA87L;
		h ^= ((long) p.getY()) * 0xC2B2AE3D27D4EB4FL;
		h ^= ((long) p.getZ()) * 0x165667B19E3779F9L;
		h ^= (h >>> 33);
		h *= 0xff51afd7ed558ccdL;
		h ^= (h >>> 33);
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= (h >>> 33);
		return h;
	}

	private static double readDouble(Object raw, double fallback) {
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}

	public List<BlockControlExecutor.BlockMutation> planControlMutations(TimelineAnimationEvent event, World world) {
		if (event == null || world == null) return List.of();
		return blockControlExecutor.planMutations(world, event);
	}

	public BlockControlExecutor.ControlPlan planControl(TimelineAnimationEvent event, World world) {
		if (event == null || world == null) {
			return new BlockControlExecutor.ControlPlan(
				TimelineAnimationActionMode.ANIMATE,
				"",
				List.of(),
				BlockControlExecutor.ControlSkipReason.INVALID_INPUT,
				0,
				0
			);
		}
		return blockControlExecutor.plan(world, event);
	}

	public void applyControlMutations(World world, List<BlockControlExecutor.BlockMutation> mutations) {
		if (world == null || mutations == null || mutations.isEmpty()) return;
		blockControlExecutor.applyMutations(world, mutations);
	}

	/** 当前帧参与动画的方块及其状态，供渲染层做 Matrix 变换后绘制 */
	public Map<BlockPos, AnimatedBlock> getCurrentFrameBlocks() {
		return animationPlayer.getCurrentFrameBlocks();
	}

	public void clear() {
		animationPlayer.clear();
		buildSequencer.clear();
	}
}
