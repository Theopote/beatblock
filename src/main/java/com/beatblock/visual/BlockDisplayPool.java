package com.beatblock.visual;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * BlockDisplay 实体池：复用显示实体，减少生成/销毁。
 */
public class BlockDisplayPool {

	private final Deque<Display.BlockDisplay> available = new ArrayDeque<>();
	private final int maxPoolSize;

	public BlockDisplayPool() {
		this(64);
	}

	public BlockDisplayPool(int maxPoolSize) {
		this.maxPoolSize = Math.max(1, maxPoolSize);
	}

	private static boolean isServerLevel(Level level) {
		return level instanceof ServerLevel;
	}

	/**
	 * 从池中获取一个 BlockDisplay；若池空则在 level 中创建并加入世界（仅服务端生成实体）。
	 */
	public Display.BlockDisplay obtain(Level level) {
		Display.BlockDisplay display = available.poll();
		if (display != null) {
			display.setNoGravity(true);
			display.setInvisible(false);
			return display;
		}
		Display.BlockDisplay newDisplay = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
		if (newDisplay != null) {
			newDisplay.setNoGravity(true);
			if (isServerLevel(level)) {
				((ServerLevel) level).addFreshEntity(newDisplay);
			}
		}
		return newDisplay;
	}

	/**
	 * 将 display 从世界移除并销毁（不再复用）。
	 */
	public void release(Level level, Display.BlockDisplay display) {
		if (display == null) return;
		display.discard();
	}

	/**
	 * 将 display 设为不可见并归还池中以便复用。
	 */
	public void returnToPool(Display.BlockDisplay display) {
		if (display != null && available.size() < maxPoolSize) {
			display.setInvisible(true);
			display.setNoGravity(true);
			available.add(display);
		}
	}

	public int getAvailableCount() {
		return available.size();
	}
}
