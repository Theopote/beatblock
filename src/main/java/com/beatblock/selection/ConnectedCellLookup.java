package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;

/**
 * 连通选区查询：按格点判断空气与材质是否一致（便于单元测试，生产由 {@link BlockStateLookup} 适配）。
 */
@FunctionalInterface
public interface ConnectedCellLookup {

	/** @return 0 = 空气/未定义，非 0 = 可连通材质 id */
	int materialAt(BlockPos pos);

	static ConnectedCellLookup fromBlockStateLookup(
		BlockStateLookup lookup,
		boolean matchFullBlockState
	) {
		return pos -> {
			if (pos == null) return 0;
			var state = lookup.getBlockState(pos);
			if (state.isAir()) return 0;
			if (matchFullBlockState) return System.identityHashCode(state);
			return System.identityHashCode(state.getBlock());
		};
	}

	static ConnectedCellLookup fromMaterialGrid(java.util.Map<BlockPos, Integer> materials) {
		java.util.Map<BlockPos, Integer> grid = new java.util.HashMap<>();
		if (materials != null) {
			for (var entry : materials.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null && entry.getValue() != 0) {
					grid.put(entry.getKey().toImmutable(), entry.getValue());
				}
			}
		}
		return pos -> {
			if (pos == null) return 0;
			return grid.getOrDefault(pos.toImmutable(), 0);
		};
	}
}
