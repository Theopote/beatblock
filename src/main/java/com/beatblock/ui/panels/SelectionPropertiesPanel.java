package com.beatblock.ui.panels;

import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.BrushShape;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import java.util.Locale;

/**
 * 选择工具属性：操作模式、空气、上限、统计与清空（对应 ChronoBlocks 属性面板中的工具上下文，精简版）。
 */
public class SelectionPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private final int[] maxBlocksScratch = new int[1];
	private final int[] sphereRadiusScratch = new int[1];
	private final int[] maxCameraDistScratch = new int[1];
	private final int[] maxWandSpreadScratch = new int[1];
	private final ImBoolean includeAirProxy = new ImBoolean(false);
	private final ImBoolean connectedFullStateProxy = new ImBoolean(true);
	private final ImBoolean selectionFillProxy = new ImBoolean(false);

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.SELECTION_PROPERTIES_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		var mgr = BeatBlockSelectionManager.get();
		ImGui.text("方块选择");
		ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.75f, 1f, 1f);
		ImGui.text("当前工具：" + currentToolTitle(mgr.getMode()));
		ImGui.popStyleColor();
		ImGui.separator();

		ImGui.textDisabled("操作模式");
		for (SelectionOperation op : SelectionOperation.values()) {
			if (ImGui.radioButton(operationLabel(op) + "##selOp" + op.name(), mgr.getOperation() == op)) {
				mgr.setOperation(op);
			}
		}
		ImGui.textWrapped("说明：新建=替换选区。单击类工具（含平面切片、选区魔棒）按住 Shift 可强制加选；笔刷/套索涂抹同样适用。");

		maxCameraDistScratch[0] = mgr.getMaxDistanceFromCamera();
		ImGui.setNextItemWidth(200f);
		if (ImGui.sliderInt("相对视角最大距离（格）##selCamDist", maxCameraDistScratch, 16, 512)) {
			mgr.setMaxDistanceFromCamera(maxCameraDistScratch[0]);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("候选方块中心到相机不能超过此距离。套索、魔棒、切片、框/线/列/笔刷等均生效，防止无界选中。");
		}

		selectionFillProxy.set(mgr.isSelectionFillEnabled());
		ImGui.checkbox("选区半透明填充（与描边叠加）##selFill", selectionFillProxy);
		mgr.setSelectionFillEnabled(selectionFillProxy.get());
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("在方块选择 UI 打开且选中方块数量不太多时，为每个格子绘制略缩小的半透明面；大量选区时仅显示总包围盒。");
		}

		includeAirProxy.set(mgr.isIncludeAir());
		ImGui.checkbox("包含空气方块##selIncludeAir", includeAirProxy);
		mgr.setIncludeAir(includeAirProxy.get());

		maxBlocksScratch[0] = mgr.getMaxBlocks();
		ImGui.setNextItemWidth(180f);
		if (ImGui.sliderInt("框选方块上限##selMaxBlocks", maxBlocksScratch, 4096, 500_000)) {
			mgr.setMaxBlocks(maxBlocksScratch[0]);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("框/线/笔刷/列/切片体积、连通与选区魔棒展开、笔刷单次盖章等超过此值时拒绝或截断。");
		}

		if (mgr.getMode() == SelectionMode.BRUSH) {
			ImGui.separator();
			ImGui.textDisabled("笔刷（球体 / 立方）");
			sphereRadiusScratch[0] = mgr.getSphereBrushRadius();
			ImGui.setNextItemWidth(160f);
			if (ImGui.sliderInt("半径（格）##selBrushR", sphereRadiusScratch, 1, 32)) {
				mgr.setSphereBrushRadius(sphereRadiusScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("球体：欧氏球；立方体：轴对齐立方体边长 2r+1；预览为包络盒。单击盖章或按住涂抹。");
			}
			if (ImGui.radioButton("球体##brushSph", mgr.getBrushShape() == BrushShape.SPHERE)) {
				mgr.setBrushShape(BrushShape.SPHERE);
			}
			ImGui.sameLine();
			if (ImGui.radioButton("立方体##brushCube", mgr.getBrushShape() == BrushShape.CUBE)) {
				mgr.setBrushShape(BrushShape.CUBE);
			}
		}

		if (mgr.getMode() == SelectionMode.CONNECTED || mgr.getMode() == SelectionMode.SELECTION_WAND) {
			ImGui.separator();
			ImGui.textDisabled("魔棒");
			maxWandSpreadScratch[0] = mgr.getMaxMagicWandSpreadFromSeed();
			ImGui.setNextItemWidth(200f);
			if (ImGui.sliderInt("最大扩散半径（格）##selWandSpread", maxWandSpreadScratch, 4, 256)) {
				mgr.setMaxMagicWandSpreadFromSeed(maxWandSpreadScratch[0]);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("从点击的种子方块算起，欧氏距离超过此值的格子不会入选（全图魔棒与选区魔棒均适用）。");
			}
			connectedFullStateProxy.set(mgr.isConnectedMatchFullState());
			ImGui.checkbox("完整方块状态一致##selConnFull", connectedFullStateProxy);
			mgr.setConnectedMatchFullState(connectedFullStateProxy.get());
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("勾选：与起点 BlockState 完全相同才算同色；关闭：仅方块类型一致。");
			}
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

		if (mgr.getMode() == SelectionMode.LINE && mgr.getLineFirstCorner() != null) {
			var c = mgr.getLineFirstCorner();
			ImGui.textWrapped(String.format(Locale.ROOT,
				"线选进行中：端点 A = %d, %d, %d。移动鼠标可预览范围，再左键点 B。", c.getX(), c.getY(), c.getZ()));
			if (ImGui.button("取消端点 A##selCancelLineA")) {
				mgr.cancelLineCorner();
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

	private static String currentToolTitle(SelectionMode m) {
		return switch (m) {
			case OFF -> "关闭";
			case CLICK -> "点击选择";
			case BOX -> "框选";
			case LINE -> "线选";
			case BRUSH -> "笔刷";
			case CONNECTED -> "魔棒（连通）";
			case COLUMN -> "整列";
			case PLANE_SLICE -> "平面切片";
			case SELECTION_WAND -> "选区魔棒";
			case LASSO -> "套索";
		};
	}
}
