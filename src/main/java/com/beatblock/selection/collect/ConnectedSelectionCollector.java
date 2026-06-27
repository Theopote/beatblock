package com.beatblock.selection.collect;

import com.beatblock.selection.BlockStateLookup;
import com.beatblock.selection.ConnectedCellLookup;
import com.beatblock.selection.ConnectedSelectionFloodFill;
import com.beatblock.selection.SelectionCollectResult;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

/** 连通魔棒：全图或限定包围盒内的六邻域 flood-fill。 */
public final class ConnectedSelectionCollector {

	private ConnectedSelectionCollector() {}

	public static SelectionCollectResult collect(
		BlockStateLookup blockStates,
		BlockPos start,
		BlockPos boundsMin,
		BlockPos boundsMax,
		boolean includeAir,
		boolean matchFullState,
		int maxBlocks,
		int maxSpreadFromSeed,
		Predicate<BlockPos> withinReach,
		String outOfReachMessage,
		String truncatedMessagePrefix
	) {
		SelectionCollectResult reachFailure = validateStart(start, withinReach, outOfReachMessage);
		if (reachFailure != null) {
			return reachFailure;
		}
		ConnectedSelectionFloodFill.Result result = ConnectedSelectionFloodFill.collect(
			blockStates,
			buildRequest(start, boundsMin, boundsMax, includeAir, matchFullState, maxBlocks, maxSpreadFromSeed, withinReach)
		);
		return toResult(result, maxBlocks, truncatedMessagePrefix);
	}

	public static SelectionCollectResult collect(
		ConnectedCellLookup cellLookup,
		BlockPos start,
		BlockPos boundsMin,
		BlockPos boundsMax,
		boolean includeAir,
		boolean matchFullState,
		int maxBlocks,
		int maxSpreadFromSeed,
		Predicate<BlockPos> withinReach,
		String outOfReachMessage,
		String truncatedMessagePrefix
	) {
		SelectionCollectResult reachFailure = validateStart(start, withinReach, outOfReachMessage);
		if (reachFailure != null) {
			return reachFailure;
		}
		ConnectedSelectionFloodFill.Result result = ConnectedSelectionFloodFill.collect(
			cellLookup,
			buildRequest(start, boundsMin, boundsMax, includeAir, matchFullState, maxBlocks, maxSpreadFromSeed, withinReach)
		);
		return toResult(result, maxBlocks, truncatedMessagePrefix);
	}

	private static SelectionCollectResult validateStart(
		BlockPos start,
		Predicate<BlockPos> withinReach,
		String outOfReachMessage
	) {
		if (start == null) {
			return SelectionCollectResult.failure(BBTexts.get("beatblock.selection.error.connected.invalid_seed"));
		}
		BlockPos startImm = start.toImmutable();
		if (withinReach != null && !withinReach.test(startImm)) {
			return SelectionCollectResult.failure(outOfReachMessage);
		}
		return null;
	}

	private static ConnectedSelectionFloodFill.Request buildRequest(
		BlockPos start,
		BlockPos boundsMin,
		BlockPos boundsMax,
		boolean includeAir,
		boolean matchFullState,
		int maxBlocks,
		int maxSpreadFromSeed,
		Predicate<BlockPos> withinReach
	) {
		return new ConnectedSelectionFloodFill.Request(
			start.toImmutable(),
			boundsMin,
			boundsMax,
			includeAir,
			matchFullState,
			maxBlocks,
			maxSpreadFromSeed,
			withinReach
		);
	}

	private static SelectionCollectResult toResult(
		ConnectedSelectionFloodFill.Result result,
		int maxBlocks,
		String truncatedMessagePrefix
	) {
		if (result.truncated()) {
			return SelectionCollectResult.success(
				result.blocks(),
				String.format(truncatedMessagePrefix, maxBlocks, result.blocks().size())
			);
		}
		return SelectionCollectResult.success(result.blocks());
	}
}
