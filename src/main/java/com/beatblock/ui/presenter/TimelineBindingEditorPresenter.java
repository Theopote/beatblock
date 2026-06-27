package com.beatblock.ui.presenter;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObject;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.binding.AnimationBindingRule;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.ui.i18n.BBTexts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Binding 规则编辑器业务逻辑：列表、模板、规则 CRUD、应用到轨道。
 */
public final class TimelineBindingEditorPresenter {

	public static final String SECTION_ALL = "ALL";
	private static final String[] TEMPLATE_LABEL_KEYS = {
		"beatblock.timeline.binding.template.rhythm_parkour",
		"beatblock.timeline.binding.template.architectural_show"
	};
	private static final String[] TEMPLATE_VALUES = {
		AnimationBindingEngine.TEMPLATE_RHYTHM_PARKOUR,
		AnimationBindingEngine.TEMPLATE_ARCHITECTURAL_SHOW
	};
	private static final String[] ACTION_LABEL_KEYS = {
		"beatblock.event.action.animate",
		"beatblock.event.action.place",
		"beatblock.event.action.clear",
		"beatblock.event.action.build"
	};
	private static final String[] ACTION_VALUES = { "ANIMATE", "PLACE", "CLEAR", "BUILD" };
	private static final String[] SPATIAL_LABEL_KEYS = {
		"beatblock.event.spatial.all",
		"beatblock.event.spatial.sequential",
		"beatblock.event.spatial.radial",
		"beatblock.event.spatial.random",
		"beatblock.event.spatial.spiral"
	};
	private static final String[] SPATIAL_VALUES = { "ALL", "SEQUENTIAL", "RADIAL", "RANDOM", "SPIRAL" };
	private static final String[] BUILD_MODE_LABEL_KEYS = {
		"beatblock.timeline.binding.build_mode.wall",
		"beatblock.timeline.binding.build_mode.bridge",
		"beatblock.timeline.binding.build_mode.tower",
		"beatblock.timeline.binding.build_mode.dissolve"
	};
	private static final String[] BUILD_MODE_VALUES = { "WALL", "BRIDGE", "TOWER", "DISSOLVE" };

	public static String[] templateLabels() {
		return BBTexts.labels(TEMPLATE_LABEL_KEYS);
	}

	public static String templateLabelAt(int index) {
		return BBTexts.get(TEMPLATE_LABEL_KEYS[index]);
	}

	public static String[] actionLabels() {
		return BBTexts.labels(ACTION_LABEL_KEYS);
	}

	public static String[] buildModeLabels() {
		return BBTexts.labels(BUILD_MODE_LABEL_KEYS);
	}

	public static String[] buildModeValues() {
		return BUILD_MODE_VALUES.clone();
	}

	public static String actionValueAt(int index) {
		return ACTION_VALUES[index];
	}

	public static int actionValueCount() {
		return ACTION_VALUES.length;
	}

	public static int indexOfActionValue(String target) {
		return indexOfValue(ACTION_VALUES, target);
	}

	public static String[] spatialLabels() {
		return BBTexts.labels(SPATIAL_LABEL_KEYS);
	}

	public static int spatialValueCount() {
		return SPATIAL_VALUES.length;
	}

	public static int indexOfSpatialValue(String target) {
		return indexOfValue(SPATIAL_VALUES, target);
	}

	public record EditorLists(
		List<String> featureKeys,
		List<String> targetDisplays,
		Map<String, String> targetDisplayToId,
		List<String> animationIds,
		List<String> sectionFilters
	) {}

	public record TemplateOutcome(String message, boolean success, List<AnimationBindingRule> rules) {}

	public record ApplyTrackOutcome(String message, boolean success, int count) {}

	public record BindingRuleEditRequest(
		boolean enabled,
		String name,
		String sourceFeatureKey,
		String animationTypeId,
		String actionValue,
		int spatialIndex,
		String targetObjectId,
		String sectionFilterSelection,
		float energyThreshold,
		float energyScale,
		float durationSeconds,
		float cooldownSeconds,
		float probability,
		float sequentialDelaySeconds,
		Map<String, Object> extraParams
	) {}

	private record TemplateMergeResult(List<AnimationBindingRule> merged, int added, int skipped) {}

	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<BlockAnimationEngine> animationEngine;

