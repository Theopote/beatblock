package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.binding.AnimationBindingRule;
import com.beatblock.ui.presenter.TimelineBindingEditorPresenter;
import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binding 规则编辑器弹窗 ImGui 渲染。
 */
final class TimelineBindingEditorPopup {

	static final String POPUP_ID = "tlBindingEditor";

	private static final String TOOLTIP_BINDING_TEMPLATE =
		"规则模板：可覆盖（Replace）或合并（Append）到当前规则集";

	private final TimelineBindingEditorPresenter binding;
	private final TimelineToolbarFeedbackPresenter feedback;
	private final ImInt bindingTemplateComboIndex = new ImInt(0);

	TimelineBindingEditorPopup(
		TimelineBindingEditorPresenter binding,
		TimelineToolbarFeedbackPresenter feedback
	) {
		this.binding = binding;
		this.feedback = feedback;
	}

	void renderIfOpen() {
		if (!ImGui.beginPopup(POPUP_ID)) return;
		Timeline timeline = binding.currentTimeline();
		if (timeline == null) {
			ImGui.textDisabled("Timeline 未初始化");
			ImGui.endPopup();
			return;
		}

		List<AnimationBindingRule> rules = new ArrayList<>(binding.loadRules(timeline));
		var lists = binding.loadEditorLists(timeline);
		List<String> featureKeys = lists.featureKeys();
		List<String> targetDisplays = lists.targetDisplays();
		Map<String, String> targetDisplayToId = lists.targetDisplayToId();
		List<String> animationIds = lists.animationIds();
		List<String> sectionFilters = lists.sectionFilters();

		boolean dirty = renderHeader(timeline, rules, lists);
		dirty |= renderRuleList(timeline, rules, featureKeys, targetDisplays, targetDisplayToId, animationIds, sectionFilters);
		if (dirty) {
			binding.saveRules(timeline, rules);
		}
		renderApplyButtons();

		ImGui.endPopup();
	}

