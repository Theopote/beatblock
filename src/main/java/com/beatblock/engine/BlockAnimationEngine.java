package com.beatblock.engine;

import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 第 3 层 — 舞台播放器门面：整合 StageObjectSystem、AnimationLibrary、AnimationPlayer。
 * <p>
 * 只接受 {@link com.beatblock.timeline.TimelineAnimationEvent}，不感知音频分析。
 * 由 {@link com.beatblock.client.BeatBlockClientDriver} 按时间轴时钟派发。
 */
public final class BlockAnimationEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger(BlockAnimationEngine.class);

	private final StageObjectSystem stageObjectSystem = new StageObjectSystem();
	private final AnimationLibrary animationLibrary = new AnimationLibrary();
	private final AnimationPlayer animationPlayer = new AnimationPlayer();
	private final BlockControlExecutor blockControlExecutor = new BlockControlExecutor(stageObjectSystem);
	private final BuildLayerManager buildLayerManager = new BuildLayerManager(stageObjectSystem);
	private final BuildSequencer buildSequencer = new BuildSequencer(stageObjectSystem, buildLayerManager);
	private final com.beatblock.engine.influence.BlockInfluenceOrchestrator influenceOrchestrator =
		new com.beatblock.engine.influence.BlockInfluenceOrchestrator();
	private Vec3d runtimeCameraPosition = Vec3d.ZERO;
	private Vec3d runtimeCameraForward = new Vec3d(0, 0, 1);

	public void setRuntimeCameraPosition(Vec3d cameraPosition) {
		if (cameraPosition == null) return;
		this.runtimeCameraPosition = cameraPosition;
	}

	public void setRuntimeCameraOrientation(float yawDegrees, float pitchDegrees) {
		this.runtimeCameraForward = com.beatblock.engine.camera.CameraViewMath.forwardFromRotation(yawDegrees, pitchDegrees);
	}

	public Vec3d getRuntimeCameraPosition() {
		return runtimeCameraPosition;
	}

	public Vec3d getRuntimeCameraForward() {
		return runtimeCameraForward;
	}

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

	public BuildLayerManager getBuildLayerManager() {
		return buildLayerManager;
	}

	/**
	 * 每帧调用：根据时间线时间更新动画，移除已结束实例。
	 */
	public void tick(double timelineTimeSeconds) {
		tick(timelineTimeSeconds, null);
	}

	/**
	 * @deprecated 直接拿 world 当权威世界写入，不保证持久化/线程安全（写的是客户端世界）。
	 * 优先使用 {@link #tick(double, World, WorldMutationSink)} 并注入权威 sink，
	 * 例如 {@code com.beatblock.client.BeatBlockAuthoritativeWorldMutator}。
	 */
	@Deprecated
	public void tick(double timelineTimeSeconds, World world) {
		tick(timelineTimeSeconds, world, WorldMutationSink.direct(blockControlExecutor, world));
	}

	/**
	 * 统一影响帧 tick：渲染层 preset 求值 + 可选世界层 BUILD mutation。
	 * world 仅用于读取当前方块状态（isChunkLoaded/getBlockState 等）；真正的写入通过 sink 完成，
	 * 由调用方决定写到哪个权威世界、在哪个线程写。
	 */
	public void tick(double timelineTimeSeconds, World world, WorldMutationSink sink) {
		influenceOrchestrator.setRuntimeCamera(runtimeCameraPosition, runtimeCameraForward);
		influenceOrchestrator.tick(timelineTimeSeconds, animationPlayer, buildSequencer, world, sink);
	}

	public com.beatblock.engine.influence.InfluenceFrame getLastInfluenceFrame() {
		return influenceOrchestrator.getLastFrame();
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
		scheduleTimelineEvent(event, new double[0], 120.0);
	}

	public void scheduleTimelineEvent(TimelineAnimationEvent event, double[] referenceBeatTimesSeconds, double timelineBpm) {
		if (event == null) return;
		TimelineAnimationActionMode actionMode = event.getActionMode();
		if (actionMode == TimelineAnimationActionMode.ANIMATE) {
			scheduleAnimateEvent(event, referenceBeatTimesSeconds, timelineBpm);
		}
	}

	private void scheduleAnimateEvent(TimelineAnimationEvent event, double[] referenceBeatTimesSeconds, double timelineBpm) {
		if (event == null) return;
		if (com.beatblock.timeline.generation.StepBurstEventFactory.isStepDispatch(event.getParameters())) {
			scheduleExpandedStepSequence(event, referenceBeatTimesSeconds, timelineBpm);
			return;
		}
		scheduleFromTimelineEventWithSpatial(event);
	}

	private void scheduleExpandedStepSequence(
		TimelineAnimationEvent event,
		double[] referenceBeatTimesSeconds,
		double timelineBpm
	) {
		if (event == null) return;
		AnimationDefinition def = animationLibrary.get(event.getAnimationTypeId());
		StageObject target = stageObjectSystem.get(event.getTargetObjectId());
		if (def == null || target == null || target.getBlocks().isEmpty()) return;

		Map<String, Object> params = event.getParameters();
		List<BlockPos> ordered = sortBlocksForSpatialMode(target, resolveSpatialMode(params, target), event);
		double edgePriority = readDouble(params.get("cameraEdgePriority"), 0.0);
		if (edgePriority > 0.0 && !ordered.isEmpty()) {
			ordered = applyEdgePrioritization(ordered, target.getBlocks(), edgePriority, runtimeCameraPosition, target.getCenter());
		}
		ordered = com.beatblock.timeline.generation.CameraStepModulation.reorderForFrustumGating(
			ordered, runtimeCameraPosition, runtimeCameraForward, params);

		List<com.beatblock.timeline.generation.StepSequencePlanner.PlannedStep> planned =
			com.beatblock.timeline.generation.StepSequencePlanner.plan(
				ordered, event, referenceBeatTimesSeconds, timelineBpm);
		planned = com.beatblock.timeline.generation.CameraStepModulation.applyAdaptiveTiming(
			planned, ordered, runtimeCameraPosition, params);
		double duration = Math.max(0.01, event.getDurationSeconds());
		float energy = event.getEnergy();
		Vec3d center = target.getCenter();
		for (int i = 0; i < planned.size(); i++) {
			var step = planned.get(i);
			StageObject perBlockTarget = new StageObject(
				target.getId() + "#step#" + i,
				target.getName(),
				List.of(step.block()),
				center,
				target.getGroupSpec()
			);
			double end = step.startTimeSeconds() + duration;
			animationPlayer.addInstance(new EngineAnimationInstance(
				def, perBlockTarget, step.startTimeSeconds(), end, energy, params));
		}
	}

	private void scheduleFromTimelineEventWithSpatial(TimelineAnimationEvent event) {
		if (event == null) return;
		AnimationDefinition def = animationLibrary.get(event.getAnimationTypeId());
		StageObject target = stageObjectSystem.get(event.getTargetObjectId());
		if (def == null || target == null) return;

		Map<String, Object> params = event.getParameters();
		net.minecraft.util.math.BlockPos singleBlock =
			com.beatblock.timeline.generation.StepBurstEventFactory.readSingleBlockPos(params);
		if (singleBlock != null) {
			scheduleSingleBlockBurst(event, target, def, singleBlock);
			return;
		}

		SpatialDispatchMode spatialMode = resolveSpatialMode(params, target);
		double stepDelay = resolveSpatialStepDelay(params, target, spatialMode, event.getDurationSeconds(), target.getBlocks().size());
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

	private void scheduleSingleBlockBurst(
		TimelineAnimationEvent event,
		StageObject target,
		AnimationDefinition def,
		net.minecraft.util.math.BlockPos block
	) {
		StageObject perBlockTarget = new StageObject(
			target.getId() + "#block",
			target.getName(),
			List.of(block),
			target.getCenter(),
			target.getGroupSpec()
		);
		double endTime = event.getTimeSeconds() + Math.max(0.01, event.getDurationSeconds());
		animationPlayer.addInstance(new EngineAnimationInstance(
			def, perBlockTarget, event.getTimeSeconds(), endTime, event.getEnergy(), event.getParameters()));
	}

	private static SpatialDispatchMode resolveSpatialMode(Map<String, Object> params, StageObject target) {
		if (params != null && params.containsKey("spatialMode")) {
			return SpatialDispatchMode.fromValue(params.get("spatialMode"));
		}
		if (!readBoolean(params != null ? params.get("inheritGroupSpatial") : null, true)) {
			return SpatialDispatchMode.ALL;
		}
		if (target == null || target.getGroupSpec() == null) return SpatialDispatchMode.ALL;
		return target.getGroupSpec().getSortingStrategy().toSpatialDispatchMode();
	}

	private static double resolveSpatialStepDelay(Map<String, Object> params, StageObject target, SpatialDispatchMode mode,
	                                             double durationSeconds, int blockCount) {
		if (mode == null || mode == SpatialDispatchMode.ALL || blockCount <= 1) return 0.0;
		if (params != null && params.containsKey("sequentialDelaySeconds")) {
			double explicit = readDouble(params.get("sequentialDelaySeconds"), -1.0);
			if (explicit >= 0.0) return explicit;
		}
		if (!readBoolean(params != null ? params.get("inheritGroupSpatial") : null, true)) {
			double duration = Math.max(0.05, durationSeconds);
			double byDuration = duration / Math.max(2.0, Math.min(28.0, blockCount * 0.6));
			return Math.max(0.01, Math.min(0.06, byDuration));
		}
		if (target != null && target.getGroupSpec() != null && target.getGroupSpec().getStaggerDelaySeconds() > 0.0) {
			return target.getGroupSpec().getStaggerDelaySeconds();
		}

		double duration = Math.max(0.05, durationSeconds);
		double byDuration = duration / Math.max(2.0, Math.min(28.0, blockCount * 0.6));
		return Math.max(0.01, Math.min(0.06, byDuration));
	}

	private List<BlockPos> applyEdgePrioritization(List<BlockPos> orderedBlocks, List<BlockPos> allBlocks, double edgeStrength, Vec3d cameraPos, Vec3d groupCenter) {
		if (orderedBlocks.isEmpty() || edgeStrength <= 0.0) return orderedBlocks;
		
		// Create a set for O(1) lookup
		java.util.Set<BlockPos> blockSet = new java.util.HashSet<>(allBlocks);
		
		// Detect edge blocks and compute camera visibility weight
		class EdgeBlockScore implements Comparable<EdgeBlockScore> {
			BlockPos pos;
			int exposedFaces;
			double cameraVisibility;
			
			EdgeBlockScore(BlockPos pos, int exposed, double visibility) {
				this.pos = pos;
				this.exposedFaces = exposed;
				this.cameraVisibility = visibility;
			}
			
			@Override
			public int compareTo(EdgeBlockScore other) {
				// Higher exposed faces = higher priority
				if (this.exposedFaces != other.exposedFaces) {
					return Integer.compare(other.exposedFaces, this.exposedFaces);
				}
				// Higher camera visibility = higher priority (among same face count)
				return Double.compare(other.cameraVisibility, this.cameraVisibility);
			}

			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof EdgeBlockScore other)) {
					return false;
				}
				return exposedFaces == other.exposedFaces
					&& Double.compare(cameraVisibility, other.cameraVisibility) == 0
					&& pos.equals(other.pos);
			}

			@Override
			public int hashCode() {
				return java.util.Objects.hash(pos, exposedFaces, cameraVisibility);
			}
		}
		
		java.util.List<EdgeBlockScore> scores = new java.util.ArrayList<>();
		Vec3d cameraDir = groupCenter.subtract(cameraPos).normalize();
		
		for (BlockPos block : orderedBlocks) {
			// Count exposed faces (neighbors that don't exist in blockSet)
			int exposedFaces = 0;
			for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
				BlockPos neighbor = block.offset(dir);
				if (!blockSet.contains(neighbor)) {
					exposedFaces++;
				}
			}
			
			// Calculate camera visibility: dot product of (block->camera) with camera direction
			Vec3d blockPos = new Vec3d(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
			Vec3d blockToCamera = cameraPos.subtract(blockPos).normalize();
			double visibility = Math.max(0.0, blockToCamera.dotProduct(cameraDir));
			
			scores.add(new EdgeBlockScore(block, exposedFaces, visibility));
		}
		
		// Sort by edge priority
		java.util.Collections.sort(scores);
		
		// Create edge-prioritized list
		java.util.List<BlockPos> edgePrioritized = new java.util.ArrayList<>();
		for (EdgeBlockScore score : scores) {
			edgePrioritized.add(score.pos);
		}
		
		// Blend: take first (strength * size) from edge-prioritized, rest from original
		double t = Math.max(0.0, Math.min(1.0, edgeStrength));
		int blendPoint = (int) Math.round(edgePrioritized.size() * t);
		java.util.List<BlockPos> result = new java.util.ArrayList<>(edgePrioritized.subList(0, blendPoint));
		
		// Add remaining blocks from original in their original order
		java.util.Set<BlockPos> added = new java.util.HashSet<>(result);
		for (BlockPos block : orderedBlocks) {
			if (!added.contains(block)) {
				result.add(block);
			}
		}
		
		return result;
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
			default -> { }
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

	private static boolean readBoolean(Object raw, boolean fallback) {
		if (raw instanceof Boolean b) return b;
		if (raw instanceof Number n) return n.intValue() != 0;
		if (raw == null) return fallback;
		String s = String.valueOf(raw).trim();
		if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return true;
		if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return false;
		return fallback;
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

	/**
	 * @deprecated 直接拿 world 当权威世界写入，不保证持久化/线程安全（写的是客户端世界）。
	 * 优先使用 {@link #applyControlMutations(List, WorldMutationSink)} 并注入权威 sink。
	 */
	@Deprecated
	public void applyControlMutations(World world, List<BlockControlExecutor.BlockMutation> mutations) {
		if (world == null || mutations == null || mutations.isEmpty()) return;
		blockControlExecutor.applyMutations(world, mutations);
	}

	/** 通过注入的 sink 应用 PLACE/CLEAR mutation；sink 决定写到哪个权威世界、在哪个线程写。 */
	public void applyControlMutations(List<BlockControlExecutor.BlockMutation> mutations, WorldMutationSink sink) {
		if (mutations == null || mutations.isEmpty() || sink == null) return;
		sink.apply(mutations);
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
