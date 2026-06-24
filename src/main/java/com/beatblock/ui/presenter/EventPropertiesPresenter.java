package com.beatblock.ui.presenter;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.StageObject;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editing.AnimationEventFormInput;
import com.beatblock.timeline.editing.AnimationEventPropertiesEditor;
import com.beatblock.timeline.editing.AnimationEventSnapshot;
import com.beatblock.timeline.editing.CameraEventPropertiesEditor;
import com.beatblock.timeline.editing.TimelineEventEditActions;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.generation.DistancePacing;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TrackDefinition;
import com.beatblock.timeline.rendering.TrackRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 事件属性面板业务逻辑：选择解析、校验、Command 提交与选项列表。
 */
public final class EventPropertiesPresenter {

	public sealed interface ApplyResult {
		record Ok() implements ApplyResult {}
		record Err(String message) implements ApplyResult {}
	}

	public record CameraViewSample(double x, double y, double z, float yaw, float pitch) {}

	@FunctionalInterface
	public interface CameraViewProvider {
		CameraViewSample currentView();
	}

	private final Predicate<String> stageObjectExists;
	private final Predicate<String> blockIdValid;
	private final Supplier<List<EventPropertiesOption>> animationOptionsSupplier;
	private final Supplier<List<EventPropertiesOption>> targetOptionsSupplier;
	private final CameraViewProvider cameraViewProvider;

	public EventPropertiesPresenter(
		Predicate<String> stageObjectExists,
		Predicate<String> blockIdValid,
		Supplier<List<EventPropertiesOption>> animationOptionsSupplier,
		Supplier<List<EventPropertiesOption>> targetOptionsSupplier,
		CameraViewProvider cameraViewProvider
	) {
		this.stageObjectExists = stageObjectExists;
		this.blockIdValid = blockIdValid;
		this.animationOptionsSupplier = animationOptionsSupplier;
		this.targetOptionsSupplier = targetOptionsSupplier;
		this.cameraViewProvider = cameraViewProvider;
	}

