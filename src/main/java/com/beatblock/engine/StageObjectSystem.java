package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * 管理演出对象（StageObject）：注册、按 id 查找。可选与 Timeline/StageManager 解析联动。
 */
public final class StageObjectSystem {

	private final Map<String, StageObject> objects = new LinkedHashMap<>();

	public void register(StageObject stageObject) {
		if (stageObject != null) objects.put(stageObject.getId(), stageObject);
	}

	public StageObject get(String id) {
		return objects.get(id);
	}

	public Collection<StageObject> getAll() {
		return Collections.unmodifiableCollection(objects.values());
	}

	public boolean remove(String id) {
		return objects.remove(id) != null;
	}

	public int size() {
		return objects.size();
	}

	public void clear() {
		objects.clear();
	}

	/** 从方块列表快速创建一个临时 StageObject（中心自动计算） */
	public static StageObject fromBlocks(String id, String name, List<BlockPos> blocks) {
		return new StageObject(id, name, blocks, null, GroupSpec.manualSnapshot());
	}

	public static StageObject fromBlocks(String id, String name, List<BlockPos> blocks, GroupSpec groupSpec) {
		return new StageObject(id, name, blocks, null, groupSpec);
	}

	public static StageObject fromSelectionCuboid(String id, String name, List<BlockPos> blocks,
	                                              BlockPos posA, BlockPos posB, boolean includeAir) {
		return new StageObject(id, name, blocks, null, GroupSpec.fromSelectionCuboid(posA, posB, includeAir));
	}

	public static StageObject fromSelectionSnapshot(String id, String name, List<BlockPos> blocks,
	                                                GroupSortingStrategy sortingStrategy,
	                                                double staggerDelaySeconds) {
		return new StageObject(
			id,
			name,
			blocks,
			null,
			GroupSpec.fromSelectionSnapshot(blocks, sortingStrategy, staggerDelaySeconds)
		);
	}
}
