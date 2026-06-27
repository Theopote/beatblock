package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.QuickStartWizardPresenter;
import com.beatblock.ui.util.AudioFilePicker;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * 快速开始向导：引导新用户 5 分钟内完成第一个作品。
 */
public final class QuickStartWizardPanel {

	private static final int PATH_CAPACITY = 512;

	private final QuickStartWizardPresenter presenter;
	private final ImString musicPath = new ImString(PATH_CAPACITY);
	private final ImInt creationTypeIndex = new ImInt(0);
	private boolean open;

	public QuickStartWizardPanel() {
		this(PresenterFactories.quickStartWizardPresenter());
	}

	QuickStartWizardPanel(QuickStartWizardPresenter presenter) {
		this.presenter = presenter;
	}

	public void open() {
		open = true;
	}

	public void render() {
		if (!open) return;

		ImGui.setNextWindowSize(520, 0, ImGuiCond.FirstUseEver);
		ImGui.setNextWindowPos(ImGui.getIO().getDisplaySizeX() * 0.5f, ImGui.getIO().getDisplaySizeY() * 0.35f,
			ImGuiCond.FirstUseEver, 0.5f, 0.35f);

		if (!ImGui.begin(BBTexts.get("beatblock.wizard.title"),
			ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.end();
			return;
		}

		try {
			renderStepIndicator(presenter.viewState().step());
			ImGui.separator();
			ImGui.spacing();

			switch (presenter.viewState().step()) {
				case IMPORT -> renderImportStep();
				case CHOOSE_TYPE -> renderTypeStep();
				case SELECT_BLOCKS -> renderSelectStep();
				case GENERATE, DONE -> renderGenerateStep();
			}

			if (!presenter.viewState().statusMessage().isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(presenter.viewState().statusMessage());
			}
		} finally {
			ImGui.end();
		}
	}

	private void renderStepIndicator(QuickStartWizardPresenter.Step current) {
		String[] labels = BBTexts.labels(
			"beatblock.wizard.step.import",
			"beatblock.wizard.step.type",
			"beatblock.wizard.step.select",
			"beatblock.wizard.step.generate"
		);
		int currentIndex = switch (current) {
			case IMPORT -> 0;
			case CHOOSE_TYPE -> 1;
			case SELECT_BLOCKS -> 2;
			case GENERATE, DONE -> 3;
		};
		for (int i = 0; i < labels.length; i++) {
			if (i > 0) ImGui.sameLine();
			if (i < currentIndex) {
				ImGui.textColored(0.4f, 1f, 0.4f, 1f, "✓ " + labels[i]);
			} else if (i == currentIndex) {
				ImGui.textColored(0.4f, 0.8f, 1f, 1f, "▶ " + labels[i]);
			} else {
				ImGui.textDisabled("○ " + labels[i]);
			}
		}
	}

	private void renderImportStep() {
		ImGui.textWrapped(BBTexts.get("beatblock.wizard.import.desc"));
		ImGui.spacing();

		ImGui.text(BBTexts.get("beatblock.wizard.import.path"));
		ImGui.setNextItemWidth(-120f);
		ImGui.inputText("##wizardMusicPath", musicPath);
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.wizard.browse") + "##wizardBrowse")) {
			String chosen = AudioFilePicker.choose(musicPath, msg -> presenter.goToStep(QuickStartWizardPresenter.Step.IMPORT));
			if (chosen != null && !chosen.isBlank()) {
				musicPath.set(chosen);
			}
		}

		ImGui.spacing();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.import.button") + "##wizardImport", -1f, 32f)) {
			presenter.importMusic(musicPath.get());
		}
		ImGui.popStyleColor(3);

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##wizardCancelImport")) {
			open = false;
		}
	}

	private void renderTypeStep() {
		ImGui.textWrapped(BBTexts.get("beatblock.wizard.type.desc"));
		ImGui.spacing();

		String[] typeLabels = BBTexts.labels(
			"beatblock.wizard.type.build",
			"beatblock.wizard.type.rhythm",
			"beatblock.wizard.type.fall"
		);
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo(BBTexts.get("beatblock.wizard.type.label") + "##wizardType", creationTypeIndex, typeLabels)) {
			presenter.setCreationType(switch (creationTypeIndex.get()) {
				case 1 -> QuickStartWizardPresenter.CreationType.RHYTHM_JUMP;
				case 2 -> QuickStartWizardPresenter.CreationType.BLOCK_FALL;
				default -> QuickStartWizardPresenter.CreationType.BUILD_APPEARANCE;
			});
		}

		String tooltip = switch (creationTypeIndex.get()) {
			case 1 -> BBTexts.get("beatblock.wizard.type.rhythm.tooltip");
			case 2 -> BBTexts.get("beatblock.wizard.type.fall.tooltip");
			default -> BBTexts.get("beatblock.wizard.type.build.tooltip");
		};
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltip);

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.back") + "##wizardBackType")) {
			presenter.goToStep(QuickStartWizardPresenter.Step.IMPORT);
		}
		ImGui.sameLine();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.next") + "##wizardNextType")) {
			presenter.advanceFromTypeStep();
		}
		ImGui.popStyleColor(3);
	}

	private void renderSelectStep() {
		ImGui.textWrapped(BBTexts.get("beatblock.wizard.select.desc"));
		ImGui.spacing();

		int count = presenter.viewState().selectionCount();
		if (count > 0) {
			ImGui.textColored(0.4f, 1f, 0.4f, 1f, BBTexts.get("beatblock.wizard.selected_count", count));
		} else {
			ImGui.textColored(1f, 0.6f, 0.2f, 1f, BBTexts.get("beatblock.wizard.select_blocks_hint"));
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.activate_box_select") + "##wizardBoxSelect", -1f, 0f)) {
			presenter.activateBoxSelectTool();
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.wizard.activate_box_select.tooltip"));
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.back") + "##wizardBackSelect")) {
			presenter.goToStep(QuickStartWizardPresenter.Step.CHOOSE_TYPE);
		}
		ImGui.sameLine();
		boolean canContinue = count > 0;
		if (!canContinue) ImGui.beginDisabled();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.next") + "##wizardNextSelect")) {
			presenter.advanceFromSelectStep();
		}
		ImGui.popStyleColor(3);
		if (!canContinue) ImGui.endDisabled();
	}

	private void renderGenerateStep() {
		var state = presenter.viewState();
		if (state.step() == QuickStartWizardPresenter.Step.DONE) {
			ImGui.textColored(0.4f, 1f, 0.4f, 1f, BBTexts.get("beatblock.wizard.done.title"));
			ImGui.textWrapped(BBTexts.get("beatblock.wizard.done.desc"));
			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.wizard.close") + "##wizardClose", -1f, 32f)) {
				open = false;
				presenter.reset();
			}
			return;
		}

		ImGui.textWrapped(BBTexts.get("beatblock.wizard.generate.desc"));
		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.wizard.generate.summary",
			typeLabel(state.creationType()),
			state.selectionCount()));

		if (!state.analysisReady() && state.creationType() != QuickStartWizardPresenter.CreationType.BLOCK_FALL) {
			ImGui.textColored(1f, 0.6f, 0.2f, 1f, BBTexts.get("beatblock.wizard.analysis_pending"));
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.back") + "##wizardBackGenerate")) {
			presenter.goToStep(QuickStartWizardPresenter.Step.SELECT_BLOCKS);
		}
		ImGui.sameLine();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.generate.button") + "##wizardGenerate", -1f, 32f)) {
			presenter.generate();
		}
		ImGui.popStyleColor(3);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.wizard.generate.tooltip"));
		}
	}

	private static String typeLabel(QuickStartWizardPresenter.CreationType type) {
		return switch (type) {
			case RHYTHM_JUMP -> BBTexts.get("beatblock.wizard.type.rhythm");
			case BLOCK_FALL -> BBTexts.get("beatblock.wizard.type.fall");
			case BUILD_APPEARANCE -> BBTexts.get("beatblock.wizard.type.build");
		};
	}
}
