package com.beatblock.ui.presenter;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.eventlibrary.EventTemplate;
import com.beatblock.ui.eventlibrary.EventTemplateStore;
import com.beatblock.ui.i18n.BBTexts;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public final class EventLibraryPanelPresenter {

	public record ViewState(
		boolean editorReady,
		boolean hasSelection,
		String selectedEventSummary,
		List<EventTemplate> templates,
		String statusMessage
	) {}

	public record ApplyOutcome(boolean success, String message) {}

	private final EventPropertiesPresenter eventPropertiesPresenter;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<StageObjectSystem> stageObjectSystem;

	private String statusMessage = "";

	public EventLibraryPanelPresenter(
		EventPropertiesPresenter eventPropertiesPresenter,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<StageObjectSystem> stageObjectSystem
	) {
		this.eventPropertiesPresenter = eventPropertiesPresenter;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.stageObjectSystem = stageObjectSystem;
	}

	public ViewState viewState() {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return new ViewState(false, false, "", EventTemplateStore.all(), statusMessage);
		}
		EventPropertiesRef ref = eventPropertiesPresenter.resolvePropertiesRef(tl, editor.getSelectionState());
		boolean hasSelection = ref != null && ref.event() != null && ref.event().getType() == EventType.ANIMATION;
		String summary = hasSelection ? summarize(ref) : "";
		return new ViewState(true, hasSelection, summary, EventTemplateStore.all(), statusMessage);
	}

	public ApplyOutcome saveFromSelection(String name) {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		EventPropertiesRef ref = eventPropertiesPresenter.resolvePropertiesRef(tl, editor.getSelectionState());
		if (ref == null || ref.event() == null || ref.event().getType() != EventType.ANIMATION) {
			return fail(BBTexts.get("beatblock.event_library.no_selection"));
		}
		TimelineAnimationEvent animationEvent = findAnimationEvent(tl, ref.event().getId());
		if (animationEvent == null) {
			return fail(BBTexts.get("beatblock.event_library.no_selection"));
		}
		EventTemplate template = EventTemplate.fromAnimationEvent(animationEvent, name);
		EventTemplateStore.add(template);
		statusMessage = BBTexts.get("beatblock.event_library.saved", template.name());
		return new ApplyOutcome(true, statusMessage);
	}

	public ApplyOutcome applyTemplate(String templateId) {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		EventTemplate template = EventTemplateStore.find(templateId).orElse(null);
		if (template == null) {
			return fail(BBTexts.get("beatblock.event_library.template_missing"));
		}
		String targetObjectId = resolveTargetObjectId(editor.getSelectionState(), tl);
		if (targetObjectId == null) {
			return fail(BBTexts.get("beatblock.timeline.record.no_stage_object"));
		}
		double timeSeconds = editor.getClock().getCurrentTimeSeconds();
		TimelineAnimationEvent event = template.toTimelineEvent(timeSeconds, targetObjectId);
		boolean written = TimelineDraftWriter.writeEvent(
			tl,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			event,
			TimelineEventOrigin.MANUAL
		);
		if (!written) {
			return fail(BBTexts.get("beatblock.event_library.apply_failed"));
		}
		tl.sortAll();
		editor.syncClockDuration();
		statusMessage = BBTexts.get("beatblock.event_library.applied", template.name());
		return new ApplyOutcome(true, statusMessage);
	}

	public ApplyOutcome deleteTemplate(String templateId) {
		if (!EventTemplateStore.remove(templateId)) {
			return fail(BBTexts.get("beatblock.event_library.template_missing"));
		}
		statusMessage = BBTexts.get("beatblock.event_library.deleted");
		return new ApplyOutcome(true, statusMessage);
	}

	private @Nullable String resolveTargetObjectId(@Nullable SelectionState selection, Timeline timeline) {
		if (selection != null && !selection.getSelectedEvents().isEmpty()) {
			for (String eventId : selection.getSelectedEvents()) {
				TimelineAnimationEvent event = findAnimationEvent(timeline, eventId);
				if (event != null && event.getTargetObjectId() != null && !event.getTargetObjectId().isBlank()) {
					return event.getTargetObjectId();
				}
			}
		}
		StageObjectSystem system = stageObjectSystem.get();
		if (system == null) {
			return null;
		}
		for (StageObject obj : system.getAll()) {
			if (obj != null && obj.getId() != null && !obj.getId().isBlank()) {
				return obj.getId();
			}
		}
		return null;
	}

	private static @Nullable TimelineAnimationEvent findAnimationEvent(Timeline timeline, String eventId) {
		if (eventId == null || eventId.isBlank()) {
			return null;
		}
		for (TimelineAnimationEvent event : timeline.getBlockAnimationEvents()) {
			if (eventId.equals(event.getEventId())) {
				return event;
			}
		}
		return null;
	}

	private static String summarize(EventPropertiesRef ref) {
		var params = ref.event().getParameters();
		Object animationType = params.get("animationType");
		return String.valueOf(animationType != null ? animationType : ref.event().getType().name())
			+ " @ " + String.format("%.2fs", ref.event().getTimeSeconds());
	}

	private ApplyOutcome fail(String message) {
		statusMessage = message;
		return new ApplyOutcome(false, message);
	}
}
