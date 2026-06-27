package com.beatblock.ui.panels;

import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.ToolPanelPresenter;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * 左侧工具面板：层次为「场景选区 → 自动化编排 → 动画场景对象」。
 * Marker 管理与时间线动作调试已拆分至 {@link MarkerPanel}；
 * 天降方块（RhythmDrop）已拆分至 {@link RhythmDropPanel}。
 * 方块选择由 {@link BeatBlockSelectionManager} 管理；StageObject 创建使用轴对齐包围盒，
 * 默认从当前方块选区的外接 AABB 一键填入，避免与「框选工具」语义重复。
 */
public class ToolPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private boolean showAutoMapSettings = false;
	private final AutoMapSettingsPanel autoMapSettingsPanel = new AutoMapSettingsPanel();
	private final ToolPanelPresenter presenter;
	private final ImString stageObjectNameBuffer = new ImString(64);
	private final ImBoolean stageObjectIncludeAir = new ImBoolean(false);
	private final ImInt stageObjectSortingIndex = new ImInt(0);
	private final ImString stageObjectStaggerBuffer = new ImString(16);
	private String stageObjectMessage;
	private long stageObjectMessageTimeMs;
	private static final String[] STAGE_GROUP_SORTING_LABELS = {
		"顺序 (SEQUENTIAL)",
		"径向 (RADIAL)",
		"螺旋 (SPIRAL)",
		"随机 (RANDOM)",
		"同时 (ALL)"
	};
	private final Runnable onSelectionToolChosen;

	public ToolPanel() {
		this(null);
	}

	public ToolPanel(Runnable onSelectionToolChosen) {
		this(onSelectionToolChosen, PresenterFactories.toolPanelPresenter());
	}

	ToolPanel(Runnable onSelectionToolChosen, ToolPanelPresenter presenter) {
		this.onSelectionToolChosen = onSelectionToolChosen;
		this.presenter = presenter;
		stageObjectNameBuffer.set("selection_object");
		stageObjectStaggerBuffer.set("0.00");
	}

	/** 由菜单栏「演出 → Smart Auto Map」调用，打开设置弹窗 */
	public void setShowAutoMapSettings(boolean show) {
		this.showAutoMapSettings = show;
	}
	/** 上次生成统计 */
	private SmartAutoMapEngine.AutoMapResult lastAutoMapResult = null;

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text("工具");
			ImGui.separator();

			renderBlockSelectionTools();

			ImGui.spacing();
			ImGui.textDisabled("自动化编排");
			ImGui.separator();
			if (ImGui.button("Smart Auto Map")) {
				showAutoMapSettings = true;
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("根据已导入音乐生成时间线事件（方块动画、镜头、粒子等），与当前选区无绑定。");
			}
			if (lastAutoMapResult != null) {
				ImGui.sameLine();
				ImGui.textDisabled(String.format("上次：动 %d · 镜 %d · 粒 %d",
					lastAutoMapResult.getAnimationEvents(),
					lastAutoMapResult.getCameraEvents(),
					lastAutoMapResult.getParticleEvents()));
			}

			renderStageObjectCreator();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW);
		}

		if (showAutoMapSettings) {
			boolean done = autoMapSettingsPanel.render(res -> lastAutoMapResult = res);
			if (done) showAutoMapSettings = false;
		}
	}

	private void renderBlockSelectionTools() {
		ImGui.text("方块选择工具");
		var state = presenter.selectionToolViewState();
		ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
		if (ImGui.beginCombo("##bselCombo", ToolPanelPresenter.selectionModeLabel(state.mode()))) {
			for (SelectionMode mode : ToolPanelPresenter.selectionComboOrder()) {
				boolean selected = state.mode() == mode;
				if (ImGui.selectable(ToolPanelPresenter.selectionModeLabel(mode), selected)) {
					if (state.mode() != mode) {
						presenter.setSelectionMode(mode);
						if (onSelectionToolChosen != null) {
							onSelectionToolChosen.run();
						}
					}
				}
				if (selected) {
					ImGui.setItemDefaultFocus();
				}
			}
			ImGui.endCombo();
		}

		// === 动态显示当前工具的属性（集成选择属性面板功能） ===
		ImGui.spacing();
		ImGui.textColored(0.7f, 0.9f, 1f, 1f, "工具设置:");
		ImGui.separator();
		renderToolSpecificProperties(state.mode());

		// === 通用属性 ===
		ImGui.spacing();
		ImGui.textColored(0.7f, 0.9f, 1f, 1f, "通用设置:");
		ImGui.separator();
		renderCommonSelectionProperties();

		ImGui.separator();
	}

	/**
	 * 根据当前选择的工具动态显示相应的属性（原SelectionPropertiesPanel的功能）
	 */
	private void renderToolSpecificProperties(SelectionMode mode) {
		var selMgr = com.beatblock.selection.BeatBlockSelectionManager.get();

		switch (mode) {
			case BRUSH -> {
				com.beatblock.selection.BrushShape shape = selMgr.getBrushShape();
				String shapeLabel = shape == com.beatblock.selection.BrushShape.SPHERE ? "球体" : "立方体";
				if (ImGui.beginCombo("形状##brushShape", shapeLabel)) {
					if (ImGui.selectable("球体##sphereOpt", shape == com.beatblock.selection.BrushShape.SPHERE)) {
						selMgr.setBrushShape(com.beatblock.selection.BrushShape.SPHERE);
					}
					if (ImGui.selectable("立方体##cubeOpt", shape == com.beatblock.selection.BrushShape.CUBE)) {
						selMgr.setBrushShape(com.beatblock.selection.BrushShape.CUBE);
					}
					ImGui.endCombo();
				}
				int[] radius = {selMgr.getSphereBrushRadius()};
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt("大小##brushSize", radius, 1, 32)) {
					selMgr.setSphereBrushRadius(radius[0]);
				}
			}
			case LINE -> {
				int[] thickness = {selMgr.getLineThicknessRadius()};
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt("线粗细##lineThick", thickness, 0, 32)) {
					selMgr.setLineThicknessRadius(thickness[0]);
				}
			}
			case CONNECTED, SELECTION_WAND -> {
				int[] spread = {selMgr.getMaxMagicWandSpreadFromSeed()};
				ImGui.setNextItemWidth(-1f);
				if (ImGui.sliderInt("扩散半径##wandSpread", spread, 1, 256)) {
					selMgr.setMaxMagicWandSpreadFromSeed(spread[0]);
				}
				boolean fullState = selMgr.isConnectedMatchFullState();
				if (ImGui.checkbox("完整状态匹配##fullState", new ImBoolean(fullState))) {
					selMgr.setConnectedMatchFullState(!fullState);
				}
			}
			case PLANE_SLICE -> {
				net.minecraft.util.math.Direction override = selMgr.getPlaneSliceFaceOverride();
				String[] faceLabels = {"自动", "+Y", "-Y", "+X", "-X", "+Z", "-Z"};
				int faceIndex = override == null ? 0 :
					switch (override) {
						case UP -> 1; case DOWN -> 2; case EAST -> 3;
						case WEST -> 4; case SOUTH -> 5; case NORTH -> 6;
					};
				ImInt faceIndexImInt = new ImInt(faceIndex);
				if (ImGui.combo("切片朝向##planeDir", faceIndexImInt, faceLabels)) {
					net.minecraft.util.math.Direction newDir = switch (faceIndexImInt.get()) {
						case 1 -> net.minecraft.util.math.Direction.UP;
						case 2 -> net.minecraft.util.math.Direction.DOWN;
						case 3 -> net.minecraft.util.math.Direction.EAST;
						case 4 -> net.minecraft.util.math.Direction.WEST;
						case 5 -> net.minecraft.util.math.Direction.SOUTH;
						case 6 -> net.minecraft.util.math.Direction.NORTH;
						default -> null;
					};
					selMgr.setPlaneSliceFaceOverride(newDir);
				}
			}
			case OFF, CLICK, BOX, COLUMN, LASSO -> ImGui.textDisabled("（无特殊设置）");
		}
	}

	/**
	 * 渲染所有工具通用的属性
	 */
	private void renderCommonSelectionProperties() {
		var selMgr = com.beatblock.selection.BeatBlockSelectionManager.get();

		ImGui.text("操作:");
		com.beatblock.selection.SelectionOperation op = selMgr.getOperation();
		if (ImGui.radioButton("新建##opNew", op == com.beatblock.selection.SelectionOperation.NEW)) {
			selMgr.setOperation(com.beatblock.selection.SelectionOperation.NEW);
		}
		ImGui.sameLine();
		if (ImGui.radioButton("加选##opAdd", op == com.beatblock.selection.SelectionOperation.ADD)) {
			selMgr.setOperation(com.beatblock.selection.SelectionOperation.ADD);
		}
		ImGui.sameLine();
		if (ImGui.radioButton("减选##opSub", op == com.beatblock.selection.SelectionOperation.SUBTRACT)) {
			selMgr.setOperation(com.beatblock.selection.SelectionOperation.SUBTRACT);
		}

		int[] maxDist = {selMgr.getMaxDistanceFromCamera()};
		ImGui.setNextItemWidth(-1f);
		if (ImGui.sliderInt("相机距离##camDist", maxDist, 16, 512)) {
			selMgr.setMaxDistanceFromCamera(maxDist[0]);
		}

		boolean includeAir = selMgr.isIncludeAir();
		if (ImGui.checkbox("包含空气##includeAir", new ImBoolean(includeAir))) {
			selMgr.setIncludeAir(!includeAir);
		}

		int selCount = selMgr.getSelectedBlocks().size();
		if (selCount > 0) {
			ImGui.textColored(0.4f, 1f, 0.4f, 1f,
				String.format(java.util.Locale.ROOT, "已选: %d 个方块", selCount));
		}
	}

	private void renderStageObjectCreator() {
		ImGui.spacing();
		ImGui.textDisabled("动画场景对象（StageObject）");
		ImGui.separator();
		ImGui.textWrapped("选择方块后创建对象，用于时间线动画事件。");

		var selectionState = presenter.selectionToolViewState();
		int selCount = selectionState.selectionCount();
		if (selCount > 0) {
			ImGui.textColored(0.4f, 1f, 0.4f, 1f,
				String.format(java.util.Locale.ROOT, "✓ 已选方块：%d 个", selCount));
		} else {
			ImGui.textColored(1f, 0.6f, 0.2f, 1f, "⚠ 请先用上方工具选择方块");
		}

		// === 快速创建按钮（推荐） ===
		boolean canCreateFromSelection = selCount > 0;
		if (!canCreateFromSelection) ImGui.beginDisabled();

		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);

		if (ImGui.button("快速创建 (推荐)##quickCreate", -1f, 32f)) {
			quickCreateFromSelection();
		}

		ImGui.popStyleColor(3);

		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("一键创建：自动命名、使用默认参数\n适合快速开始创作");
		}

		if (!canCreateFromSelection) ImGui.endDisabled();

		// === 精确创建（快照模式） ===
		ImGui.spacing();
		if (!canCreateFromSelection) ImGui.beginDisabled();
		if (ImGui.button("精确创建 (保留选区形状)##stageCreateFromSelection", -1f, 0f)) {
			var outcome = presenter.createFromSelectionSnapshot(buildQuickStageObjectRequest());
			applyStageObjectMessage(outcome.result());
		}
		if (!canCreateFromSelection) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("保留当前选中的方块集合（不扩成矩形），适合不规则形状");
		}

		// === 高级选项（折叠） ===
		ImGui.spacing();
		ImGui.setNextItemOpen(false, ImGuiCond.Once);
		if (ImGui.collapsingHeader("高级选项 (可选)##stageAdvanced")) {
			ImGui.textWrapped("自定义名称和参数。大部分情况使用默认即可。");

			ImGui.spacing();
			ImGui.text("对象名称:");
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("##stageObjName", stageObjectNameBuffer);

			ImGui.text("包围盒角点:");
			ToolPanelPresenter.CornerState corners = presenter.currentCorners();
			ImGui.textDisabled("  A: " + ToolPanelPresenter.formatPos(corners.posA()));
			ImGui.textDisabled("  B: " + ToolPanelPresenter.formatPos(corners.posB()));

			if (ImGui.button("用选区包围盒填入##stageFromSel", -1f, 0f)) {
				applyStageObjectMessage(presenter.fillCornersFromSelection().result());
			}

			ImGui.setNextItemOpen(false, ImGuiCond.Once);
			if (ImGui.treeNode("准星拾取角点##stageManualCorner")) {
				ImGui.textWrapped("用准星对准方块，分别指定长方体的两个对角。");
				if (ImGui.button("准星 → A##stageObjSetA")) {
					applyStageObjectMessage(presenter.setCornerFromCrosshair(true).result());
				}
				ImGui.sameLine();
				if (ImGui.button("准星 → B##stageObjSetB")) {
					applyStageObjectMessage(presenter.setCornerFromCrosshair(false).result());
				}
				if (ImGui.button("清空##stageObjClearSelection")) {
					applyStageObjectMessage(presenter.clearCorners().result());
				}
				ImGui.treePop();
			}

			ImGui.spacing();
			ImGui.checkbox("包含空气方块##stageObjIncludeAir", stageObjectIncludeAir);

			ImGui.spacing();
			ImGui.text("排序策略:");
			ImGui.setNextItemWidth(-1f);
			ImGui.combo("##stageGroupSorting", stageObjectSortingIndex, STAGE_GROUP_SORTING_LABELS);

			ImGui.text("步进延迟(秒):");
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("##stageGroupStagger", stageObjectStaggerBuffer);

			ImGui.spacing();
			boolean canCreate = corners.posA() != null && corners.posB() != null;
			if (!canCreate) ImGui.beginDisabled();
			if (ImGui.button("使用自定义参数创建##stageObjCreate", -1f, 0f)) {
				var outcome = presenter.createFromCuboid(buildStageObjectRequest());
				applyStageObjectMessage(outcome.result());
			}
			if (!canCreate) ImGui.endDisabled();
		}

		if (stageObjectMessage != null && !stageObjectMessage.isBlank()
				&& System.currentTimeMillis() - stageObjectMessageTimeMs < 5000L) {
			ImGui.textWrapped(stageObjectMessage);
		}

		renderStageObjectList();
	}

	private void renderStageObjectList() {
		var objects = presenter.listStageObjects();
		if (objects.isEmpty()) {
			ImGui.spacing();
			ImGui.textDisabled("暂无已注册的 StageObject。");
			return;
		}

		ImGui.spacing();
		ImGui.text("已注册对象 (" + objects.size() + ")");
		String removeId = null;
		if (ImGui.beginChild("##StageObjectList", 0, Math.min(objects.size() * 22f + 8f, 160f), true)) {
			for (var obj : objects) {
				String label = obj.name() + "  [" + obj.id() + "]  " + obj.blockCount() + " blocks";
				ImGui.text(label);
				ImGui.sameLine();
				ImGui.textDisabled("(" + obj.sourceType() + ")");
				ImGui.sameLine();
				if (ImGui.smallButton("Delete##stageObjDel_" + obj.id())) {
					removeId = obj.id();
				}
			}
		}
		ImGui.endChild();

		if (removeId != null) {
			applyStageObjectMessage(presenter.removeStageObject(removeId));
		}
	}

	private ToolPanelPresenter.StageObjectCreateRequest buildStageObjectRequest() {
		return new ToolPanelPresenter.StageObjectCreateRequest(
			stageObjectNameBuffer.get(),
			stageObjectIncludeAir.get(),
			ToolPanelPresenter.sortingStrategyAtIndex(stageObjectSortingIndex.get()),
			ToolPanelPresenter.parseStaggerSeconds(stageObjectStaggerBuffer.get())
		);
	}

	/**
	 * 快速创建：自动生成名称，使用默认参数
	 */
	private void quickCreateFromSelection() {
		// 自动生成名称 selection_1, selection_2, ...
		String autoName = generateAutoObjectName();

		// 使用默认参数
		ToolPanelPresenter.StageObjectCreateRequest request =
			new ToolPanelPresenter.StageObjectCreateRequest(
				autoName,
				false,  // 默认不包含空气
				com.beatblock.engine.GroupSortingStrategy.SEQUENTIAL,  // 默认顺序
				0.0     // 默认无延迟
			);

		var outcome = presenter.createFromSelectionSnapshot(request);
		applyStageObjectMessage(outcome.result());

		// 如果创建成功，显示提示
		if (outcome.result().ok()) {
			stageObjectMessage = "✓ 已创建对象: " + autoName +
				"\n现在可以在时间线中添加事件并选择此对象";
			stageObjectMessageTimeMs = System.currentTimeMillis();
		}
	}

	/**
	 * 用于快速创建的简化请求（使用当前输入但允许为空时自动命名）
	 */
	private ToolPanelPresenter.StageObjectCreateRequest buildQuickStageObjectRequest() {
		String name = stageObjectNameBuffer.get();
		if (name == null || name.isBlank()) {
			name = generateAutoObjectName();
		}
		return new ToolPanelPresenter.StageObjectCreateRequest(
			name,
			stageObjectIncludeAir.get(),
			ToolPanelPresenter.sortingStrategyAtIndex(stageObjectSortingIndex.get()),
			ToolPanelPresenter.parseStaggerSeconds(stageObjectStaggerBuffer.get())
		);
	}

	/**
	 * 自动生成对象名称 selection_1, selection_2, ...
	 */
	private String generateAutoObjectName() {
		var existingObjects = presenter.listStageObjects();
		int counter = 1;
		while (true) {
			String candidate = "selection_" + counter;
			boolean exists = existingObjects.stream()
				.anyMatch(obj -> obj.id().equals(candidate));
			if (!exists) {
				return candidate;
			}
			counter++;
		}
	}

	private void applyStageObjectMessage(com.beatblock.ui.presenter.PresenterResult result) {
		if (result == null || result.messageOrEmpty().isBlank()) {
			return;
		}
		setStageObjectMessage(result.messageOrEmpty());
	}

	private void setStageObjectMessage(String msg) {
		stageObjectMessage = msg;
		stageObjectMessageTimeMs = System.currentTimeMillis();
	}
}
