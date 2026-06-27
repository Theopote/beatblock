package com.beatblock.ui.presenter;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.timeline.Timeline;
import com.beatblock.ui.i18n.BBTexts;

import java.util.List;
import java.util.function.Supplier;

/**
 * 快速开始向导业务逻辑：导入音乐 → 选择类型 → 框选方块 → 一键生成。
 */
public final class QuickStartWizardPresenter {

	public enum CreationType {
		BUILD_APPEARANCE,
		RHYTHM_JUMP,
		BLOCK_FALL
	}

	public enum Step {
		IMPORT,
		CHOOSE_TYPE,
		SELECT_BLOCKS,
		GENERATE,
		DONE
	}

	public record ViewState(
		Step step,
		boolean musicLoaded,
		boolean analysisReady,
		int selectionCount,
		CreationType creationType,
		String statusMessage
	) {}

	public record GenerateOutcome(
		PresenterResult result,
		SmartAutoMapEngine.AutoMapResult autoMapResult,
		String stageObjectId
	) {}

	private final MenuBarPresenter menuBarPresenter;
	private final AutoMapSettingsPanelPresenter autoMapPresenter;
	private final ToolPanelPresenter toolPanelPresenter;
	private final RhythmDropPanelPresenter rhythmDropPresenter;
	private final Supplier<Timeline> timeline;

	private Step step = Step.IMPORT;
	private CreationType creationType = CreationType.BUILD_APPEARANCE;
	private String statusMessage = "";

	public QuickStartWizardPresenter(
		MenuBarPresenter menuBarPresenter,
		AutoMapSettingsPanelPresenter autoMapPresenter,
		ToolPanelPresenter toolPanelPresenter,
		RhythmDropPanelPresenter rhythmDropPresenter,
		Supplier<Timeline> timeline
	) {
		this.menuBarPresenter = menuBarPresenter;
		this.autoMapPresenter = autoMapPresenter;
		this.toolPanelPresenter = toolPanelPresenter;
		this.rhythmDropPresenter = rhythmDropPresenter;
		this.timeline = timeline;
	}

	public ViewState viewState() {
		return new ViewState(
			step,
			isMusicLoaded(),
			autoMapPresenter.canGenerate(),
			selectionCount(),
			creationType,
			statusMessage
		);
	}

	public Step step() {
		return step;
	}

	public void reset() {
		step = Step.IMPORT;
		creationType = CreationType.BUILD_APPEARANCE;
		statusMessage = "";
	}

	public void setCreationType(CreationType type) {
		if (type != null) {
			creationType = type;
		}
	}

	public PresenterResult importMusic(String path) {
		PresenterResult result = menuBarPresenter.importAudio(path);
		if (result.ok()) {
			statusMessage = BBTexts.get("beatblock.wizard.music_imported");
			step = Step.CHOOSE_TYPE;
		} else {
			statusMessage = result.messageOrEmpty();
		}
		return result;
	}

	public void goToStep(Step target) {
		if (target != null) {
			step = target;
		}
	}

	public void advanceFromTypeStep() {
		step = Step.SELECT_BLOCKS;
		activateBoxSelectTool();
	}

	public void advanceFromSelectStep() {
		if (selectionCount() > 0) {
			step = Step.GENERATE;
		} else {
			statusMessage = BBTexts.get("beatblock.wizard.select_blocks_hint");
		}
	}

	public void activateBoxSelectTool() {
		BeatBlockSelectionManager mgr = BeatBlockSelectionManager.get();
		if (mgr != null) {
			mgr.setMode(SelectionMode.BOX);
		}
	}

