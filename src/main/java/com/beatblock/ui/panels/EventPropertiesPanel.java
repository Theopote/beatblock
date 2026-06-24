package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.engine.influence.ChannelSpec;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.editing.AnimationEventFormInput;
import com.beatblock.timeline.editing.AnimationEventPropertiesEditor;
import com.beatblock.timeline.editing.CameraEventPropertiesEditor;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.AnimationEditorViewState;
import com.beatblock.ui.presenter.EventPropertiesFormSnapshot;
import com.beatblock.ui.presenter.EventPropertiesOption;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.timeline.rendering.TrackRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

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
	private final ImString distancePaceSecondsBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString distancePaceMinGapBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraNearDistanceBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraFarDistanceBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraNearScaleBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraFarScaleBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString cameraEdgePriorityBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString entryDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString idleDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString exitDurationBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString placeBlockBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString flashBlockBuffer = new ImString(INPUT_BUFFER_SIZE);
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
	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public EventPropertiesPanel() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	EventPropertiesPanel(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
	}

	private BeatBlockContext runtime() {
		return context.get();
	}

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
	private static final String[] PACING_MODE_LABELS = {
		"节拍网格 (BEAT_GRID)",
		"固定间隔 (FIXED_INTERVAL)",
		"跳跃距离 (DISTANCE)"
	};
	private static final String[] PACING_MODE_VALUES = {
		"BEAT_GRID",
		"FIXED_INTERVAL",
		"DISTANCE"
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

			Timeline timeline = runtime().timeline();
			TimelineEditor editor = runtime().timelineEditor();
			if (timeline == null || editor == null) {
				ImGui.textDisabled("时间线未初始化。");
				return;
			}

			EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, editor.getSelectionState());
			if (ref == null) {
				boundRefKey = null;
				validationError = null;
				ImGui.textWrapped("选中时间线上的事件或摄像机片段后，可在此编辑属性。");
				return;
			}

			String rk = EventPropertiesRef.refKey(ref);
			if (!rk.equals(boundRefKey)) {
				bindBuffers(ref);
			}

			renderEventSummary(ref, timeline);
			ImGui.separator();

			boolean trackLocked = presenter.isTrackLocked(timeline, editor, ref.track().getId());
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

	private void renderEventSummary(EventPropertiesRef ref, Timeline timeline) {
		ImGui.textDisabled("Track");
		ImGui.sameLine();
		ImGui.text(ref.track().getName().isBlank() ? ref.track().getId() : ref.track().getName());
		if (ref.event() == null) {
			ImGui.textDisabled("片段 ID");
			ImGui.sameLine();
			ImGui.text(ref.clip().getId());
			ImGui.textDisabled("显示路径");
			ImGui.sameLine();
			ImGui.text(EventPropertiesPresenter.isPathVisible(timeline, ref.clip().getId()) ? "是" : "否");
			return;
		}
		Map<String, Object> params = ref.event().getParameters();
		AnimationEditorViewState viewState = presenter.readAnimationEditorState(params);
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
			String sourceFeature = viewState.sourceFeature();
			if (!sourceFeature.isBlank()) {
				ImGui.textDisabled("Source Feature");
				ImGui.sameLine();
				ImGui.text(TrackRegistry.localizedName(sourceFeature));
			}
			String generatedBy = viewState.generatedBy();
			if (!generatedBy.isBlank()) {
				ImGui.textDisabled("Generated By");
				ImGui.sameLine();
				ImGui.text(generatedBy);
			}
			ImGui.textDisabled("Action Mode");
			ImGui.sameLine();
			ImGui.text(TimelineAnimationActionMode.fromValue(viewState.actionMode()).name());
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

	private void renderAnimationEditor(EventPropertiesRef ref, Timeline timeline) {
		Map<String, Object> params = ref.event().getParameters();
		AnimationEditorViewState viewState = presenter.readAnimationEditorState(params);
		List<EventPropertiesOption> actionOptions = presenter.actionOptions();
		List<EventPropertiesOption> animationOptions = presenter.animationOptions();
		List<EventPropertiesOption> targetOptions = presenter.targetOptions();

		ImGui.text("Timing");
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("开始时间 (s)##eventTime", timeBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("持续时间 (s)##eventDuration", durationBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("能量 (0-1)##eventEnergy", energyBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("能量阈值 (0-1)##eventEnergyThreshold", energyThresholdBuffer);

		String currentAnimationId = viewState.animationId();
		String currentTargetId = viewState.targetId();
		String currentActionMode = viewState.actionMode();
		boolean inheritGroupSpatial = viewState.inheritGroupSpatial();
		boolean stepDispatch = viewState.stepDispatch();
		boolean cameraAdaptiveStep = viewState.cameraAdaptiveStep();
		boolean cameraFrustumGating = viewState.cameraFrustumGating();
		boolean usePhaseAnimation = viewState.usePhaseAnimation();
		ImInt stepStartModeIndex = new ImInt(indexOfValue(STEP_START_MODE_VALUES, viewState.stepStartMode()));
		ImInt stepCompletionIndex = new ImInt(indexOfValue(STEP_COMPLETION_VALUES, viewState.stepCompletionMode()));
		ImInt pacingModeIndex = new ImInt(indexOfValue(PACING_MODE_VALUES, viewState.pacingMode()));
		if (pacingModeIndex.get() < 0 || pacingModeIndex.get() >= PACING_MODE_VALUES.length) pacingModeIndex.set(0);
		ImInt actionIndex = new ImInt(indexOfOption(actionOptions, currentActionMode));
		ImInt animationIndex = new ImInt(indexOfOption(animationOptions, currentAnimationId));
		ImInt targetIndex = new ImInt(indexOfOption(targetOptions, currentTargetId));
		ImInt spatialModeIndex = new ImInt(indexOfValue(SPATIAL_MODE_VALUES, viewState.spatialMode()));
		if (spatialModeIndex.get() < 0 || spatialModeIndex.get() >= SPATIAL_MODE_VALUES.length) spatialModeIndex.set(0);
		String[] actionLabels = optionLabels(actionOptions);
		String[] animationLabels = optionLabels(animationOptions);
		String[] targetLabels = optionLabels(targetOptions);

		ImGui.spacing();
		ImGui.text("Binding");
		if (ImGui.combo("动作模式##eventActionMode", actionIndex, actionLabels)) {
			validationError = null;
		}
		if (ImGui.combo("动画模板 (Preset)##eventAnimation", animationIndex, animationLabels)) {
			validationError = null;
		}
		renderPresetChannelPreview(animationOptions.get(animationIndex.get()).id());
		boolean vfxEnabled = viewState.vfxEnabled();
		ImBoolean vfxEnabledProxy = new ImBoolean(vfxEnabled);
		if (ImGui.checkbox("粒子强调 (VFX)##eventVfxEnabled", vfxEnabledProxy)) {
			vfxEnabled = vfxEnabledProxy.get();
			validationError = null;
		}
		BlockInfluencePreset selectedPreset = BlockInfluencePresets.get(animationOptions.get(animationIndex.get()).id());
		if (selectedPreset != null && !selectedPreset.channelsFor(InfluenceDimension.APPEARANCE).isEmpty()) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("踩点闪烁方块##eventFlashBlock", flashBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("APPEARANCE 通道在动画中点切换到此方块，结束后还原。默认: minecraft:gold_block");
			}
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
			if (ImGui.combo("节奏来源##eventPacingMode", pacingModeIndex, PACING_MODE_LABELS)) {
				validationError = null;
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("跑酷推荐「跳跃距离」：按方块路径 3D 距离累加时间；建造常用「节拍网格」");
			}
			boolean distancePacing = "DISTANCE".equals(PACING_MODE_VALUES[pacingModeIndex.get()]);
			if (!distancePacing) {
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("每拍方块数##eventBlocksPerBeat", blocksPerBeatBuffer);
			}
			if (distancePacing) {
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("每方块距离 (秒)##eventDistancePaceSeconds", distancePaceSecondsBuffer);
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("最小间隔 (秒)##eventDistancePaceMinGap", distancePaceMinGapBuffer);
			}
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
		ImGui.textDisabled(String.format(Locale.ROOT, "Mapping: %s", viewState.mappingProfile()));
		ImGui.textDisabled(String.format(Locale.ROOT, "Source Stem: %s", viewState.sourceStem()));
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
				PACING_MODE_VALUES[Math.max(0, Math.min(pacingModeIndex.get(), PACING_MODE_VALUES.length - 1))],
				cameraAdaptiveStep,
				cameraFrustumGating,
				usePhaseAnimation,
				vfxEnabled);
		}
		if (reset) {
			bindBuffers(ref);
		}
	}

	private void renderRuntimeStatus(EventPropertiesRef ref) {
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

	private void applyAnimationChanges(EventPropertiesRef ref, Timeline timeline, String actionMode, String animationId,
	                                  String targetObjectId, boolean inheritGroupSpatial, String spatialMode,
	                                  boolean stepDispatch, String stepStartMode, String stepCompletionMode,
	                                  String pacingMode, boolean cameraAdaptiveStep, boolean cameraFrustumGating,
	                                  boolean usePhaseAnimation, boolean vfxEnabled) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = "时间线编辑器未初始化。";
			return;
		}
		try {
			AnimationEventFormInput input = AnimationEventPropertiesEditor.parseFormInput(
				valueOf(timeBuffer),
				valueOf(durationBuffer),
				valueOf(energyBuffer),
				valueOf(energyThresholdBuffer),
				valueOf(spatialDelayBuffer),
				valueOf(blocksPerBeatBuffer),
				valueOf(distancePaceSecondsBuffer),
				valueOf(distancePaceMinGapBuffer),
				valueOf(cameraNearDistanceBuffer),
				valueOf(cameraFarDistanceBuffer),
				valueOf(cameraNearScaleBuffer),
				valueOf(cameraFarScaleBuffer),
				valueOf(cameraEdgePriorityBuffer),
				valueOf(entryDurationBuffer),
				valueOf(idleDurationBuffer),
				valueOf(exitDurationBuffer),
				valueOf(placeBlockBuffer),
				valueOf(flashBlockBuffer),
				actionMode,
				animationId,
				targetObjectId,
				inheritGroupSpatial,
				spatialMode,
				stepDispatch,
				stepStartMode,
				stepCompletionMode,
				pacingMode,
				cameraAdaptiveStep,
				cameraFrustumGating,
				usePhaseAnimation,
				vfxEnabled
			);
			var result = presenter.applyAnimationEvent(ref, timeline, editor.getCommandManager(), input);
			if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
				validationError = message;
				return;
			}
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = "时间、持续时间或能量格式不正确。";
		}
	}

	private void bindBuffers(EventPropertiesRef ref) {
		applyFormSnapshot(presenter.buildFormSnapshot(ref, runtime().timeline()));
		validationError = null;
	}

	private void applyFormSnapshot(EventPropertiesFormSnapshot snap) {
		camSegParamBuffers.clear();
		boundRefKey = snap.refKey();
		camClipStartBuffer.set(snap.camClipStart());
		camClipEndBuffer.set(snap.camClipEnd());
		camClipPathVisibleProxy.set(snap.camClipPathVisible());
		timeBuffer.set(snap.time());
		durationBuffer.set(snap.duration());
		energyBuffer.set(snap.energy());
		energyThresholdBuffer.set(snap.energyThreshold());
		spatialDelayBuffer.set(snap.spatialDelay());
		blocksPerBeatBuffer.set(snap.blocksPerBeat());
		distancePaceSecondsBuffer.set(snap.distancePaceSeconds());
		distancePaceMinGapBuffer.set(snap.distancePaceMinGap());
		cameraNearDistanceBuffer.set(snap.cameraNearDistance());
		cameraFarDistanceBuffer.set(snap.cameraFarDistance());
		cameraNearScaleBuffer.set(snap.cameraNearScale());
		cameraFarScaleBuffer.set(snap.cameraFarScale());
		cameraEdgePriorityBuffer.set(snap.cameraEdgePriority());
		placeBlockBuffer.set(snap.placeBlock());
		flashBlockBuffer.set(snap.flashBlock());
		camSegDurBuffer.set(snap.camSegDuration());
		camSegPathVisibleProxy.set(snap.camSegPathVisible());
		for (Map.Entry<String, String> entry : snap.camSegParams().entrySet()) {
			camSegParamBuffers.put(entry.getKey(), new ImString(entry.getValue(), INPUT_BUFFER_SIZE));
		}
		camXBuffer.set(snap.camX());
		camYBuffer.set(snap.camY());
		camZBuffer.set(snap.camZ());
		camYawBuffer.set(snap.camYaw());
		camPitchBuffer.set(snap.camPitch());
		camEaseBuffer.set(snap.camEase());
	}

	private void renderCameraClipOnlyPanel(EventPropertiesRef ref, Timeline timeline) {
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
			applyCameraClipOnly(ref, timeline);
		}
		ImGui.sameLine();
		if (ImGui.button("重置##camClipReset", 120f, 0f)) {
			bindBuffers(ref);
		}
	}

	private void applyCameraClipOnly(EventPropertiesRef ref, Timeline timeline) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = "时间线编辑器未初始化。";
			return;
		}
		try {
			double newStart = Double.parseDouble(valueOf(camClipStartBuffer).trim());
			double newEnd = Double.parseDouble(valueOf(camClipEndBuffer).trim());
			var result = presenter.applyCameraClipOnly(
				ref,
				timeline,
				editor.getCommandManager(),
				newStart,
				newEnd,
				camClipPathVisibleProxy.get()
			);
			if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
				validationError = message;
				return;
			}
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = "时间格式不正确。";
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

	private void renderCameraSegmentPanel(EventPropertiesRef ref, Timeline timeline) {
		CameraSegmentKind kind = CameraSegmentKind.fromParam(ref.event().getParameters().get("kind"));

		// 镜头类型下拉选择
		ImInt kindIdx = new ImInt(kindIndex(kind));
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo("镜头类型##camSegKind", kindIdx, CAM_KIND_LABELS)) {
			CameraSegmentKind newKind = CAM_KINDS[kindIdx.get()];
			if (newKind != kind) {
				applyCameraKindChange(ref, timeline, newKind);
				kind = newKind;
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
		var captured = presenter.captureSegmentViewParams(kind);
		if (captured.isEmpty()) {
			validationError = "无可用相机，无法捕获。";
			return;
		}
		for (Map.Entry<String, String> entry : captured.get().entrySet()) {
			ImString buf = camSegParamBuffers.computeIfAbsent(entry.getKey(), k -> new ImString(INPUT_BUFFER_SIZE));
			buf.set(entry.getValue());
		}
		validationError = null;
	}

	private void applyCameraKindChange(EventPropertiesRef ref, Timeline timeline, CameraSegmentKind newKind) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = "时间线编辑器未初始化。";
			return;
		}
		var result = presenter.applyCameraKindChange(ref, timeline, editor.getCommandManager(), newKind);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
			return;
		}
		validationError = null;
		bindBuffers(ref);
	}

	private void applyCameraSegmentPanel(EventPropertiesRef ref, Timeline timeline) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = "时间线编辑器未初始化。";
			return;
		}
		try {
			double duration = Double.parseDouble(valueOf(camSegDurBuffer).trim());
			CameraSegmentKind currentKind = CameraSegmentKind.fromParam(ref.event().getParameters().get("kind"));
			Map<String, String> rawParams = new HashMap<>();
			for (String key : CameraEventPropertiesEditor.paramKeysForKind(currentKind)) {
				ImString buf = camSegParamBuffers.get(key);
				if (buf != null) {
					rawParams.put(key, valueOf(buf));
				}
			}
			var result = presenter.applyCameraSegment(
				ref,
				timeline,
				editor.getCommandManager(),
				duration,
				camSegPathVisibleProxy.get(),
				rawParams
			);
			if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
				validationError = message;
				return;
			}
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = "时长或参数格式不正确。";
		}
	}

	private void renderCameraKeyframePanel(EventPropertiesRef ref, Timeline timeline, SelectionState selectionState) {
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
			var view = presenter.currentCameraView();
			if (view.isEmpty()) {
				validationError = "无可用相机，无法捕获。";
			} else {
				EventPropertiesPresenter.CameraViewSample sample = view.get();
				camXBuffer.set(String.format(Locale.ROOT, "%.6f", sample.x()));
				camYBuffer.set(String.format(Locale.ROOT, "%.6f", sample.y()));
				camZBuffer.set(String.format(Locale.ROOT, "%.6f", sample.z()));
				camYawBuffer.set(String.format(Locale.ROOT, "%.3f", sample.yaw()));
				camPitchBuffer.set(String.format(Locale.ROOT, "%.3f", sample.pitch()));
				validationError = null;
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

	private void applyCameraKeyframe(EventPropertiesRef ref, Timeline timeline) {
		TimelineEditor editor = runtime().timelineEditor();
		if (editor == null) {
			validationError = "时间线编辑器未初始化。";
			return;
		}
		try {
			double newTime = Double.parseDouble(valueOf(timeBuffer).trim());
			double x = Double.parseDouble(valueOf(camXBuffer).trim());
			double y = Double.parseDouble(valueOf(camYBuffer).trim());
			double z = Double.parseDouble(valueOf(camZBuffer).trim());
			double yaw = Double.parseDouble(valueOf(camYawBuffer).trim());
			double pitch = Double.parseDouble(valueOf(camPitchBuffer).trim());
			String ease = valueOf(camEaseBuffer).trim();
			var result = presenter.applyCameraKeyframe(
				ref,
				timeline,
				editor.getCommandManager(),
				newTime,
				x,
				y,
				z,
				yaw,
				pitch,
				ease
			);
			if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
				validationError = message;
				return;
			}
			validationError = null;
			bindBuffers(ref);
		} catch (NumberFormatException ex) {
			validationError = "时间或坐标格式不正确。";
		}
	}

	private void renderPresetChannelPreview(String presetId) {
		BlockInfluencePreset preset = BlockInfluencePresets.get(presetId);
		if (preset == null || preset.getChannels().isEmpty()) return;
		if (!ImGui.treeNode("Preset 通道##eventPresetChannels")) return;
		try {
			ImGui.textDisabled(preset.getDisplayName() + " · " + preset.getDefaultDurationSeconds() + "s");
			for (ChannelSpec channel : preset.getChannels()) {
				if (channel == null || !channel.enabled()) continue;
				ImGui.bulletText(String.format(Locale.ROOT, "%s / %s / %s (%.2f→%.2f)",
					channel.dimension(), channel.path(), channel.curve(), channel.from(), channel.to()));
			}
		} finally {
			ImGui.treePop();
		}
	}

	private static int indexOfOption(List<EventPropertiesOption> options, String id) {
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).id().equals(id)) {
				return i;
			}
		}
		return 0;
	}

	private static String[] optionLabels(List<EventPropertiesOption> options) {
		String[] labels = new String[options.size()];
		for (int i = 0; i < options.size(); i++) {
			labels[i] = options.get(i).label();
		}
		return labels;
	}

	private static int indexOfValue(String[] values, String target) {
		if (values == null || values.length == 0) return 0;
		if (target == null) return 0;
		for (int i = 0; i < values.length; i++) {
			if (target.equalsIgnoreCase(values[i])) return i;
		}
		return 0;
	}

	private static String valueOf(ImString text) {
		String value = text != null ? text.get() : null;
		return value != null ? value : "";
	}
}
