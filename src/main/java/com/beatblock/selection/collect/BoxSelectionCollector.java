package com.beatblock.selection.collect;

import com.beatblock.selection.SelectionCollectResult;
import com.beatblock.selection.SelectionRegions;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/** 轴对齐框选：两角点定义的长方体。 */
public final class BoxSelectionCollector {

	private BoxSelectionCollector() {}

	public static SelectionCollectResult collect(
		World world,
		BlockPos cornerA,
		BlockPos cornerB,
		boolean includeAir,
		int maxBlocks,
		Predicate<BlockPos> withinReach
	) {
		if (cornerA == null || cornerB == null) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.box.invalid_corners"));
		}
		List<BlockPos> raw = SelectionRegions.cuboidPositions(cornerA, cornerB, maxBlocks);
		if (raw == null) {
			return SelectionCollectResult.failure(BBTexts.get(
				"beatblock.selection.error.box.volume_exceeded",
				SelectionRegions.cuboidVolume(cornerA, cornerB),
				maxBlocks
			));
		}
		if (world == null) {
			return SelectionCollectResult.success(raw);
		}
		return SelectionCollectResult.success(
			SelectionCollectSupport.filterBlocks(world, raw, includeAir, withinReach)
		);
	}
}