	public EventPropertiesRef resolvePropertiesRef(Timeline timeline, SelectionState selectionState) {
		EventPropertiesRef fromEvent = resolveSelectedEventRefFromEvents(timeline, selectionState);
		if (fromEvent != null) {
			return fromEvent;
		}
		if (timeline == null || selectionState == null || selectionState.getSelectedClips().isEmpty()) {
			return null;
		}
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) {
			return null;
		}
		List<String> clipIds = new ArrayList<>(selectionState.getSelectedClips());
		clipIds.sort(String::compareTo);
		for (String clipId : clipIds) {
			if (clipId == null) {
				continue;
			}
			Clip c = cam.getClip(clipId);
			if (c == null) {
				continue;
			}
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(c);
			if (seg != null) {
				return new EventPropertiesRef(cam, c, seg);
			}
			return new EventPropertiesRef(cam, c, null);
		}
		return null;
	}

	public boolean isTrackLocked(Timeline timeline, TimelineEditor editor, String trackId) {
		if (editor == null || trackId == null || trackId.isBlank()) {
			return false;
		}
		int rowIndex = logicalRowForTrackId(timeline, trackId);
		return rowIndex >= 0 && editor.getTrackListState().isLocked(rowIndex);
	}

	public List<EventPropertiesOption> actionOptions() {
		List<EventPropertiesOption> options = new ArrayList<>();
		for (TimelineAnimationActionMode mode : TimelineAnimationActionMode.values()) {
			String label = switch (mode) {
				case ANIMATE -> "动画";
				case PLACE -> "放置";
				case CLEAR -> "清除";
				case BUILD -> "建造";
			};
			options.add(new EventPropertiesOption(mode.name(), label + " [" + mode.name() + "]"));
		}
		return options;
	}

	public List<EventPropertiesOption> animationOptions() {
		return animationOptionsSupplier.get();
	}

	public List<EventPropertiesOption> targetOptions() {
		return targetOptionsSupplier.get();
	}

	public ApplyResult applyAnimationEvent(
		EventPropertiesRef ref,
		Timeline timeline,
		CommandManager commandManager,
		AnimationEventFormInput input
	) {
		if (commandManager == null) {
			return new ApplyResult.Err("时间线编辑器未初始化。");
		}
		if (ref == null || ref.event() == null) {
			return new ApplyResult.Err("无动画事件。");
		}
		var result = AnimationEventPropertiesEditor.buildUpdatedSnapshot(
			input,
			new HashMap<>(ref.event().getParameters()),
			stageObjectExists,
			blockIdValid
		);
		if (result instanceof AnimationEventPropertiesEditor.Result.Err err) {
			return new ApplyResult.Err(err.message());
		}
		AnimationEventSnapshot after = ((AnimationEventPropertiesEditor.Result.Ok) result).snapshot();
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(ref.event(), ref.clip());
		commitEventEdit(ref, timeline, commandManager, before, after);
		return new ApplyResult.Ok();
	}

	public ApplyResult applyCameraClipOnly(
		EventPropertiesRef ref,
		Timeline timeline,
		CommandManager commandManager,
		double newStart,
		double newEnd,
		boolean pathVisible
	) {
		if (commandManager == null) {
			return new ApplyResult.Err("时间线编辑器未初始化。");
		}
		if (ref == null || ref.clip() == null) {
			return new ApplyResult.Err("无摄像机片段。");
		}
		double oldStart = ref.clip().getStartTimeSeconds();
		Map<String, Double> existingTimes = new HashMap<>();
		for (TimelineEvent ev : ref.clip().getEvents()) {
			existingTimes.put(ev.getId(), ev.getTimeSeconds());
		}
		var result = CameraEventPropertiesEditor.buildClipOnlySnapshot(
			oldStart, newStart, newEnd, pathVisible,
			existingTimes, timeline, ref.clip().getId()
		);
		if (result instanceof CameraEventPropertiesEditor.Result.Err err) {
			return new ApplyResult.Err(err.message());
		}
		AnimationEventSnapshot after = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		AnimationEventSnapshot before = AnimationEventSnapshot.captureClipOnly(ref.clip(), timeline, ref.clip().getId());
		commitEventEdit(ref, timeline, commandManager, before, after);
		return new ApplyResult.Ok();
	}

	public ApplyResult applyCameraKindChange(
		EventPropertiesRef ref,
		Timeline timeline,
		CommandManager commandManager,
		CameraSegmentKind newKind
	) {
		if (commandManager == null) {
			return new ApplyResult.Err("时间线编辑器未初始化。");
		}
		if (ref == null || ref.event() == null) {
			return new ApplyResult.Err("无镜头段事件。");
		}
		var result = CameraEventPropertiesEditor.buildKindChangeSnapshot(
			newKind,
			ref.event().getParameters(),
			defaultSegmentParams(newKind),
			ref.clip().getStartTimeSeconds(),
			ref.clip().getEndTimeSeconds()
		);
		if (result instanceof CameraEventPropertiesEditor.Result.Err err) {
			return new ApplyResult.Err(err.message());
		}
		AnimationEventSnapshot after = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(
			ref.event(), ref.clip(), timeline, ref.clip().getId());
		commitEventEdit(ref, timeline, commandManager, before, after);
		return new ApplyResult.Ok();
	}

	public ApplyResult applyCameraSegment(
		EventPropertiesRef ref,
		Timeline timeline,
		CommandManager commandManager,
		double durationSeconds,
		boolean pathVisible,
		Map<String, String> rawParams
	) {
		if (commandManager == null) {
			return new ApplyResult.Err("时间线编辑器未初始化。");
		}
		if (ref == null || ref.event() == null || ref.clip() == null) {
			return new ApplyResult.Err("无镜头段事件。");
		}
		CameraSegmentKind currentKind = CameraSegmentKind.fromParam(ref.event().getParameters().get("kind"));
		var result = CameraEventPropertiesEditor.buildSegmentSnapshot(
			ref.clip().getStartTimeSeconds(),
			durationSeconds,
			pathVisible,
			currentKind,
			new HashMap<>(ref.event().getParameters()),
			rawParams != null ? rawParams : Map.of(),
			timeline,
			ref.clip().getId()
		);
		if (result instanceof CameraEventPropertiesEditor.Result.Err err) {
			return new ApplyResult.Err(err.message());
		}
		AnimationEventSnapshot after = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(
			ref.event(), ref.clip(), timeline, ref.clip().getId());
		commitEventEdit(ref, timeline, commandManager, before, after);
		return new ApplyResult.Ok();
	}

	public ApplyResult applyCameraKeyframe(
		EventPropertiesRef ref,
		Timeline timeline,
		CommandManager commandManager,
		double newTime,
		double x,
		double y,
		double z,
		double yaw,
		double pitch,
		String ease
	) {
		if (commandManager == null) {
			return new ApplyResult.Err("时间线编辑器未初始化。");
		}
		if (ref == null || ref.event() == null || ref.clip() == null) {
			return new ApplyResult.Err("无关键帧事件。");
		}
		var result = CameraEventPropertiesEditor.buildKeyframeSnapshot(
			ref.clip().getStartTimeSeconds(),
			ref.clip().getEndTimeSeconds(),
			newTime, x, y, z, yaw, pitch, ease,
			new HashMap<>(ref.event().getParameters())
		);
		if (result instanceof CameraEventPropertiesEditor.Result.Err err) {
			return new ApplyResult.Err(err.message());
		}
		AnimationEventSnapshot after = ((CameraEventPropertiesEditor.Result.Ok) result).snapshot();
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(ref.event(), ref.clip());
		commitEventEdit(ref, timeline, commandManager, before, after);
		return new ApplyResult.Ok();
	}

	public Optional<Map<String, String>> captureSegmentViewParams(CameraSegmentKind kind) {
		CameraViewSample view = cameraViewProvider != null ? cameraViewProvider.currentView() : null;
		if (view == null) {
			return Optional.empty();
		}
		Map<String, String> params = new HashMap<>();
		switch (kind) {
			case DOLLY -> {
				putParam(params, "startX", view.x());
				putParam(params, "startY", view.y());
				putParam(params, "startZ", view.z());
				putParam(params, "baseYawDeg", view.yaw());
				putParam(params, "basePitchDeg", view.pitch());
			}
			case ORBIT -> {
				putParam(params, "targetX", view.x());
				putParam(params, "targetY", view.y());
				putParam(params, "targetZ", view.z());
			}
			case CRANE -> {
				putParam(params, "startX", view.x());
				putParam(params, "startY", view.y());
				putParam(params, "startZ", view.z());
				putParam(params, "yawDeg", view.yaw());
				putParam(params, "pitchDeg", view.pitch());
			}
			case SHAKE -> {
				putParam(params, "anchorX", view.x());
				putParam(params, "anchorY", view.y());
				putParam(params, "anchorZ", view.z());
				putParam(params, "yawDeg", view.yaw());
				putParam(params, "pitchDeg", view.pitch());
			}
			case PATH -> {}
		}
		return Optional.of(params);
	}

	public Optional<CameraViewSample> currentCameraView() {
		if (cameraViewProvider == null) {
			return Optional.empty();
		}
		CameraViewSample view = cameraViewProvider.currentView();
		return view != null ? Optional.of(view) : Optional.empty();
	}

	public Map<String, Object> defaultSegmentParams(CameraSegmentKind kind) {
		Map<String, Object> defaults = new HashMap<>();
		CameraViewSample view = cameraViewProvider != null ? cameraViewProvider.currentView() : null;
		double ex = 0;
		double ey = 64;
		double ez = 0;
		float yaw = 0f;
		float pitch = 0f;
		if (view != null) {
			ex = view.x();
			ey = view.y();
			ez = view.z();
			yaw = view.yaw();
			pitch = view.pitch();
		}
		switch (kind) {
			case DOLLY -> {
				defaults.put("startX", ex);
				defaults.put("startY", ey);
				defaults.put("startZ", ez);
				defaults.put("endX", ex);
				defaults.put("endY", ey);
				defaults.put("endZ", ez);
				defaults.put("baseYawDeg", yaw);
				defaults.put("basePitchDeg", pitch);
			}
			case ORBIT -> {
				defaults.put("targetX", ex);
				defaults.put("targetY", ey);
				defaults.put("targetZ", ez);
				defaults.put("radius", 10.0);
				defaults.put("height", 4.0);
				defaults.put("yawStartDeg", 0.0);
				defaults.put("yawEndDeg", 270.0);
			}
			case CRANE -> {
				defaults.put("startX", ex);
				defaults.put("startY", ey);
				defaults.put("startZ", ez);
				defaults.put("endX", ex);
				defaults.put("endY", ey + 8.0);
				defaults.put("endZ", ez);
				defaults.put("yawDeg", yaw);
				defaults.put("pitchDeg", pitch);
			}
			case SHAKE -> {
				defaults.put("anchorX", ex);
				defaults.put("anchorY", ey);
				defaults.put("anchorZ", ez);
				defaults.put("yawDeg", yaw);
				defaults.put("pitchDeg", pitch);
				defaults.put("distance", 10.0);
				defaults.put("amplitude", 0.35);
				defaults.put("frequencyHz", 18.0);
				defaults.put("beatSync", 1.0);
				defaults.put("beatsPerPulse", 0.5);
			}
			case PATH -> {}
		}
		return defaults;
	}

	public static boolean isPathVisible(Timeline timeline, String clipId) {
		return CameraPathMetadata.isPathVisible(timeline, clipId);
	}

	public static double defaultDistancePaceSeconds() {
		return DistancePacing.DEFAULT_SECONDS_PER_BLOCK_UNIT;
	}

	public static double defaultDistancePaceMinGap() {
		return DistancePacing.DEFAULT_MIN_GAP_SECONDS;
	}

	private void commitEventEdit(
		EventPropertiesRef ref,
		Timeline timeline,
		CommandManager commandManager,
		AnimationEventSnapshot before,
		AnimationEventSnapshot after
	) {
		TimelineEventEditActions.execute(
			timeline,
			commandManager,
			ref.track().getId(),
			ref.clip(),
			ref.event(),
			before,
			after
		);
	}

	private static EventPropertiesRef resolveSelectedEventRefFromEvents(
		Timeline timeline,
		SelectionState selectionState
	) {
		if (timeline == null || selectionState == null || selectionState.getSelectedEvents().isEmpty()) {
			return null;
		}
		List<String> selectedIds = new ArrayList<>(selectionState.getSelectedEvents());
		selectedIds.sort(String::compareTo);
		for (String eventId : selectedIds) {
			for (Track track : timeline.getTracks()) {
				for (Clip clip : track.getClips()) {
					TimelineEvent event = clip.getEvent(eventId);
					if (event != null) {
						return new EventPropertiesRef(track, clip, event);
					}
				}
			}
		}
		return null;
	}

	private static int logicalRowForTrackId(Timeline timeline, String trackId) {
		if (trackId == null || trackId.isBlank()) {
			return -1;
		}
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
			default -> {}
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

	private static void putParam(Map<String, String> params, String key, double value) {
		params.put(key, String.format(Locale.ROOT, "%.6f", value));
	}

	private static void putParam(Map<String, String> params, String key, float value) {
		params.put(key, String.format(Locale.ROOT, "%.3f", value));
	}

	/** 从动画引擎收集动画模板选项（无引擎时仅「未绑定」）。 */
	public static List<EventPropertiesOption> collectAnimationOptions(
		Supplier<Map<String, AnimationDefinition>> librarySupplier
	) {
		List<EventPropertiesOption> options = new ArrayList<>();
		options.add(new EventPropertiesOption("", "未绑定"));
		Map<String, AnimationDefinition> library = librarySupplier != null ? librarySupplier.get() : null;
		if (library == null || library.isEmpty()) {
			return options;
		}
		List<AnimationDefinition> defs = new ArrayList<>(library.values());
		defs.sort(Comparator.comparing(AnimationDefinition::getName, String.CASE_INSENSITIVE_ORDER));
		for (AnimationDefinition def : defs) {
			options.add(new EventPropertiesOption(
				def.getId(),
				def.getName() + " [" + def.getId() + "]"
			));
		}
		return options;
	}

	/** 从舞台对象系统收集目标选项（无引擎时仅「未绑定」）。 */
	public static List<EventPropertiesOption> collectTargetOptions(Supplier<List<StageObject>> objectsSupplier) {
		List<EventPropertiesOption> options = new ArrayList<>();
		options.add(new EventPropertiesOption("", "未绑定"));
		List<StageObject> objects = objectsSupplier != null ? objectsSupplier.get() : null;
		if (objects == null || objects.isEmpty()) {
			return options;
		}
		List<StageObject> sorted = new ArrayList<>(objects);
		sorted.sort(Comparator.comparing(StageObject::getName, String.CASE_INSENSITIVE_ORDER));
		for (StageObject object : sorted) {
			options.add(new EventPropertiesOption(
				object.getId(),
				object.getName() + " [" + object.getId() + "]"
			));
		}
		return options;
	}
}