	private boolean renderHeader(
		Timeline timeline,
		List<AnimationBindingRule> rules,
		TimelineBindingEditorPresenter.EditorLists lists
	) {
		boolean dirty = false;
		ImGui.textDisabled("Binding Rules");
		ImGui.sameLine();
		ImGui.text("(" + rules.size() + ")");

		if (ImGui.button("Create Defaults##bindingCreateDefaults")) {
			rules.clear();
			rules.addAll(binding.createDefaultRules(timeline));
			dirty = true;
		}
		ImGui.sameLine();
		if (ImGui.button("Add Rule##bindingAddRule")) {
			List<AnimationBindingRule> updated = binding.tryAddRule(timeline, rules, lists);
			rules.clear();
			rules.addAll(updated);
			dirty = true;
		}

		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(TimelineBindingEditorPresenter.TEMPLATE_LABELS));
		ImGui.combo("Template##bindingTemplate", bindingTemplateComboIndex, TimelineBindingEditorPresenter.TEMPLATE_LABELS);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_TEMPLATE);
		ImGui.sameLine();
		if (ImGui.button("Replace##bindingApplyTemplate")) {
			var outcome = binding.replaceWithTemplate(timeline, rules, bindingTemplateComboIndex.get());
			rules.clear();
			rules.addAll(outcome.rules());
			feedback.setTemplateApplyFeedback(outcome.message(), outcome.success());
			dirty = true;
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip("覆盖当前规则集");
		ImGui.sameLine();
		if (ImGui.button("Append##bindingAppendTemplate")) {
			var outcome = binding.appendTemplate(timeline, rules, bindingTemplateComboIndex.get());
			rules.clear();
			rules.addAll(outcome.rules());
			feedback.setTemplateApplyFeedback(outcome.message(), outcome.success());
			dirty = true;
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip("保留现有规则并追加模板（自动去重）");
		TimelineToolbarImGui.renderFeedback(feedback.viewTemplateApplyFeedback());
		return dirty;
	}

	private boolean renderRuleList(
		Timeline timeline,
		List<AnimationBindingRule> rules,
		List<String> featureKeys,
		List<String> targetDisplays,
		Map<String, String> targetDisplayToId,
		List<String> animationIds,
		List<String> sectionFilters
	) {
		if (featureKeys.isEmpty()) {
			ImGui.textDisabled("当前没有可用特征轨，请先导入并分析音频。\n");
		}
		if (rules.isEmpty()) {
			ImGui.textDisabled("没有规则，可点击 Create Defaults 或 Add Rule。\n");
		}

		int removeIndex = -1;
		boolean changedAny = false;
		for (int i = 0; i < rules.size(); i++) {
			AnimationBindingRule rule = rules.get(i);
			String nodeLabel = rule.name() + "##bindingRuleNode_" + rule.id();
			if (!ImGui.treeNode(nodeLabel)) continue;

			ImGui.pushID("binding_rule_" + rule.id());
			RuleEditOutcome outcome = renderRuleEditor(
				rule, i, rules, featureKeys, targetDisplays, targetDisplayToId, animationIds, sectionFilters);
			if (outcome.changed()) changedAny = true;
			if (outcome.removeIndex() >= 0) removeIndex = outcome.removeIndex();

			ImGui.popID();
			ImGui.treePop();
		}

		if (removeIndex >= 0) {
			List<AnimationBindingRule> updated = binding.removeRule(rules, removeIndex);
			rules.clear();
			rules.addAll(updated);
			changedAny = true;
		}

		return changedAny;
	}

	private record RuleEditOutcome(boolean changed, int removeIndex) {
		static RuleEditOutcome unchanged() {
			return new RuleEditOutcome(false, -1);
		}
	}

	private RuleEditOutcome renderRuleEditor(
		AnimationBindingRule rule,
		int ruleIndex,
		List<AnimationBindingRule> rules,
		List<String> featureKeys,
		List<String> targetDisplays,
		Map<String, String> targetDisplayToId,
		List<String> animationIds,
		List<String> sectionFilters
	) {
		boolean changed = false;

		boolean[] enabled = new boolean[] { rule.enabled() };
		if (ImGui.checkbox("Enabled", enabled[0])) changed = true;

		ImString nameBuf = new ImString(rule.name(), 128);
		if (ImGui.inputText("Name", nameBuf)) changed = true;

		if (featureKeys.isEmpty()) {
			if (ImGui.button("Delete##bindingDelete_" + ruleIndex)) {
				return new RuleEditOutcome(false, ruleIndex);
			}
			return RuleEditOutcome.unchanged();
		}

		int featureIndex = TimelineBindingEditorPresenter.indexOfValue(featureKeys, rule.sourceFeatureKey());
		ImInt featureCombo = new ImInt(Math.max(0, featureIndex));
		if (ImGui.combo("Feature", featureCombo, TimelineBindingEditorPresenter.toComboArray(featureKeys))) changed = true;
		featureIndex = featureCombo.get();
		if (featureIndex < 0 || featureIndex >= featureKeys.size()) featureIndex = 0;

		int animationIndex = TimelineBindingEditorPresenter.indexOfValue(animationIds, rule.animationTypeId());
		ImInt animationCombo = new ImInt(Math.max(0, animationIndex));
		if (ImGui.combo("Animation", animationCombo, TimelineBindingEditorPresenter.toComboArray(animationIds))) changed = true;
		animationIndex = animationCombo.get();
		if (animationIndex < 0 || animationIndex >= animationIds.size()) animationIndex = 0;

		int actionIndex = TimelineBindingEditorPresenter.indexOfValue(
			TimelineBindingEditorPresenter.ACTION_VALUES, rule.actionMode().name());
		ImInt actionCombo = new ImInt(Math.max(0, actionIndex));
		if (ImGui.combo("Action", actionCombo, TimelineBindingEditorPresenter.ACTION_LABELS)) changed = true;
		actionIndex = actionCombo.get();
		if (actionIndex < 0 || actionIndex >= TimelineBindingEditorPresenter.ACTION_VALUES.length) actionIndex = 0;

		int spatialIndex = TimelineBindingEditorPresenter.indexOfValue(
			TimelineBindingEditorPresenter.SPATIAL_VALUES, rule.spatialMode().name());
		ImInt spatialCombo = new ImInt(Math.max(0, spatialIndex));
		if (ImGui.combo("Spatial", spatialCombo, TimelineBindingEditorPresenter.SPATIAL_LABELS)) changed = true;
		spatialIndex = spatialCombo.get();
		if (spatialIndex < 0 || spatialIndex >= TimelineBindingEditorPresenter.SPATIAL_VALUES.length) spatialIndex = 0;

		int targetIndex = TimelineBindingEditorPresenter.indexOfTargetDisplay(
			targetDisplays, targetDisplayToId, rule.targetObjectId());
		ImInt targetCombo = new ImInt(Math.max(0, targetIndex));
		if (!targetDisplays.isEmpty()
			&& ImGui.combo("Target", targetCombo, TimelineBindingEditorPresenter.toComboArray(targetDisplays))) {
			changed = true;
		}
		targetIndex = targetCombo.get();
		if (targetIndex < 0 || targetIndex >= targetDisplays.size()) targetIndex = 0;

		int sectionIndex = TimelineBindingEditorPresenter.indexOfSectionFilter(sectionFilters, rule.sectionFilter());
		ImInt sectionCombo = new ImInt(Math.max(0, sectionIndex));
		if (!sectionFilters.isEmpty()
			&& ImGui.combo("Section", sectionCombo, TimelineBindingEditorPresenter.toComboArray(sectionFilters))) {
			changed = true;
		}
		sectionIndex = sectionCombo.get();
		if (sectionIndex < 0 || sectionIndex >= sectionFilters.size()) sectionIndex = 0;

		float[] threshold = new float[] { rule.energyThreshold() };
		if (ImGui.sliderFloat("Threshold", threshold, 0f, 1f, "%.2f")) changed = true;

		float[] scale = new float[] { rule.energyScale() };
		if (ImGui.sliderFloat("Energy Scale", scale, 0f, 2f, "%.2f")) changed = true;

		float[] duration = new float[] { (float) rule.durationSeconds() };
		if (ImGui.sliderFloat("Duration", duration, 0.05f, 4f, "%.2f s")) changed = true;

		float[] cooldown = new float[] { (float) rule.cooldownSeconds() };
		if (ImGui.sliderFloat("Cooldown", cooldown, 0f, 1.5f, "%.2f s")) changed = true;

		float[] probability = new float[] { rule.probability() };
		if (ImGui.sliderFloat("Probability", probability, 0f, 1f, "%.2f")) changed = true;

		float[] seqDelay = new float[] { (float) rule.sequentialDelaySeconds() };
		if (ImGui.sliderFloat("Step Delay", seqDelay, 0f, 0.5f, "%.2f s")) changed = true;

		Map<String, Object> extraCopy = new HashMap<>(rule.extraParams());
		String uiAnimation = animationIds.isEmpty() ? rule.animationTypeId() : animationIds.get(animationIndex);
		changed |= renderAnimationExtraParams(uiAnimation, extraCopy);

		String uiAction = TimelineBindingEditorPresenter.ACTION_VALUES[actionIndex];
		if ("BUILD".equalsIgnoreCase(uiAction)) {
			changed |= renderBuildExtraParams(extraCopy);
		}

		if (changed) {
			String selectedFeature = featureKeys.get(featureIndex);
			String selectedAnimation = animationIds.isEmpty() ? rule.animationTypeId() : animationIds.get(animationIndex);
			String selectedTargetDisplay = targetDisplays.isEmpty() ? "" : targetDisplays.get(targetIndex);
			String selectedTargetId = targetDisplayToId.getOrDefault(selectedTargetDisplay, rule.targetObjectId());
			String selectedSection = sectionFilters.isEmpty()
				? TimelineBindingEditorPresenter.SECTION_ALL
				: sectionFilters.get(sectionIndex);
			rules.set(ruleIndex, TimelineBindingEditorPresenter.buildUpdatedRule(rule,
				new TimelineBindingEditorPresenter.BindingRuleEditRequest(
					enabled[0],
					nameBuf.get(),
					selectedFeature,
					selectedAnimation,
					TimelineBindingEditorPresenter.ACTION_VALUES[actionIndex],
					spatialIndex,
					selectedTargetId,
					selectedSection,
					threshold[0],
					scale[0],
					duration[0],
					cooldown[0],
					probability[0],
					seqDelay[0],
					extraCopy
				)));
		}

		if (ImGui.button("Delete##bindingDelete_" + ruleIndex)) {
			return new RuleEditOutcome(changed, ruleIndex);
		}
		return new RuleEditOutcome(changed, -1);
	}

	private static boolean renderAnimationExtraParams(String uiAnimation, Map<String, Object> extraCopy) {
		boolean changed = false;
		if ("WaveMotion".equalsIgnoreCase(uiAnimation)) {
			float[] waveAmp = new float[] {
				(float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "waveAmplitude", 0.5) };
			float[] wavePhase = new float[] {
				(float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "wavePhaseOffset", 0.5) };
			if (ImGui.sliderFloat("Wave Amp", waveAmp, 0f, 3f, "%.2f")) changed = true;
			if (ImGui.sliderFloat("Wave Phase", wavePhase, 0f, 3f, "%.2f")) changed = true;
			extraCopy.put("waveAmplitude", waveAmp[0]);
			extraCopy.put("wavePhaseOffset", wavePhase[0]);
		} else if ("BlockExplosion".equalsIgnoreCase(uiAnimation)) {
			float[] impactRadius = new float[] {
				(float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "impactRadius", 4.0) };
			float[] impactBurst = new float[] {
				(float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "impactBurst", 1.0) };
			if (ImGui.sliderFloat("Impact Radius", impactRadius, 1f, 16f, "%.1f")) changed = true;
			if (ImGui.sliderFloat("Impact Burst", impactBurst, 0f, 3f, "%.2f")) changed = true;
			extraCopy.put("impactRadius", impactRadius[0]);
			extraCopy.put("impactBurst", impactBurst[0]);
		} else if ("BlockDrop".equalsIgnoreCase(uiAnimation)) {
			float[] meteorHeight = new float[] {
				(float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "meteorHeight", 8.0) };
			float[] meteorScatter = new float[] {
				(float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "meteorScatter", 2.0) };
			if (ImGui.sliderFloat("Meteor Height", meteorHeight, 2f, 32f, "%.1f")) changed = true;
			if (ImGui.sliderFloat("Meteor Scatter", meteorScatter, 0f, 8f, "%.1f")) changed = true;
			extraCopy.put("meteorHeight", meteorHeight[0]);
			extraCopy.put("meteorScatter", meteorScatter[0]);
		}
		return changed;
	}

	private static boolean renderBuildExtraParams(Map<String, Object> extraCopy) {
		boolean changed = false;
		String[] buildModeLabels = { "WALL", "BRIDGE", "TOWER", "DISSOLVE" };
		int bmIdx = TimelineBindingEditorPresenter.indexOfValue(
			buildModeLabels, String.valueOf(extraCopy.getOrDefault("buildMode", "WALL")));
		ImInt bmCombo = new ImInt(Math.max(0, bmIdx));
		if (ImGui.combo("Build Mode", bmCombo, buildModeLabels)) changed = true;
		extraCopy.put("buildMode", buildModeLabels[Math.max(0, Math.min(bmCombo.get(), buildModeLabels.length - 1))]);

		ImString blockBuf = new ImString(128);
		blockBuf.set(String.valueOf(extraCopy.getOrDefault("placeBlock", "minecraft:diamond_block")));
		if (ImGui.inputText("Block ID##buildBlockId", blockBuf)) changed = true;
		extraCopy.put("placeBlock", blockBuf.get().trim());

		ImBoolean dissolveFlag = new ImBoolean(
			"true".equalsIgnoreCase(String.valueOf(extraCopy.getOrDefault("buildDissolve", "false"))));
		if (ImGui.checkbox("Dissolve (reverse)", dissolveFlag)) changed = true;
		extraCopy.put("buildDissolve", String.valueOf(dissolveFlag.get()));
		return changed;
	}

	private void renderApplyButtons() {
		ImGui.separator();
		if (ImGui.button("Apply To Block Track##bindingApplyBlock")) {
			var outcome = binding.applyToBlockTrack();
			feedback.setTemplateApplyFeedback(outcome.message(), outcome.success());
		}
		ImGui.sameLine();
		if (ImGui.button("Apply To Auto Track##bindingApplyAuto")) {
			var outcome = binding.applyToAutoTrack();
			feedback.setTemplateApplyFeedback(outcome.message(), outcome.success());
		}
	}
}
