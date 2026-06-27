package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.ui.i18n.BBTexts;

import java.util.function.Supplier;

/** 工程模板：初始化时间线或绑定规则集。 */
public final class ProjectTemplatePresenter {

	public enum TemplateId {
		BLANK,
		RHYTHM_PARKOUR,
		ARCHITECTURAL_SHOW
	}

	public record ApplyOutcome(boolean success, String message) {}

	private final Supplier<Timeline> timeline;
	private final TimelineBindingEditorPresenter bindingPresenter;

	public ProjectTemplatePresenter(
		Supplier<Timeline> timeline,
		TimelineBindingEditorPresenter bindingPresenter
	) {
		this.timeline = timeline;
		this.bindingPresenter = bindingPresenter;
	}

	public ApplyOutcome apply(TemplateId templateId) {
		Timeline current = timeline.get();
		if (current == null) {
			return new ApplyOutcome(false, BBTexts.get("beatblock.message.timeline_unavailable"));
		}
		return switch (templateId) {
			case BLANK -> applyBlank(current);
			case RHYTHM_PARKOUR -> applyBindingTemplate(current, 0);
			case ARCHITECTURAL_SHOW -> applyBindingTemplate(current, 1);
		};
	}

	private ApplyOutcome applyBlank(Timeline current) {
		TimelineDraftWriter.clearTrack(current, Timeline.TRACK_ID_ANIMATION_BLOCK);
		TimelineDraftWriter.clearTrack(current, Timeline.TRACK_ID_ANIMATION_AUTO);
		current.setDurationSeconds(Math.max(current.getDurationSeconds(), 60.0));
		current.sortAll();
		return new ApplyOutcome(true, BBTexts.get("beatblock.template.blank.applied"));
	}

	private ApplyOutcome applyBindingTemplate(Timeline current, int templateIndex) {
		var outcome = bindingPresenter.replaceWithTemplate(current, bindingPresenter.loadRules(current), templateIndex);
		return new ApplyOutcome(outcome.success(), outcome.message());
	}

	public static String labelKey(TemplateId id) {
		return switch (id) {
			case BLANK -> "beatblock.template.blank";
			case RHYTHM_PARKOUR -> "beatblock.timeline.binding.template.rhythm_parkour";
			case ARCHITECTURAL_SHOW -> "beatblock.timeline.binding.template.architectural_show";
		};
	}

	public static String descriptionKey(TemplateId id) {
		return switch (id) {
			case BLANK -> "beatblock.template.blank.desc";
			case RHYTHM_PARKOUR -> "beatblock.template.rhythm_parkour.desc";
			case ARCHITECTURAL_SHOW -> "beatblock.template.architectural_show.desc";
		};
	}
}
