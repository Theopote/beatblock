package com.beatblock.ui.panels;

import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import java.util.Locale;

/**
 * 选择工具属性：操作模式、空气、上限、统计与清空（对应 ChronoBlocks 属性面板中的工具上下文，精简版）。
 */
public class SelectionPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private final int[] maxBlocksScratch = new int[1];
	private final ImBoolean includeAirProxy = new ImBoolean(false);

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.SELECTION_PROPERTIES_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		var mgr = BeatBlockSelectionManager.get();
		ImGui.text("方块选择");
		ImGui.separator();

		ImGui.textDisabled("操作模式");
		for (SelectionOperation op : SelectionOperation.values()) {
			if (ImGui.radioButton(operationLabel(op) + "##selOp" + op.name(), mgr.getOperation() == op)) {
				mgr.setOperation(op);
			}
		}
		ImGui.textWrapped("说明：新建=替换选区。点击模式下按住 Shift 可强制加选（与 ChronoBlocks 一致）。");

		includeAirProxy.set(mgr.isIncludeAir());
		ImGui.checkbox("包含空气方块##selIncludeAir", includeAirProxy);
		mgr.setIncludeAir(includeAirProxy.get());

		maxBlocksScratch[0] = mgr.getMaxBlocks();
		ImGui.setNextItemWidth(180f);
		if (ImGui.sliderInt("框选方块上限##selMaxBlocks", maxBlocksScratch, 4096, 500_000)) {
			mgr.setMaxBlocks(maxBlocksScratch[0]);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("长方体框选包含的方块数超过此值时将拒绝，避免误操作卡死。");
		}

		ImGui.separator();
		ImGui.textDisabled(String.format(Locale.ROOT, "已选方块: %d", mgr.getSelectionCount()));
		var min = mgr.getBoundingMin();
		var max = mgr.getBoundingMax();
		if (min != null && max != null) {
			ImGui.textDisabled(String.format(Locale.ROOT,
				"包围盒: [%d,%d,%d] — [%d,%d,%d]",
				min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
		}

		if (mgr.getMode() == SelectionMode.BOX && mgr.getBoxFirstCorner() != null) {
			var c = mgr.getBoxFirstCorner();
			ImGui.textWrapped(String.format(Locale.ROOT,
				"框选进行中：角点 A = %d, %d, %d。在场景区移动鼠标可预览选框，再左键点 B。", c.getX(), c.getY(), c.getZ()));
			if (ImGui.button("取消角点 A##selCancelBoxA")) {
				mgr.cancelBoxCorner();
			}
		}

		if (!mgr.getLastMessage().isBlank()) {
			ImGui.textWrapped(mgr.getLastMessage());
		}
		if (ImGui.button("清空选区##selClearAll")) {
			mgr.clearSelection();
		}
		ImGui.sameLine();
		if (ImGui.button("清除提示##selClearMsg")) {
			mgr.clearMessage();
		}

		ImGui.end();
	}

	private static String operationLabel(SelectionOperation op) {
		return switch (op) {
			case NEW -> "新建选区";
			case ADD -> "加选";
			case SUBTRACT -> "减选";
			case INTERSECT -> "交集";
		};
	}
}
