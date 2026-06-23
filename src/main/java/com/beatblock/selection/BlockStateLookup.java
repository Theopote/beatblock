package com.beatblock.selection;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * 供选区算法查询方块状态（测试与生产均可注入）。
 */
@FunctionalInterface
public interface BlockStateLookup {

	BlockState getBlockState(BlockPos pos);
}
