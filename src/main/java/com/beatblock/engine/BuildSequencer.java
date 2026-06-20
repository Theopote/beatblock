package com.beatblock.engine;

import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * 累积式建造序列器：将 StageObject 的方块按 BuildSequenceMode 排序，
 * 根据事件时长逐步放置（BUILD）或逐步移除（DISSOLVE 反向），
 * 每帧由 BeatBlockClientDriver 驱动 tick，随时间推进逐块出现。
 */
public final class BuildSequencer {

	private final StageObjectSystem stageObjectSystem;

	public BuildSequencer(StageObjectSystem stageObjectSystem) {
		this.stageObjectSystem = stageObjectSystem;
	}

	/**
	 * 活跃的建造序列实例。
	 */
	public static final class BuildInstance {
		private final String eventId;
		private final List<BlockPos> orderedBlocks;
		private final BlockState targetState;
		private final double startTime;
		private final double endTime;
		private final boolean dissolve;
		private int placedCount;

		BuildInstance(String eventId, List<BlockPos> orderedBlocks, BlockState targetState,
		              double startTime, double endTime, boolean dissolve) {
			this.eventId = eventId;
			this.orderedBlocks = orderedBlocks;
			this.targetState = targetState;
			this.startTime = startTime;
			this.endTime = endTime;
			this.dissolve = dissolve;
			this.placedCount = 0;
		}

		public String getEventId() { return eventId; }
		public boolean isFinished() { return placedCount >= orderedBlocks.size(); }
		public int getPlacedCount() { return placedCount; }
		public int getTotalBlocks() { return orderedBlocks.size(); }
	}

	private final List<BuildInstance> activeInstances = new ArrayList<>();

	/**
	 * 从 TimelineAnimationEvent 创建建造序列并加入活跃列表。
	 * @return 新创建的 BuildInstance，若无法创建则返回 null
	 */
	public BuildInstance schedule(TimelineAnimationEvent event) {
		if (event == null) return null;
		StageObject target = stageObjectSystem.get(event.getTargetObjectId());
		if (target == null || target.getBlocks().isEmpty()) return null;

		Map<String, Object> params = event.getParameters();
		BuildSequenceMode mode = BuildSequenceMode.fromValue(params.get("buildMode"));
		boolean dissolve = "true".equalsIgnoreCase(String.valueOf(params.get("buildDissolve")));
		BlockState toState = dissolve
			? Blocks.AIR.getDefaultState()
			: resolveBuildBlockState(params);

		List<BlockPos> ordered = sortBlocksForBuild(target, mode, event);
		if (dissolve) Collections.reverse(ordered);

		double startTime = event.getTimeSeconds();
		double endTime = startTime + Math.max(0.05, event.getDurationSeconds());
		BuildInstance instance = new BuildInstance(event.getEventId(), ordered, toState, startTime, endTime, dissolve);
		activeInstances.add(instance);
		return instance;
	}

	/**
	 * 将本帧 EXISTENCE 维度的建造 mutation 写入 {@link InfluenceFrame}（由 orchestrator 统一 apply）。
	 */
	public void contributeExistenceMutations(
		com.beatblock.engine.influence.InfluenceFrame frame,
		double currentTime,
		World world
	) {
		if (frame == null || world == null || activeInstances.isEmpty()) return;
		Iterator<BuildInstance> it = activeInstances.iterator();
		while (it.hasNext()) {
			BuildInstance inst = it.next();
			if (currentTime < inst.startTime) continue;
			int target = computeTargetCount(inst, currentTime);
			while (inst.placedCount < target && inst.placedCount < inst.orderedBlocks.size()) {
				BlockPos pos = inst.orderedBlocks.get(inst.placedCount);
				if (world.isChunkLoaded(pos)) {
					BlockState current = world.getBlockState(pos);
					if (!current.equals(inst.targetState)) {
						frame.addWorldMutation(new BlockControlExecutor.BlockMutation(
							pos.toImmutable(), current, inst.targetState));
						frame.addVfxTrigger(new com.beatblock.engine.influence.VfxTrigger(
							inst.dissolve ? "existence_dissolve" : "existence_place",
							pos.toImmutable(),
							currentTime,
							1f
						));
					}
				}
				inst.placedCount++;
			}
			if (inst.isFinished()) it.remove();
		}
	}

