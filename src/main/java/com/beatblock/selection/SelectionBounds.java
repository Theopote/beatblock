package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;

import java.util.Collection;

/**
 * 选区包围盒（由已选方块集合计算）。
 */
public final class SelectionBounds {

	public record Box(BlockPos min, BlockPos max) {
		public boolean isEmpty() {
			return min == null || max == null;
		}
	}

	private SelectionBounds() {}

	public static Box fromPositions(Collection<BlockPos> positions) {
		if (positions == null || positions.isEmpty()) {
			return new Box(null, null);
		}
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : positions) {
			if (p == null) continue;
			minX = Math.min(minX, p.getX());
			minY = Math.min(minY, p.getY());
			minZ = Math.min(minZ, p.getZ());
			maxX = Math.max(maxX, p.getX());
			maxY = Math.max(maxY, p.getY());
			maxZ = Math.max(maxZ, p.getZ());
		}
		if (minX == Integer.MAX_VALUE) {
			return new Box(null, null);
		}
		return new Box(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
	}
}
