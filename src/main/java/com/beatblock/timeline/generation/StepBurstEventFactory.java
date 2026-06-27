package com.beatblock.timeline.generation;

import com.beatblock.engine.GroupSpec;
import com.beatblock.engine.StageObject;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 将单个 {@code dispatchModel=STEP} 事件展开为 N 个带绝对时间戳的 BURST 事件（Timeline 持久化用）。
 */
public final class StepBurstEventFactory {

	private static final Set<String> STEP_ONLY_PARAMS = Set.of(
		"blocksPerBeat",
		"stepStartMode",
		"stepCompletionMode",
		"cameraAdaptiveStep",
		"cameraFrustumGating",
		"cameraEdgePriority",
		"pacingMode",
		"distancePaceSecondsPerBlock",
		"distancePaceMinGapSeconds"
	);

	private StepBurstEventFactory() {}

	public static boolean isStepDispatch(Map<String, Object> params) {
		if (params == null) return false;
		return "STEP".equalsIgnoreCase(String.valueOf(params.get("dispatchModel")).trim());
	}

	public static List<TimelineAnimationEvent> expand(
		TimelineAnimationEvent stepEvent,
		StageObject target,
		double[] referenceBeatTimesSeconds,
		double timelineBpm,
		Vec3d runtimeCameraPosition
	) {
		return expand(stepEvent, target, referenceBeatTimesSeconds, timelineBpm, runtimeCameraPosition, null);
	}

	public static List<TimelineAnimationEvent> expand(
		TimelineAnimationEvent stepEvent,
		StageObject target,
		double[] referenceBeatTimesSeconds,
		double timelineBpm,
		Vec3d runtimeCameraPosition,
		Vec3d runtimeCameraForward
	) {
		if (stepEvent == null || target == null || target.getBlocks().isEmpty()) {
			return List.of();
		}
		if (!isStepDispatch(stepEvent.getParameters())) {
			return List.of();
		}

		Map<String, Object> params = stepEvent.getParameters();
		List<BlockPos> ordered = sortBlocksForSpatialMode(target, resolveSpatialMode(params, target), stepEvent);
		double edgePriority = readDouble(params.get("cameraEdgePriority"), 0.0);
		if (edgePriority > 0.0 && runtimeCameraPosition != null) {
			ordered = applyEdgePrioritization(
				ordered, target.getBlocks(), edgePriority, runtimeCameraPosition, target.getCenter());
		}
		ordered = CameraStepModulation.reorderForFrustumGating(
			ordered, runtimeCameraPosition, runtimeCameraForward, params);

		List<StepSequencePlanner.PlannedStep> planned = StepSequencePlanner.plan(
			ordered, stepEvent, referenceBeatTimesSeconds, timelineBpm);
		planned = CameraStepModulation.applyAdaptiveTiming(
			planned, ordered, runtimeCameraPosition, params);
		if (planned.isEmpty()) return List.of();

		List<TimelineAnimationEvent> burstEvents = new ArrayList<>(planned.size());
		String sourceEventId = stepEvent.getEventId();
		for (StepSequencePlanner.PlannedStep step : planned) {
			Map<String, Object> burstParams = burstParamsFromStep(stepEvent.getParameters(), step.block(), sourceEventId);
			burstEvents.add(new TimelineAnimationEvent(
				"",
				step.startTimeSeconds(),
				stepEvent.getDurationSeconds(),
				stepEvent.getAnimationTypeId(),
				stepEvent.getTargetObjectId(),
				stepEvent.getEnergy(),
				burstParams
			));
		}
		return burstEvents;
	}

	static Map<String, Object> burstParamsFromStep(
		Map<String, Object> sourceParams,
		BlockPos block,
		String sourceEventId
	) {
		Map<String, Object> params = new HashMap<>(sourceParams != null ? sourceParams : Map.of());
		for (String key : STEP_ONLY_PARAMS) {
			params.remove(key);
		}
		params.put("dispatchModel", "BURST");
		params.put("spatialMode", SpatialDispatchMode.ALL.name());
		params.put("singleBlockX", block.getX());
		params.put("singleBlockY", block.getY());
		params.put("singleBlockZ", block.getZ());
		if (sourceEventId != null && !sourceEventId.isBlank()) {
			params.put("bakedFromStepEventId", sourceEventId);
		}
		return params;
	}