	public GenerateOutcome generate() {
		if (selectionCount() <= 0) {
			PresenterResult failure = PresenterResult.failure(BBTexts.get("beatblock.wizard.select_blocks_hint"));
			statusMessage = failure.messageOrEmpty();
			return new GenerateOutcome(failure, null, null);
		}

		String autoName = generateAutoObjectName();
		ToolPanelPresenter.StageObjectCreateRequest createRequest = new ToolPanelPresenter.StageObjectCreateRequest(
			autoName,
			false,
			GroupSortingStrategy.SEQUENTIAL,
			0.0
		);
		ToolPanelPresenter.CreateStageObjectOutcome createOutcome =
			toolPanelPresenter.createFromSelectionSnapshot(createRequest);
		if (!createOutcome.result().ok()) {
			statusMessage = createOutcome.result().messageOrEmpty();
			return new GenerateOutcome(createOutcome.result(), null, null);
		}

		String objectId = createOutcome.objectId();
		PresenterResult genResult;
		SmartAutoMapEngine.AutoMapResult autoMapResult = null;

		switch (creationType) {
			case BUILD_APPEARANCE -> {
				AutoMapSettings settings = buildAutoMapSettings(
					AutoMapStyle.EDM, Complexity.MEDIUM, true, true, objectId);
				var outcome = autoMapPresenter.generate(settings);
				genResult = outcome.result();
				autoMapResult = outcome.autoMapResult();
				if (genResult.ok()) {
					statusMessage = BBTexts.get("beatblock.wizard.generated_build",
						autoMapResult != null ? autoMapResult.getAnimationEvents() : 0);
				}
			}
			case RHYTHM_JUMP -> {
				AutoMapSettings settings = buildAutoMapSettings(
					AutoMapStyle.MINIMAL, Complexity.LOW, false, false, objectId);
				var outcome = autoMapPresenter.generate(settings);
				genResult = outcome.result();
				autoMapResult = outcome.autoMapResult();
				if (genResult.ok()) {
					statusMessage = BBTexts.get("beatblock.wizard.generated_rhythm",
						autoMapResult != null ? autoMapResult.getAnimationEvents() : 0);
				}
			}
			case BLOCK_FALL -> {
				RhythmDropPanelPresenter.GenerateRequest request = new RhythmDropPanelPresenter.GenerateRequest(
					RhythmDropPanelPresenter.defaultRequest().fallDurationSeconds(),
					RhythmDropPanelPresenter.defaultRequest().fallHeightBlocks(),
					true,
					objectId
				);
				genResult = rhythmDropPresenter.generateFromSelection(request);
				if (genResult.ok()) {
					statusMessage = BBTexts.get("beatblock.wizard.generated_fall", autoName);
				}
			}
			default -> genResult = PresenterResult.failure(BBTexts.get("beatblock.wizard.unknown_type"));
		}

		if (!genResult.ok() && statusMessage.isBlank()) {
			statusMessage = genResult.messageOrEmpty();
		}
		if (genResult.ok()) {
			step = Step.DONE;
		}
		return new GenerateOutcome(genResult, autoMapResult, objectId);
	}

	private static AutoMapSettings buildAutoMapSettings(
		AutoMapStyle style,
		Complexity complexity,
		boolean camera,
		boolean particles,
		String objectId
	) {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setStyle(style);
		settings.setComplexity(complexity);
		settings.setCameraEnabled(camera);
		settings.setParticlesEnabled(particles);
		settings.setTargetObjectIds(List.of(objectId));
		return settings;
	}

	private boolean isMusicLoaded() {
		Timeline tl = timeline.get();
		if (tl == null) return false;
		Object audioPath = tl.getMetadata("audioPath");
		return audioPath != null && !String.valueOf(audioPath).isBlank();
	}

	private int selectionCount() {
		BeatBlockSelectionManager mgr = BeatBlockSelectionManager.get();
		return mgr != null ? mgr.getSelectionCount() : 0;
	}

	private String generateAutoObjectName() {
		var existingObjects = toolPanelPresenter.listStageObjects();
		int counter = 1;
		while (true) {
			String candidate = "selection_" + counter;
			boolean exists = existingObjects.stream()
				.anyMatch(obj -> obj.id().equals(candidate));
			if (!exists) {
				return candidate;
			}
			counter++;
		}
	}
}
