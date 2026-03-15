package com.beatblock.timeline.editor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 选择系统：多选 Event / Clip / Track，支持 Shift/Ctrl 与框选。
 */
public class SelectionState {

	private final Set<String> selectedEvents = new HashSet<>();
	private final Set<String> selectedClips = new HashSet<>();
	private final Set<String> selectedTracks = new HashSet<>();

	public Set<String> getSelectedEvents() {
		return Collections.unmodifiableSet(selectedEvents);
	}

	public Set<String> getSelectedClips() {
		return Collections.unmodifiableSet(selectedClips);
	}

	public Set<String> getSelectedTracks() {
		return Collections.unmodifiableSet(selectedTracks);
	}

	public void selectEvent(String eventId) {
		if (eventId != null) selectedEvents.add(eventId);
	}

	public void selectClip(String clipId) {
		if (clipId != null) selectedClips.add(clipId);
	}

	public void selectTrack(String trackId) {
		if (trackId != null) selectedTracks.add(trackId);
	}

	public void deselectEvent(String eventId) {
		selectedEvents.remove(eventId);
	}

	public void deselectClip(String clipId) {
		selectedClips.remove(clipId);
	}

	public void deselectTrack(String trackId) {
		selectedTracks.remove(trackId);
	}

	public void clearEvents() {
		selectedEvents.clear();
	}

	public void clearClips() {
		selectedClips.clear();
	}

	public void clearTracks() {
		selectedTracks.clear();
	}

	public void clearAll() {
		selectedEvents.clear();
		selectedClips.clear();
		selectedTracks.clear();
	}

	public boolean isEventSelected(String eventId) {
		return eventId != null && selectedEvents.contains(eventId);
	}

	public boolean isClipSelected(String clipId) {
		return clipId != null && selectedClips.contains(clipId);
	}

	public boolean isTrackSelected(String trackId) {
		return trackId != null && selectedTracks.contains(trackId);
	}
}
