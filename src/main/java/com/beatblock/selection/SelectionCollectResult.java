package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;

import java.util.List;

/** 单次选区收集（魔棒/整列/框选等）的结果。 */
public record SelectionCollectResult(List<BlockPos> blocks, String errorMessage) {

	public static SelectionCollectResult success(List<BlockPos> blocks) {
		return new SelectionCollectResult(blocks, null);
	}

	public static SelectionCollectResult failure(String errorMessage) {
		return new SelectionCollectResult(null, errorMessage);
	}

	public boolean failed() {
		return errorMessage != null;
	}
}
