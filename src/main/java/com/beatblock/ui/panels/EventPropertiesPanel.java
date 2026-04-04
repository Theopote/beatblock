package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.StageObject;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TrackDefinition;
import com.beatblock.timeline.rendering.TrackRegistry;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 右侧事件属性面板。
 */
public class EventPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	private final ImString timeBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString durationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString energyBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString energyThresholdBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString spatialDelayBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString blocksPerBeatBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraNearDistanceBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraFarDistanceBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraNearScaleBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraFarScaleBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraEdgePriorityBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString entryDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString idleDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString exitDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString placeBlockBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camSegDurBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camXBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camYBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camZBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camYawBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camPitchBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camEaseBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camClipStartBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString camClipEndBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final Map<String, ImString> camSegParamBuffers = new HashMap<>();
	private final ImBoolean camClipPathVisibleProxy = new ImBoolean(true);
	private final ImBoolean camSegPathVisibleProxy = new ImBoolean(true);
	private String validationError;
	private static final String[] SPATIAL_MODE_LABELS = {
		"同时 (ALL)",
		"顺序 (SEQUENTIAL)",
		"径向 (RADIAL)",
		"随机 (RANDOM)",
		"螺旋 (SPIRAL)"
	};
	private static final String[] SPATIAL_MODE_VALUES = {
		"ALL",
		"SEQUENTIAL",
		"RADIAL",
		"RANDOM",
		"SPIRAL"
	};
	private static final String[] STEP_START_MODE_LABELS = {
		"下一个节拍 (NEXT_BEAT)",
		"立即开始 (IMMEDIATE)"
	};
	private static final String[] STEP_START_MODE_VALUES = {
		"NEXT_BEAT",
		"IMMEDIATE"
	};
	private static final String[] STEP_COMPLETION_LABELS = {
		"保持结束态 (KEEP)",
		"循环序列 (LOOP)"
	};
	private static final String[] STEP_COMPLETION_VALUES = {
		"KEEP",
		"LOOP"
	};
	private static final String[] PHASE_LABELS = {
		"无 (None)",
		"进入 (Entry)",
		"保持 (Idle)",
		"退出 (Exit)"
	};
	private static final String[] PHASE_VALUES = {
		"NONE",
		"ENTRY",
		"IDLE",
		"EXIT"
	};

	/** event 可为 null，表示仅选中摄像机片段（无具体事件焦点）。 */
	private record EventRef(Track track, Clip clip, TimelineEvent event) {}
	private record Option(String id, String label) {}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.EVENT_PROPERTIES_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.EVENT_PROPERTIES_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text("事件属性");
			ImGui.separator();

			Timeline timeline = BeatBlock.timeline;
			TimelineEditor editor = BeatBlock.timelineEditor;
			if (timeline == null || editor == null) {
				ImGui.textDisabled("时间线未初始化。");
				return;
			}

			EventRef ref = resolvePropertiesRef(timeline, editor.getSelectionState());
			if (ref == null) {
				boundRefKey = null;
				validationError = null;
				ImGui.textWrapped("选中时间线上的事件或摄像机片段后，可在此编辑属性。");
				return;
			}

			String rk = refKey(ref);
			if (!rk.equals(boundRefKey)) {
				bindBuffers(ref);
			}

			renderEventSummary(ref, timeline);
			ImGui.separator();

			boolean trackLocked = isTrackLocked(timeline, editor, ref.track().getId());
			if (trackLocked) {
				ImGui.textDisabled("当前轨道已锁定，属性只读。");
				ImGui.separator();
				ImGui.beginDisabled();
			}

			if (ref.event() == null) {
				renderCameraClipOnlyPanel(ref, timeline);
			} else {
				EventType et = ref.event().getType();
				if (et == EventType.ANIMATION) {
					renderAnimationEditor(ref, timeline);
				} else if (et == EventType.CAMERA_SEGMENT) {
					renderCameraSegmentPanel(ref, timeline);
				} else if (et == EventType.CAMERA_KEYFRAME) {
					renderCameraKeyframePanel(ref, timeline, editor.getSelectionState());
				} else {
					ImGui.textDisabled("当前事件类型的侧栏编辑尚未接入。");
				}
			}

			if (trackLocked) {
				ImGui.endDisabled();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.EVENT_PROPERTIES_WINDOW);
		}
	}

	private void renderEventSummary(EventRef ref, Timeline timeline) {
		ImGui.textDisabled("Track");
		ImGui.sameLine();
		ImGui.text(ref.track().getName().isBlank() ? ref.track().getId() : ref.track().getName());
		if (ref.event() == null) {
			ImGui.textDisabled("片段 ID");
			ImGui.sameLine();
			ImGui.text(ref.clip().getId());
			ImGui.textDisabled("显示路径");
			ImGui.sameLine();
			ImGui.text(CameraPathMetadata.isPathVisible(timeline, ref.clip().getId()) ? "是" : "否");
			return;
		}
		Map<String, Object> params = ref.event().getParameters();
		ImGui.textDisabled("Event ID");
		ImGui.sameLine();
		ImGui.text(ref.event().getId());
		EventType et = ref.event().getType();
		if (et == EventType.ANIMATION) {
			if (Timeline.isBlockAnimationFeatureTrackId(ref.track().getId())) {
				ImGui.textDisabled("Feature Lane");
				ImGui.sameLine();
				ImGui.text(TrackRegistry.localizedName(Timeline.blockAnimationFeatureKeyFromTrackId(ref.track().getId())));
			}
			String sourceFeature = stringParam(params, "sourceFeature");
			if (!sourceFeature.isBlank()) {
				ImGui.textDisabled("Source Feature");
				ImGui.sameLine();
				ImGui.text(TrackRegistry.localizedName(sourceFeature));
			}
			String generatedBy = stringParam(params, "generatedBy");
			if (!generatedBy.isBlank()) {
				ImGui.textDisabled("Generated By");
				ImGui.sameLine();
				ImGui.text(generatedBy);
			}
			ImGui.textDisabled("Action Mode");
			ImGui.sameLine();
			Object actionMode = params.get("actionMode") != null ? params.get("actionMode") : params.get("mode");
			ImGui.text(TimelineAnimationActionMode.fromValue(actionMode).name());
		}
		if (et == EventType.CAMERA_SEGMENT || et == EventType.CAMERA_KEYFRAME) {
			ImGui.textDisabled("事件类型");
			ImGui.sameLine();
			ImGui.text(et.name());
		}
		if (et == EventType.CAMERA_SEGMENT) {
			ImGui.textDisabled("镜头类型");
			ImGui.sameLine();
			ImGui.text(CameraSegmentKind.fromParam(params.get("kind")).name());
		}
	}

	private void renderAnimationEditor(EventRef ref, Timeline timeline) {
		Map<String, Object> params = ref.event().getParameters();
		List<Option> actionOptions = collectActionOptions();
		List<Option> animationOptions = collectAnimationOptions();
		List<Option> targetOptions = collectTargetOptions();

		ImGui.text("Timing");
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("开始时间 (s)##eventTime", timeBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("持续时间 (s)##eventDuration", durationBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("能量 (0-1)##eventEnergy", energyBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("能量阈值 (0-1)##eventEnergyThreshold", energyThresholdBuffer);

		String currentAnimationId = stringParam(params, "animationType");
		String currentTargetId = stringParam(params, "targetObject");
		String currentActionMode = stringParam(params, "actionMode", stringParam(params, "mode", TimelineAnimationActionMode.ANIMATE.name()));
		boolean inheritGroupSpatial = booleanParam(params, "inheritGroupSpatial", true);
		boolean stepDispatch = "STEP".equalsIgnoreCase(stringParam(params, "dispatchModel", "BURST"));
		boolean cameraAdaptiveStep = booleanParam(params, "cameraAdaptiveStep", false);
		boolean cameraFrustumGating = booleanParam(params, "cameraFrustumGating", false);
		boolean usePhaseAnimation = booleanParam(params, "usePhaseAnimation", false);
		ImInt stepStartModeIndex = new ImInt(indexOfValue(STEP_START_MODE_VALUES, stringParam(params, "stepStartMode", "NEXT_BEAT")));
		ImInt stepCompletionIndex = new ImInt(indexOfValue(STEP_COMPLETION_VALUES, stringParam(params, "stepCompletionMode", "KEEP")));
		ImInt actionIndex = new ImInt(indexOfOption(actionOptions, currentActionMode));
		ImInt animationIndex = new ImInt(indexOfOption(animationOptions, currentAnimationId));
		ImInt targetIndex = new ImInt(indexOfOption(targetOptions, currentTargetId));
		ImInt spatialModeIndex = new ImInt(indexOfValue(SPATIAL_MODE_VALUES, stringParam(params, "spatialMode", "ALL")));
		if (spatialModeIndex.get() < 0 || spatialModeIndex.get() >= SPATIAL_MODE_VALUES.length) spatialModeIndex.set(0);
		String[] actionLabels = optionLabels(actionOptions);
		String[] animationLabels = optionLabels(animationOptions);
		String[] targetLabels = optionLabels(targetOptions);

		ImGui.spacing();
		ImGui.text("Binding");
		if (ImGui.combo("动作模式##eventActionMode", actionIndex, actionLabels)) {
			validationError = null;
		}
		if (ImGui.combo("动画模板##eventAnimation", animationIndex, animationLabels)) {
			validationError = null;
		}
		if (ImGui.combo("目标对象##eventTarget", targetIndex, targetLabels)) {
			validationError = null;
		}
		ImBoolean stepDispatchProxy = new ImBoolean(stepDispatch);
		if (ImGui.checkbox("每拍推进序列 (STEP)##eventDispatchStep", stepDispatchProxy)) {
			stepDispatch = stepDispatchProxy.get();
			validationError = null;
		}
		if (stepDispatch) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("每拍方块数##eventBlocksPerBeat", blocksPerBeatBuffer);
			if (ImGui.combo("起始对齐##eventStepStartMode", stepStartModeIndex, STEP_START_MODE_LABELS)) {
				validationError = null;
			}
			if (ImGui.combo("完成后行为##eventStepCompletionMode", stepCompletionIndex, STEP_COMPLETION_LABELS)) {
				validationError = null;
			}
			ImBoolean cameraAdaptiveProxy = new ImBoolean(cameraAdaptiveStep);
			if (ImGui.checkbox("镜头距离自适应推进##eventCameraAdaptiveStep", cameraAdaptiveProxy)) {
				cameraAdaptiveStep = cameraAdaptiveProxy.get();
				validationError = null;
			}
			if (cameraAdaptiveStep) {
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("近距离阈值##eventCameraNearDistance", cameraNearDistanceBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("远距离阈值##eventCameraFarDistance", cameraFarDistanceBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("近景倍率##eventCameraNearScale", cameraNearScaleBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("远景倍率##eventCameraFarScale", cameraFarScaleBuffer);
			}
			ImBoolean frustumGatingProxy = new ImBoolean(cameraFrustumGating);
			if (ImGui.checkbox("镜头视椎体门控 (暂停出屏)##eventCameraFrustumGating", frustumGatingProxy)) {
				cameraFrustumGating = frustumGatingProxy.get();
				validationError = null;
			}
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("边界优先级 (0-1)##eventCameraEdgePriority", cameraEdgePriorityBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("0 = 无边界优先 | 1 = 优先边界方块");
			}
			ImBoolean phaseAnimationProxy = new ImBoolean(usePhaseAnimation);
			if (ImGui.checkbox("三段式动画 (进-保-退)##eventUsePhaseAnimation", phaseAnimationProxy)) {
				usePhaseAnimation = phaseAnimationProxy.get();
				validationError = null;
			}
			if (usePhaseAnimation) {
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("进入阶段 (%)##eventEntryDuration", entryDurationBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("保持阶段 (%)##eventIdleDuration", idleDurationBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("退出阶段 (%)##eventExitDuration", exitDurationBuffer);
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip("百分比应相加为100% (例如: 20% 入场, 60% 保持, 20% 出场)");
				}
			}
		}
		ImBoolean inheritSpatialProxy = new ImBoolean(inheritGroupSpatial);
		if (ImGui.checkbox("继承组排序/延迟##eventInheritGroupSpatial", inheritSpatialProxy)) {
			inheritGroupSpatial = inheritSpatialProxy.get();
			validationError = null;
		}
		if (!inheritGroupSpatial) {
			if (ImGui.combo("空间调度##eventSpatialMode", spatialModeIndex, SPATIAL_MODE_LABELS)) {
				validationError = null;
			}
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("步进延迟 (s)##eventSpatialDelay", spatialDelayBuffer);
		}
		TimelineAnimationActionMode selectedActionMode = TimelineAnimationActionMode.fromValue(actionOptions.get(actionIndex.get()).id());
		if (selectedActionMode == TimelineAnimationActionMode.PLACE) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("放置方块ID##eventPlaceBlock", placeBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("例如: minecraft:diamond_block");
			}
		}

		ImGui.spacing();
		ImGui.text("Metadata");
		ImGui.textDisabled(String.format(Locale.ROOT, "Mapping: %s", stringParam(params, "mappingProfile", "manual")));
		ImGui.textDisabled(String.format(Locale.ROOT, "Source Stem: %s", stringParam(params, "sourceStem", "-")));
		renderRuntimeStatus(ref);

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}

		ImGui.spacing();
		boolean applied = ImGui.button("应用##eventPropertiesApply", 120f, 0f);
		ImGui.sameLine();
		boolean reset = ImGui.button("重置##eventPropertiesReset", 120f, 0f);

		if (applied) {
			applyAnimationChanges(ref, timeline,
				actionOptions.get(actionIndex.get()).id(),
				animationOptions.get(animationIndex.get()).id(),
				targetOptions.get(targetIndex.get()).id(),
				inheritGroupSpatial,
				SPATIAL_MODE_VALUES[Math.max(0, Math.min(spatialModeIndex.get(), SPATIAL_MODE_VALUES.length - 1))],
				stepDispatch,
				STEP_START_MODE_VALUES[Math.max(0, Math.min(stepStartModeIndex.get(), STEP_START_MODE_VALUES.length - 1))],
				STEP_COMPLETION_VALUES[Math.max(0, Math.min(stepCompletionIndex.get(), STEP_COMPLETION_VALUES.length - 1))],
				cameraAdaptiveStep,
				cameraFrustumGating,
				numericParam(params, "cameraEdgePriority", 0.0),
				usePhaseAnimation);
		}
		if (reset) {
			bindBuffers(ref);
		}
	}

	private void renderRuntimeStatus(EventRef ref) {
		String eventId = ref != null && ref.event() != null ? ref.event().getId() : "";
		if (eventId == null || eventId.isBlank()) return;
		BeatBlockClientDriver.TimelineActionExecutionReport report = BeatBlockClientDriver.getTimelineActionExecutionReport(eventId);
		if (report == null) return;

		long ageMs = Math.max(0L, System.currentTimeMillis() - report.timestampMs());
		ImGui.textDisabled(String.format(Locale.ROOT,
			"Runtime: %s | %s | mutations=%d | %dms ago",
			report.actionMode().name(),
			report.status(),
			report.mutationCount(),
			ageMs));
		if (report.detail() != null && !report.detail().isBlank()) {
			ImGui.textDisabled("detail: " + report.detail());
		}
	}

	private void applyAnimationChanges(EventRef ref, Timeline timeline, String actionMode, String animationId,
	                                  String targetObjectId, boolean inheritGroupSpatial, String spatialMode,
	                                  boolean stepDispatch, String stepStartMode, String stepCompletionMode,
	                                  boolean cameraAdaptiveStep, boolean cameraFrustumGating, double cameraEdgePriority,
	                                  boolean usePhaseAnimation) {
		try {
			double newTime = Math.max(0.0, Double.parseDouble(valueOf(timeBuffer).trim()));
			double newDuration = Math.max(0.01, Double.parseDouble(valueOf(durationBuffer).trim()));
			float newEnergy = (float) Math.max(0.0, Math.min(1.0, Double.parseDouble(valueOf(energyBuffer).trim())));
			float newEnergyThreshold = (float) Math.max(0.0, Math.min(1.0, Double.parseDouble(valueOf(energyThresholdBuffer).trim())));
			double spatialDelay = 0.0;
			if (!inheritGroupSpatial) {
				String rawDelay = valueOf(spatialDelayBuffer).trim();
				if (!rawDelay.isEmpty()) spatialDelay = Math.max(0.0, Double.parseDouble(rawDelay));
			}
			int blocksPerBeat = 1;
			if (stepDispatch) {
				String raw = valueOf(blocksPerBeatBuffer).trim();
				if (!raw.isEmpty()) {
					blocksPerBeat = Math.max(1, (int) Math.round(Double.parseDouble(raw)));
				}
			}
			double nearDistance = 8.0;
			double farDistance = 48.0;
			double nearScale = 0.6;
			double farScale = 1.5;
			if (stepDispatch && cameraAdaptiveStep) {
				String nearDistRaw = valueOf(cameraNearDistanceBuffer).trim();
				String farDistRaw = valueOf(cameraFarDistanceBuffer).trim();
				String nearScaleRaw = valueOf(cameraNearScaleBuffer).trim();
				String farScaleRaw = valueOf(cameraFarScaleBuffer).trim();
				if (!nearDistRaw.isEmpty()) nearDistance = Math.max(0.5, Double.parseDouble(nearDistRaw));
				if (!farDistRaw.isEmpty()) farDistance = Math.max(nearDistance + 0.001, Double.parseDouble(farDistRaw));
				if (!nearScaleRaw.isEmpty()) nearScale = Math.max(0.1, Double.parseDouble(nearScaleRaw));
				if (!farScaleRaw.isEmpty()) farScale = Math.max(0.1, Double.parseDouble(farScaleRaw));
			}
			double entryPercent = 20.0;
			double idlePercent = 60.0;
			double exitPercent = 20.0;
			if (stepDispatch && usePhaseAnimation) {
				String entryRaw = valueOf(entryDurationBuffer).trim();
				String idleRaw = valueOf(idleDurationBuffer).trim();
				String exitRaw = valueOf(exitDurationBuffer).trim();
				if (!entryRaw.isEmpty()) entryPercent = Math.max(0.0, Math.min(100.0, Double.parseDouble(entryRaw)));
				if (!idleRaw.isEmpty()) idlePercent = Math.max(0.0, Math.min(100.0, Double.parseDouble(idleRaw)));
				if (!exitRaw.isEmpty()) exitPercent = Math.max(0.0, Math.min(100.0, Double.parseDouble(exitRaw)));
				// Renormalize if they don't add to 100
				double total = entryPercent + idlePercent + exitPercent;
				if (total > 0.1) {
					entryPercent = (entryPercent / total) * 100.0;
					idlePercent = (idlePercent / total) * 100.0;
					exitPercent = (exitPercent / total) * 100.0;
				}
			}
			TimelineAnimationActionMode mode = TimelineAnimationActionMode.fromValue(actionMode);
			if (targetObjectId == null || targetObjectId.isBlank()) {
				validationError = "请先选择目标对象。";
				return;
			}
			if (BeatBlock.blockAnimationEngine == null || BeatBlock.blockAnimationEngine.getStageObjectSystem().get(targetObjectId) == null) {
				validationError = "目标对象不存在，请重新选择。";
				return;
			}
			String placeBlockId = null;
			if (mode == TimelineAnimationActionMode.PLACE) {
				String blockId = valueOf(placeBlockBuffer).trim();
				if (blockId.isEmpty()) {
					blockId = "minecraft:diamond_block";
				}
				Identifier parsed = Identifier.tryParse(blockId);
				if (parsed == null || !Registries.BLOCK.containsId(parsed)) {
					validationError = "方块ID无效，示例: minecraft:diamond_block";
					return;
				}
				placeBlockId = parsed.toString();
			}

			ref.event().setTimeSeconds(newTime);
			ref.event().setParameter("actionMode", mode.name());
			ref.event().setParameter("mode", mode.name());
			ref.event().setParameter("durationSeconds", newDuration);
			ref.event().setParameter("energy", newEnergy);
			ref.event().setParameter("energyThreshold", newEnergyThreshold);
			ref.event().setParameter("animationType", animationId);
			ref.event().setParameter("targetObject", targetObjectId);
			ref.event().setParameter("dispatchModel", stepDispatch ? "STEP" : "BURST");
			if (stepDispatch) {
				ref.event().setParameter("blocksPerBeat", blocksPerBeat);
				ref.event().setParameter("stepStartMode", stepStartMode);
				ref.event().setParameter("stepCompletionMode", stepCompletionMode);
				ref.event().setParameter("cameraAdaptiveStep", cameraAdaptiveStep);
				ref.event().setParameter("cameraFrustumGating", cameraFrustumGating);
				ref.event().setParameter("cameraEdgePriority", Math.max(0.0, Math.min(1.0, cameraEdgePriority)));
				ref.event().setParameter("usePhaseAnimation", usePhaseAnimation);
				if (usePhaseAnimation) {
					ref.event().setParameter("entryDurationPercent", entryPercent);
					ref.event().setParameter("idleDurationPercent", idlePercent);
					ref.event().setParameter("exitDurationPercent", exitPercent);
				} else {
					ref.event().removeParameter("entryDurationPercent");
					ref.event().removeParameter("idleDurationPercent");
					ref.event().removeParameter("exitDurationPercent");
				}
				if (cameraAdaptiveStep) {
					ref.event().setParameter("cameraNearDistance", nearDistance);
					ref.event().setParameter("cameraFarDistance", farDistance);
					ref.event().setParameter("cameraNearScale", nearScale);
					ref.event().setParameter("cameraFarScale", farScale);
				} else {
					ref.event().removeParameter("cameraNearDistance");
					ref.event().removeParameter("cameraFarDistance");
					ref.event().removeParameter("cameraNearScale");
					ref.event().removeParameter("cameraFarScale");
				}
			} else {
				ref.event().removeParameter("blocksPerBeat");
				ref.event().removeParameter("stepStartMode");
				ref.event().removeParameter("stepCompletionMode");
				ref.event().removeParameter("cameraAdaptiveStep");
				ref.event().removeParameter("cameraFrustumGating");
				ref.event().removeParameter("cameraEdgePriority");
				ref.event().removeParameter("usePhaseAnimation");
				ref.event().removeParameter("entryDurationPercent");
				ref.event().removeParameter("idleDurationPercent");
				ref.event().removeParameter("exitDurationPercent");
				ref.event().removeParameter("cameraNearDistance");
				ref.event().removeParameter("cameraFarDistance");
				ref.event().removeParameter("cameraNearScale");
				ref.event().removeParameter("cameraFarScale");
			}
			ref.event().setParameter("inheritGroupSpatial", inheritGroupSpatial);
			if (inheritGroupSpatial) {
				ref.event().removeParameter("spatialMode");
				ref.event().removeParameter("sequentialDelaySeconds");
			} else {
				SpatialDispatchMode modeValue = SpatialDispatchMode.fromValue(spatialMode);
				ref.event().setParameter("spatialMode", modeValue.name());
				ref.event().setParameter("sequentialDelaySeconds", spatialDelay);
			}
			if (mode == TimelineAnimationActionMode.PLACE) {
				ref.event().setParameter("placeBlock", placeBlockId);
			} else {
				ref.event().removeParameter("placeBlock");
				ref.event().removeParameter("placeBlockId");
			}
			ref.clip().setStartTimeSeconds(newTime);
			ref.clip().setEndTimeSeconds(newTime + newDuration);
			timeline.markAnimationEventsDirty(ref.track().getId());
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = "时间、持续时间或能量格式不正确。";
		}
	}

	private static String refKey(EventRef ref) {
		if (ref.event() == null) return "clip:" + ref.clip().getId();
		return "event:" + ref.event().getId();
	}

	private void bindBuffers(EventRef ref) {
		camSegParamBuffers.clear();
		boundRefKey = refKey(ref);
		if (ref.event() == null) {
			camClipStartBuffer.set(String.format(Locale.ROOT, "%.6f", ref.clip().getStartTimeSeconds()));
			camClipEndBuffer.set(String.format(Locale.ROOT, "%.6f", ref.clip().getEndTimeSeconds()));
			camClipPathVisibleProxy.set(CameraPathMetadata.isPathVisible(BeatBlock.timeline, ref.clip().getId()));
			validationError = null;
			return;
		}
		TimelineEvent event = ref.event();
		Map<String, Object> params = event.getParameters();
		timeBuffer.set(String.format(Locale.ROOT, "%.6f", event.getTimeSeconds()));
		durationBuffer.set(String.format(Locale.ROOT, "%.6f", numericParam(params, "durationSeconds", 0.25)));
		energyBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "energy", 1.0)));
		energyThresholdBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "energyThreshold", 0.15)));
		spatialDelayBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "sequentialDelaySeconds", 0.0)));
		blocksPerBeatBuffer.set(String.format(Locale.ROOT, "%d", Math.max(1, (int) Math.round(numericParam(params, "blocksPerBeat", 1.0)))));
		cameraNearDistanceBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "cameraNearDistance", 8.0)));
		cameraFarDistanceBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "cameraFarDistance", 48.0)));
		cameraNearScaleBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "cameraNearScale", 0.6)));
		cameraFarScaleBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "cameraFarScale", 1.5)));
		cameraEdgePriorityBuffer.set(String.format(Locale.ROOT, "%.2f", numericParam(params, "cameraEdgePriority", 0.0)));
		String placeBlock = stringParam(params, "placeBlock", stringParam(params, "placeBlockId", "minecraft:diamond_block"));
		placeBlockBuffer.set(placeBlock);
		if (event.getType() == EventType.CAMERA_SEGMENT && ref.clip() != null) {
			camSegDurBuffer.set(String.format(Locale.ROOT, "%.6f", ref.clip().getDurationSeconds()));
			camSegPathVisibleProxy.set(CameraPathMetadata.isPathVisible(BeatBlock.timeline, ref.clip().getId()));
			for (Map.Entry<String, Object> e : params.entrySet()) {
				String k = e.getKey();
				if ("kind".equals(k)) continue;
				camSegParamBuffers.put(k, new ImString(String.valueOf(e.getValue()), INPUT_BUFFER_SIZE));
			}
		}
		if (event.getType() == EventType.CAMERA_KEYFRAME) {
			camXBuffer.set(String.format(Locale.ROOT, "%.6f", numericParam(params, "x", 0)));
			camYBuffer.set(String.format(Locale.ROOT, "%.6f", numericParam(params, "y", 0)));
			camZBuffer.set(String.format(Locale.ROOT, "%.6f", numericParam(params, "z", 0)));
			camYawBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "yawDeg", 0)));
			camPitchBuffer.set(String.format(Locale.ROOT, "%.3f", numericParam(params, "pitchDeg", 0)));
			camEaseBuffer.set(stringParam(params, "ease", "SMOOTH"));
		}
		validationError = null;
	}

	private void renderCameraClipOnlyPanel(EventRef ref, Timeline timeline) {
		ImGui.text("片段起止时间（秒）");
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("开始##camClipStart", camClipStartBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("结束##camClipEnd", camClipEndBuffer);
		ImGui.checkbox("显示路径##camClipPathVis", camClipPathVisibleProxy);
		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}
		ImGui.spacing();
		if (ImGui.button("应用##camClipApply", 120f, 0f)) {
			try {
				double newStart = Double.parseDouble(valueOf(camClipStartBuffer).trim());
				double newEnd = Double.parseDouble(valueOf(camClipEndBuffer).trim());
				if (newEnd <= newStart) {
					validationError = "结束时间须大于开始时间。";
				} else {
					double oldStart = ref.clip().getStartTimeSeconds();
					double delta = newStart - oldStart;
					ref.clip().setStartTimeSeconds(Math.max(0, newStart));
					ref.clip().setEndTimeSeconds(newEnd);
					for (TimelineEvent ev : ref.clip().getEvents()) {
						double nt = ev.getTimeSeconds() + delta;
						nt = Math.max(newStart, Math.min(newEnd, nt));
						ev.setTimeSeconds(nt);
					}
					CameraPathMetadata.setPathVisible(timeline, ref.clip().getId(), camClipPathVisibleProxy.get());
					timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), ref.clip().getEndTimeSeconds()));
					validationError = null;
					bindBuffers(ref);
				}
			} catch (NumberFormatException ex) {
				validationError = "时间格式不正确。";
			}
		}
		ImGui.sameLine();
		if (ImGui.button("重置##camClipReset", 120f, 0f)) {
			bindBuffers(ref);
		}
	}

	private static final String[] CAM_KIND_LABELS = { "路径 [PATH]", "推拉 [DOLLY]", "环绕 [ORBIT]", "升降 [CRANE]", "震动 [SHAKE]" };
	private static final CameraSegmentKind[] CAM_KINDS = CameraSegmentKind.values();

	private static int kindIndex(CameraSegmentKind kind) {
		for (int i = 0; i < CAM_KINDS.length; i++) {
			if (CAM_KINDS[i] == kind) return i;
		}
		return 0;
	}

	private void renderCameraSegmentPanel(EventRef ref, Timeline timeline) {
		CameraSegmentKind kind = CameraSegmentKind.fromParam(ref.event().getParameters().get("kind"));

		// 镜头类型下拉选择
		ImInt kindIdx = new ImInt(kindIndex(kind));
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo("镜头类型##camSegKind", kindIdx, CAM_KIND_LABELS)) {
			CameraSegmentKind newKind = CAM_KINDS[kindIdx.get()];
			if (newKind != kind) {
				// 清理旧类型参数，保留新类型共用的参数
				List<String> newKeys = paramKeysForKind(newKind);
				for (String existingKey : new ArrayList<>(ref.event().getParameters().keySet())) {
					if (!"kind".equals(existingKey) && !newKeys.contains(existingKey)) {
						ref.event().removeParameter(existingKey);
					}
				}
				ref.event().setParameter("kind", newKind.name());
				initSegmentDefaults(ref.event(), newKind);
				kind = newKind;
				bindBuffers(ref);
			}
		}

		ImGui.text("片段时长（秒）");
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("##camSegDurInp", camSegDurBuffer);
		ImGui.checkbox("显示路径##camSegPathVis", camSegPathVisibleProxy);

		// 按镜头类型分组显示对应参数
		ImGui.separator();
		switch (kind) {
			case PATH -> ImGui.textDisabled("路径模式：关键帧插值，参数在关键帧事件上编辑。");
			case DOLLY -> {
				ImGui.textDisabled("推拉参数");
				renderSegParam("起点 X", "startX");
				renderSegParam("起点 Y", "startY");
				renderSegParam("起点 Z", "startZ");
				renderSegParam("终点 X", "endX");
				renderSegParam("终点 Y", "endY");
				renderSegParam("终点 Z", "endZ");
				renderSegParam("基准 Yaw (°)", "baseYawDeg");
				renderSegParam("基准 Pitch (°)", "basePitchDeg");
			}
			case ORBIT -> {
				ImGui.textDisabled("环绕参数");
				renderSegParam("目标 X", "targetX");
				renderSegParam("目标 Y", "targetY");
				renderSegParam("目标 Z", "targetZ");
				renderSegParam("半径", "radius");
				renderSegParam("高度偏移", "height");
				renderSegParam("起始角度 (°)", "yawStartDeg");
				renderSegParam("终止角度 (°)", "yawEndDeg");
			}
			case CRANE -> {
				ImGui.textDisabled("升降参数");
				renderSegParam("起点 X", "startX");
				renderSegParam("起点 Y", "startY");
				renderSegParam("起点 Z", "startZ");
				renderSegParam("终点 X", "endX");
				renderSegParam("终点 Y", "endY");
				renderSegParam("终点 Z", "endZ");
				renderSegParam("Yaw (°)", "yawDeg");
				renderSegParam("Pitch (°)", "pitchDeg");
			}
			case SHAKE -> {
				ImGui.textDisabled("震动参数");
				renderSegParam("锚点 X", "anchorX");
				renderSegParam("锚点 Y", "anchorY");
				renderSegParam("锚点 Z", "anchorZ");
				renderSegParam("Yaw (°)", "yawDeg");
				renderSegParam("Pitch (°)", "pitchDeg");
				renderSegParam("距离", "distance");
				renderSegParam("振幅", "amplitude");
				renderSegParam("频率 (Hz)", "frequencyHz");
				renderSegParam("节拍同步", "beatSync");
				renderSegParam("每拍脉冲", "beatsPerPulse");
			}
		}

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}
		ImGui.spacing();

		if (kind != CameraSegmentKind.PATH) {
			if (ImGui.button("捕获当前视角##camSegCapture", 160f, 0f)) {
				captureCurrentViewToSegment(kind);
			}
			ImGui.sameLine();
		}
		if (ImGui.button("应用##camSegApply", 120f, 0f)) {
			applyCameraSegmentPanel(ref, timeline);
		}
		ImGui.sameLine();
		if (ImGui.button("重置##camSegReset", 120f, 0f)) {
			bindBuffers(ref);
		}
	}

	private void renderSegParam(String label, String key) {
		ImString buf = camSegParamBuffers.get(key);
		if (buf == null) {
			buf = new ImString("", INPUT_BUFFER_SIZE);
			camSegParamBuffers.put(key, buf);
		}
		ImGui.text(label);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("##camSegP_" + key, buf);
	}

	private void captureCurrentViewToSegment(CameraSegmentKind kind) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
			validationError = "无可用相机，无法捕获。";
			return;
		}
		net.minecraft.client.render.Camera camera = mc.gameRenderer.getCamera();
		net.minecraft.util.math.Vec3d eye = camera.getCameraPos();
		float yaw = camera.getYaw();
		float pitch = camera.getPitch();
		switch (kind) {
			case DOLLY -> {
				setSegBuf("startX", eye.x); setSegBuf("startY", eye.y); setSegBuf("startZ", eye.z);
				setSegBuf("baseYawDeg", yaw);
				setSegBuf("basePitchDeg", pitch);
			}
			case ORBIT -> {
				setSegBuf("targetX", eye.x); setSegBuf("targetY", eye.y); setSegBuf("targetZ", eye.z);
			}
			case CRANE -> {
				setSegBuf("startX", eye.x); setSegBuf("startY", eye.y); setSegBuf("startZ", eye.z);
				setSegBuf("yawDeg", yaw); setSegBuf("pitchDeg", pitch);
			}
			case SHAKE -> {
				setSegBuf("anchorX", eye.x); setSegBuf("anchorY", eye.y); setSegBuf("anchorZ", eye.z);
				setSegBuf("yawDeg", yaw); setSegBuf("pitchDeg", pitch);
			}
			default -> {}
		}
		validationError = null;
	}

	private void setSegBuf(String key, double value) {
        ImString buf = camSegParamBuffers.computeIfAbsent(key, k -> new ImString(INPUT_BUFFER_SIZE));
        buf.set(String.format(Locale.ROOT, "%.6f", value));
	}

	private void applyCameraSegmentPanel(EventRef ref, Timeline timeline) {
		try {
			double dur = Double.parseDouble(valueOf(camSegDurBuffer).trim());
			double t0 = ref.clip().getStartTimeSeconds();
			ref.clip().setEndTimeSeconds(t0 + Math.max(0.05, dur));
			CameraPathMetadata.setPathVisible(timeline, ref.clip().getId(), camSegPathVisibleProxy.get());
			// 仅写回当前镜头类型对应的参数，避免残留旧类型参数
			CameraSegmentKind currentKind = CameraSegmentKind.fromParam(ref.event().getParameters().get("kind"));
			List<String> validKeys = paramKeysForKind(currentKind);
			for (String key : validKeys) {
				ImString buf = camSegParamBuffers.get(key);
				if (buf == null) continue;
				String raw = valueOf(buf).trim();
				if (raw.isEmpty()) continue;
				try {
					ref.event().setParameter(key, Double.parseDouble(raw));
				} catch (NumberFormatException ex) {
					ref.event().setParameter(key, raw);
				}
			}
			timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), ref.clip().getEndTimeSeconds()));
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = "时长或参数格式不正确。";
		}
	}

	private void renderCameraKeyframePanel(EventRef ref, Timeline timeline, SelectionState selectionState) {
		// 所属片段上下文
		if (ref.clip() != null) {
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(ref.clip());
			CameraSegmentKind clipKind = seg != null
				? CameraSegmentKind.fromParam(seg.getParameters().get("kind"))
				: null;
			ImGui.textDisabled("所属片段");
			ImGui.sameLine();
			ImGui.text(ref.clip().getId());
			if (clipKind != null) {
				ImGui.textDisabled("片段类型");
				ImGui.sameLine();
				ImGui.text(clipKind.name());
			}
			ImGui.textDisabled(String.format(Locale.ROOT, "片段范围: %.3fs — %.3fs",
				ref.clip().getStartTimeSeconds(), ref.clip().getEndTimeSeconds()));
			if (clipKind != null && clipKind != CameraSegmentKind.PATH) {
				ImGui.spacing();
				ImGui.textColored(1f, 0.65f, 0.2f, 1f, "⚠ 当前片段类型非路径（PATH），关键帧的位姿参数不会被摄像机使用。");
			}
			ImGui.separator();
		}

		ImGui.text("时间与位姿");
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("时间 (s)##camKfTime", timeBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("X##camKfX", camXBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("Y##camKfY", camYBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("Z##camKfZ", camZBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("Yaw (°)##camKfYaw", camYawBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("Pitch (°)##camKfPitch", camPitchBuffer);
		ImGui.setNextItemWidth(-1f);
		String[] easeOptions = { "SMOOTH", "LINEAR" };
		int easeIdx = "LINEAR".equalsIgnoreCase(valueOf(camEaseBuffer).trim()) ? 1 : 0;
		ImInt easeInt = new ImInt(easeIdx);
		if (ImGui.combo("过渡方式##camKfEase", easeInt, easeOptions)) {
			camEaseBuffer.set(easeOptions[easeInt.get()]);
		}
		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}
		ImGui.spacing();
		if (ImGui.button("捕获当前视角##camKfCapture", 160f, 0f)) {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
				net.minecraft.client.render.Camera camera = mc.gameRenderer.getCamera();
				net.minecraft.util.math.Vec3d eye = camera.getCameraPos();
				camXBuffer.set(String.format(Locale.ROOT, "%.6f", eye.x));
				camYBuffer.set(String.format(Locale.ROOT, "%.6f", eye.y));
				camZBuffer.set(String.format(Locale.ROOT, "%.6f", eye.z));
				camYawBuffer.set(String.format(Locale.ROOT, "%.3f", camera.getYaw()));
				camPitchBuffer.set(String.format(Locale.ROOT, "%.3f", camera.getPitch()));
				validationError = null;
			} else {
				validationError = "无可用相机，无法捕获。";
			}
		}
		ImGui.sameLine();
		if (ImGui.button("应用##camKfApply", 120f, 0f)) {
			applyCameraKeyframe(ref, timeline);
		}
		ImGui.sameLine();
		if (ImGui.button("重置##camKfReset", 120f, 0f)) {
			bindBuffers(ref);
		}
		ImGui.spacing();
		if (ImGui.button("删除关键帧##camKfDelete", 160f, 0f)) {
			String id = ref.event().getId();
			if (CameraKeyframeActions.deleteKeyframeEvent(timeline, id) && selectionState != null) {
				selectionState.deselectEvent(id);
				boundRefKey = null;
			}
		}

		try {
			double x = Double.parseDouble(valueOf(camXBuffer).trim());
			double y = Double.parseDouble(valueOf(camYBuffer).trim());
			double z = Double.parseDouble(valueOf(camZBuffer).trim());
			double yaw = Double.parseDouble(valueOf(camYawBuffer).trim());
			double pitch = Double.parseDouble(valueOf(camPitchBuffer).trim());
			com.beatblock.client.camera.TimelineCameraController.getInstance().previewKeyframeDirect(
				new com.beatblock.client.camera.TimelineCameraEvaluator.CameraSample(
					new net.minecraft.util.math.Vec3d(x, y, z), (float) yaw, (float) pitch
				)
			);
		} catch (NumberFormatException ignored) {}
	}

	private void applyCameraKeyframe(EventRef ref, Timeline timeline) {
		try {
			double newTime = Math.max(0.0, Double.parseDouble(valueOf(timeBuffer).trim()));
			if (ref.clip() != null) {
				newTime = Math.max(ref.clip().getStartTimeSeconds(), Math.min(ref.clip().getEndTimeSeconds(), newTime));
			}
			double x = Double.parseDouble(valueOf(camXBuffer).trim());
			double y = Double.parseDouble(valueOf(camYBuffer).trim());
			double z = Double.parseDouble(valueOf(camZBuffer).trim());
			double yaw = Double.parseDouble(valueOf(camYawBuffer).trim());
			double pitch = Double.parseDouble(valueOf(camPitchBuffer).trim());
			String ease = valueOf(camEaseBuffer).trim();
			if (ease.isEmpty()) ease = "SMOOTH";
			ref.event().setTimeSeconds(newTime);
			ref.event().setParameter("x", x);
			ref.event().setParameter("y", y);
			ref.event().setParameter("z", z);
			ref.event().setParameter("yawDeg", yaw);
			ref.event().setParameter("pitchDeg", pitch);
			ref.event().setParameter("ease", ease);
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = "时间或坐标格式不正确。";
		}
	}

	private EventRef resolvePropertiesRef(Timeline timeline, SelectionState selectionState) {
		EventRef fromEvent = resolveSelectedEventRefFromEvents(timeline, selectionState);
		if (fromEvent != null) return fromEvent;
		if (timeline == null || selectionState == null || selectionState.getSelectedClips().isEmpty()) return null;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) return null;
		List<String> clipIds = new ArrayList<>(selectionState.getSelectedClips());
		clipIds.sort(String::compareTo);
		for (String clipId : clipIds) {
			if (clipId == null) continue;
			Clip c = cam.getClip(clipId);
			if (c == null) continue;
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(c);
			if (seg != null) return new EventRef(cam, c, seg);
			return new EventRef(cam, c, null);
		}
		return null;
	}

	private EventRef resolveSelectedEventRefFromEvents(Timeline timeline, SelectionState selectionState) {
		if (timeline == null || selectionState == null || selectionState.getSelectedEvents().isEmpty()) return null;
		List<String> selectedIds = new ArrayList<>(selectionState.getSelectedEvents());
		selectedIds.sort(String::compareTo);
		for (String eventId : selectedIds) {
			for (Track track : timeline.getTracks()) {
				for (Clip clip : track.getClips()) {
					TimelineEvent event = clip.getEvent(eventId);
					if (event != null) return new EventRef(track, clip, event);
				}
			}
		}
		return null;
	}

	private boolean isTrackLocked(Timeline timeline, TimelineEditor editor, String trackId) {
		if (editor == null || trackId == null || trackId.isBlank()) return false;
		int rowIndex = logicalRowForTrackId(timeline, trackId);
		return rowIndex >= 0 && editor.getTrackListState().isLocked(rowIndex);
	}

	private int logicalRowForTrackId(Timeline timeline, String trackId) {
		if (trackId == null || trackId.isBlank()) return -1;
        switch (trackId) {
            case Timeline.TRACK_ID_AUDIO -> {
                return TimelineTrackMeta.ROW_AUDIO_GROUP;
            }
            case Timeline.TRACK_ID_ANIMATION_BLOCK -> {
                return TimelineTrackMeta.ROW_ANIM_BLOCK;
            }
            case Timeline.TRACK_ID_ANIMATION_AUTO -> {
                return TimelineTrackMeta.ROW_ANIM_AUTO;
            }
            case Timeline.TRACK_ID_CAMERA -> {
                return TimelineTrackMeta.ROW_CAMERA;
            }
            case Timeline.TRACK_ID_GLOBAL -> {
                return TimelineTrackMeta.ROW_GLOBAL_EVENT;
            }
        }
        if (timeline != null && Timeline.isBlockAnimationFeatureTrackId(trackId)) {
			List<TrackDefinition> defs = TrackRegistry.buildBlockAnimationControlTracks(timeline);
			for (int i = 0; i < defs.size() && i < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; i++) {
				if (trackId.equals(defs.get(i).getKey())) {
					return TimelineTrackMeta.ROW_ANIM_FEATURES_START + i;
				}
			}
		}
		return -1;
	}

	private List<Option> collectAnimationOptions() {
		List<Option> options = new ArrayList<>();
		options.add(new Option("", "未绑定"));
		if (BeatBlock.blockAnimationEngine == null) return options;
		List<AnimationDefinition> defs = new ArrayList<>(BeatBlock.blockAnimationEngine.getAnimationLibrary().getAll().values());
		defs.sort(Comparator.comparing(AnimationDefinition::getName, String.CASE_INSENSITIVE_ORDER));
		for (AnimationDefinition def : defs) {
			options.add(new Option(def.getId(), def.getName() + " [" + def.getId() + "]"));
		}
		return options;
	}

	private List<Option> collectActionOptions() {
		List<Option> options = new ArrayList<>();
		for (TimelineAnimationActionMode mode : TimelineAnimationActionMode.values()) {
			String label = switch (mode) {
				case ANIMATE -> "动画";
				case PLACE -> "放置";
				case CLEAR -> "清除";
				case BUILD -> "建造";
			};
			options.add(new Option(mode.name(), label + " [" + mode.name() + "]"));
		}
		return options;
	}

	private List<Option> collectTargetOptions() {
		List<Option> options = new ArrayList<>();
		options.add(new Option("", "未绑定"));
		if (BeatBlock.blockAnimationEngine == null) return options;
		List<StageObject> objects = new ArrayList<>(BeatBlock.blockAnimationEngine.getStageObjectSystem().getAll());
		objects.sort(Comparator.comparing(StageObject::getName, String.CASE_INSENSITIVE_ORDER));
		for (StageObject object : objects) {
			options.add(new Option(object.getId(), object.getName() + " [" + object.getId() + "]"));
		}
		return options;
	}

	private static int indexOfOption(List<Option> options, String id) {
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).id().equals(id)) return i;
		}
		return 0;
	}

	private static String[] optionLabels(List<Option> options) {
		String[] labels = new String[options.size()];
		for (int i = 0; i < options.size(); i++) {
			labels[i] = options.get(i).label();
		}
		return labels;
	}

	private static String stringParam(Map<String, Object> params, String key) {
		return stringParam(params, key, "");
	}

	private static String stringParam(Map<String, Object> params, String key, String fallback) {
		Object value = params != null ? params.get(key) : null;
		return value != null ? String.valueOf(value) : fallback;
	}

	private static double numericParam(Map<String, Object> params, String key, double fallback) {
		Object value = params != null ? params.get(key) : null;
		return value instanceof Number ? ((Number) value).doubleValue() : fallback;
	}

	private static boolean booleanParam(Map<String, Object> params, String key, boolean fallback) {
		Object value = params != null ? params.get(key) : null;
		if (value instanceof Boolean b) return b;
		if (value instanceof Number n) return n.intValue() != 0;
		if (value == null) return fallback;
		String s = String.valueOf(value).trim();
		if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return true;
		if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return false;
		return fallback;
	}

	private static int indexOfValue(String[] values, String target) {
		if (values == null || values.length == 0) return 0;
		if (target == null) return 0;
		for (int i = 0; i < values.length; i++) {
			if (target.equalsIgnoreCase(values[i])) return i;
		}
		return 0;
	}

	/** 每种镜头类型对应的有效参数键名列表（不含 kind）。 */
	private static List<String> paramKeysForKind(CameraSegmentKind kind) {
		return switch (kind) {
			case PATH -> List.of();
			case DOLLY -> List.of("startX", "startY", "startZ", "endX", "endY", "endZ", "baseYawDeg", "basePitchDeg");
			case ORBIT -> List.of("targetX", "targetY", "targetZ", "radius", "height", "yawStartDeg", "yawEndDeg");
			case CRANE -> List.of("startX", "startY", "startZ", "endX", "endY", "endZ", "yawDeg", "pitchDeg");
			case SHAKE -> List.of("anchorX", "anchorY", "anchorZ", "yawDeg", "pitchDeg", "distance", "amplitude", "frequencyHz", "beatSync", "beatsPerPulse");
		};
	}

	/** 切换镜头类型时，为新类型尚未设置的参数填入默认值（尽量使用当前玩家视角）。 */
	private static void initSegmentDefaults(TimelineEvent event, CameraSegmentKind kind) {
		Map<String, Object> p = event.getParameters();
		MinecraftClient mc = MinecraftClient.getInstance();
		double ex = 0, ey = 64, ez = 0;
		float yaw = 0f, pitch = 0f;
		if (mc != null && mc.player != null) {
			net.minecraft.util.math.Vec3d eye = mc.player.getEyePos();
			ex = eye.x; ey = eye.y; ez = eye.z;
			yaw = mc.player.getYaw();
			pitch = mc.player.getPitch();
		}
		switch (kind) {
			case DOLLY -> {
				putIfAbsent(event, p, "startX", ex); putIfAbsent(event, p, "startY", ey); putIfAbsent(event, p, "startZ", ez);
				putIfAbsent(event, p, "endX", ex);   putIfAbsent(event, p, "endY", ey);   putIfAbsent(event, p, "endZ", ez);
				putIfAbsent(event, p, "baseYawDeg", yaw);
				putIfAbsent(event, p, "basePitchDeg", pitch);
			}
			case ORBIT -> {
				putIfAbsent(event, p, "targetX", ex); putIfAbsent(event, p, "targetY", ey); putIfAbsent(event, p, "targetZ", ez);
				putIfAbsent(event, p, "radius", 10.0);  putIfAbsent(event, p, "height", 4.0);
				putIfAbsent(event, p, "yawStartDeg", 0.0); putIfAbsent(event, p, "yawEndDeg", 270.0);
			}
			case CRANE -> {
				putIfAbsent(event, p, "startX", ex); putIfAbsent(event, p, "startY", ey); putIfAbsent(event, p, "startZ", ez);
				putIfAbsent(event, p, "endX", ex);   putIfAbsent(event, p, "endY", ey + 8.0); putIfAbsent(event, p, "endZ", ez);
				putIfAbsent(event, p, "yawDeg", yaw); putIfAbsent(event, p, "pitchDeg", pitch);
			}
			case SHAKE -> {
				putIfAbsent(event, p, "anchorX", ex); putIfAbsent(event, p, "anchorY", ey); putIfAbsent(event, p, "anchorZ", ez);
				putIfAbsent(event, p, "yawDeg", yaw); putIfAbsent(event, p, "pitchDeg", pitch);
				putIfAbsent(event, p, "distance", 10.0);  putIfAbsent(event, p, "amplitude", 0.35);
				putIfAbsent(event, p, "frequencyHz", 18.0); putIfAbsent(event, p, "beatSync", 1.0);
				putIfAbsent(event, p, "beatsPerPulse", 0.5);
			}
			case PATH -> {}
		}
	}

	private static void putIfAbsent(TimelineEvent event, Map<String, Object> p, String key, double value) {
		if (!p.containsKey(key)) event.setParameter(key, value);
	}

	private static String valueOf(ImString text) {
		String value = text != null ? text.get() : null;
		return value != null ? value : "";
	}
}
