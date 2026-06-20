package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 第 2 层 — 舞台对象：参与演出的一组方块（概念模型中的「对象」）。
 * <p>
 * 动画与建造动作通过 {@link com.beatblock.timeline.TimelineAnimationEvent#getTargetObjectId()}
 * 引用本类实例。与 {@link com.beatblock.timeline.StageObject}（时间轴 UI 侧）对应，
 * 由 {@link StageObjectSystem} 管理运行时副本。
 */
public final class StageObject {

	private final String id;
	private final String name;
	private final List<BlockPos> blocks;
	private final Vec3d center;
	private final GroupSpec groupSpec;

	public StageObject(String id, String name, List<BlockPos> blocks, Vec3d center) {
		this(id, name, blocks, center, null);
	}

	public StageObject(String id, String name, List<BlockPos> blocks, Vec3d center, GroupSpec groupSpec) {
		this.id = id != null ? id : "";
		this.name = name != null ? name : id;
		this.blocks = blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
		this.center = center != null ? center : computeCenter(this.blocks);
		this.groupSpec = groupSpec != null ? groupSpec : GroupSpec.manualSnapshot();
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

	public GroupSpec getGroupSpec() {
		return groupSpec;
	}
}
