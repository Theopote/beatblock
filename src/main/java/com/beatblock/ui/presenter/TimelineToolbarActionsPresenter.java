package com.beatblock.ui.presenter;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.generation.StepSequenceBaker;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.ui.presenter.RhythmDropPanelPresenter.GenerateRequest;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

/**
 * 时间线工具栏生成动作：Binding Map、Auto Map、STEP 烘焙。
 */
public final class TimelineToolbarActionsPresenter {

	public record ActionOutcome(String message, boolean success, int count) {}

	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<Vec3d> cameraPosition;
	private final RhythmDropPanelPresenter rhythmDropPresenter;

	public TimelineToolbarActionsPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<Vec3d> cameraPosition
	) {
		this(timeline, timelineEditor, cameraPosition, PresenterFactories.rhythmDropPanelPresenter());
	}

	TimelineToolbarActionsPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<Vec3d> cameraPosition,
		RhythmDropPanelPresenter rhythmDropPresenter
	) {
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.cameraPosition = cameraPosition;
		this.rhythmDropPresenter = rhythmDropPresenter;
	}

	public ActionOutcome runBindingMap() {
		Timeline current = timeline.get();
		if (current == null) {
			return new ActionOutcome("Binding Map skipped: timeline unavailable", false, -1);
		}
		int count = AnimationBindingEngine.applyRules(current, TimelineTrackMeta.ROW_ANIM_BLOCK, true);
		syncClockDuration();
		return new ActionOutcome("Binding Map generated " + count + " events", count > 0, count);
	}

	public ActionOutcome runAutoMap() {
		Timeline current = timeline.get();
		if (current == null) {
			return new ActionOutcome("Auto Map skipped: timeline unavailable", false, -1);
		}
		AutoMapConfig config = AutoMapConfig.createDefault();
		int count = AutoMapGenerator.generate(current, config, true);
		syncClockDuration();
		return new ActionOutcome("Auto Map generated " + count + " events", count > 0, count);
	}

	public ActionOutcome runGenerateRhythmDrops() {
		PresenterResult result = rhythmDropPresenter.generateFromSelectionWithDefaults();
		syncClockDuration();
		return new ActionOutcome(
			result.messageOrEmpty().isBlank()
				? (result.ok() ? "RhythmDrop 已生成" : "RhythmDrop 生成失败")
				: result.messageOrEmpty(),
			result.ok(),
			rhythmDropPresenter.viewState().selectionCount()
		);
	}

	public ActionOutcome runGenerateRhythmDrops(GenerateRequest request) {
		PresenterResult result = rhythmDropPresenter.generateFromSelection(request);
		syncClockDuration();
		return new ActionOutcome(
			result.messageOrEmpty().isBlank()
				? (result.ok() ? "RhythmDrop 已生成" : "RhythmDrop 生成失败")
				: result.messageOrEmpty(),
			result.ok(),
			rhythmDropPresenter.viewState().selectionCount()
		);
	}

	public ActionOutcome runBakeStepSequences() {
		Timeline current = timeline.get();
		if (current == null) {
			return new ActionOutcome("Bake STEP skipped: timeline unavailable", false, 0);
		}
		TimelineEditor editor = timelineEditor.get();
		Vec3d camera = cameraPosition != null ? cameraPosition.get() : Vec3d.ZERO;
		StepSequenceBaker.BakeResult result = StepSequenceBaker.bake(
			current,
			editor != null ? editor.getCommandManager() : null,
			camera != null ? camera : Vec3d.ZERO
		);
		syncClockDuration();
		if (result.stepEventsBaked() <= 0) {
			String detail = result.stepEventsSkipped() > 0
				? " (" + result.stepEventsSkipped() + " STEP events skipped; check StageObject)"
				: " (no STEP events on timeline)";
			return new ActionOutcome("Bake STEP: nothing baked" + detail, false, 0);
		}
		return new ActionOutcome(
			"Bake STEP: " + result.stepEventsBaked() + " STEP -> " + result.burstEventsCreated() + " burst events",
			true,
			result.burstEventsCreated()
		);
	}

	private void syncClockDuration() {
		TimelineEditor editor = timelineEditor.get();
		if (editor != null) {
			editor.syncClockDuration();
		}
	}
}
