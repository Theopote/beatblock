package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 演出对象：定义哪些方块参与演出。动画针对方块集合（如 Temple、Bridge、Wave Blocks）。
 * center 用于旋转、波动、爆炸等效果的中心参考。
 */
public final class StageObject {

	private final String id;
	private final String name;
	private final List<BlockPos> blocks;
	private final Vec3d center;

	public StageObject(String id, String name, List<BlockPos> blocks, Vec3d center) {
		this.id = id != null ? id : "";
		this.name = name != null ? name : id;
		this.blocks = blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
		this.center = center != null ? center : computeCenter(this.blocks);
	}

	private static Vec3d computeCenter(List<BlockPos> blocks) {
		if (blocks == null || blocks.isEmpty()) return Vec3d.ZERO;
		double x = 0, y = 0, z = 0;
		for (BlockPos p : blocks) {
			x += p.getX() + 0.5;
			y += p.getY();
			z += p.getZ() + 0.5;
		}
		int n = blocks.size();
		return new Vec3d(x / n, y / n, z / n);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<BlockPos> getBlocks() {
		return Collections.unmodifiableList(blocks);
	}

	public Vec3d getCenter() {
		return center;
	}
}
