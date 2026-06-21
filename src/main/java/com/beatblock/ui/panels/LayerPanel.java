package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import com.beatblock.timeline.command.layer.DeleteLayerCommand;
import com.beatblock.timeline.command.layer.RenameLayerCommand;
import com.beatblock.timeline.command.layer.ToggleLayerVisibilityCommand;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 建造图层面板：列出 BuildLayer，支持重命名、图标切换可见性、右键删除确认与拖入时间线。
 */
public class LayerPanel {

	public static final String DRAG_PAYLOAD_TYPE = "BB_BUILD_LAYER_ID";
	private static final String CONTEXT_POPUP = "##LayerRowContext";
	private static final String DELETE_CONFIRM_POPUP = "##LayerDeleteConfirm";
	private static final float ICON_BTN = 22f;

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final ImString newLayerNameBuffer = new ImString("layer", 64);
	private final Map<String, ImString> nameEditBuffers = new HashMap<>();
	private final Map<String, String> nameCommitted = new HashMap<>();

	private String selectedLayerId;
	private String pendingDeleteLayerId;
	private String statusMessage = "";

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.LAYER_PANEL_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.LAYER_PANEL_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			renderContent();
			renderDeleteConfirmPopup();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.LAYER_PANEL_WINDOW);
		}
	}

	private void renderContent() {
		ImGui.text("建造图层");
		ImGui.separator();
		ImGui.textWrapped("从选区创建图层 → 点击眼睛隐藏 → 拖入「建造还原」轨道绑定片段播放。已属于某图层的方块无法再次选入其他图层。");

		var selMgr = BeatBlockSelectionManager.get();
		int selCount = selMgr.getSelectionCount();
		ImGui.textDisabled(String.format(Locale.ROOT, "当前选区：%d 个方块", selCount));

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("图层名称##layerName", newLayerNameBuffer);

		if (selCount <= 0) ImGui.beginDisabled();
		if (ImGui.button("从选区新建图层##layerCreate", -1f, 0f)) {
			createLayerFromSelection();
		}
		if (selCount <= 0) ImGui.endDisabled();

		ImGui.separator();
		renderLayerList();

		if (!statusMessage.isBlank()) {
			ImGui.spacing();
			ImGui.textWrapped(statusMessage);
		}
	}

	private void renderLayerList() {
		if (BeatBlock.blockAnimationEngine == null) {
			ImGui.textDisabled("动画引擎未就绪。");
			return;
		}
		var manager = BeatBlock.blockAnimationEngine.getBuildLayerManager();
		List<BuildLayer> layers = new ArrayList<>(manager.getAll());
		pruneNameBuffers(layers);

		if (layers.isEmpty()) {
			ImGui.textDisabled("暂无图层。");
			return;
		}

		for (BuildLayer layer : layers) {
			renderLayerRow(layer, manager);
		}
	}

	private void renderLayerRow(BuildLayer layer, BuildLayerManager manager) {
		ImGui.pushID(layer.getId());
		boolean selected = layer.getId().equals(selectedLayerId);

		IconButtonStyle.pushBeatBlockIconButton();
		String visTooltip = renderVisibilityIconButton(layer);
		IconButtonStyle.popBeatBlockIconButton();
		if (visTooltip != null) {
			ImGui.setTooltip(visTooltip);
		}

		ImGui.sameLine();
		float nameWidth = Math.max(80f, ImGui.getContentRegionAvail().x - 8f);
		ImGui.setNextItemWidth(nameWidth);
		ImString nameBuf = nameBufferFor(layer);
		int flags = ImGuiInputTextFlags.EnterReturnsTrue;
		if (ImGui.inputText("##layerNameEdit", nameBuf, flags)) {
			commitLayerRename(layer, manager, nameBuf.get());
		}
		if (ImGui.isItemDeactivatedAfterEdit()) {
			commitLayerRename(layer, manager, nameBuf.get());
		}
		if (ImGui.isItemClicked()) {
			selectedLayerId = layer.getId();
		}
		boolean requestDeleteConfirm = false;
		if (ImGui.beginPopupContextItem(CONTEXT_POPUP)) {
			boolean canDelete = layer.canDelete();
			if (!canDelete) ImGui.beginDisabled();
			if (ImGui.menuItem("删除图层...")) {
				pendingDeleteLayerId = layer.getId();
				requestDeleteConfirm = true;
				ImGui.closeCurrentPopup();
			}
			if (!canDelete) {
				ImGui.endDisabled();
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("已绑定轨道，不可删除");
				}
			}
			ImGui.endPopup();
		}
		if (requestDeleteConfirm) {
			ImGui.openPopup(DELETE_CONFIRM_POPUP);
		}

		if (ImGui.beginDragDropSource()) {
			if (layer.canBindToTrack()) {
				ImGui.text("绑定到建造还原轨道");
				ImGui.setDragDropPayload(DRAG_PAYLOAD_TYPE, layer.getId().getBytes(), ImGuiCond.Once);
			} else {
				ImGui.textDisabled(stateHint(layer));
			}
			ImGui.endDragDropSource();
		}

		if (layer.getState() == LayerVisibilityState.BOUND_TO_TRACK) {
			ImGui.sameLine();
			ImGui.textDisabled("[已绑定]");
		}

		if (selected) {
			ImGui.indent();
			ImGui.textDisabled(String.format(Locale.ROOT, "方块数：%d", layer.getStageObject().getBlocks().size()));
			if (layer.getBoundClipId() != null) {
				ImGui.textDisabled("绑定片段：" + layer.getBoundClipId());
			}
			ImGui.unindent();
		}

		ImGui.popID();
	}

	private String renderVisibilityIconButton(BuildLayer layer) {
		boolean canToggle = layer.canToggleVisibility();
		boolean visible = layer.getState() == LayerVisibilityState.FREE_VISIBLE;
		String icon = visible ? Icons.EYE : Icons.Action.HIDDEN;

		if (!canToggle) {
			ImGui.beginDisabled();
			icon = Icons.Action.LOCK;
		} else if (!visible) {
			ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.45f, 0.45f, 1f);
		}

		if (ImGui.button(icon + "##layerVis_" + layer.getId(), ICON_BTN, ICON_BTN)) {
			toggleVisibility(layer);
		}

		if (!canToggle) {
			ImGui.endDisabled();
		} else if (!visible) {
			ImGui.popStyleColor();
		}

		String tooltip = null;
		if (ImGui.isItemHovered()) {
			if (!canToggle) {
				tooltip = "已绑定轨道，可见性由片段播放头控制";
			} else if (visible) {
				tooltip = "当前可见，点击隐藏（世界方块变为空气）";
			} else {
				tooltip = "当前隐藏，点击显示（恢复捕获的方块）";
			}
		}
		return tooltip;
	}

	private void renderDeleteConfirmPopup() {
		if (pendingDeleteLayerId == null) return;
		BuildLayer layer = BeatBlock.blockAnimationEngine != null
			? BeatBlock.blockAnimationEngine.getBuildLayerManager().get(pendingDeleteLayerId)
			: null;

		ImGui.setNextWindowSize(360f, 0f);
		if (!ImGui.beginPopupModal(DELETE_CONFIRM_POPUP, ImGuiWindowFlags.AlwaysAutoResize)) {
			return;
		}

		ImGui.text(Icons.Action.WARNING + " 删除图层");
		ImGui.separator();

		if (layer == null) {
			ImGui.textWrapped("图层已不存在。");
		} else {
			ImGui.textWrapped(String.format(Locale.ROOT,
				"确定删除图层「%s」？此操作不可通过面板撤销以外的途径轻易恢复。",
				layer.getName()));
			if (layer.getState() == LayerVisibilityState.FREE_HIDDEN) {
				ImGui.spacing();
				ImGui.textWrapped("该图层当前为隐藏状态，删除前会先恢复世界中的方块。");
			}
		}

		ImGui.spacing();
		if (ImGui.button("确认删除##layerDeleteOk", 120f, 0f) && layer != null) {
			deleteLayer(layer);
			pendingDeleteLayerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button("取消##layerDeleteCancel", 120f, 0f)) {
			pendingDeleteLayerId = null;
			ImGui.closeCurrentPopup();
		}

		ImGui.endPopup();
	}

	private ImString nameBufferFor(BuildLayer layer) {
		ImString buf = nameEditBuffers.computeIfAbsent(layer.getId(), id -> new ImString(layer.getName(), 64));
		String committed = nameCommitted.get(layer.getId());
		if (!layer.getName().equals(committed)) {
			buf.set(layer.getName());
			nameCommitted.put(layer.getId(), layer.getName());
		}
		return buf;
	}

	private void commitLayerRename(BuildLayer layer, BuildLayerManager manager, String rawName) {
		if (BeatBlock.timelineEditor == null || layer == null || manager == null) return;
		String trimmed = rawName != null ? rawName.trim() : "";
		if (trimmed.isEmpty()) {
			nameBufferFor(layer).set(layer.getName());
			statusMessage = "图层名称不能为空。";
			return;
		}
		if (trimmed.equals(layer.getName())) {
			nameCommitted.put(layer.getId(), trimmed);
			return;
		}
		if (manager.isNameTaken(trimmed, layer.getId())) {
			nameBufferFor(layer).set(layer.getName());
			statusMessage = "名称已被其他图层使用：" + trimmed;
			return;
		}
		var cmd = new RenameLayerCommand(manager, layer.getId(), trimmed);
		BeatBlock.timelineEditor.getCommandManager().execute(cmd);
		nameCommitted.put(layer.getId(), trimmed);
		statusMessage = "已重命名为：" + trimmed;
	}

	private void createLayerFromSelection() {
		if (BeatBlock.blockAnimationEngine == null || BeatBlock.timelineEditor == null) {
			statusMessage = "引擎或时间线编辑器不可用。";
			return;
		}
		var selMgr = BeatBlockSelectionManager.get();
		List<BlockPos> blocks = new ArrayList<>(selMgr.getSelectedBlocks());
		if (blocks.isEmpty()) {
			statusMessage = "请先建立方块选区。";
			return;
		}
		var manager = BeatBlock.blockAnimationEngine.getBuildLayerManager();
		int claimed = manager.countClaimedBlocks(blocks);
		if (claimed >= blocks.size()) {
			statusMessage = "选区内方块均已属于其他图层，无法创建新图层。";
			return;
		}
		String name = newLayerNameBuffer.get() != null ? newLayerNameBuffer.get().trim() : "";
		var cmd = new CreateLayerCommand(manager, name.isEmpty() ? "layer" : name, blocks);
		BeatBlock.timelineEditor.getCommandManager().execute(cmd);
		if (cmd.getCreatedLayer() != null) {
			BuildLayer created = cmd.getCreatedLayer();
			selectedLayerId = created.getId();
			nameCommitted.put(created.getId(), created.getName());
			statusMessage = "已创建图层：" + created.getName();
			if (claimed > 0) {
				statusMessage += String.format(Locale.ROOT, "（已跳过 %d 个已属于其他图层的方块）", claimed);
			}
			selMgr.removeBlocks(created.getStageObject().getBlocks());
		} else {
			statusMessage = "创建图层失败。";
		}
	}

	private void toggleVisibility(BuildLayer layer) {
		if (BeatBlock.timelineEditor == null || BeatBlock.blockAnimationEngine == null) return;
		World world = BuildLayerManager.currentWorld();
		if (world == null) {
			statusMessage = "当前无世界上下文，无法切换可见性。";
			return;
		}
		boolean wasVisible = layer.getState() == LayerVisibilityState.FREE_VISIBLE;
		var cmd = new ToggleLayerVisibilityCommand(
			BeatBlock.blockAnimationEngine.getBuildLayerManager(),
			layer.getId()
		);
		BeatBlock.timelineEditor.getCommandManager().execute(cmd);
		statusMessage = wasVisible ? "已隐藏图层（世界方块已清除）" : "已显示图层（方块已恢复）";
	}

	private void deleteLayer(BuildLayer layer) {
		if (BeatBlock.timelineEditor == null || BeatBlock.blockAnimationEngine == null) return;
		var cmd = new DeleteLayerCommand(
			BeatBlock.blockAnimationEngine.getBuildLayerManager(),
			layer.getId()
		);
		BeatBlock.timelineEditor.getCommandManager().execute(cmd);
		nameEditBuffers.remove(layer.getId());
		nameCommitted.remove(layer.getId());
		if (layer.getId().equals(selectedLayerId)) selectedLayerId = null;
		statusMessage = "已删除图层：" + layer.getName();
	}

	private void pruneNameBuffers(List<BuildLayer> layers) {
		Set<String> alive = new HashSet<>();
		for (BuildLayer layer : layers) {
			alive.add(layer.getId());
		}
		nameEditBuffers.keySet().removeIf(id -> !alive.contains(id));
		nameCommitted.keySet().removeIf(id -> !alive.contains(id));
	}

	private static String stateHint(BuildLayer layer) {
		return switch (layer.getState()) {
			case FREE_VISIBLE -> "请先隐藏图层再拖入轨道";
			case BOUND_TO_TRACK -> "已绑定到轨道";
			default -> "";
		};
	}
}
