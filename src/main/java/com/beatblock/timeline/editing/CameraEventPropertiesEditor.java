package com.beatblock.timeline.editing;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.ui.i18n.BBTexts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄像机片段 / 关键帧 / 纯片段编辑（无 ImGui 依赖）。
 */
public final class CameraEventPropertiesEditor {

	private CameraEventPropertiesEditor() {}

	public sealed interface Result {
		record Ok(AnimationEventSnapshot snapshot) implements Result {}
		record Err(String message) implements Result {}
	}

	public static List<String> paramKeysForKind(CameraSegmentKind kind) {
		return switch (kind) {
			case PATH -> List.of();
			case DOLLY -> List.of("startX", "startY", "startZ", "endX", "endY", "endZ", "baseYawDeg", "basePitchDeg");
			case ORBIT -> List.of("targetX", "targetY", "targetZ", "radius", "height", "yawStartDeg", "yawEndDeg");
			case CRANE -> List.of("startX", "startY", "startZ", "endX", "endY", "endZ", "yawDeg", "pitchDeg");
			case SHAKE -> List.of(
				"anchorX", "anchorY", "anchorZ", "yawDeg", "pitchDeg",
				"distance", "amplitude", "frequencyHz", "beatSync", "beatsPerPulse"
			);
		};
	}

	public static Result buildSegmentSnapshot(
		double clipStartSeconds,
		double durationSeconds,
		boolean pathVisible,
		CameraSegmentKind kind,
		Map<String, Object> existingParameters,
		Map<String, String> paramRawValues,
		Timeline timeline,
		String clipId
	) {
		durationSeconds = Math.max(0.05, durationSeconds);
		double clipEnd = clipStartSeconds + durationSeconds;
		Map<String, Object> parameters = new HashMap<>(
			existingParameters != null ? existingParameters : Map.of()
		);
		for (String key : new HashMap<>(parameters).keySet()) {
			if ("kind".equals(key)) continue;
			if (!paramKeysForKind(kind).contains(key)) {
				parameters.remove(key);
			}
		}
		for (String key : paramKeysForKind(kind)) {
			String raw = paramRawValues != null ? paramRawValues.get(key) : null;
			if (raw == null || raw.isBlank()) continue;
			try {
				parameters.put(key, Double.parseDouble(raw.trim()));
			} catch (NumberFormatException ex) {
				parameters.put(key, raw.trim());
			}
		}
		double timelineDuration = timeline != null
			? Math.max(timeline.getDurationSeconds(), clipEnd)
			: clipEnd;
		Map<String, String> metadata = clipId != null && !clipId.isBlank()
			? Map.of(CameraPathMetadata.metadataKey(clipId), CameraPathMetadata.metadataValue(pathVisible))
			: Map.of();
		return new Result.Ok(new AnimationEventSnapshot(
			clipStartSeconds,
			parameters,
			clipStartSeconds,
			clipEnd,
			Map.of(),
			metadata,
			timelineDuration
		));
	}

	public static Result buildKindChangeSnapshot(
		CameraSegmentKind newKind,
		Map<String, Object> existingParameters,
		Map<String, Object> defaultValues,
		double clipStartSeconds,
		double clipEndSeconds
	) {
		Map<String, Object> parameters = new HashMap<>(
			existingParameters != null ? existingParameters : Map.of()
		);
		for (String key : new HashMap<>(parameters).keySet()) {
			if ("kind".equals(key)) continue;
			if (!paramKeysForKind(newKind).contains(key)) {
				parameters.remove(key);
			}
		}
		parameters.put("kind", newKind.name());
		if (defaultValues != null) {
			for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
				parameters.putIfAbsent(entry.getKey(), entry.getValue());
			}
		}
		return new Result.Ok(new AnimationEventSnapshot(
			clipStartSeconds,
			parameters,
			clipStartSeconds,
			clipEndSeconds
		));
	}

	public static Result buildKeyframeSnapshot(
		double clipStartSeconds,
		double clipEndSeconds,
		double timeSeconds,
		double x,
		double y,
		double z,
		double yawDeg,
		double pitchDeg,
		String ease,
		Map<String, Object> existingParameters
	) {
		double clampedTime = Math.max(clipStartSeconds, Math.min(clipEndSeconds, Math.max(0.0, timeSeconds)));
		String resolvedEase = ease == null || ease.isBlank() ? "SMOOTH" : ease.trim();
		Map<String, Object> parameters = new HashMap<>(
			existingParameters != null ? existingParameters : Map.of()
		);
		parameters.put("x", x);
		parameters.put("y", y);
		parameters.put("z", z);
		parameters.put("yawDeg", yawDeg);
		parameters.put("pitchDeg", pitchDeg);
		parameters.put("ease", resolvedEase);
		return new Result.Ok(new AnimationEventSnapshot(
			clampedTime,
			parameters,
			clipStartSeconds,
			clipEndSeconds
		));
	}

	public static Result buildClipOnlySnapshot(
		double oldClipStart,
		double newStart,
		double newEnd,
		boolean pathVisible,
		Map<String, Double> existingEventTimes,
		Timeline timeline,
		String clipId
	) {
		if (newEnd <= newStart) {
			return new Result.Err(BBTexts.get("beatblock.camera.end_must_be_after_start"));
		}
		Map<String, Double> shiftedTimes = existingEventTimes != null
			? shiftClipEventTimes(existingEventTimes.entrySet(), oldClipStart, newStart, newEnd)
			: Map.of();
		double timelineDuration = timeline != null
			? Math.max(timeline.getDurationSeconds(), newEnd)
			: newEnd;
		Map<String, String> metadata = clipId != null && !clipId.isBlank()
			? Map.of(CameraPathMetadata.metadataKey(clipId), CameraPathMetadata.metadataValue(pathVisible))
			: Map.of();
		double primaryTime = shiftedTimes.isEmpty() ? newStart : shiftedTimes.values().iterator().next();
		return new Result.Ok(new AnimationEventSnapshot(
			primaryTime,
			Map.of(),
			newStart,
			newEnd,
			shiftedTimes,
			metadata,
			timelineDuration
		));
	}

	public static Map<String, Double> shiftClipEventTimes(
		Iterable<Map.Entry<String, Double>> eventTimes,
		double oldClipStart,
		double newClipStart,
		double newClipEnd
	) {
		double delta = newClipStart - oldClipStart;
		Map<String, Double> shifted = new HashMap<>();
		for (Map.Entry<String, Double> entry : eventTimes) {
			double nt = entry.getValue() + delta;
			shifted.put(entry.getKey(), Math.max(newClipStart, Math.min(newClipEnd, nt)));
		}
		return shifted;
	}
}
