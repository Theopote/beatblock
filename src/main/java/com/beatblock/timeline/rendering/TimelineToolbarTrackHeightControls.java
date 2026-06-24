package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import imgui.ImGui;

final class TimelineToolbarTrackHeightControls {

	private static final String TOOLTIP_TRACK_HEIGHT = "调整音频轨（波形/低中高频）高度，便于看清节奏细节";
	private static final String TOOLTIP_TRACK_HEIGHT_RESET = "恢复音频轨默认高度";
	private static final float SLIDER_WIDTH = 120f;
	private static final float SLIDER_WIDTH_COMPACT = 180f;

	void renderInline(TimelineEditor editor) {
		if (editor == null) return;
		var trackHeight = TimelineToolbarViewPresenter.trackHeightViewState(editor);
		float[] value = new float[] { trackHeight.current() };

		ImGui.setNextItemWidth(SLIDER_WIDTH);
		if (ImGui.sliderFloat("Track H", value, trackHeight.min(), trackHeight.max(), "%.0f px")) {
			TimelineToolbarViewPresenter.setTrackHeight(editor, value[0]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT);
		TimelineToolbarImGui.nextItemInGroup();
		if (ImGui.button("Reset##tlTrackHReset")) {
			TimelineToolbarViewPresenter.resetTrackHeight(editor);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT_RESET);
	}

	void renderCompact(TimelineEditor editor) {
		if (editor == null) return;
		var trackHeight = TimelineToolbarViewPresenter.trackHeightViewState(editor);
		float[] value = new float[] { trackHeight.current() };

		ImGui.separator();
		ImGui.textDisabled("Track Height");
		ImGui.setNextItemWidth(SLIDER_WIDTH_COMPACT);
		if (ImGui.sliderFloat("Track H##tlMoreTrackH", value, trackHeight.min(), trackHeight.max(), "%.0f px")) {
			TimelineToolbarViewPresenter.setTrackHeight(editor, value[0]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT);
		if (ImGui.button("Reset##tlMoreTrackHReset")) {
			TimelineToolbarViewPresenter.resetTrackHeight(editor);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT_RESET);
	}
}
