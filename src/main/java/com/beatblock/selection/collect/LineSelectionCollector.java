package com.beatblock.selection.collect;

import com.beatblock.selection.BlockSelectionLine;
import com.beatblock.selection.SelectionCollectResult;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/** 线选：体素折线或圆柱扫掠。 */
public final class LineSelectionCollector {

	private LineSelectionCollector() {}

	public static SelectionCollectResult collect(
		World world,
		BlockPos endA,
		BlockPos endB,
		int thicknessRadius,
		boolean includeAir,
		int maxBlocks,
		Predicate<BlockPos> withinReach
	) {
		if (endA == null || endB == null) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.line.invalid_endpoints"));
		}
		List<BlockPos> raw = BlockSelectionLine.blocksForSegment(endA, endB, thicknessRadius, maxBlocks);
		if (raw == null) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.line.candidates_exceeded", maxBlocks));
		}
		if (world == null) {
			return SelectionCollectResult.success(raw);
		}
		return SelectionCollectResult.success(
			SelectionCollectSupport.filterBlocks(world, raw, includeAir, withinReach)
		);
	}
}
