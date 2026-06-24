package com.beatblock.timeline.rendering;

import com.beatblock.ui.presenter.TimelineToolbarConfigPresenter;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * Demucs 映射预设/片段模式控件及高级参数弹窗。
 */
final class TimelineDemucsMappingControls {

	static final String ADVANCED_POPUP_ID = "tlDemucsMappingAdvanced";

	private static final String TOOLTIP_DEMUCS_PRESET =
		"Demucs 映射预设：Drive=更强律动，Detail=更细节，Balanced=平衡";
	private static final String TOOLTIP_CLIP_GENERATION_MODE =
		"控制轨片段生成策略：Trigger=逐点短片段，Sustain=持续分段，Mixed=按特征自动混合";
	private static final String TOOLTIP_DEMUCS_ADVANCED = "高级参数：时长/能量阈值/最小间隔";

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
		ImGui.textDisabled("Demucs Mapping");
		renderPresetCombo("Preset##tlMoreDemucsPreset");
		renderClipModeCombo("Clip Mode##tlMoreClipMode");
		if (ImGui.button("Advanced##tlMoreDemucsAdvanced")) {
			openAdvancedPopup();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_ADVANCED);
		renderAdvancedPopup();
	}

	private void renderInlineControls(Runnable nextInlineItem) {
		renderPresetCombo("Demucs");
		if (nextInlineItem != null) nextInlineItem.run();
		renderClipModeCombo("Clip Mode");
		if (nextInlineItem != null) nextInlineItem.run();
		if (ImGui.button("Map...##tlDemucsAdvanced")) {
			openAdvancedPopup();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_ADVANCED);
		renderAdvancedPopup();
	}

	private void renderPresetCombo(String label) {
		String[] presetLabels = TimelineToolbarConfigPresenter.demucsPresetLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(presetLabels));
		if (ImGui.combo(label, demucsPresetComboIndex, presetLabels)) {
			config.writeDemucsPreset(TimelineToolbarConfigPresenter.demucsPresetValueAt(demucsPresetComboIndex.get()));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_PRESET);
	}

	private void renderClipModeCombo(String label) {
		String[] modeLabels = TimelineToolbarConfigPresenter.clipGenerationModeLabels();
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(modeLabels));
		if (ImGui.combo(label, clipGenerationModeComboIndex, modeLabels)) {
			config.writeClipGenerationMode(
				TimelineToolbarConfigPresenter.clipGenerationModeValueAt(clipGenerationModeComboIndex.get()));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_CLIP_GENERATION_MODE);
	}

	private void renderAdvancedPopup() {
		if (!ImGui.beginPopup(ADVANCED_POPUP_ID)) return;
		ImGui.textDisabled("Demucs Mapping Advanced");

		var scales = config.readGlobalScales();
		float[] durationScale = new float[] { (float) scales.durationScale() };
		float[] energyScale = new float[] { (float) scales.energyScale() };
		float[] gapScale = new float[] { (float) scales.gapScale() };

		boolean changed = false;
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Duration Scale##demucsDur", durationScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Energy Threshold##demucsEnergy", energyScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MAX, "%.2f");
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Min Gap Scale##demucsGap", gapScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");

		if (changed) {
			config.writeGlobalScales(durationScale[0], energyScale[0], gapScale[0]);
		}

		if (ImGui.button("Reset to 1.0##demucsScaleReset")) {
			config.resetGlobalScalesToDefault();
		}

		ImGui.separator();
		if (ImGui.treeNode("Per-Feature Overrides##demucsFeatureOverrides")) {
			for (int i = 0; i < TimelineToolbarConfigPresenter.demucsFeatureCount(); i++) {
				String featureKey = TimelineToolbarConfigPresenter.demucsFeatureKeyAt(i);
				String label = TimelineToolbarConfigPresenter.demucsFeatureLabelAt(i);
				if (ImGui.treeNode(label + "##demucsFeatureNode_" + featureKey)) {
					boolean nodeChanged = false;
					float[] fDur = new float[] { (float) config.readFeatureScale(featureKey, "duration") };
					float[] fEnergy = new float[] { (float) config.readFeatureEnergyScale(featureKey) };
					float[] fGap = new float[] { (float) config.readFeatureScale(featureKey, "gap") };

					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Duration##demucsFeatDur_" + featureKey, fDur,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");
					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Energy##demucsFeatEnergy_" + featureKey, fEnergy,
						(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MAX, "%.2f");
					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Gap##demucsFeatGap_" + featureKey, fGap,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");

					if (nodeChanged) {
						config.writeFeatureScales(featureKey, fDur[0], fEnergy[0], fGap[0]);
					}

					ImGui.treePop();
				}
			}

			if (ImGui.button("Reset Feature Overrides##demucsFeatReset")) {
				config.resetAllFeatureOverrides();
			}

			ImGui.treePop();
		}

		ImGui.endPopup();
	}
}
