package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * 管理演出对象（StageObject）：注册、按 id 查找。可选与 Timeline/StageManager 解析联动。
 */
public final class StageObjectSystem {

	private final Map<String, StageObject> objects = new HashMap<>();

	public void register(StageObject stageObject) {
		if (stageObject != null) objects.put(stageObject.getId(), stageObject);
	}

	public StageObject get(String id) {
		return objects.get(id);
	}

	public Collection<StageObject> getAll() {
		return Collections.unmodifiableCollection(objects.values());
	}

	public void clear() {
		objects.clear();
	}

	/** 从方块列表快速创建一个临时 StageObject（中心自动计算） */
	public static StageObject fromBlocks(String id, String name, List<BlockPos> blocks) {
		return new StageObject(id, name, blocks, null);
	}
}
