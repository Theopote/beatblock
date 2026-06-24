package com.beatblock.timeline.rendering;

import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
import imgui.ImGui;

final class TimelineToolbarImGui {

	static final float ITEM_SPACING = 4f;
	static final float GROUP_SPACING = 8f;

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

	static void nextItemInGroup() {
		ImGui.sameLine(0f, ITEM_SPACING);
	}

	static void nextGroup() {
		ImGui.sameLine(0f, GROUP_SPACING);
	}

	static void nextGroupOrWrap(float estimatedNextGroupWidth) {
		float threshold = estimatedNextGroupWidth > 0 ? estimatedNextGroupWidth : 80f;
		ImGui.sameLine(0f, GROUP_SPACING);
		if (ImGui.getContentRegionAvailX() < threshold) {
			ImGui.newLine();
		}
	}

	static String hoveredTooltip(String current, String text) {
		if (current == null && ImGui.isItemHovered()) {
			return text;
		}
		return current;
	}
}
