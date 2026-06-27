package com.beatblock.selection.collect;

import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionBrushRegions;
import com.beatblock.selection.SelectionCollectResult;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/** 笔刷选区：球体或立方包络。 */
public final class BrushSelectionCollector {

	private BrushSelectionCollector() {}

	public static SelectionCollectResult collect(
		World world,
		BlockPos center,
		BrushShape shape,
		int radius,
		boolean includeAir,
		int maxBlocks,
		Predicate<BlockPos> withinReach
	) {
		if (center == null) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.brush.invalid_center"));
		}
		BrushShape resolved = shape != null ? shape : BrushShape.SPHERE;
		List<BlockPos> raw = switch (resolved) {
			case SPHERE -> SelectionBrushRegions.spherePositions(center, radius, maxBlocks);
			case CUBE -> SelectionBrushRegions.cubePositions(center, radius, maxBlocks);
		};
		if (raw == null) {
			long worst = (2L * radius + 1) * (2L * radius + 1) * (2L * radius + 1);
			return SelectionCollectResult.failure(resolved == BrushShape.CUBE
				? BBTexts.get("beatblock.selection.error.brush.cube_envelope_exceeded", worst, maxBlocks)
				: BBTexts.get("beatblock.selection.error.brush.sphere_envelope_exceeded", worst, maxBlocks));
		}
		if (world == null) {
			return SelectionCollectResult.success(raw);
		}
		return SelectionCollectResult.success(
			SelectionCollectSupport.filterBlocks(world, raw, includeAir, withinReach)
		);
	}
}
