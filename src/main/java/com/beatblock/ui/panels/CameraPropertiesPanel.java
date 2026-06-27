package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editing.CameraEventPropertiesEditor;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.EventPropertiesFormSnapshot;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 右侧摄像机属性面板：片段起止、分段参数、关键帧位姿。
 */
public class CameraPropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	private final ImString timeBuffer = new ImString(INPUT_BUFFER_SIZE);
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

	public CameraPropertiesPanel() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	CameraPropertiesPanel(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
	}

	private BeatBlockContext runtime() {
		return context.get();
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.CAMERA_PROPERTIES_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.CAMERA_PROPERTIES_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text("摄像机属性");
			ImGui.separator();

			Timeline timeline = runtime().timeline();
			TimelineEditor editor = runtime().timelineEditor();
			if (timeline == null || editor == null) {
				ImGui.textDisabled("时间线未初始化。");
				return;
			}

			EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, editor.getSelectionState());
			if (!isCameraRef(ref)) {
				boundRefKey = null;
				validationError = null;
				ImGui.textWrapped("选中摄像机片段或关键帧后，可在此编辑属性。");
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
				if (et == EventType.CAMERA_SEGMENT) {
					renderCameraSegmentPanel(ref, timeline);
				} else if (et == EventType.CAMERA_KEYFRAME) {
					renderCameraKeyframePanel(ref, timeline, editor.getSelectionState());
				}
			}

			if (trackLocked) {
				ImGui.endDisabled();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.CAMERA_PROPERTIES_WINDOW);
		}
	}

	private static boolean isCameraRef(EventPropertiesRef ref) {
		if (ref == null) {
			return false;
		}
		if (ref.event() == null) {
			return true;
		}
		EventType et = ref.event().getType();
		return et == EventType.CAMERA_SEGMENT || et == EventType.CAMERA_KEYFRAME;
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
		EventType et = ref.event().getType();
		ImGui.textDisabled("Event ID");
		ImGui.sameLine();
		ImGui.text(ref.event().getId());
		ImGui.textDisabled("事件类型");
		ImGui.sameLine();
		ImGui.text(et.name());
		if (et == EventType.CAMERA_SEGMENT) {
			ImGui.textDisabled("镜头类型");
			ImGui.sameLine();
			ImGui.text(CameraSegmentKind.fromParam(params.get("kind")).name());
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
		} catch (NumberFormatException e) {
			com.beatblock.BeatBlock.LOGGER.debug("Invalid camera preview coordinates", e);
		}
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

	private static String valueOf(ImString text) {
		String value = text != null ? text.get() : null;
		return value != null ? value : "";
	}
}
