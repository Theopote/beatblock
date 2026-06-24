package com.beatblock.ui.panels;

import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.client.BeatBlockWorldPick;
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
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/**
 * 左侧工具面板：层次为「场景选区 → 自动化编排 → 动画场景对象」。
 * Marker 管理与时间线动作调试已拆分至 {@link MarkerPanel}。
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
		ImGui.textWrapped("笔刷含球体/立方等形状：单击盖章或按住涂抹。框选/线选为两点；套索为拖画。线粗细等在「视图 → 选择属性」；换选择工具不会自动打开该面板。");
		ImGui.separator();
	}

	private void renderStageObjectCreator() {
		ImGui.spacing();
		ImGui.textDisabled("动画场景对象（StageObject）");
		ImGui.separator();
		ImGui.textWrapped(
				"时间线里的方块动画事件通过名称引用 StageObject。创建时需要一块「轴对齐长方体」内的方块："
						+ "请优先用上方「方块选择工具」做出选区（任意形状均可），再点下面按钮把该选区的外接包围盒填入；"
						+ "只有不打算用选区工具时，才展开「手动角点」用准星点两个角。");

		var selectionState = presenter.selectionToolViewState();
		int selCount = selectionState.selectionCount();
		if (selCount > 0) {
			ImGui.textDisabled(String.format(Locale.ROOT, "当前方块选区：%d 个方块（包围盒用于下方创建）", selCount));
		} else {
			ImGui.textDisabled("当前无方块选区：可先用框选/魔棒等建立选区，或展开「手动角点」。");
		}

		if (ImGui.button("用当前方块选区包围盒填入##stageFromSel", -1f, 0f)) {
			applyStageObjectMessage(presenter.fillCornersFromSelection().result());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("与上方选区工具联动：取 BeatBlock 选择管理器中已选方块的最小/最大角作为创建包围盒（与框选完成后的结果一致，无需再点「设置 A/B」）。");
		}

		boolean canCreateFromSelection = selCount > 0;
		if (!canCreateFromSelection) ImGui.beginDisabled();
		if (ImGui.button("从当前方块选区直接创建（精确快照）##stageCreateFromSelection", -1f, 0f)) {
			var outcome = presenter.createFromSelectionSnapshot(buildStageObjectRequest());
			applyStageObjectMessage(outcome.result());
		}
		if (!canCreateFromSelection) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("按当前选中的方块集合直接建组（不扩成包围盒），并记录为 selection_snapshot 来源。");
		}

		ToolPanelPresenter.CornerState corners = presenter.currentCorners();
		ImGui.textDisabled("创建包围盒角点");
		ImGui.textDisabled("  A: " + ToolPanelPresenter.formatPos(corners.posA()));
		ImGui.textDisabled("  B: " + ToolPanelPresenter.formatPos(corners.posB()));
		long selectionVolume = ToolPanelPresenter.estimateSelectionVolume(corners.posA(), corners.posB());
		if (selectionVolume > 0) {
			ImGui.textDisabled(String.format(Locale.ROOT, "  包围盒体积（估算）: %d 方块", selectionVolume));
		}

		ImGui.setNextItemOpen(false, ImGuiCond.Once);
		if (ImGui.collapsingHeader("手动角点（准星拾取，可选）##stageManualHdr")) {
			ImGui.textWrapped("与「方块选择」独立：在场景区用准星对准方块，分别指定长方体的两个对角。");
			if (ImGui.button("准星 → A##stageObjSetA")) {
				applyStageObjectMessage(presenter.setCornerFromCrosshair(true).result());
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("使用当前光标射线击中的方块坐标");
			}
			ImGui.sameLine();
			if (ImGui.button("准星 → B##stageObjSetB")) {
				applyStageObjectMessage(presenter.setCornerFromCrosshair(false).result());
			}
			if (ImGui.button("清空手动角点##stageObjClearSelection")) {
				applyStageObjectMessage(presenter.clearCorners().result());
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("仅清除此处角点，不影响上方「方块选择工具」的选区");
			}
			BlockPos lastLeft = BeatBlockWorldPick.getLastLeftClickedBlock();
			if (lastLeft != null) {
				ImGui.textDisabled("最近左键方块: " + ToolPanelPresenter.formatPos(lastLeft));
			}
		}

		ImGui.checkbox("包含空气方块##stageObjIncludeAir", stageObjectIncludeAir);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("关闭后仅采集非空气方块，推荐用于已有建筑对象");
		}

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("对象名称##stageObjName", stageObjectNameBuffer);

		ImGui.setNextItemWidth(-1f);
		ImGui.combo("组排序策略##stageGroupSorting", stageObjectSortingIndex, STAGE_GROUP_SORTING_LABELS);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("组默认步进延迟(秒)##stageGroupStagger", stageObjectStaggerBuffer);

		boolean canCreate = corners.posA() != null && corners.posB() != null;
		if (!canCreate) ImGui.beginDisabled();
		if (ImGui.button("从选区创建 StageObject##stageObjCreate", -1f, 0f)) {
			var outcome = presenter.createFromCuboid(buildStageObjectRequest());
			applyStageObjectMessage(outcome.result());
		}
		if (!canCreate) ImGui.endDisabled();

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
