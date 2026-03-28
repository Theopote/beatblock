package com.beatblock.client;

import net.minecraft.util.math.BlockPos;

/**
 * BeatBlock UI 打开时，场景区左键拾取的方块（不破坏方块，供工具面板等使用）。
 */
public final class BeatBlockWorldPick {

	private static BlockPos lastLeftClickedBlock;

	private BeatBlockWorldPick() {}

	public static void setLastLeftClickedBlock(BlockPos pos) {
		lastLeftClickedBlock = pos != null ? pos.toImmutable() : null;
	}

	public static BlockPos getLastLeftClickedBlock() {
		return lastLeftClickedBlock;
	}

	public static void clear() {
		lastLeftClickedBlock = null;
	}
}