	/**
	 * @deprecated 由 {@link com.beatblock.engine.influence.BlockInfluenceOrchestrator} 统一 tick
	 */
	@Deprecated
	public int tick(double currentTime, World world) {
		if (world == null || activeInstances.isEmpty()) return 0;
		com.beatblock.engine.influence.InfluenceFrame frame = new com.beatblock.engine.influence.InfluenceFrame();
		contributeExistenceMutations(frame, currentTime, world);
		BlockControlExecutor executor = new BlockControlExecutor(stageObjectSystem);
		executor.applyMutations(world, frame.getWorldMutations());
		return frame.getWorldMutations().size();
	}

	public List<BuildInstance> getActiveInstances() {
		return Collections.unmodifiableList(activeInstances);
	}

	public void clear() {
		activeInstances.clear();
	}

	// --- internal ---

	private static int computeTargetCount(BuildInstance inst, double currentTime) {
		if (currentTime >= inst.endTime) return inst.orderedBlocks.size();
		double progress = (currentTime - inst.startTime) / (inst.endTime - inst.startTime);
		progress = Math.max(0.0, Math.min(1.0, progress));
		return (int) Math.ceil(progress * inst.orderedBlocks.size());
	}

	private List<BlockPos> sortBlocksForBuild(StageObject target, BuildSequenceMode mode, TimelineAnimationEvent event) {
		List<BlockPos> blocks = new ArrayList<>(target.getBlocks());
		if (blocks.size() <= 1) return blocks;
		Vec3d center = target.getCenter();

		switch (mode) {
			case WALL -> blocks.sort(Comparator
				.comparingInt(BlockPos::getY)
				.thenComparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getZ));
			case BRIDGE -> blocks.sort(Comparator
				.comparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getZ)
				.thenComparingInt(BlockPos::getY));
			case TOWER -> blocks.sort(Comparator
				.comparingDouble((BlockPos p) -> horizontalDistSq(p, center))
				.thenComparingInt(BlockPos::getY));
			case DISSOLVE -> {
				long seed = buildSeed(event, target);
				blocks.sort(Comparator.comparingLong(p -> mixHash(seed, p)));
			}
		}
		return blocks;
	}

	private static double horizontalDistSq(BlockPos pos, Vec3d center) {
		double dx = (pos.getX() + 0.5) - center.x;
		double dz = (pos.getZ() + 0.5) - center.z;
		return dx * dx + dz * dz;
	}

	private static long buildSeed(TimelineAnimationEvent event, StageObject target) {
		long t = Double.doubleToLongBits(event.getTimeSeconds());
		long id = Objects.hashCode(event.getEventId());
		long tid = Objects.hashCode(target.getId());
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
		return h;
	}

	private static BlockState resolveBuildBlockState(Map<String, Object> params) {
		Object raw = params != null ? params.get("placeBlock") : null;
		if (raw == null && params != null) raw = params.get("placeBlockId");
		if (raw == null) return Blocks.DIAMOND_BLOCK.getDefaultState();
		String str = String.valueOf(raw).trim();
		if (str.isEmpty()) return Blocks.DIAMOND_BLOCK.getDefaultState();
		try {
			Identifier id = Identifier.of(str);
			if (!Registries.BLOCK.containsId(id)) return Blocks.DIAMOND_BLOCK.getDefaultState();
			Block block = Registries.BLOCK.get(id);
			return block.getDefaultState();
		} catch (Exception ex) {
			return Blocks.DIAMOND_BLOCK.getDefaultState();
		}
	}
}
