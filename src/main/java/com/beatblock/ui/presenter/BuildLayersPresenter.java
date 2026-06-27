package com.beatblock.ui.presenter;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import com.beatblock.timeline.command.layer.DeleteLayerCommand;
import com.beatblock.timeline.command.layer.RenameLayerCommand;
import com.beatblock.timeline.command.layer.ToggleLayerVisibilityCommand;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 建造图层面板业务逻辑：校验、Command 封装与提交。
 */
public final class BuildLayersPresenter {

	public record RenameOutcome(PresenterResult result, String committedName) {}

	public record CreateOutcome(
		PresenterResult result,
		String createdLayerId,
		List<BlockPos> blocksToRemoveFromSelection
	) {}

	public record ToggleVisibilityOutcome(PresenterResult result) {}

	public record DeleteOutcome(PresenterResult result) {}

	private final Supplier<CommandManager> commandManager;
	private final Supplier<BuildLayerManager> layerManager;

	public BuildLayersPresenter(
		Supplier<CommandManager> commandManager,
		Supplier<BuildLayerManager> layerManager
	) {
		this.commandManager = commandManager;
		this.layerManager = layerManager;
	}

	public RenameOutcome renameLayer(String layerId, String rawName) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new RenameOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")), null);
		}
		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new RenameOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.layer_not_found")), null);
		}

		String trimmed = rawName != null ? rawName.trim() : "";
		if (trimmed.isEmpty()) {
			return new RenameOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.layer_name_empty")),
				layer.getName()
			);
		}
		if (trimmed.equals(layer.getName())) {
			return new RenameOutcome(PresenterResult.success(""), trimmed);
		}
		if (manager.isNameTaken(trimmed, layer.getId())) {
			return new RenameOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.layer_name_taken", trimmed)),
				layer.getName()
			);
		}

		commands.execute(new RenameLayerCommand(manager, layer.getId(), trimmed));
		return new RenameOutcome(PresenterResult.success(BBTexts.get("beatblock.message.layer_renamed", trimmed)), trimmed);
	}

	public CreateOutcome createLayerFromSelection(String rawName, List<BlockPos> selectedBlocks) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new CreateOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.engine_or_timeline_unavailable")), null, List.of());
		}

		List<BlockPos> blocks = selectedBlocks != null ? selectedBlocks : List.of();
		if (blocks.isEmpty()) {
			return new CreateOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.create_selection_first")), null, List.of());
		}

		int claimed = manager.countClaimedBlocks(blocks);
		if (claimed >= blocks.size()) {
			return new CreateOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.all_blocks_claimed")),
				null,
				List.of()
			);
		}

		String name = rawName != null ? rawName.trim() : "";
		var cmd = new CreateLayerCommand(manager, name.isEmpty() ? "layer" : name, blocks);
		commands.execute(cmd);

		BuildLayer created = cmd.getCreatedLayer();
		if (created == null) {
			return new CreateOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.create_layer_failed")), null, List.of());
		}

		String message = claimed > 0
			? BBTexts.get("beatblock.message.layer_created_skipped", created.getName(), claimed)
			: BBTexts.get("beatblock.message.layer_created", created.getName());
		return new CreateOutcome(
			PresenterResult.success(message),
			created.getId(),
			new ArrayList<>(created.getStageObject().getBlocks())
		);
	}

	public BuildLayerManager currentLayerManager() {
		return layerManager.get();
	}

	public BuildLayer findLayer(String layerId) {
		BuildLayerManager manager = layerManager.get();
		if (manager == null || layerId == null || layerId.isBlank()) {
			return null;
		}
		return manager.get(layerId);
	}

	public ToggleVisibilityOutcome toggleVisibility(String layerId) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")));
		}

		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.layer_not_found")));
		}

		World world = BuildLayerManager.currentWorld();
		if (world == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.no_world_context")));
		}

		boolean wasVisible = layer.getState() == LayerVisibilityState.FREE_VISIBLE;
		commands.execute(new ToggleLayerVisibilityCommand(manager, layer.getId()));
		String message = wasVisible
			? BBTexts.get("beatblock.message.layer_hidden")
			: BBTexts.get("beatblock.message.layer_shown");
		return new ToggleVisibilityOutcome(PresenterResult.success(message));
	}

	public DeleteOutcome deleteLayer(String layerId) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new DeleteOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")));
		}

		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new DeleteOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.layer_not_found")));
		}

		String layerName = layer.getName();
		commands.execute(new DeleteLayerCommand(manager, layer.getId()));
		return new DeleteOutcome(PresenterResult.success(BBTexts.get("beatblock.message.layer_deleted", layerName)));
	}
}
