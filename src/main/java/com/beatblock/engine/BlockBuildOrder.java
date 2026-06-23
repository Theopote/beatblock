package com.beatblock.engine;

import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 建造序列方块排序与进度块数计算（纯逻辑，便于单测）。
 */
public final class BlockBuildOrder {

	private BlockBuildOrder() {}

	public static List<BlockPos> sortBlocks(
		List<BlockPos> blocks,
		BuildSequenceMode mode,
		Vec3d center,
		TimelineAnimationEvent event,
		StageObject target
	) {
		if (blocks == null || blocks.isEmpty()) return List.of();
		List<BlockPos> ordered = new ArrayList<>(blocks);
		if (ordered.size() <= 1) return ordered;
		Vec3d c = center != null ? center : Vec3d.ZERO;
		BuildSequenceMode resolved = mode != null ? mode : BuildSequenceMode.WALL;

		switch (resolved) {
			case WALL -> ordered.sort(Comparator
				.comparingInt(BlockPos::getY)
				.thenComparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getZ));
			case BRIDGE -> ordered.sort(Comparator
				.comparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getZ)
				.thenComparingInt(BlockPos::getY));
			case TOWER -> ordered.sort(Comparator
				.comparingDouble((BlockPos p) -> horizontalDistSq(p, c))
				.thenComparingInt(BlockPos::getY));
			case DISSOLVE -> {
				long seed = buildSeed(event, target);
				ordered.sort(Comparator.comparingLong(p -> mixHash(seed, p)));
			}
		}
		return ordered;
	}

	public static int computeTargetBlockCount(int totalBlocks, double startTime, double endTime, double currentTime) {
		if (totalBlocks <= 0) return 0;
		if (currentTime >= endTime) return totalBlocks;
		if (currentTime < startTime) return 0;
		double span = endTime - startTime;
		if (span <= 1e-9) return totalBlocks;
		double progress = (currentTime - startTime) / span;
		progress = Math.max(0.0, Math.min(1.0, progress));
		return (int) Math.ceil(progress * totalBlocks);
	}

	private static double horizontalDistSq(BlockPos pos, Vec3d center) {
		double dx = (pos.getX() + 0.5) - center.x;
		double dz = (pos.getZ() + 0.5) - center.z;
		return dx * dx + dz * dz;
	}

	private static long buildSeed(TimelineAnimationEvent event, StageObject target) {
		long t = event != null ? Double.doubleToLongBits(event.getTimeSeconds()) : 0L;
		long id = event != null ? Objects.hashCode(event.getEventId()) : 0L;
		long tid = target != null ? Objects.hashCode(target.getId()) : 0L;
		return t ^ (id * 31L) ^ (tid * 131L);
	}

	private static long mixHash(long seed, BlockPos p) {
		long h = seed;
		h ^= ((long) p.getX()) * 0x9E3779B185EBCA87L;
		h ^= ((long) p.getY()) * 0xC2B2AE3D27D4EB4FL;
		h ^= ((long) p.getZ()) * 0x165667B19E3779F9L;
		h ^= (h >>> 33);
		h *= 0xff51afd7ed558ccdL;
		h ^= (h >>> 33);
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= (h >>> 33);
		return h;
	}
}
