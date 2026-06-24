package com.beatblock.ui.presenter;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import com.beatblock.timeline.command.layer.DeleteLayerCommand;
import com.beatblock.timeline.command.layer.RenameLayerCommand;
import com.beatblock.timeline.command.layer.ToggleLayerVisibilityCommand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
			return new RenameOutcome(PresenterResult.failure("编辑器不可用。"), null);
		}
		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new RenameOutcome(PresenterResult.failure("图层不存在。"), null);
		}

		String trimmed = rawName != null ? rawName.trim() : "";
		if (trimmed.isEmpty()) {
			return new RenameOutcome(
				PresenterResult.failure("图层名称不能为空。"),
				layer.getName()
			);
		}
		if (trimmed.equals(layer.getName())) {
			return new RenameOutcome(PresenterResult.success(""), trimmed);
		}
		if (manager.isNameTaken(trimmed, layer.getId())) {
			return new RenameOutcome(
				PresenterResult.failure("名称已被其他图层使用：" + trimmed),
				layer.getName()
			);
		}

		commands.execute(new RenameLayerCommand(manager, layer.getId(), trimmed));
		return new RenameOutcome(PresenterResult.success("已重命名为：" + trimmed), trimmed);
	}

	public CreateOutcome createLayerFromSelection(String rawName, List<BlockPos> selectedBlocks) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new CreateOutcome(PresenterResult.failure("引擎或时间线编辑器不可用。"), null, List.of());
		}

		List<BlockPos> blocks = selectedBlocks != null ? selectedBlocks : List.of();
		if (blocks.isEmpty()) {
			return new CreateOutcome(PresenterResult.failure("请先建立方块选区。"), null, List.of());
		}

		int claimed = manager.countClaimedBlocks(blocks);
		if (claimed >= blocks.size()) {
			return new CreateOutcome(
				PresenterResult.failure("选区内方块均已属于其他图层，无法创建新图层。"),
				null,
				List.of()
			);
		}

		String name = rawName != null ? rawName.trim() : "";
		var cmd = new CreateLayerCommand(manager, name.isEmpty() ? "layer" : name, blocks);
		commands.execute(cmd);

		BuildLayer created = cmd.getCreatedLayer();
		if (created == null) {
			return new CreateOutcome(PresenterResult.failure("创建图层失败。"), null, List.of());
		}

		String message = "已创建图层：" + created.getName();
		if (claimed > 0) {
			message += String.format(Locale.ROOT, "（已跳过 %d 个已属于其他图层的方块）", claimed);
		}
		return new CreateOutcome(
			PresenterResult.success(message),
			created.getId(),
			new ArrayList<>(created.getStageObject().getBlocks())
		);
	}

	public ToggleVisibilityOutcome toggleVisibility(String layerId) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure("编辑器不可用。"));
		}

		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure("图层不存在。"));
		}

		World world = BuildLayerManager.currentWorld();
		if (world == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure("当前无世界上下文，无法切换可见性。"));
		}

		boolean wasVisible = layer.getState() == LayerVisibilityState.FREE_VISIBLE;
		commands.execute(new ToggleLayerVisibilityCommand(manager, layer.getId()));
		String message = wasVisible ? "已隐藏图层（世界方块已清除）" : "已显示图层（方块已恢复）";
		return new ToggleVisibilityOutcome(PresenterResult.success(message));
	}

	public DeleteOutcome deleteLayer(String layerId) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new DeleteOutcome(PresenterResult.failure("编辑器不可用。"));
		}

		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new DeleteOutcome(PresenterResult.failure("图层不存在。"));
		}

		String layerName = layer.getName();
		commands.execute(new DeleteLayerCommand(manager, layer.getId()));
		return new DeleteOutcome(PresenterResult.success("已删除图层：" + layerName));
	}
}
