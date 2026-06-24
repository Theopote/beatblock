package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import com.beatblock.ui.presenter.TimelineTransportPresenter;
import imgui.ImGui;
import imgui.type.ImInt;

final class TimelineToolbarLoopSpeedControls {

	private static final String TOOLTIP_LOOP_IN = "将当前时间设为循环起点；也可 Alt+左键点击标尺";
	private static final String TOOLTIP_LOOP_OUT = "将当前时间设为循环终点；也可 Alt+右键点击标尺";
	private static final String TOOLTIP_LOOP_CLEAR = "清除循环区间（保留 Loop 开关）";
	private static final String TOOLTIP_SPEED = "播放速度";

	private final TimelineTransportPresenter transport;
	private final ImInt speedComboIndex;
	private final TimelineToolbarActionRollbackControls actionRollback;

	TimelineToolbarLoopSpeedControls(
		TimelineTransportPresenter transport,
		ImInt speedComboIndex,
		TimelineToolbarActionRollbackControls actionRollback
	) {
		this.transport = transport;
		this.speedComboIndex = speedComboIndex;
		this.actionRollback = actionRollback;
	}

	void renderInline(
		TimelineEditor editor,
		TimelineToolbarState toolbarState,
		double seekStep,
		double now
	) {
		renderLoopButtons(toolbarState, now, seekStep, "", "", "");
		TimelineToolbarImGui.nextItemInGroup();
		renderSpeed(editor, "Speed", "");
		TimelineToolbarImGui.nextItemInGroup();
		actionRollback.renderInline();
	}

	void renderCompact(TimelineEditor editor, TimelineToolbarState toolbarState, double seekStep, double now) {
		ImGui.textDisabled("Loop & Speed");
		renderLoopButtons(toolbarState, now, seekStep, "##tlMoreIn", "##tlMoreOut", "##tlMoreClr");
		ImGui.sameLine();
		renderSpeed(editor, "Speed##tlMoreSpeed", TOOLTIP_SPEED);
		actionRollback.renderCompact();
	}

	private void renderLoopButtons(
		TimelineToolbarState toolbarState,
		double now,
		double seekStep,
		String inSuffix,
		String outSuffix,
		String clrSuffix
	) {
		if (ImGui.button("In" + inSuffix)) {
			transport.setLoopInAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_IN);
		if (inSuffix.isEmpty()) TimelineToolbarImGui.nextItemInGroup();
		else ImGui.sameLine();

		if (ImGui.button("Out" + outSuffix)) {
			transport.setLoopOutAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_OUT);
		if (inSuffix.isEmpty()) TimelineToolbarImGui.nextItemInGroup();
		else ImGui.sameLine();

		if (ImGui.button("Clr" + clrSuffix)) {
			transport.clearLoopRange(toolbarState);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_CLEAR);
	}

	private void renderSpeed(TimelineEditor editor, String label, String tooltipOverride) {
		speedComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestSpeed(transport.currentPlaybackSpeed(editor)));
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(TimelineToolbarViewPresenter.SPEED_LABELS));
		if (ImGui.combo(label, speedComboIndex, TimelineToolbarViewPresenter.SPEED_LABELS)) {
			TimelineToolbarViewPresenter.applySpeedPreset(editor, transport, speedComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? TOOLTIP_SPEED : tooltipOverride);
	}
}