	public TimelineBindingEditorPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<BlockAnimationEngine> animationEngine
	) {
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.animationEngine = animationEngine;
	}

	public Timeline currentTimeline() {
		return timeline.get();
	}

	public List<AnimationBindingRule> loadRules(Timeline current) {
		if (current == null) {
			return List.of();
		}
		return new ArrayList<>(AnimationBindingEngine.loadRules(current));
	}

	public EditorLists loadEditorLists(Timeline current) {
		if (current == null) {
			return new EditorLists(List.of(), List.of(), Map.of(), List.of("Pulse"), List.of(SECTION_ALL));
		}
		List<String> featureKeys = new ArrayList<>(current.getFeatureTracks().keySet());
		featureKeys.sort(String.CASE_INSENSITIVE_ORDER);
		Map<String, String> targetDisplayToId = new HashMap<>();
		List<String> targetDisplays = collectTargetDisplays(targetDisplayToId);
		return new EditorLists(
			featureKeys,
			targetDisplays,
			targetDisplayToId,
			collectAnimationIds(),
			collectSectionFilters(current)
		);
	}

	public List<AnimationBindingRule> createDefaultRules(Timeline current) {
		if (current == null) {
			return List.of();
		}
		List<AnimationBindingRule> rules = new ArrayList<>(AnimationBindingEngine.createDefaultRules(current));
		AnimationBindingEngine.saveRules(current, rules);
		return rules;
	}

	public List<AnimationBindingRule> tryAddRule(
		Timeline current,
		List<AnimationBindingRule> rules,
		EditorLists lists
	) {
		if (current == null || rules == null) {
			return rules;
		}
		AnimationBindingRule added = buildAddedRule(lists.featureKeys(), lists.targetDisplays(), lists.targetDisplayToId());
		if (added == null) {
			return rules;
		}
		List<AnimationBindingRule> updated = new ArrayList<>(rules);
		updated.add(added);
		AnimationBindingEngine.saveRules(current, updated);
		return updated;
	}

	public TemplateOutcome replaceWithTemplate(Timeline current, List<AnimationBindingRule> rules, int templateIndex) {
		if (current == null) {
			return new TemplateOutcome("Timeline unavailable", false, rules);
		}
		int idx = clampTemplateIndex(templateIndex);
		List<AnimationBindingRule> templated = new ArrayList<>(
			AnimationBindingEngine.createTemplateRules(current, TEMPLATE_VALUES[idx]));
		if (templated.isEmpty()) {
			return new TemplateOutcome("Template " + templateLabelAt(idx) + " produced no rules", false, rules);
		}
		AnimationBindingEngine.saveRules(current, templated);
		return new TemplateOutcome(
			"Template " + templateLabelAt(idx) + " replaced all rules: " + templated.size(),
			true,
			templated
		);
	}

	public TemplateOutcome appendTemplate(Timeline current, List<AnimationBindingRule> rules, int templateIndex) {
		if (current == null) {
			return new TemplateOutcome("Timeline unavailable", false, rules);
		}
		int idx = clampTemplateIndex(templateIndex);
		List<AnimationBindingRule> templated = new ArrayList<>(
			AnimationBindingEngine.createTemplateRules(current, TEMPLATE_VALUES[idx]));
		if (templated.isEmpty()) {
			return new TemplateOutcome("Template " + templateLabelAt(idx) + " produced no rules", false, rules);
		}
		TemplateMergeResult merge = mergeTemplateRules(rules, templated);
		AnimationBindingEngine.saveRules(current, merge.merged());
		return new TemplateOutcome(
			"Template " + templateLabelAt(idx) + " appended: +" + merge.added() + ", skipped " + merge.skipped(),
			merge.added() > 0,
			merge.merged()
		);
	}

	public List<AnimationBindingRule> saveRules(Timeline current, List<AnimationBindingRule> rules) {
		if (current == null || rules == null) {
			return rules;
		}
		AnimationBindingEngine.saveRules(current, rules);
		return rules;
	}

	public List<AnimationBindingRule> removeRule(List<AnimationBindingRule> rules, int index) {
		if (rules == null || index < 0 || index >= rules.size()) {
			return rules;
		}
		List<AnimationBindingRule> updated = new ArrayList<>(rules);
		updated.remove(index);
		return updated;
	}

	public ApplyTrackOutcome applyToBlockTrack() {
		return applyRulesToTrack(TimelineTrackMeta.ROW_ANIM_BLOCK, "Apply To Block Track");
	}

	public ApplyTrackOutcome applyToAutoTrack() {
		return applyRulesToTrack(TimelineTrackMeta.ROW_ANIM_AUTO, "Apply To Auto Track");
	}

	public static AnimationBindingRule buildUpdatedRule(AnimationBindingRule rule, BindingRuleEditRequest request) {
		if (rule == null || request == null) {
			return rule;
		}
		int spatialIndex = Math.max(0, Math.min(request.spatialIndex(), SPATIAL_VALUES.length - 1));
		String sectionFilter = SECTION_ALL.equalsIgnoreCase(request.sectionFilterSelection())
			? ""
			: request.sectionFilterSelection().toLowerCase(Locale.ROOT);
		String name = request.name() == null || request.name().isBlank() ? rule.name() : request.name().trim();
		return AnimationBindingRule.builder()
			.id(rule.id())
			.name(name)
			.enabled(request.enabled())
			.sourceFeatureKey(request.sourceFeatureKey())
			.animationTypeId(request.animationTypeId())
			.actionMode(TimelineAnimationActionMode.fromValue(request.actionValue()))
			.targetObjectId(request.targetObjectId())
			.energyThreshold(request.energyThreshold())
			.energyScale(request.energyScale())
			.durationSeconds(request.durationSeconds())
			.cooldownSeconds(request.cooldownSeconds())
			.probability(request.probability())
			.spatialMode(SpatialDispatchMode.fromValue(SPATIAL_VALUES[spatialIndex]))
			.sequentialDelaySeconds(request.sequentialDelaySeconds())
			.sectionFilter(sectionFilter)
			.extraParams(request.extraParams() != null ? request.extraParams() : Map.of())
			.build();
	}

	public static double extraParamAsDouble(Map<String, Object> params, String key, double fallback) {
		if (params == null || key == null || key.isBlank()) {
			return fallback;
		}
		Object raw = params.get(key);
		if (raw instanceof Number n) {
			return n.doubleValue();
		}
		if (raw == null) {
			return fallback;
		}
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}

	public static String[] toComboArray(List<String> values) {
		if (values == null || values.isEmpty()) {
			return new String[] { "" };
		}
		return new LinkedHashSet<>(values).toArray(new String[0]);
	}

	public static int indexOfValue(List<String> values, String target) {
		if (values == null || values.isEmpty() || target == null) {
			return 0;
		}
		for (int i = 0; i < values.size(); i++) {
			if (target.equalsIgnoreCase(values.get(i))) {
				return i;
			}
		}
		return 0;
	}

	public static int indexOfValue(String[] values, String target) {
		if (values == null || values.length == 0 || target == null) {
			return 0;
		}
		for (int i = 0; i < values.length; i++) {
			if (target.equalsIgnoreCase(values[i])) {
				return i;
			}
		}
		return 0;
	}

	public static int indexOfTargetDisplay(List<String> targetDisplays, Map<String, String> displayToId, String targetId) {
		if (targetDisplays == null || targetDisplays.isEmpty() || displayToId == null) {
			return 0;
		}
		if (targetId == null || targetId.isBlank()) {
			return 0;
		}
		for (int i = 0; i < targetDisplays.size(); i++) {
			String id = displayToId.get(targetDisplays.get(i));
			if (targetId.equals(id)) {
				return i;
			}
		}
		return 0;
	}

	public static int indexOfSectionFilter(List<String> filters, String ruleSectionFilter) {
		if (filters == null || filters.isEmpty()) {
			return 0;
		}
		if (ruleSectionFilter == null || ruleSectionFilter.isBlank()) {
			return 0;
		}
		String wanted = ruleSectionFilter.trim().toUpperCase(Locale.ROOT);
		for (int i = 0; i < filters.size(); i++) {
			if (wanted.equalsIgnoreCase(filters.get(i))) {
				return i;
			}
		}
		return 0;
	}

	public static int clampTemplateIndex(int templateIndex) {
		return Math.max(0, Math.min(templateIndex, TEMPLATE_VALUES.length - 1));
	}

	private ApplyTrackOutcome applyRulesToTrack(int trackRowIndex, String labelPrefix) {
		Timeline current = timeline.get();
		if (current == null) {
			return new ApplyTrackOutcome(labelPrefix + " skipped: timeline unavailable", false, -1);
		}
		int count = AnimationBindingEngine.applyRules(current, trackRowIndex, false);
		TimelineEditor editor = timelineEditor.get();
		if (editor != null) {
			editor.syncClockDuration();
		}
		return new ApplyTrackOutcome(labelPrefix + " generated " + count + " events", count > 0, count);
	}

	private AnimationBindingRule buildAddedRule(
		List<String> featureKeys,
		List<String> targetDisplays,
		Map<String, String> targetDisplayToId
	) {
		if (featureKeys == null || featureKeys.isEmpty() || targetDisplays == null || targetDisplays.isEmpty()) {
			return null;
		}
		String feature = featureKeys.getFirst();
		String targetDisplay = targetDisplays.getFirst();
		String targetId = targetDisplayToId.getOrDefault(targetDisplay, "");
		if (targetId.isBlank()) {
			return null;
		}
		return AnimationBindingRule.builder()
			.name("Bind " + feature)
			.sourceFeatureKey(feature)
			.animationTypeId("Pulse")
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.targetObjectId(targetId)
			.energyThreshold(0.2f)
			.energyScale(1.0f)
			.durationSeconds(0.4)
			.cooldownSeconds(0.08)
			.probability(1.0f)
			.spatialMode(SpatialDispatchMode.ALL)
			.sequentialDelaySeconds(0.0)
			.build();
	}

	private List<String> collectAnimationIds() {
		List<String> ids = new ArrayList<>();
		BlockAnimationEngine engine = animationEngine.get();
		if (engine != null) {
			List<AnimationDefinition> defs = new ArrayList<>(engine.getAnimationLibrary().getAll().values());
			defs.sort(Comparator.comparing(AnimationDefinition::getId, String.CASE_INSENSITIVE_ORDER));
			for (AnimationDefinition def : defs) {
				ids.add(def.getId());
			}
		}
		if (ids.isEmpty()) {
			ids.add("Pulse");
		}
		return ids;
	}

	private List<String> collectSectionFilters(Timeline timeline) {
		LinkedHashSet<String> filters = new LinkedHashSet<>();
		filters.add(SECTION_ALL);
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null || marker.getType() != MarkerType.SECTION) {
				continue;
			}
			String label = extractSectionFilterLabel(marker.getName());
			if (!label.isBlank()) {
				filters.add(label.toUpperCase(Locale.ROOT));
			}
		}
		return new ArrayList<>(filters);
	}

	private List<String> collectTargetDisplays(Map<String, String> outDisplayToId) {
		List<String> displays = new ArrayList<>();
		BlockAnimationEngine engine = animationEngine.get();
		if (engine == null || outDisplayToId == null) {
			return displays;
		}
		List<StageObject> objects = new ArrayList<>(engine.getStageObjectSystem().getAll());
		objects.sort(Comparator.comparing(StageObject::getName, String.CASE_INSENSITIVE_ORDER));
		for (StageObject object : objects) {
			String display = object.getName() + " [" + object.getId() + "]";
			if (outDisplayToId.containsKey(display)) {
				continue;
			}
			outDisplayToId.put(display, object.getId());
			displays.add(display);
		}
		return displays;
	}

	private static String extractSectionFilterLabel(String markerName) {
		if (markerName == null) {
			return "";
		}
		String text = markerName.trim();
		if (text.isBlank()) {
			return "";
		}
		String upper = text.toUpperCase(Locale.ROOT);
		if (upper.startsWith("SECTION ")) {
			text = text.substring("SECTION ".length()).trim();
		}
		return text;
	}

	private static TemplateMergeResult mergeTemplateRules(
		List<AnimationBindingRule> existing,
		List<AnimationBindingRule> incoming
	) {
		List<AnimationBindingRule> out = new ArrayList<>();
		if (existing != null) {
			out.addAll(existing);
		}
		if (incoming == null || incoming.isEmpty()) {
			return new TemplateMergeResult(out, 0, 0);
		}
		LinkedHashSet<String> fingerprints = new LinkedHashSet<>();
		for (AnimationBindingRule rule : out) {
			if (rule != null) {
				fingerprints.add(ruleFingerprint(rule));
			}
		}
		int added = 0;
		int skipped = 0;
		for (AnimationBindingRule rule : incoming) {
			if (rule == null) {
				continue;
			}
			String fp = ruleFingerprint(rule);
			if (fingerprints.contains(fp)) {
				skipped++;
				continue;
			}
			fingerprints.add(fp);
			out.add(rule);
			added++;
		}
		return new TemplateMergeResult(out, added, skipped);
	}

	private static String ruleFingerprint(AnimationBindingRule rule) {
		StringBuilder sb = new StringBuilder(256);
		sb.append(rule.sourceFeatureKey().toLowerCase(Locale.ROOT)).append('|');
		sb.append(rule.animationTypeId().toLowerCase(Locale.ROOT)).append('|');
		sb.append(rule.actionMode().name()).append('|');
		sb.append(rule.targetObjectId().toLowerCase(Locale.ROOT)).append('|');
		sb.append(rule.sectionFilter().toLowerCase(Locale.ROOT)).append('|');
		sb.append(rule.spatialMode().name()).append('|');
		sb.append(String.format(Locale.ROOT, "%.3f|%.3f|%.3f|%.3f|%.3f|%.3f",
			rule.energyThreshold(),
			rule.energyScale(),
			rule.durationSeconds(),
			rule.cooldownSeconds(),
			rule.probability(),
			rule.sequentialDelaySeconds()));
		if (!rule.extraParams().isEmpty()) {
			Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			for (Map.Entry<String, Object> entry : rule.extraParams().entrySet()) {
				if (entry.getKey() != null) {
					sorted.put(entry.getKey().toLowerCase(Locale.ROOT), String.valueOf(entry.getValue()));
				}
			}
			sb.append('|').append(sorted);
		}
		return sb.toString();
	}
}
