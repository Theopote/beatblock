package com.beatblock.ui.eventlibrary;

import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 可复用的事件配置快照（不含时间与目标对象，应用时注入）。 */
public record EventTemplate(
	@NonNull String id,
	@NonNull String name,
	@NonNull String animationTypeId,
	double durationSeconds,
	float energy,
	@NonNull Map<String, Object> parameters
) {

	public EventTemplate {
		id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
		name = name != null && !name.isBlank() ? name : animationTypeId;
		animationTypeId = animationTypeId != null ? animationTypeId : "";
		durationSeconds = Math.max(0.01, durationSeconds);
		energy = Math.max(0f, Math.min(1f, energy));
		parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
	}

	public static @NonNull EventTemplate fromAnimationEvent(
		@NonNull TimelineAnimationEvent event,
		@Nullable String name
	) {
		AnimationEventParams params = AnimationEventParams.fromAnimationEvent(event);
		Map<String, Object> stored = new HashMap<>(params.toParameterMap());
		stored.remove("targetObject");
		stored.put("eventOrigin", TimelineEventOrigin.MANUAL.name());
		String label = name != null && !name.isBlank()
			? name.trim()
			: params.animationType() + " · " + String.format("%.2fs", params.durationSeconds());
		return new EventTemplate(
			UUID.randomUUID().toString(),
			label,
			params.animationType(),
			params.durationSeconds(),
			params.energy(),
			stored
		);
	}

	public @NonNull TimelineAnimationEvent toTimelineEvent(double timeSeconds, @NonNull String targetObjectId) {
		Map<String, Object> merged = new HashMap<>(parameters);
		merged.put("targetObject", targetObjectId);
		AnimationEventParams params = AnimationEventParams.fromParameterMap(merged);
		return new TimelineAnimationEvent(
			"",
			timeSeconds,
			params.durationSeconds(),
			params.animationType(),
			targetObjectId,
			params.energy(),
			params.toParameterMap()
		);
	}
}
