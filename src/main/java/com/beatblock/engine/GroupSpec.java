package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StageObject 的可复用编排定义（第 1 阶段）：
 * - sourceType/sourceParams: 该组来自哪里（如手动 AABB、选区快照）
 * - sortingStrategy/staggerDelaySeconds: 组内执行顺序策略与时间偏移策略
 *
 * 该结构先作为数据承载，不改变现有运行时执行语义。
 */
public final class GroupSpec {

	private final String sourceType;
	private final Map<String, Object> sourceParams;
	private final GroupSortingStrategy sortingStrategy;
	private final double staggerDelaySeconds;

	public GroupSpec(String sourceType, Map<String, Object> sourceParams,
	                 GroupSortingStrategy sortingStrategy, double staggerDelaySeconds) {
		this.sourceType = normalize(sourceType, "manual_snapshot");
		this.sourceParams = sourceParams == null ? Collections.emptyMap() : Map.copyOf(sourceParams);
		this.sortingStrategy = sortingStrategy != null ? sortingStrategy : GroupSortingStrategy.SEQUENTIAL;
		this.staggerDelaySeconds = Math.max(0.0, staggerDelaySeconds);
	}

	public static GroupSpec manualSnapshot() {
		return new GroupSpec("manual_snapshot", Map.of(), GroupSortingStrategy.SEQUENTIAL, 0.0);
	}

	public static GroupSpec fromSelectionCuboid(BlockPos a, BlockPos b, boolean includeAir) {
		return fromSelectionCuboid(a, b, includeAir, GroupSortingStrategy.SEQUENTIAL, 0.0);
	}

	public static GroupSpec fromSelectionCuboid(BlockPos a, BlockPos b, boolean includeAir,
	                                            GroupSortingStrategy sortingStrategy, double staggerDelaySeconds) {
		Map<String, Object> params = new LinkedHashMap<>();
		if (a != null) {
			params.put("posA", encodePos(a));
		}
		if (b != null) {
			params.put("posB", encodePos(b));
		}
		params.put("includeAir", includeAir);
		return new GroupSpec("selection_cuboid", params, sortingStrategy, staggerDelaySeconds);
	}

	public static GroupSpec fromSelectionSnapshot(List<BlockPos> selectedBlocks,
	                                              GroupSortingStrategy sortingStrategy,
	                                              double staggerDelaySeconds) {
		List<BlockPos> blocks = selectedBlocks != null ? new ArrayList<>(selectedBlocks) : List.of();
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("count", blocks.size());
		if (!blocks.isEmpty()) {
			BlockPos min = blocks.getFirst();
			BlockPos max = blocks.getFirst();
			for (BlockPos pos : blocks) {
				if (pos == null) continue;
				min = new BlockPos(
					Math.min(min.getX(), pos.getX()),
					Math.min(min.getY(), pos.getY()),
					Math.min(min.getZ(), pos.getZ())
				);
				max = new BlockPos(
					Math.max(max.getX(), pos.getX()),
					Math.max(max.getY(), pos.getY()),
					Math.max(max.getZ(), pos.getZ())
				);
			}
			params.put("boundsMin", encodePos(min));
			params.put("boundsMax", encodePos(max));
		}
		return new GroupSpec("selection_snapshot", params, sortingStrategy, staggerDelaySeconds);
	}

	private static Map<String, Integer> encodePos(BlockPos pos) {
		Map<String, Integer> out = new LinkedHashMap<>();
		out.put("x", pos.getX());
		out.put("y", pos.getY());
		out.put("z", pos.getZ());
		return out;
	}

	private static String normalize(String value, String fallback) {
		if (value == null) return fallback;
		String v = value.trim();
		return v.isEmpty() ? fallback : v;
	}

	public String getSourceType() {
		return sourceType;
	}

	public Map<String, Object> getSourceParams() {
		return sourceParams;
	}

	public GroupSortingStrategy getSortingStrategy() {
		return sortingStrategy;
	}

	public String getSortingStrategyCode() {
		return sortingStrategy.getCode();
	}

	public double getStaggerDelaySeconds() {
		return staggerDelaySeconds;
	}
}