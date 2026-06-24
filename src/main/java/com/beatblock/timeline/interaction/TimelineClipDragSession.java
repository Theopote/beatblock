package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 片段拖动（音频联动 / 摄像机片内事件）快照。 */
public final class TimelineClipDragSession {

	private double initialStart;
	private double initialEnd;
	private double initialMouseTime;
	private final Map<String, Double> linkedEventOriginalTimes = new HashMap<>();
	private final Map<String, List<double[]>> featureEventSnapshot = new HashMap<>();
	private final Map<String, Double> cameraClipEventOriginalTimes = new HashMap<>();
	private ClipDragStateSnapshot undoSnapshot;

	public double initialStart() {
		return initialStart;
	}

	public double initialEnd() {
		return initialEnd;
	}

	public double initialMouseTime() {
		return initialMouseTime;
	}

	public Map<String, Double> linkedEventOriginalTimes() {
		return linkedEventOriginalTimes;
	}

	public Map<String, List<double[]>> featureEventSnapshot() {
		return featureEventSnapshot;
	}

	public Map<String, Double> cameraClipEventOriginalTimes() {
		return cameraClipEventOriginalTimes;
	}

	public ClipDragStateSnapshot undoSnapshot() {
		return undoSnapshot;
	}

	public static TimelineClipDragSession beginAudioClipDrag(
		Timeline timeline,
		String trackId,
		String clipId,
		Clip hitClip,
		InteractionState interactionState,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mx,
		float my
	) {
		TimelineClipDragSession session = new TimelineClipDragSession();
		session.initialStart = hitClip.getStartTimeSeconds();
		session.initialEnd = hitClip.getEndTimeSeconds();
		session.initialMouseTime = viewState.screenToTime(mx - layout.contentLeft);

		double clipStart = session.initialStart;
		double clipEnd = session.initialEnd;
		for (Track st : timeline.getTracks()) {
			if (Timeline.TRACK_ID_AUDIO.equals(st.getId())) continue;
			for (Clip sc : st.getClips()) {
				for (TimelineEvent se : sc.getEvents()) {
					double eventTime = se.getTimeSeconds();
					if (eventTime >= clipStart && eventTime <= clipEnd) {
						session.linkedEventOriginalTimes.put(se.getId(), eventTime);
					}
				}
			}
		}
		for (Map.Entry<String, FeatureTrack> entry : timeline.getFeatureTracks().entrySet()) {
			List<FeatureEvent> events = entry.getValue().getEvents();
			List<double[]> snap = new ArrayList<>(events.size());
			for (FeatureEvent fe : events) {
				snap.add(new double[] {fe.getTimeSeconds(), fe.getEnergy()});
			}
			session.featureEventSnapshot.put(entry.getKey(), snap);
		}
		session.undoSnapshot = ClipDragStateSnapshot.capture(
			timeline,
			trackId,
			clipId,
			session.linkedEventOriginalTimes,
			session.featureEventSnapshot
		);

		interactionState.setMode(com.beatblock.timeline.editor.InteractionMode.DRAG_CLIP);
		interactionState.setMouseStart(mx, my);
		interactionState.setActiveClipId(clipId);
		interactionState.setActiveTrackId(trackId);
		return session;
	}

	public static TimelineClipDragSession beginCameraClipDrag(
		Timeline timeline,
		String trackId,
		String clipId,
		Clip hitClip,
		InteractionState interactionState,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mx,
		float my
	) {
		TimelineClipDragSession session = new TimelineClipDragSession();
		session.initialStart = hitClip.getStartTimeSeconds();
		session.initialEnd = hitClip.getEndTimeSeconds();
		session.initialMouseTime = viewState.screenToTime(mx - layout.contentLeft);
		for (TimelineEvent se : hitClip.getEvents()) {
			session.cameraClipEventOriginalTimes.put(se.getId(), se.getTimeSeconds());
		}
		session.undoSnapshot = ClipDragStateSnapshot.capture(
			timeline,
			trackId,
			clipId,
			session.cameraClipEventOriginalTimes,
			Map.of()
		);

		interactionState.setMode(com.beatblock.timeline.editor.InteractionMode.DRAG_CLIP);
		interactionState.setMouseStart(mx, my);
		interactionState.setActiveClipId(clipId);
		interactionState.setActiveTrackId(trackId);
		return session;
	}

	public void clear() {
		initialStart = 0.0;
		initialEnd = 0.0;
		initialMouseTime = 0.0;
		linkedEventOriginalTimes.clear();
		featureEventSnapshot.clear();
		cameraClipEventOriginalTimes.clear();
		undoSnapshot = null;
	}
}
