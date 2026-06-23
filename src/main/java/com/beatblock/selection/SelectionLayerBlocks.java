package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 过滤已被 BUILD 图层占用的方块（测试可注入 {@link Predicate}）。
 */
public final class SelectionLayerBlocks {

	private SelectionLayerBlocks() {}

	public static List<BlockPos> excludeClaimed(List<BlockPos> blocks, Predicate<BlockPos> isClaimed) {
		if (blocks == null || blocks.isEmpty()) return List.of();
		List<BlockPos> out = new ArrayList<>(blocks.size());
		for (BlockPos pos : blocks) {
			if (pos == null || (isClaimed != null && isClaimed.test(pos))) continue;
			out.add(pos.toImmutable());
		}
		return out;
	}

	public static int countClaimed(List<BlockPos> blocks, Predicate<BlockPos> isClaimed) {
		if (blocks == null || blocks.isEmpty() || isClaimed == null) return 0;
		int count = 0;
		for (BlockPos pos : blocks) {
			if (pos != null && isClaimed.test(pos)) count++;
		}
		return count;
	}
}
