package com.beatblock.selection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 平面切片 AABB（与 {@link BeatBlockSelectionManager#computePlaneSliceBounds} 一致）。
 */
public record PlaneSliceBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {

	public static final PlaneSliceBounds EMPTY = new PlaneSliceBounds(1, 0, 1, 0, 1, 0);

	public boolean isEmpty() {
		return minX > maxX || minY > maxY || minZ > maxZ;
	}

	public long volume() {
		if (isEmpty()) return 0;
		return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
	}

	public List<BlockPos> positions() {
		if (isEmpty()) return List.of();
		List<BlockPos> out = new ArrayList<>((int) Math.min(volume(), Integer.MAX_VALUE));
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					out.add(new BlockPos(x, y, z));
				}
			}
		}
		return out;
	}

	public static PlaneSliceBounds compute(
		BlockPos hitPos,
		Direction face,
		BlockPos selectionMin,
		BlockPos selectionMax,
		ChunkPos chunk,
		int worldBottomY,
		int worldTopY
	) {
		if (hitPos == null || face == null || chunk == null) return EMPTY;
		Direction.Axis axis = face.getAxis();
		boolean useSel = selectionMin != null && selectionMax != null;
		int cx0 = chunk.getStartX();
		int cx1 = cx0 + 15;
		int cz0 = chunk.getStartZ();
		int cz1 = cz0 + 15;

		return switch (axis) {
			case Y -> {
				int y = hitPos.getY();
				if (useSel) {
					BlockPos smin = Objects.requireNonNull(selectionMin);
					BlockPos smax = Objects.requireNonNull(selectionMax);
					if (y < smin.getY() || y > smax.getY()) {
						yield EMPTY;
					}
					yield new PlaneSliceBounds(smin.getX(), smax.getX(), y, y, smin.getZ(), smax.getZ());
				}
				yield new PlaneSliceBounds(cx0, cx1, y, y, cz0, cz1);
			}
			case X -> {
				int x = hitPos.getX();
				if (useSel) {
					BlockPos smin = Objects.requireNonNull(selectionMin);
					BlockPos smax = Objects.requireNonNull(selectionMax);
					if (x < smin.getX() || x > smax.getX()) {
						yield EMPTY;
					}
					yield new PlaneSliceBounds(x, x, smin.getY(), smax.getY(), smin.getZ(), smax.getZ());
				}
				yield new PlaneSliceBounds(x, x, worldBottomY, worldTopY, cz0, cz1);
			}
			case Z -> {
				int z = hitPos.getZ();
				if (useSel) {
					BlockPos smin = Objects.requireNonNull(selectionMin);
					BlockPos smax = Objects.requireNonNull(selectionMax);
					if (z < smin.getZ() || z > smax.getZ()) {
						yield EMPTY;
					}
					yield new PlaneSliceBounds(smin.getX(), smax.getX(), smin.getY(), smax.getY(), z, z);
				}
				yield new PlaneSliceBounds(cx0, cx1, worldBottomY, worldTopY, z, z);
			}
		};
	}
}
