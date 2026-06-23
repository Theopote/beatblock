package com.beatblock.engine;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * 累积式建造序列器：将 StageObject 的方块按 BuildSequenceMode 排序，
 * 根据事件时长逐步放置（BUILD）或逐步移除（DISSOLVE 反向），
 * 每帧由 BeatBlockClientDriver 驱动 tick，随时间推进逐块出现。
 * <p>
 * 绑定图层的 BUILD 反向事件使用 {@link BuildLayer#getCapturedStates()} 逐块还原。
 */
public final class BuildSequencer {

	private final StageObjectSystem stageObjectSystem;
	private final BuildLayerManager buildLayerManager;

	public BuildSequencer(StageObjectSystem stageObjectSystem, BuildLayerManager buildLayerManager) {
		this.stageObjectSystem = stageObjectSystem;
		this.buildLayerManager = buildLayerManager;
	}

	/**
	 * 活跃的建造序列实例。
	 */
	public static final class BuildInstance {
		private final String eventId;
		private final List<BlockPos> orderedBlocks;
		private final BlockState targetState;
		private final Map<BlockPos, BlockState> perBlockTargetStates;
		private final double startTime;
		private final double endTime;
		private final boolean dissolve;
		private int placedCount;

		BuildInstance(String eventId, List<BlockPos> orderedBlocks, BlockState targetState,
		              Map<BlockPos, BlockState> perBlockTargetStates,
		              double startTime, double endTime, boolean dissolve) {
			this.eventId = eventId;
			this.orderedBlocks = orderedBlocks;
			this.targetState = targetState;
			this.perBlockTargetStates = perBlockTargetStates;
			this.startTime = startTime;
			this.endTime = endTime;
			this.dissolve = dissolve;
			this.placedCount = 0;
		}

		public String getEventId() { return eventId; }
		public boolean isFinished() { return placedCount >= orderedBlocks.size(); }
		public int getPlacedCount() { return placedCount; }
		public int getTotalBlocks() { return orderedBlocks.size(); }

		BlockState resolveTargetState(BlockPos pos) {
			if (perBlockTargetStates != null && pos != null) {
				BlockState perBlock = perBlockTargetStates.get(pos);
				if (perBlock != null) return perBlock;
			}
			return targetState;
		}
	}

	private final List<BuildInstance> activeInstances = new ArrayList<>();

	/**
	 * 从 TimelineAnimationEvent 创建建造序列并加入活跃列表。
	 * @return 新创建的 BuildInstance，若无法创建则返回 null
	 */
	public BuildInstance schedule(TimelineAnimationEvent event) {
		if (event == null) return null;

		Map<String, Object> params = event.getParameters();
		String layerId = readLayerId(params);
		BuildLayer layer = layerId != null && buildLayerManager != null ? buildLayerManager.get(layerId) : null;

		StageObject target;
		Map<BlockPos, BlockState> perBlockTargets = null;
		boolean layerReveal = false;

		if (layer != null) {
			target = layer.getStageObject();
			perBlockTargets = new LinkedHashMap<>(layer.getCapturedStates());
			layerReveal = true;
		} else {
			target = stageObjectSystem.get(event.getTargetObjectId());
		}
		if (target == null || target.getBlocks().isEmpty()) return null;

		BuildSequenceMode mode = BuildSequenceMode.fromValue(params.get("buildMode"));
		boolean dissolve = !layerReveal && "true".equalsIgnoreCase(String.valueOf(params.get("buildDissolve")));
		BlockState toState = dissolve
			? Blocks.AIR.getDefaultState()
			: (layerReveal ? Blocks.AIR.getDefaultState() : resolveBuildBlockState(params));

		List<BlockPos> ordered = BlockBuildOrder.sortBlocks(target.getBlocks(), mode, target.getCenter(), event, target);
		if (dissolve) Collections.reverse(ordered);

		double startTime = event.getTimeSeconds();
		double endTime = startTime + Math.max(0.05, event.getDurationSeconds());
		BuildInstance instance = new BuildInstance(
			event.getEventId(), ordered, toState, perBlockTargets, startTime, endTime, dissolve || layerReveal);
		activeInstances.add(instance);
		return instance;
	}

	/**
	 * 将本帧 EXISTENCE 维度的建造 mutation 写入 {@link com.beatblock.engine.influence.InfluenceFrame}（由 orchestrator 统一 apply）。
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
				BlockState desired = inst.resolveTargetState(pos);
				if (world.isChunkLoaded(pos)) {
					BlockState current = world.getBlockState(pos);
					if (!current.equals(desired)) {
						frame.addWorldMutation(new BlockControlExecutor.BlockMutation(
							pos.toImmutable(), current, desired));
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

	private static String readLayerId(Map<String, Object> params) {
		if (params == null) return null;
		Object raw = params.get("layerId");
		if (raw == null) return null;
		String id = String.valueOf(raw).trim();
		return id.isEmpty() ? null : id;
	}

	private static int computeTargetCount(BuildInstance inst, double currentTime) {
		return BlockBuildOrder.computeTargetBlockCount(
			inst.orderedBlocks.size(),
			inst.startTime,
			inst.endTime,
			currentTime
		);
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
