package com.beatblock.selection.collect;

import com.beatblock.selection.PlaneSliceBounds;
import com.beatblock.selection.SelectionCollectResult;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/** 平面切片：与选区包围盒或当前区块 ∩ 平面求交。 */
public final class PlaneSliceSelectionCollector {

	private PlaneSliceSelectionCollector() {}

	public static SelectionCollectResult collect(
		World world,
		BlockPos pos,
		Direction face,
		BlockPos selectionMin,
		BlockPos selectionMax,
		boolean includeAir,
		int maxBlocks,
		Predicate<BlockPos> withinReach
	) {
		if (world == null || pos == null || face == null) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.plane_slice.invalid_params"));
		}
		PlaneSliceBounds bounds = PlaneSliceBounds.compute(
			pos,
			face,
			selectionMin,
			selectionMax,
			new ChunkPos(pos),
			world.getBottomY(),
			world.getBottomY() + world.getHeight() - 1
		);
		if (bounds.isEmpty()) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.plane_slice.no_intersection"));
		}
		long vol = bounds.volume();
		if (vol > maxBlocks) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.plane_slice.volume_exceeded", vol, maxBlocks));
		}
		List<BlockPos> raw = bounds.positions();
		List<BlockPos> filtered = SelectionCollectSupport.filterBlocks(world, raw, includeAir, withinReach);
		if (filtered.size() > maxBlocks) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.plane_slice.count_exceeded", maxBlocks));
		}
		return SelectionCollectResult.success(filtered);
	}
}
