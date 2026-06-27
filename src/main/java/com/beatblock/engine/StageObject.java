package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第 2 层 — 舞台对象：参与演出的一组方块（概念模型中的「对象」）。
 * <p>
 * 动画与建造动作通过 {@link com.beatblock.timeline.TimelineAnimationEvent#getTargetObjectId()}
 * 引用本类实例。与 {@link com.beatblock.timeline.StageObject}（时间轴 UI 侧）对应，
 * 由 {@link StageObjectSystem} 管理运行时副本。
 * <p>
 * 性能优化：缓存按不同策略排序的方块列表，避免每次播放重复计算。
 */
public final class StageObject {

	private final String id;
	private final String name;
	private final List<BlockPos> blocks;
	private final Vec3d center;
	private final GroupSpec groupSpec;
	/** 排序结果缓存：策略 → 排序后的方块列表 */
	private final Map<GroupSortingStrategy, List<BlockPos>> sortedBlocksCache = new ConcurrentHashMap<>();

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

	/**
	 * 获取按指定策略排序的方块列表（结果会被缓存）。
	 *
	 * @param strategy 排序策略
	 * @return 排序后的方块列表（不可变视图）
	 */
	public List<BlockPos> getBlocksSorted(GroupSortingStrategy strategy) {
		if (strategy == null || strategy == GroupSortingStrategy.NONE) {
			return getBlocks();
		}
		return sortedBlocksCache.computeIfAbsent(strategy, s -> {
			List<BlockPos> sorted = new ArrayList<>(blocks);
			sortBlocks(sorted, s);
			return Collections.unmodifiableList(sorted);
		});
	}

	/**
	 * 清除排序缓存（方块列表修改后调用）。
	 */
	public void clearSortedCache() {
		sortedBlocksCache.clear();
	}

	/**
	 * 对方块列表进行原地排序。
	 */
	private void sortBlocks(List<BlockPos> blockList, GroupSortingStrategy strategy) {
		switch (strategy) {
			case SEQUENTIAL -> {
				// 保持原顺序（已经是原顺序）
			}
			case RADIAL -> blockList.sort((a, b) -> {
				double distA = center.squaredDistanceTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
				double distB = center.squaredDistanceTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
				return Double.compare(distA, distB);
			});
			case SPIRAL -> {
				// 简化螺旋：先按半径，再按角度
				blockList.sort((a, b) -> {
					double dx1 = a.getX() - center.x, dz1 = a.getZ() - center.z;
					double dx2 = b.getX() - center.x, dz2 = b.getZ() - center.z;
					double r1 = Math.sqrt(dx1 * dx1 + dz1 * dz1);
					double r2 = Math.sqrt(dx2 * dx2 + dz2 * dz2);
					int cmp = Double.compare(r1, r2);
					if (cmp != 0) return cmp;
					double angle1 = Math.atan2(dz1, dx1);
					double angle2 = Math.atan2(dz2, dx2);
					return Double.compare(angle1, angle2);
				});
			}
			case RANDOM -> {
				// 随机打乱（使用固定种子以保持可重复性）
				java.util.Random rng = new java.util.Random(id.hashCode());
				Collections.shuffle(blockList, rng);
			}
		}
	}

	public Vec3d getCenter() {
		return center;
	}

	public GroupSpec getGroupSpec() {
		return groupSpec;
	}
}
