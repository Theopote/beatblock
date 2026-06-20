package com.beatblock.engine;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;

/** 从事件参数解析 {@link BlockState}（放置 / 踩点闪烁等）。 */
public final class BlockStateResolver {

	private BlockStateResolver() {}

	public static BlockState placementState(Map<String, Object> params) {
		return fromParam(params, "placeBlock", "placeBlockId", Blocks.DIAMOND_BLOCK.getDefaultState());
	}

	public static BlockState flashState(Map<String, Object> params) {
		return fromParam(params, "flashBlock", "flashBlockId", Blocks.GOLD_BLOCK.getDefaultState());
	}

	private static BlockState fromParam(
		Map<String, Object> params,
		String primaryKey,
		String aliasKey,
		BlockState fallback
	) {
		Object raw = params != null ? params.get(primaryKey) : null;
		if (raw == null && params != null) raw = params.get(aliasKey);
		if (raw == null) return fallback;
		String str = String.valueOf(raw).trim();
		if (str.isEmpty()) return fallback;
		try {
			Identifier id = Identifier.of(str);
			if (!Registries.BLOCK.containsId(id)) return fallback;
			Block block = Registries.BLOCK.get(id);
			return block.getDefaultState();
		} catch (Exception ex) {
			return fallback;
		}
	}
}