	public static BlockPos readSingleBlockPos(Map<String, Object> params) {
		if (params == null || !params.containsKey("singleBlockX")) return null;
		try {
			int x = readInt(params.get("singleBlockX"), Integer.MIN_VALUE);
			int y = readInt(params.get("singleBlockY"), Integer.MIN_VALUE);
			int z = readInt(params.get("singleBlockZ"), Integer.MIN_VALUE);
			if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) return null;
			return new BlockPos(x, y, z);
		} catch (Exception ex) {
			return null;
		}
	}

	private static SpatialDispatchMode resolveSpatialMode(Map<String, Object> params, StageObject target) {
		if (params != null && params.containsKey("spatialMode")) {
			return SpatialDispatchMode.fromValue(params.get("spatialMode"));
		}
		if (!readBoolean(params != null ? params.get("inheritGroupSpatial") : null, true)) {
			return SpatialDispatchMode.ALL;
		}
		GroupSpec groupSpec = target.getGroupSpec();
		if (groupSpec == null) return SpatialDispatchMode.ALL;
		return groupSpec.getSortingStrategy().toSpatialDispatchMode();
	}

	private static List<BlockPos> sortBlocksForSpatialMode(
		StageObject target,
		SpatialDispatchMode mode,
		TimelineAnimationEvent event
	) {
		List<BlockPos> blocks = new ArrayList<>(target.getBlocks());
		if (blocks.size() <= 1 || mode == SpatialDispatchMode.ALL) return blocks;
		Vec3d center = target.getCenter();
		long seed = spatialSeed(event, target);

		return switch (mode) {
			case SEQUENTIAL -> {
				blocks.sort(Comparator
					.comparingInt(BlockPos::getX)
					.thenComparingInt(BlockPos::getZ)
					.thenComparingInt(BlockPos::getY));
				yield blocks;
			}
			case RADIAL -> {
				blocks.sort(Comparator
					.comparingDouble((BlockPos p) -> distanceSqToCenter(p, center))
					.thenComparingInt(BlockPos::getY));
				yield blocks;
			}
			case SPIRAL -> {
				blocks.sort(Comparator
					.comparingDouble((BlockPos p) -> angleAroundCenter(p, center))
					.thenComparingDouble(p -> distanceSqToCenter(p, center)));
				yield blocks;
			}
			case RANDOM -> {
				blocks.sort(Comparator.comparingLong(p -> mixedHash(seed, p)));
				yield blocks;
			}
			default -> blocks;
		};
	}

	private static List<BlockPos> applyEdgePrioritization(
		List<BlockPos> orderedBlocks,
		List<BlockPos> allBlocks,
		double edgeStrength,
		Vec3d cameraPos,
		Vec3d groupCenter
	) {
		if (orderedBlocks.isEmpty() || edgeStrength <= 0.0) return orderedBlocks;

		Set<BlockPos> blockSet = new HashSet<>(allBlocks);
		record EdgeBlockScore(BlockPos pos, int exposedFaces, double cameraVisibility)
			implements Comparable<EdgeBlockScore> {
			@Override
			public int compareTo(EdgeBlockScore other) {
				if (this.exposedFaces != other.exposedFaces) {
					return Integer.compare(other.exposedFaces, this.exposedFaces);
				}
				return Double.compare(other.cameraVisibility, this.cameraVisibility);
			}
		}

		List<EdgeBlockScore> scores = new ArrayList<>();
		Vec3d cameraDir = groupCenter.subtract(cameraPos).normalize();
		for (BlockPos block : orderedBlocks) {
			int exposedFaces = 0;
			for (Direction dir : Direction.values()) {
				if (!blockSet.contains(block.offset(dir))) exposedFaces++;
			}
			Vec3d blockPos = new Vec3d(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
			Vec3d blockToCamera = cameraPos.subtract(blockPos).normalize();
			double visibility = Math.max(0.0, blockToCamera.dotProduct(cameraDir));
			scores.add(new EdgeBlockScore(block, exposedFaces, visibility));
		}
		scores.sort(EdgeBlockScore::compareTo);

		List<BlockPos> edgePrioritized = new ArrayList<>(scores.size());
		for (EdgeBlockScore score : scores) {
			edgePrioritized.add(score.pos());
		}

		double t = Math.max(0.0, Math.min(1.0, edgeStrength));
		int blendPoint = (int) Math.round(edgePrioritized.size() * t);
		List<BlockPos> result = new ArrayList<>(edgePrioritized.subList(0, blendPoint));
		Set<BlockPos> added = new HashSet<>(result);
		for (BlockPos block : orderedBlocks) {
			if (!added.contains(block)) result.add(block);
		}
		return result;
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

	private static int readInt(Object raw, int fallback) {
		if (raw instanceof Number n) return n.intValue();
		if (raw == null) return fallback;
		try {
			return (int) Math.round(Double.parseDouble(String.valueOf(raw).trim()));
		} catch (Exception ex) {
			return fallback;
		}
	}

	private static boolean readBoolean(Object raw, boolean fallback) {
		if (raw instanceof Boolean b) return b;
		if (raw instanceof Number n) return n.intValue() != 0;
		if (raw == null) return fallback;
		String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
		if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
		if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
		return fallback;
	}
}
