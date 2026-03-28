package com.beatblock.engine;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes PLACE/CLEAR timeline control actions against world blocks.
 * ANIMATE remains handled by the animation engine path.
 */
public final class BlockControlExecutor {

	private final StageObjectSystem stageObjectSystem;

	public BlockControlExecutor(StageObjectSystem stageObjectSystem) {
		this.stageObjectSystem = stageObjectSystem;
	}

	public record BlockMutation(BlockPos pos, BlockState fromState, BlockState toState) {}

	public enum ControlSkipReason {
		NONE,
		INVALID_INPUT,
		ANIMATE_MODE,
		MISSING_TARGET,
		EMPTY_TARGET,
		NO_CHUNK_LOADED,
		NO_STATE_CHANGE
	}

	public record ControlPlan(
		TimelineAnimationActionMode actionMode,
		String targetObjectId,
		List<BlockMutation> mutations,
		ControlSkipReason skipReason,
		int scannedBlocks,
		int loadedBlocks
	) {
		public boolean hasMutations() {
			return mutations != null && !mutations.isEmpty();
		}
	}

	public List<BlockMutation> planMutations(World world, TimelineAnimationEvent event) {
		return plan(world, event).mutations();
	}

	public ControlPlan plan(World world, TimelineAnimationEvent event) {
		List<BlockMutation> mutations = new ArrayList<>();
		if (world == null || event == null) {
			return new ControlPlan(TimelineAnimationActionMode.ANIMATE, "", mutations,
				ControlSkipReason.INVALID_INPUT, 0, 0);
		}

		TimelineAnimationActionMode actionMode = event.getActionMode();
		if (actionMode == TimelineAnimationActionMode.ANIMATE
				|| actionMode == TimelineAnimationActionMode.BUILD) {
			return new ControlPlan(actionMode, event.getTargetObjectId(), mutations,
				ControlSkipReason.ANIMATE_MODE, 0, 0);
		}

		StageObject target = stageObjectSystem != null ? stageObjectSystem.get(event.getTargetObjectId()) : null;
		if (target == null) {
			return new ControlPlan(actionMode, event.getTargetObjectId(), mutations,
				ControlSkipReason.MISSING_TARGET, 0, 0);
		}
		if (target.getBlocks().isEmpty()) {
			return new ControlPlan(actionMode, event.getTargetObjectId(), mutations,
				ControlSkipReason.EMPTY_TARGET, 0, 0);
		}

		BlockState toState = actionMode == TimelineAnimationActionMode.CLEAR
			? Blocks.AIR.getDefaultState()
			: resolvePlacementBlockState(event);

		int scannedBlocks = 0;
		int loadedBlocks = 0;
		for (BlockPos pos : target.getBlocks()) {
			scannedBlocks++;
			if (pos == null || !world.isChunkLoaded(pos)) continue;
			loadedBlocks++;
			BlockState fromState = world.getBlockState(pos);
			if (!fromState.equals(toState)) {
				mutations.add(new BlockMutation(pos.toImmutable(), fromState, toState));
			}
		}

		ControlSkipReason reason = ControlSkipReason.NONE;
		if (loadedBlocks == 0) {
			reason = ControlSkipReason.NO_CHUNK_LOADED;
		} else if (mutations.isEmpty()) {
			reason = ControlSkipReason.NO_STATE_CHANGE;
		}
		return new ControlPlan(actionMode, event.getTargetObjectId(), mutations, reason, scannedBlocks, loadedBlocks);
	}

	public void applyMutations(World world, List<BlockMutation> mutations) {
		if (world == null || mutations == null || mutations.isEmpty()) return;
		for (BlockMutation mutation : mutations) {
			if (mutation == null || mutation.pos() == null || mutation.toState() == null) continue;
			if (!world.isChunkLoaded(mutation.pos())) continue;
			if (!world.getBlockState(mutation.pos()).equals(mutation.toState())) {
				world.setBlockState(mutation.pos(), mutation.toState(), 3);
			}
		}
	}

	private static BlockState resolvePlacementBlockState(TimelineAnimationEvent event) {
		if (event == null) return Blocks.DIAMOND_BLOCK.getDefaultState();
		Object param = event.getParameters().get("placeBlock");
		if (param == null) param = event.getParameters().get("placeBlockId");
		if (param == null) return Blocks.DIAMOND_BLOCK.getDefaultState();

		String raw = String.valueOf(param).trim();
		if (raw.isEmpty()) return Blocks.DIAMOND_BLOCK.getDefaultState();
		Identifier id;
		try {
			id = Identifier.of(raw);
		} catch (Exception ex) {
			return Blocks.DIAMOND_BLOCK.getDefaultState();
		}
		if (!Registries.BLOCK.containsId(id)) return Blocks.DIAMOND_BLOCK.getDefaultState();
		Block block = Registries.BLOCK.get(id);
		if (block == null) return Blocks.DIAMOND_BLOCK.getDefaultState();
		return block.getDefaultState();
	}
}
