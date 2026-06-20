package com.beatblock.engine.influence;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.BlockControlExecutor;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单帧方块影响求值结果：渲染层变换 + 世界 mutation 计划 + VFX 触发（期 3 消费）。
 */
public final class InfluenceFrame {

	private final Map<BlockPos, AnimatedBlock> animatedBlocks = new HashMap<>();
	private final List<BlockControlExecutor.BlockMutation> worldMutations = new ArrayList<>();
	private final List<VfxTrigger> vfxTriggers = new ArrayList<>();

	public Map<BlockPos, AnimatedBlock> getAnimatedBlocks() {
		return Collections.unmodifiableMap(animatedBlocks);
	}

	public List<BlockControlExecutor.BlockMutation> getWorldMutations() {
		return Collections.unmodifiableList(worldMutations);
	}

	public List<VfxTrigger> getVfxTriggers() {
		return Collections.unmodifiableList(vfxTriggers);
	}

	public AnimatedBlock animatedBlockFor(BlockPos pos) {
		return animatedBlocks.computeIfAbsent(pos.toImmutable(), AnimatedBlock::new);
	}

	public void addWorldMutation(BlockControlExecutor.BlockMutation mutation) {
		if (mutation != null) {
			worldMutations.add(mutation);
		}
	}

	public void addVfxTrigger(VfxTrigger trigger) {
		if (trigger != null) {
			vfxTriggers.add(trigger);
		}
	}

	public boolean isEmpty() {
		return animatedBlocks.isEmpty() && worldMutations.isEmpty() && vfxTriggers.isEmpty();
	}
}
