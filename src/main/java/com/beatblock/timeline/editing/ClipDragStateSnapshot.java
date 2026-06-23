package com.beatblock.timeline.editing;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 片段拖动 / 缩放时受影响的时间线状态快照（片段时间、联动事件、特征轨）。
 */
public record ClipDragStateSnapshot(
	Map<String, ClipBounds> clipsByKey,
	Map<String, Double> eventTimesById,
	Map<String, List<FeatureEvent>> featureTracksByKey,
	double timelineDurationSeconds
) {

	public record ClipBounds(String trackId, String clipId, double startSeconds, double endSeconds) {}

	public ClipDragStateSnapshot {
		clipsByKey = clipsByKey != null ? Map.copyOf(clipsByKey) : Map.of();
		eventTimesById = eventTimesById != null ? Map.copyOf(eventTimesById) : Map.of();
		featureTracksByKey = featureTracksByKey != null ? copyFeatureTracks(featureTracksByKey) : Map.of();
	}

	public static ClipDragStateSnapshot capture(
		Timeline timeline,
		String primaryTrackId,
		String primaryClipId,
		Map<String, Double> eventTimesById,
		Map<String, List<double[]>> featureSnapshot
	) {
		Map<String, ClipBounds> clips = new LinkedHashMap<>();
		Track primaryTrack = timeline != null ? timeline.getTrack(primaryTrackId) : null;
		if (primaryTrack != null) {
			Clip primaryClip = primaryTrack.getClip(primaryClipId);
			if (primaryClip != null) {
				clips.put(clipKey(primaryTrackId, primaryClipId), new ClipBounds(
					primaryTrackId,
					primaryClipId,
					primaryClip.getStartTimeSeconds(),
					primaryClip.getEndTimeSeconds()
				));
			}
		}
		Map<String, List<FeatureEvent>> features = new HashMap<>();
		if (featureSnapshot != null) {
			for (Map.Entry<String, List<double[]>> entry : featureSnapshot.entrySet()) {
				List<FeatureEvent> events = new ArrayList<>();
				if (entry.getValue() != null) {
					for (double[] pair : entry.getValue()) {
						events.add(new FeatureEvent(pair[0], (float) pair[1]));
					}
				}
				features.put(entry.getKey(), List.copyOf(events));
			}
		}
		double duration = timeline != null ? timeline.getDurationSeconds() : 0.0;
		return new ClipDragStateSnapshot(
			clips,
			eventTimesById != null ? new HashMap<>(eventTimesById) : Map.of(),
			features,
			duration
		);
	}

	public ClipDragStateSnapshot captureCurrent(Timeline timeline) {
		if (timeline == null) return this;
		Map<String, ClipBounds> clips = new LinkedHashMap<>();
		for (ClipBounds bounds : clipsByKey.values()) {
			Track track = timeline.getTrack(bounds.trackId());
			Clip clip = track != null ? track.getClip(bounds.clipId()) : null;
			if (clip != null) {
				clips.put(clipKey(bounds.trackId(), bounds.clipId()), new ClipBounds(
					bounds.trackId(),
					bounds.clipId(),
					clip.getStartTimeSeconds(),
					clip.getEndTimeSeconds()
				));
			}
		}
		Map<String, Double> eventTimes = new HashMap<>();
		for (String eventId : eventTimesById.keySet()) {
			TimelineEvent event = findEvent(timeline, eventId);
			if (event != null) {
				eventTimes.put(eventId, event.getTimeSeconds());
			}
		}
		Map<String, List<FeatureEvent>> features = new HashMap<>();
		for (String featureKey : featureTracksByKey.keySet()) {
			FeatureTrack track = timeline.getFeatureTracks().get(featureKey);
			if (track == null) continue;
			List<FeatureEvent> events = new ArrayList<>();
			for (FeatureEvent event : track.getEvents()) {
				events.add(new FeatureEvent(event.getTimeSeconds(), event.getEnergy()));
			}
			features.put(featureKey, List.copyOf(events));
		}
		return new ClipDragStateSnapshot(clips, eventTimes, features, timeline.getDurationSeconds());
	}

	public void applyTo(Timeline timeline) {
		if (timeline == null) return;
		for (ClipBounds bounds : clipsByKey.values()) {
			Track track = timeline.getTrack(bounds.trackId());
			if (track == null) continue;
			Clip clip = track.getClip(bounds.clipId());
			if (clip == null) continue;
			clip.setStartTimeSeconds(bounds.startSeconds());
			clip.setEndTimeSeconds(bounds.endSeconds());
		}
		for (Map.Entry<String, Double> entry : eventTimesById.entrySet()) {
			TimelineEvent event = findEvent(timeline, entry.getKey());
			if (event != null) {
				event.setTimeSeconds(entry.getValue());
			}
		}
		for (Map.Entry<String, List<FeatureEvent>> entry : featureTracksByKey.entrySet()) {
			FeatureTrack track = timeline.getFeatureTracks().get(entry.getKey());
			if (track == null) continue;
			track.clear();
			for (FeatureEvent event : entry.getValue()) {
				track.addEvent(new FeatureEvent(event.getTimeSeconds(), event.getEnergy()));
			}
		}
		if (timelineDurationSeconds > 0.0) {
			timeline.setDurationSeconds(timelineDurationSeconds);
		}
		markAnimationTracksDirty(timeline);
	}

	public static String clipKey(String trackId, String clipId) {
		return trackId + "|" + clipId;
	}

	private static TimelineEvent findEvent(Timeline timeline, String eventId) {
		for (Track track : timeline.getTracks()) {
			for (Clip clip : track.getClips()) {
				TimelineEvent event = clip.getEvent(eventId);
				if (event != null) return event;
			}
		}
		return null;
	}

	private static Map<String, List<FeatureEvent>> copyFeatureTracks(Map<String, List<FeatureEvent>> source) {
		Map<String, List<FeatureEvent>> copy = new HashMap<>();
		for (Map.Entry<String, List<FeatureEvent>> entry : source.entrySet()) {
			List<FeatureEvent> events = new ArrayList<>();
			for (FeatureEvent event : entry.getValue()) {
				events.add(new FeatureEvent(event.getTimeSeconds(), event.getEnergy()));
			}
			copy.put(entry.getKey(), List.copyOf(events));
		}
		return Map.copyOf(copy);
	}

	private static void markAnimationTracksDirty(Timeline timeline) {
		for (Track track : timeline.getTracks()) {
			String trackId = track.getId();
			if (Timeline.TRACK_ID_ANIMATION_BLOCK.equals(trackId)
				|| Timeline.TRACK_ID_ANIMATION_AUTO.equals(trackId)
				|| Timeline.TRACK_ID_BUILD_REVERSE.equals(trackId)
				|| Timeline.isBlockAnimationFeatureTrackId(trackId)) {
				timeline.markAnimationEventsDirty(trackId);
			}
		}
	}
}
