package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import imgui.ImGui;
import imgui.type.ImInt;

final class TimelineToolbarViewControls {

	private static final String TOOLTIP_ZOOM = "时间线横向缩放";
	private static final String TOOLTIP_FIT = "缩放至整段时长可见";

	private final ImInt zoomComboIndex;
	private final TimelineToolbarTrackHeightControls trackHeight;

	TimelineToolbarViewControls(ImInt zoomComboIndex, TimelineToolbarTrackHeightControls trackHeight) {
		this.zoomComboIndex = zoomComboIndex;
		this.trackHeight = trackHeight;
	}

	void renderInline(TimelineEditor editor) {
		if (editor == null) return;
		renderZoom(editor, "Zoom", "");
		TimelineToolbarImGui.nextItemInGroup();
		renderFit(editor, "Fit", "", 130f);
		TimelineToolbarImGui.nextItemInGroup();
		trackHeight.renderInline(editor);
	}

	void renderCompact(TimelineEditor editor) {
		if (editor == null) return;
		ImGui.separator();
		ImGui.textDisabled("View");
		renderZoom(editor, "Zoom##tlMoreZoom", TOOLTIP_ZOOM);
		renderFit(editor, "Fit##tlMoreFit", TOOLTIP_FIT, 16f);
		trackHeight.renderCompact(editor);
	}

	private void renderZoom(TimelineEditor editor, String label, String tooltipOverride) {
		zoomComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestZoom(editor.getViewState().getZoom()));
		String[] zoomLabels = TimelineToolbarViewPresenter.zoomPresetLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(zoomLabels));
		if (ImGui.combo(label, zoomComboIndex, zoomLabels)) {
			TimelineToolbarViewPresenter.applyZoomPreset(editor, zoomComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? TOOLTIP_ZOOM : tooltipOverride);
	}

	private void renderFit(TimelineEditor editor, String label, String tooltipOverride, float widthPadding) {
		if (ImGui.button(label)) {
			TimelineToolbarViewPresenter.fitToDuration(
				editor, editor.getTimeline(), ImGui.getContentRegionAvailX() - widthPadding);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? TOOLTIP_FIT : tooltipOverride);
	}
}
