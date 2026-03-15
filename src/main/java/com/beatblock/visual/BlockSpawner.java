package com.beatblock.visual;

import com.beatblock.stage.StageZone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 在舞台区域内生成/放置 BlockDisplay。
 */
public class BlockSpawner {

	private static boolean isServerLevel(Level level) {
		return level instanceof ServerLevel;
	}

	/**
	 * 在舞台中心附近生成一个 BlockDisplay，使用给定方块状态。
	 */
	public Display.BlockDisplay spawnAtStage(Level level, StageZone stage, BlockState blockState,
	                                         BlockDisplayPool pool) {
		if (level == null || stage == null || pool == null) return null;
		Display.BlockDisplay display = pool.obtain(level);
		if (display == null) return null;
		double x = stage.getCenterX();
		double y = stage.getCenterY();
		double z = stage.getCenterZ();
		display.setPos(x, y, z);
		display.setBlockState(blockState != null ? blockState : level.getBlockState(BlockPos.ZERO));
		if (isServerLevel(level)) {
			((ServerLevel) level).addFreshEntity(display);
		}
		return display;
	}

	/**
	 * 在指定坐标生成 BlockDisplay。
	 */
	public Display.BlockDisplay spawnAt(Level level, double x, double y, double z, BlockState blockState,
	                                    BlockDisplayPool pool) {
		if (level == null || pool == null) return null;
		Display.BlockDisplay display = pool.obtain(level);
		if (display == null) return null;
		display.setPos(x, y, z);
		display.setBlockState(blockState != null ? blockState : level.getBlockState(BlockPos.ZERO));
		if (isServerLevel(level)) {
			((ServerLevel) level).addFreshEntity(display);
		}
		return display;
	}
}
