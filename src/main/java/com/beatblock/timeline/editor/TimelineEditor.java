package com.beatblock.timeline.editor;

import com.beatblock.timeline.Timeline;

import java.util.HashMap;
import java.util.Map;

/**
 * 时间线编辑器：聚合 TimeSystem / Viewport / Selection / Interaction，与 Timeline 数据解耦。
 */
public class TimelineEditor {

	private final Timeline timeline;
	private final TimelineClock clock;
	private final TimelineViewState viewState;
	private final SelectionState selectionState;
	private final InteractionState interactionState;
	private final SelectionBox selectionBox;
	private final Map<String, TrackUIState> trackUIStates = new HashMap<>();

	public TimelineEditor(Timeline timeline) {
		this.timeline = timeline;
		this.clock = new TimelineClock();
		this.viewState = new TimelineViewState();
		this.selectionState = new SelectionState();
		this.interactionState = new InteractionState();
		this.selectionBox = new SelectionBox();
		if (timeline != null) {
			clock.setDurationSeconds(timeline.getDurationSeconds());
			for (var t : timeline.getTracks()) {
				trackUIStates.put(t.getId(), new TrackUIState());
			}
		}
	}

	public Timeline getTimeline() {
		return timeline;
	}

	public TimelineClock getClock() {
		return clock;
	}

	public TimelineViewState getViewState() {
		return viewState;
	}

	public SelectionState getSelectionState() {
		return selectionState;
	}

	public InteractionState getInteractionState() {
		return interactionState;
	}

	public SelectionBox getSelectionBox() {
		return selectionBox;
	}

	public TrackUIState getTrackUIState(String trackId) {
		return trackUIStates.computeIfAbsent(trackId, k -> new TrackUIState());
	}

	/** 同步时钟时长与 Timeline 一致 */
	public void syncClockDuration() {
		if (timeline != null) {
			clock.setDurationSeconds(timeline.getDurationSeconds());
		}
	}
}
