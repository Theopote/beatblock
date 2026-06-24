package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.ui.presenter.TimelineToolbarActionsPresenter;
import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
import imgui.ImGui;

final class TimelineToolbarToolsControls {

	private static final String TOOLTIP_BINDING_MAP = "按绑定规则将音频特征批量转换为动画事件；无规则时自动创建默认规则";
	private static final String TOOLTIP_BINDING_EDITOR = "编辑特征绑定规则：来源特征、动作、目标对象、阈值和冷却";
	private static final String TOOLTIP_AUTO_MAP = "根据频段事件自动生成动画事件（需先导入音乐）";
	private static final String TOOLTIP_BAKE_STEP =
		"将 dispatchModel=STEP 的事件烘焙为 N 个带绝对时间的普通 BURST 事件（可 Undo）；需 StageObject 与参考节拍";

	private final TimelineToolbarActionsPresenter actions;
	private final TimelineToolbarFeedbackPresenter feedback;
	private final TimelineBindingEditorPopup bindingEditorPopup;

	TimelineToolbarToolsControls(
		TimelineToolbarActionsPresenter actions,
		TimelineToolbarFeedbackPresenter feedback,
		TimelineBindingEditorPopup bindingEditorPopup
	) {
		this.actions = actions;
		this.feedback = feedback;
		this.bindingEditorPopup = bindingEditorPopup;
	}

	void renderInline() {
		renderStageObjectWarning(true);
		renderBindingMap("Binding Map", "");
		TimelineToolbarImGui.nextItemInGroup();
		renderBindingEditorOpen("Bindings...##tlBindingEditorOpen");
		bindingEditorPopup.renderIfOpen();
		TimelineToolbarImGui.nextItemInGroup();
		renderAutoMap("Auto Map", "");
		TimelineToolbarImGui.nextItemInGroup();
		renderBakeStep("烘焙 STEP##tlBakeStep", "");
		TimelineToolbarImGui.nextItemInGroup();
		TimelineToolbarImGui.renderFeedback(feedback.viewToolActionFeedback());
	}

	void renderCompact() {
		ImGui.separator();
		ImGui.textDisabled("Tools");
		renderBindingMap("Binding Map##tlMoreBindingMap", TOOLTIP_BINDING_MAP);
		ImGui.sameLine();
		renderBindingEditorOpen("Bindings...##tlMoreBindingEditorOpen");
		bindingEditorPopup.renderIfOpen();
		renderAutoMap("Auto Map##tlMoreAutoMap", TOOLTIP_AUTO_MAP);
		renderBakeStep("烘焙 STEP##tlMoreBakeStep", TOOLTIP_BAKE_STEP);
		TimelineToolbarImGui.renderFeedback(feedback.viewToolActionFeedback());
	}

	private void renderStageObjectWarning(boolean inlineSpacing) {
		int objCount = BeatBlock.blockAnimationEngine != null
			? BeatBlock.blockAnimationEngine.getStageObjectSystem().size() : 0;
		if (objCount != 0) return;
		ImGui.textColored(0.95f, 0.65f, 0.30f, 1f, "无对象");
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("请先在工具面板中创建 StageObject（选区→创建），否则 Binding Map 无法生成事件");
		}
		if (inlineSpacing) TimelineToolbarImGui.nextItemInGroup();
	}

	private void renderBindingMap(String label, String tooltipOverride) {
		if (ImGui.button(label)) {
			var outcome = actions.runBindingMap();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? TOOLTIP_BINDING_MAP : tooltipOverride);
	}

	private void renderBindingEditorOpen(String label) {
		if (ImGui.button(label)) {
			ImGui.openPopup(TimelineBindingEditorPopup.POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_EDITOR);
	}

	private void renderAutoMap(String label, String tooltipOverride) {
		if (ImGui.button(label)) {
			var outcome = actions.runAutoMap();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? TOOLTIP_AUTO_MAP : tooltipOverride);
	}

	private void renderBakeStep(String label, String tooltipOverride) {
		if (ImGui.button(label)) {
			var outcome = actions.runBakeStepSequences();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? TOOLTIP_BAKE_STEP : tooltipOverride);
	}
}
