package com.beatblock.timeline.rendering;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.TimelineToolbarConfigPresenter;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * Demucs 映射预设/片段模式控件及高级参数弹窗。
 */
final class TimelineDemucsMappingControls {

	static final String ADVANCED_POPUP_ID = "tlDemucsMappingAdvanced";

	private final TimelineToolbarConfigPresenter config;
	private final ImInt demucsPresetComboIndex = new ImInt(1);
	private final ImInt clipGenerationModeComboIndex = new ImInt(0);

	TimelineDemucsMappingControls(TimelineToolbarConfigPresenter config) {
		this.config = config;
	}

	void render(boolean compactMode, Runnable nextInlineItem) {
		if (!config.isDemucsSeparationActive()) return;
		config.ensureDemucsMappingConfigLoaded();

		demucsPresetComboIndex.set(TimelineToolbarConfigPresenter.indexOfDemucsPresetValue(config.readDemucsPreset()));
		clipGenerationModeComboIndex.set(
			TimelineToolbarConfigPresenter.indexOfClipGenerationMode(config.readClipGenerationMode()));

		if (compactMode) {
			renderCompactControls();
			return;
		}

		renderInlineControls(nextInlineItem);
	}

	void openAdvancedPopup() {
		ImGui.openPopup(ADVANCED_POPUP_ID);
	}

	private void renderCompactControls() {
		ImGui.separator();
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.demucs.title"));
		renderPresetCombo(BBTexts.get("beatblock.timeline.demucs.preset") + "##tlMoreDemucsPreset");
		renderClipModeCombo(BBTexts.get("beatblock.timeline.demucs.clip_mode") + "##tlMoreClipMode");
		if (ImGui.button(BBTexts.get("beatblock.timeline.demucs.advanced") + "##tlMoreDemucsAdvanced")) {
			openAdvancedPopup();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.demucs.advanced.tooltip"));
		renderAdvancedPopup();
	}

	private void renderInlineControls(Runnable nextInlineItem) {
		renderPresetCombo(BBTexts.get("beatblock.timeline.demucs.label"));
		if (nextInlineItem != null) nextInlineItem.run();
		renderClipModeCombo(BBTexts.get("beatblock.timeline.demucs.clip_mode"));
		if (nextInlineItem != null) nextInlineItem.run();
		if (ImGui.button(BBTexts.get("beatblock.timeline.demucs.map") + "##tlDemucsAdvanced")) {
			openAdvancedPopup();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.demucs.advanced.tooltip"));
		renderAdvancedPopup();
	}

	private void renderPresetCombo(String label) {
		String[] presetLabels = TimelineToolbarConfigPresenter.demucsPresetLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(presetLabels));
		if (ImGui.combo(label, demucsPresetComboIndex, presetLabels)) {
			config.writeDemucsPreset(TimelineToolbarConfigPresenter.demucsPresetValueAt(demucsPresetComboIndex.get()));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.demucs.preset.tooltip"));
	}

	private void renderClipModeCombo(String label) {
		String[] modeLabels = TimelineToolbarConfigPresenter.clipGenerationModeLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(modeLabels));
		if (ImGui.combo(label, clipGenerationModeComboIndex, modeLabels)) {
			config.writeClipGenerationMode(
				TimelineToolbarConfigPresenter.clipGenerationModeValueAt(clipGenerationModeComboIndex.get()));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.demucs.clip_mode.tooltip"));
	}

	private void renderAdvancedPopup() {
		if (!ImGui.beginPopup(ADVANCED_POPUP_ID)) return;
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.demucs.advanced_title"));

		var scales = config.readGlobalScales();
		float[] durationScale = new float[] { (float) scales.durationScale() };
		float[] energyScale = new float[] { (float) scales.energyScale() };
		float[] gapScale = new float[] { (float) scales.gapScale() };

		boolean changed = false;
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat(BBTexts.get("beatblock.timeline.demucs.duration_scale") + "##demucsDur", durationScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat(BBTexts.get("beatblock.timeline.demucs.energy_threshold") + "##demucsEnergy", energyScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MAX, "%.2f");
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat(BBTexts.get("beatblock.timeline.demucs.min_gap_scale") + "##demucsGap", gapScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");

		if (changed) {
			config.writeGlobalScales(durationScale[0], energyScale[0], gapScale[0]);
		}

		if (ImGui.button(BBTexts.get("beatblock.timeline.demucs.reset_scales") + "##demucsScaleReset")) {
			config.resetGlobalScalesToDefault();
		}

		ImGui.separator();
		if (ImGui.treeNode(BBTexts.get("beatblock.timeline.demucs.feature_overrides") + "##demucsFeatureOverrides")) {
			for (int i = 0; i < TimelineToolbarConfigPresenter.demucsFeatureCount(); i++) {
				String featureKey = TimelineToolbarConfigPresenter.demucsFeatureKeyAt(i);
				String label = TimelineToolbarConfigPresenter.demucsFeatureLabelAt(i);
				if (ImGui.treeNode(label + "##demucsFeatureNode_" + featureKey)) {
					boolean nodeChanged = false;
					float[] fDur = new float[] { (float) config.readFeatureScale(featureKey, "duration") };
					float[] fEnergy = new float[] { (float) config.readFeatureEnergyScale(featureKey) };
					float[] fGap = new float[] { (float) config.readFeatureScale(featureKey, "gap") };

					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat(BBTexts.get("beatblock.timeline.demucs.duration") + "##demucsFeatDur_" + featureKey, fDur,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");
					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat(BBTexts.get("beatblock.timeline.demucs.energy") + "##demucsFeatEnergy_" + featureKey, fEnergy,
						(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MAX, "%.2f");
					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat(BBTexts.get("beatblock.timeline.demucs.gap") + "##demucsFeatGap_" + featureKey, fGap,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");

					if (nodeChanged) {
						config.writeFeatureScales(featureKey, fDur[0], fEnergy[0], fGap[0]);
					}

					ImGui.treePop();
				}
			}

			if (ImGui.button(BBTexts.get("beatblock.timeline.demucs.reset_features") + "##demucsFeatReset")) {
				config.resetAllFeatureOverrides();
			}

			ImGui.treePop();
		}

		ImGui.endPopup();
	}
}
