package com.beatblock.automap.engine;

import net.minecraft.util.math.Vec3d;

/**
 * 舞台布局描述：类型、中心、规模，供自动编排空间分布参考。
 */
public final class StageLayout {

	private final LayoutType type;
	private final Vec3d center;
	private final int size;

	public StageLayout(LayoutType type, Vec3d center, int size) {
		this.type = type != null ? type : LayoutType.GRID;
		this.center = center != null ? center : Vec3d.ZERO;
		this.size = Math.max(0, size);
	}

	public LayoutType getType() { return type; }
	public Vec3d getCenter() { return center; }
	public int getSize() { return size; }
}
