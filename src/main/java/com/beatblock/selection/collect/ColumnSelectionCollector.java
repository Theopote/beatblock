package com.beatblock.selection.collect;

import com.beatblock.selection.SelectionCollectResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** 整列选区：固定 X/Z，遍历世界高度。 */
public final class ColumnSelectionCollector {

	private ColumnSelectionCollector() {}

	public static SelectionCollectResult collect(
		World world,
		BlockPos pos,
		boolean includeAir,
		int maxBlocks,
		Predicate<BlockPos> withinReach
	) {
		if (world == null || pos == null) {
			return SelectionCollectResult.failure("整列：无效位置。");
		}
		int x = pos.getX();
		int z = pos.getZ();
		int minY = world.getBottomY();
		int maxY = minY + world.getHeight() - 1;
		int span = maxY - minY + 1;
		if (span > maxBlocks) {
			return SelectionCollectResult.failure(String.format("整列高度 %d 超过上限 %d。", span, maxBlocks));
		}
		List<BlockPos> out = new ArrayList<>(Math.min(span, 4096));
		for (int y = minY; y <= maxY; y++) {
			BlockPos p = new BlockPos(x, y, z);
			if (withinReach != null && !withinReach.test(p)) continue;
			if (!includeAir && world.getBlockState(p).isAir()) continue;
			out.add(p.toImmutable());
		}
		return SelectionCollectResult.success(out);
	}
}
