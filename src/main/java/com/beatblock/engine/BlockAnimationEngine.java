package com.beatblock.engine;

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
	private final BuildSequencer buildSequencer = new BuildSequencer(stageObjectSystem);
	private final List<StepSequenceState> stepSequences = new ArrayList<>();
	private Vec3d runtimeCameraPosition = Vec3d.ZERO;

	private enum DispatchModel {
		BURST,
		STEP;

		static DispatchModel fromValue(Object value) {
			if (value == null) return BURST;
			String s = String.valueOf(value).trim();
			if (s.isEmpty()) return BURST;
			if ("STEP".equalsIgnoreCase(s)) return STEP;
			return BURST;
		}
	}

	private enum StepStartMode {
		IMMEDIATE,
		NEXT_BEAT;

		static StepStartMode fromValue(Object value) {
			if (value == null) return NEXT_BEAT;
			String s = String.valueOf(value).trim();
			if (s.isEmpty()) return NEXT_BEAT;
			if ("IMMEDIATE".equalsIgnoreCase(s)) return IMMEDIATE;
			return NEXT_BEAT;
		}
	}

	private enum StepCompletionMode {
		KEEP,
		LOOP;

		static StepCompletionMode fromValue(Object value) {
			if (value == null) return KEEP;
			String s = String.valueOf(value).trim();
			if (s.isEmpty()) return KEEP;
			if ("LOOP".equalsIgnoreCase(s)) return LOOP;
			return KEEP;
		}
	}

	private static final class StepSequenceState {
		private final AnimationDefinition definition;
		private final StageObject target;
		private final Map<String, Object> params;
		private final List<BlockPos> orderedBlocks;
		private final double durationSeconds;
		private final float energy;
		private final int blocksPerBeat;
		private final double startGateTime;
		private final StepCompletionMode completionMode;
		private final StepStartMode startMode;
		private int nextIndex;
		private int cycles;

		private StepSequenceState(AnimationDefinition definition, StageObject target, Map<String, Object> params,
		                         List<BlockPos> orderedBlocks, double durationSeconds, float energy,
		                         int blocksPerBeat, double startGateTime,
		                         StepStartMode startMode, StepCompletionMode completionMode) {
			this.definition = definition;
			this.target = target;
			this.params = params;
			this.orderedBlocks = orderedBlocks;
			this.durationSeconds = durationSeconds;
			this.energy = energy;
			this.blocksPerBeat = Math.max(1, blocksPerBeat);
			this.startGateTime = startGateTime;
			this.startMode = startMode;
			this.completionMode = completionMode;
			this.nextIndex = 0;
			this.cycles = 0;
		}

		private boolean finished() {
			return nextIndex >= orderedBlocks.size();
		}
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

	public void setRuntimeCameraPosition(Vec3d cameraPosition) {
		if (cameraPosition == null) return;
		this.runtimeCameraPosition = cameraPosition;
	}

	/**
	 * 每帧调用：根据时间线时间更新动画，移除已结束实例。
	 * STEP + NEXT_BEAT 由 {@link #tickStepBeats} 按参考轨节拍点推进。
	 */
	public void tick(double timelineTimeSeconds) {
		animationPlayer.removeEnded(timelineTimeSeconds);
		animationPlayer.update(timelineTimeSeconds);
	}

	/**
	 * 按参考轨显式节拍时刻推进 STEP 序列（{@code (previous, current]} 区间内各触发一次）。
	 *
	 * @param referenceBeatTimesSeconds 升序节拍时刻；为空时不推进
	 */
	public void tickStepBeats(double previousTimeSeconds, double currentTimeSeconds, double[] referenceBeatTimesSeconds) {
		if (stepSequences.isEmpty()
			|| referenceBeatTimesSeconds == null
			|| referenceBeatTimesSeconds.length == 0
			|| currentTimeSeconds + 1e-6 < previousTimeSeconds) {
			return;
		}
		int start = firstBeatIndexAfter(previousTimeSeconds, referenceBeatTimesSeconds);
		for (int i = start; i < referenceBeatTimesSeconds.length; i++) {
			double beat = referenceBeatTimesSeconds[i];
			if (beat > currentTimeSeconds + 1e-6) {
				break;
			}
			advanceStepSequencesOnBeat(beat);
		}
	}

	private static int firstBeatIndexAfter(double timeSeconds, double[] beatTimesSeconds) {
		int lo = 0;
		int hi = beatTimesSeconds.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (beatTimesSeconds[mid] <= timeSeconds + 1e-6) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
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
			scheduleAnimateEvent(event);
		}
	}

	private void scheduleAnimateEvent(TimelineAnimationEvent event) {
		if (event == null) return;
		DispatchModel model = DispatchModel.fromValue(event.getParameters().get("dispatchModel"));
		if (model == DispatchModel.STEP) {
			enqueueStepSequence(event);
			return;
		}
		scheduleFromTimelineEventWithSpatial(event);
	}

	private void scheduleFromTimelineEventWithSpatial(TimelineAnimationEvent event) {
		if (event == null) return;
		AnimationDefinition def = animationLibrary.get(event.getAnimationTypeId());
		StageObject target = stageObjectSystem.get(event.getTargetObjectId());
		if (def == null || target == null) return;

		Map<String, Object> params = event.getParameters();
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

	private void enqueueStepSequence(TimelineAnimationEvent event) {
		if (event == null) return;
		AnimationDefinition def = animationLibrary.get(event.getAnimationTypeId());
		StageObject target = stageObjectSystem.get(event.getTargetObjectId());
		if (def == null || target == null || target.getBlocks().isEmpty()) return;

		Map<String, Object> params = event.getParameters();
		SpatialDispatchMode spatialMode = resolveSpatialMode(params, target);
		List<BlockPos> ordered = sortBlocksForSpatialMode(target, spatialMode, event);
		
		// Apply edge prioritization if enabled
		double edgePriority = readDouble(params.get("cameraEdgePriority"), 0.0);
		if (edgePriority > 0.0 && !ordered.isEmpty()) {
			ordered = applyEdgePrioritization(ordered, target.getBlocks(), edgePriority, runtimeCameraPosition, target.getCenter());
		}
		
		int blocksPerBeat = (int) Math.max(1, Math.round(readDouble(params.get("blocksPerBeat"), 1.0)));
		double duration = Math.max(0.01, event.getDurationSeconds());
		StepStartMode startMode = StepStartMode.fromValue(params.get("stepStartMode"));
		StepCompletionMode completionMode = StepCompletionMode.fromValue(params.get("stepCompletionMode"));
		StepSequenceState state = new StepSequenceState(
			def,
			target,
			params,
			ordered,
			duration,
			event.getEnergy(),
			blocksPerBeat,
			event.getTimeSeconds(),
			startMode,
			completionMode
		);
		if (startMode == StepStartMode.IMMEDIATE) {
			advanceStepSequence(state, event.getTimeSeconds());
			if (state.finished() && completionMode == StepCompletionMode.KEEP) {
				return;
			}
		}
		stepSequences.add(state);
	}

	private void advanceStepSequencesOnBeat(double beatTimeSeconds) {
		if (stepSequences.isEmpty()) return;
		List<StepSequenceState> done = new ArrayList<>();
		for (StepSequenceState state : stepSequences) {
			if (state == null || state.startMode != StepStartMode.NEXT_BEAT) continue;
			if (beatTimeSeconds + 1e-6 < state.startGateTime) continue;
			advanceStepSequence(state, beatTimeSeconds);
			if (state.finished()) done.add(state);
		}
		if (!done.isEmpty()) {
			stepSequences.removeAll(done);
		}
	}

	private void advanceStepSequence(StepSequenceState state, double beatTimeSeconds) {
		if (state == null || state.finished()) return;
		Vec3d center = state.target.getCenter();
		int effectiveBlocksPerBeat = resolveEffectiveBlocksPerBeat(state);
		for (int i = 0; i < effectiveBlocksPerBeat && !state.finished(); i++) {
			BlockPos block = state.orderedBlocks.get(state.nextIndex);
			StageObject perBlockTarget = new StageObject(
				state.target.getId() + "#step#" + state.nextIndex,
				state.target.getName(),
				List.of(block),
				center,
				state.target.getGroupSpec()
			);
			animationPlayer.addInstance(new EngineAnimationInstance(
				state.definition,
				perBlockTarget,
				beatTimeSeconds,
				beatTimeSeconds + state.durationSeconds,
				state.energy,
				state.params
			));
			state.nextIndex++;
		}
		if (state.finished() && state.completionMode == StepCompletionMode.LOOP) {
			state.nextIndex = 0;
			state.cycles++;
		}
	}

	private boolean isTargetVisibleToCamera(Vec3d targetCenter) {
		// Simple frustum visibility check: target must be in front of camera and within rough view cone
		Vec3d camPos = runtimeCameraPosition;
		Vec3d toTarget = targetCenter.subtract(camPos);
		double distToTarget = toTarget.length();
		
		// Behind camera or too close (camera inside target) -> not visible
		if (distToTarget < 0.1) return true; // Camera too close, assume visible to avoid jitter
		
		// Too far away -> not visible (configurable as a gating parameter)
		double maxGatingDistance = 160.0; // Approximate max render distance in Minecraft
		if (distToTarget > maxGatingDistance) return false;
		
		// For now, if in front and not too far, consider visible.
		// A more sophisticated check would compute the view angle and frustum cone.
		return true;
	}

	private int resolveEffectiveBlocksPerBeat(StepSequenceState state) {
		if (state == null) return 1;
		
		// Check frustum gating: if enabled and target not visible, pause (return 0)
		if (readBoolean(state.params.get("cameraFrustumGating"), false)) {
			Vec3d center = state.target.getCenter();
			if (!isTargetVisibleToCamera(center)) {
				return 0; // Pause progression when outside frustum
			}
		}
		
		int base = Math.max(1, state.blocksPerBeat);
		if (!readBoolean(state.params.get("cameraAdaptiveStep"), false)) return base;

		double nearDistance = Math.max(0.5, readDouble(state.params.get("cameraNearDistance"), 8.0));
		double farDistance = Math.max(nearDistance + 0.001, readDouble(state.params.get("cameraFarDistance"), 48.0));
		double nearScale = Math.max(0.1, readDouble(state.params.get("cameraNearScale"), 0.6));
		double farScale = Math.max(0.1, readDouble(state.params.get("cameraFarScale"), 1.5));

		Vec3d center = state.target.getCenter();
		double dist = center.distanceTo(runtimeCameraPosition);
		double t = (dist - nearDistance) / (farDistance - nearDistance);
		t = Math.max(0.0, Math.min(1.0, t));
		double scale = nearScale + (farScale - nearScale) * t;

		return Math.max(1, (int) Math.round(base * scale));
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
		stepSequences.clear();
	}
}
