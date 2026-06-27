package com.beatblock.engine.layer;

import com.beatblock.BeatBlock;
import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** BuildLayer 列表 ↔ JSON（.osc 持久化）。 */
public final class BuildLayerPersistence {

	private BuildLayerPersistence() {}

	public static JsonArray toJson(BuildLayerManager manager) {
		JsonArray arr = new JsonArray();
		if (manager == null) return arr;
		for (BuildLayer layer : manager.getAll()) {
			arr.add(layerToJson(layer));
		}
		return arr;
	}

	public static void loadInto(BuildLayerManager manager, JsonArray arr) {
		if (manager == null) return;
		manager.clear();
		if (arr == null) return;
		for (int i = 0; i < arr.size(); i++) {
			BuildLayer layer = layerFromJson(arr.get(i).getAsJsonObject());
			if (layer != null) manager.registerRestored(layer);
		}
	}

	private static JsonObject layerToJson(BuildLayer layer) {
		JsonObject root = new JsonObject();
		root.addProperty("id", layer.getId());
		root.addProperty("name", layer.getName());
		root.addProperty("state", layer.getState().name());
		if (layer.getBoundClipId() != null) {
			root.addProperty("boundClipId", layer.getBoundClipId());
		}
		StageObject stage = layer.getStageObject();
		root.addProperty("stageObjectId", stage.getId());
		root.addProperty("stageObjectName", stage.getName());

		JsonArray blocks = new JsonArray();
		for (BlockPos pos : stage.getBlocks()) {
			blocks.add(posToJson(pos));
		}
		root.add("blocks", blocks);

		JsonArray captured = new JsonArray();
		for (Map.Entry<BlockPos, BlockState> entry : layer.getCapturedStates().entrySet()) {
			JsonObject item = new JsonObject();
			item.add("pos", posToJson(entry.getKey()));
			item.add("state", BlockStateCodec.toJson(entry.getValue()));
			captured.add(item);
		}
		root.add("capturedStates", captured);
		return root;
	}

	private static BuildLayer layerFromJson(JsonObject root) {
		if (root == null || !root.has("id")) return null;
		String id = root.get("id").getAsString();
		String name = root.has("name") ? root.get("name").getAsString() : id;
		LayerVisibilityState state = LayerVisibilityState.FREE_VISIBLE;
		if (root.has("state")) {
			try {
				state = LayerVisibilityState.valueOf(root.get("state").getAsString());
			} catch (IllegalArgumentException e) {
				BeatBlock.LOGGER.debug("Unknown build layer state in .osc, using FREE_VISIBLE", e);
			}
		}
		String boundClipId = root.has("boundClipId") && !root.get("boundClipId").isJsonNull()
			? root.get("boundClipId").getAsString() : null;

		List<BlockPos> blocks = new ArrayList<>();
		if (root.has("blocks")) {
			JsonArray arr = root.getAsJsonArray("blocks");
			for (int i = 0; i < arr.size(); i++) {
				BlockPos pos = posFromJson(arr.get(i).getAsJsonObject());
				if (pos != null) blocks.add(pos);
			}
		}
		if (blocks.isEmpty()) return null;

		String stageId = root.has("stageObjectId") ? root.get("stageObjectId").getAsString() : id + "_stage";
		StageObject stageObject = StageObjectSystem.fromSelectionSnapshot(
			stageId, name, blocks, GroupSortingStrategy.SEQUENTIAL, 0.0);

		Map<BlockPos, BlockState> captured = new LinkedHashMap<>();
		if (root.has("capturedStates")) {
			JsonArray arr = root.getAsJsonArray("capturedStates");
			for (int i = 0; i < arr.size(); i++) {
				JsonObject item = arr.get(i).getAsJsonObject();
				BlockPos pos = posFromJson(item.getAsJsonObject("pos"));
				BlockState blockState = BlockStateCodec.fromJson(item.getAsJsonObject("state"));
				if (pos != null && blockState != null) captured.put(pos.toImmutable(), blockState);
			}
		}

		return new BuildLayer(id, name, stageObject, state, captured, boundClipId);
	}

	private static JsonObject posToJson(BlockPos pos) {
		JsonObject o = new JsonObject();
		o.addProperty("x", pos.getX());
		o.addProperty("y", pos.getY());
		o.addProperty("z", pos.getZ());
		return o;
	}

	private static BlockPos posFromJson(JsonObject o) {
		if (o == null || !o.has("x")) return null;
		return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
	}
}
