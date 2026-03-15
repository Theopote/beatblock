package com.beatblock.stage;

import net.minecraft.world.phys.AABB;

/**
 * 舞台区域：世界内空间范围，用于生成与限制 BlockDisplay。
 */
public class StageZone {

	private final String id;
	private final AABB bounds;
	private final double centerX, centerY, centerZ;

	public StageZone(String id, AABB bounds) {
		this.id = id != null ? id : "default";
		this.bounds = bounds != null ? bounds : new AABB(0, 0, 0, 1, 1, 1);
		this.centerX = (this.bounds.minX + this.bounds.maxX) * 0.5;
		this.centerY = (this.bounds.minY + this.bounds.maxY) * 0.5;
		this.centerZ = (this.bounds.minZ + this.bounds.maxZ) * 0.5;
	}

	public StageZone(String id, double x1, double y1, double z1, double x2, double y2, double z2) {
		this(id, new AABB(x1, y1, z1, x2, y2, z2));
	}

	public String getId() {
		return id;
	}

	public AABB getBounds() {
		return bounds;
	}

	public double getCenterX() { return centerX; }
	public double getCenterY() { return centerY; }
	public double getCenterZ() { return centerZ; }

	public boolean contains(double x, double y, double z) {
		return x >= bounds.minX && x <= bounds.maxX
			&& y >= bounds.minY && y <= bounds.maxY
			&& z >= bounds.minZ && z <= bounds.maxZ;
	}
}
