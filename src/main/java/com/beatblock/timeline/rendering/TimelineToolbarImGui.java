package com.beatblock.timeline.rendering;

import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
import imgui.ImGui;

final class TimelineToolbarImGui {

	private TimelineToolbarImGui() {}

	static float comboWidthForLabels(String[] labels) {
		float maxText = 0f;
		for (String label : labels) {
			if (label == null) continue;
			maxText = Math.max(maxText, ImGui.calcTextSize(label).x);
		}
		return maxText + 40f;
	}

	static void renderFeedback(TimelineToolbarFeedbackPresenter.FeedbackViewState state) {
		if (!state.visible()) return;
		if (state.success()) {
			ImGui.textColored(0.55f, 0.92f, 0.62f, state.alpha(), state.message());
		} else {
			ImGui.textColored(0.95f, 0.80f, 0.42f, state.alpha(), state.message());
		}
	}
}
