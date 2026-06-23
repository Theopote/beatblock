package com.beatblock.selection;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * 六邻域连通选区（魔棒 / 选区内魔棒）纯 BFS 实现。
 */
public final class ConnectedSelectionFloodFill {

	public record Request(
		BlockPos start,
		BlockPos boundsMin,
		BlockPos boundsMax,
		boolean includeAir,
		boolean matchFullBlockState,
		int maxBlocks,
		int maxSpreadFromSeed,
		Predicate<BlockPos> withinReach
	) {
		public Request {
			if (maxBlocks < 1) maxBlocks = 1;
		}

		public static Request unbounded(
			BlockPos start,
			boolean includeAir,
			boolean matchFullBlockState,
			int maxBlocks,
			int maxSpreadFromSeed
		) {
			return new Request(
				start,
				null,
				null,
				includeAir,
				matchFullBlockState,
				maxBlocks,
				maxSpreadFromSeed,
				null
			);
		}
	}

	public record Result(List<BlockPos> blocks, boolean truncated) {}

	private ConnectedSelectionFloodFill() {}

	public static Result collect(BlockStateLookup lookup, Request request) {
		if (lookup == null || request == null || request.start() == null) {
			return new Result(List.of(), false);
		}
		BlockPos start = request.start().toImmutable();
		if (!withinReach(request, start)) {
			return new Result(List.of(), false);
		}

		BlockState anchor = lookup.getBlockState(start);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		HashSet<BlockPos> visited = new HashSet<>();
		List<BlockPos> result = new ArrayList<>();
		boolean truncated = false;

		queue.add(start);
		visited.add(start);

		while (!queue.isEmpty()) {
			if (result.size() >= request.maxBlocks()) {
				truncated = true;
				break;
			}
			BlockPos p = queue.removeFirst();
			result.add(p);

			for (Direction d : Direction.values()) {
				BlockPos n = p.offset(d);
				if (visited.contains(n)) continue;
				if (!withinBounds(request, n)) continue;
				if (!withinReach(request, n)) continue;
				if (!withinSpread(request, start, n)) continue;
				BlockState st = lookup.getBlockState(n);
				if (!request.includeAir() && st.isAir()) continue;
				if (!connectedMatches(st, anchor, request.matchFullBlockState())) continue;
				visited.add(n);
				queue.add(n.toImmutable());
			}
		}

		return new Result(result, truncated);
	}

	private static boolean withinReach(Request request, BlockPos pos) {
		Predicate<BlockPos> reach = request.withinReach();
		return reach == null || reach.test(pos);
	}

	private static boolean withinBounds(Request request, BlockPos pos) {
		BlockPos min = request.boundsMin();
		BlockPos max = request.boundsMax();
		if (min == null || max == null) return true;
		return pos.getX() >= min.getX() && pos.getX() <= max.getX()
			&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
			&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	private static boolean withinSpread(Request request, BlockPos seed, BlockPos pos) {
		int maxSpread = request.maxSpreadFromSeed();
		if (maxSpread <= 0) return true;
		double dx = (pos.getX() + 0.5) - (seed.getX() + 0.5);
		double dy = (pos.getY() + 0.5) - (seed.getY() + 0.5);
		double dz = (pos.getZ() + 0.5) - (seed.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz <= (double) maxSpread * maxSpread;
	}

	private static boolean connectedMatches(BlockState state, BlockState anchor, boolean matchFullBlockState) {
		if (matchFullBlockState) return state.equals(anchor);
		return state.getBlock() == anchor.getBlock();
	}

	public static BlockStateLookup fromStates(java.util.Map<BlockPos, BlockState> states, BlockState missing) {
		BlockState fallback = missing != null ? missing : Blocks.AIR.getDefaultState();
		return pos -> {
			if (pos == null) return fallback;
			return states.getOrDefault(pos.toImmutable(), fallback);
		};
	}

	public static BlockStateLookup fromStates(java.util.Map<BlockPos, BlockState> states) {
		return fromStates(states, null);
	}
}
