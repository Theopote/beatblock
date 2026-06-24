package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.SelectionBox;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineLayout;

import static com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots.InteractiveTrackSlot;
import static com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots.build;

/** 内容区框选：按下、拖动、释放选中事件。 */
public final class TimelineBoxSelectHandler {

	private TimelineBoxSelectHandler() {}

	public static void begin(
		SelectionState selectionState,
		SelectionBox selectionBox,
		InteractionState interactionState,
		float mx,
		float my
	) {
		if (selectionState != null) {
			selectionState.clearEvents();
			selectionState.clearClips();
		}
		if (selectionBox != null) {
			selectionBox.setStart(mx, my);
			selectionBox.setEnd(mx, my);
			selectionBox.setActive(true);
		}
		if (interactionState != null) {
			interactionState.setMode(InteractionMode.BOX_SELECT);
			interactionState.setMouseStart(mx, my);
		}
	}

	public static void updateEnd(SelectionBox selectionBox, float mx, float my) {
		if (selectionBox != null) {
			selectionBox.setEnd(mx, my);
		}
	}

	public static void applySelection(
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState viewState,
		SelectionBox selectionBox,
		SelectionState selectionState
	) {
		if (timeline == null || layout == null || viewState == null || selectionBox == null || selectionState == null) {
			return;
		}
		if (!selectionBox.isActive()) return;

		float boxMinX = selectionBox.getMinX();
		float boxMaxX = selectionBox.getMaxX();
		float boxMinY = selectionBox.getMinY();
		float boxMaxY = selectionBox.getMaxY();

		for (InteractiveTrackSlot slot : build(timeline)) {
			int logicalRow = slot.rowIndex();
			if (!layout.isRowVisible(logicalRow)) continue;
			float rowTopY = layout.getRowScreenY(logicalRow);
			float rowBottomY = rowTopY + layout.getRowHeight(logicalRow);
			if (rowBottomY < boxMinY || rowTopY > boxMaxY) continue;

			Track track = timeline.getTrack(slot.trackId());
			if (track == null) continue;
			for (Clip clip : track.getClips()) {
				for (TimelineEvent event : clip.getEvents()) {
					float screenX = layout.contentLeft + viewState.timeToScreen(event.getTimeSeconds());
					if (screenX >= boxMinX && screenX <= boxMaxX) {
						selectionState.selectEvent(event.getId());
					}
				}
			}
		}
	}
}
