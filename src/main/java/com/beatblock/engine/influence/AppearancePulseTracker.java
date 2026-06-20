package com.beatblock.engine.influence;

import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.BlockStateResolver;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.EngineAnimationInstance;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * APPEARANCE 通道：动画中点触发材质闪烁，实例结束时还原。
 */
public final class AppearancePulseTracker {

	private final Map<String, Map<BlockPos, BlockState>> snapshots = new HashMap<>();
	private final Map<String, Boolean> flashed = new HashMap<>();

	public static boolean crossedMidpoint(float t, float previousT) {
		return t >= 0.5f && previousT < 0.5f;
	}

	public void contribute(
		String instanceKey,
		EngineAnimationInstance instance,
		BlockInfluencePreset preset,
		InfluenceFrame frame,
		World world,
		float t,
		float previousT,
		EffectContext ctx
	) {
		if (instance == null || preset == null || frame == null || world == null || ctx == null) return;
		if (!presetHasAppearance(preset)) return;
		if (!crossedMidpoint(t, previousT) || flashed.getOrDefault(instanceKey, false)) return;

		BlockState flash = BlockStateResolver.flashState(ctx.getExtraParams());
		for (BlockPos pos : instance.getTarget().getBlocks()) {
			if (pos == null || !world.isChunkLoaded(pos)) continue;
			BlockState current = world.getBlockState(pos);
			if (current.isAir()) continue;
			BlockPos immutable = pos.toImmutable();
			snapshots.computeIfAbsent(instanceKey, ignored -> new HashMap<>())
				.putIfAbsent(immutable, current);
			if (!current.equals(flash)) {
				frame.addWorldMutation(new BlockControlExecutor.BlockMutation(immutable, current, flash));
			}
			if (vfxEnabled(ctx.getExtraParams())) {
				frame.addVfxTrigger(new VfxTrigger(
					"appearance_flash",
					immutable,
					instance.getStartTimeSeconds() + t * Math.max(0.01, instance.getEndTimeSeconds() - instance.getStartTimeSeconds()),
					instance.getEnergy()
				));
			}
		}
		flashed.put(instanceKey, true);
	}

	public void revert(String instanceKey, InfluenceFrame frame, World world) {
		if (instanceKey == null || frame == null || world == null) return;
		Map<BlockPos, BlockState> snap = snapshots.remove(instanceKey);
		flashed.remove(instanceKey);
		if (snap == null) return;
		for (Map.Entry<BlockPos, BlockState> entry : snap.entrySet()) {
			BlockPos pos = entry.getKey();
			BlockState original = entry.getValue();
			if (pos == null || original == null || !world.isChunkLoaded(pos)) continue;
			BlockState current = world.getBlockState(pos);
			if (!current.equals(original)) {
				frame.addWorldMutation(new BlockControlExecutor.BlockMutation(pos, current, original));
			}
		}
	}

	public void clearInstance(String instanceKey) {
		snapshots.remove(instanceKey);
		flashed.remove(instanceKey);
	}

	private static boolean presetHasAppearance(BlockInfluencePreset preset) {
		return !preset.channelsFor(InfluenceDimension.APPEARANCE).isEmpty();
	}

	private static boolean vfxEnabled(Map<String, Object> params) {
		if (params == null) return true;
		Object raw = params.get("vfxEnabled");
		if (raw == null) return true;
		if (raw instanceof Boolean b) return b;
		return !"false".equalsIgnoreCase(String.valueOf(raw).trim());
	}
}
