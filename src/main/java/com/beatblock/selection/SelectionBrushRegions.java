package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔刷几何：球体/立方体候选格（不含 World 过滤）。
 */
public final class SelectionBrushRegions {

	private SelectionBrushRegions() {}

	public static List<BlockPos> spherePositions(BlockPos center, int radius, int maxBlocks) {
		if (center == null || radius < 0) return List.of();
		int rr = radius * radius;
		long worst = (2L * radius + 1) * (2L * radius + 1) * (2L * radius + 1);
		if (worst > maxBlocks) return null;

		List<BlockPos> out = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dy * dy + dz * dz > rr) continue;
					out.add(center.add(dx, dy, dz));
					if (out.size() > maxBlocks) return null;
				}
			}
		}
		return out;
	}

	public static List<BlockPos> cubePositions(BlockPos center, int radius, int maxBlocks) {
		if (center == null || radius < 0) return List.of();
		long worst = (2L * radius + 1) * (2L * radius + 1) * (2L * radius + 1);
		if (worst > maxBlocks) return null;

		List<BlockPos> out = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					out.add(center.add(dx, dy, dz));
					if (out.size() > maxBlocks) return null;
				}
			}
		}
		return out;
	}
}
