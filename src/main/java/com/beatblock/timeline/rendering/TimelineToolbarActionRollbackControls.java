package com.beatblock.timeline.rendering;

import com.beatblock.ui.presenter.TimelineToolbarConfigPresenter;
import imgui.ImGui;
import imgui.type.ImInt;

final class TimelineToolbarActionRollbackControls {

	private static final String TOOLTIP_ACTION_ROLLBACK =
		"PLACE/CLEAR 预览回滚策略：Preview 会在停止/回退时恢复方块；Persistent 会保留写入结果";

	private final TimelineToolbarConfigPresenter config;
	private final ImInt comboIndex;

	TimelineToolbarActionRollbackControls(TimelineToolbarConfigPresenter config, ImInt comboIndex) {
		this.config = config;
		this.comboIndex = comboIndex;
	}

	void renderInline() {
		render(false);
	}

	void renderCompact() {
		render(true);
	}

	private void render(boolean compactPopup) {
		config.ensureActionExecutionConfigLoaded();
		comboIndex.set(TimelineToolbarConfigPresenter.indexOfActionRollbackValue(config.readActionRollbackMode()));
		String label = compactPopup ? "Rollback##tlMoreActionRollback" : "Rollback";
		String[] rollbackLabels = TimelineToolbarConfigPresenter.actionRollbackLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(rollbackLabels));
		if (ImGui.combo(label, comboIndex, rollbackLabels)) {
			config.writeActionRollbackMode(TimelineToolbarConfigPresenter.actionRollbackValueAt(comboIndex.get()));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK);
	}
}
