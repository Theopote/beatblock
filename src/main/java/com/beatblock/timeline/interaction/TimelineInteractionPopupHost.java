package com.beatblock.timeline.interaction;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import java.util.List;

/** {@link TimelineInteractionPopups} 所需宿主回调。 */
public interface TimelineInteractionPopupHost {

	TimelineInteractionPopupState popupState();

	List<TimelineInteractionClipboard.ClipboardEvent> clipboardEvents();

	TimelineEditor timelineEditor();

	void seekClockAndMusic(TimelineClock clock, double timeSeconds);

	void copySelectedEvents(SelectionState selectionState);

	void pasteClipboardEvents(SelectionState selectionState, double anchorTimeSeconds, TimelineTrackListState trackListState);

	void deleteSelectedEntries(SelectionState selectionState, TimelineTrackListState trackListState);

	TimelineEventRef resolvePropertiesEventRef(SelectionState selectionState);

	boolean canDeleteContextClip(TimelineTrackListState trackListState);
}
