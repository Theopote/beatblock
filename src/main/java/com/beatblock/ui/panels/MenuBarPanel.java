package com.beatblock.ui.panels;

import com.beatblock.ui.BeatBlockPanelVisibility;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.MenuBarPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

/**
 * 顶部通栏菜单栏：文件、编辑、视图、演出、帮助。
 */
public class MenuBarPanel {

	private static final int IMPORT_PATH_CAPACITY = 512;

	private final MenuBarPresenter presenter;
	private final Runnable onCloseRequest;
	private final BeatBlockPanelVisibility panels;
	private final Runnable onOpenSmartAutoMap;
	private final Runnable onGenerateRhythmDrop;
	private final Runnable onResetLayout;
	private final Runnable onSaveLayout;
	private final Runnable onLoadLayout;
	private final Runnable onOpenQuickStartWizard;
	private boolean showImportDialog;
	private boolean showOpenProjectDialog;
	private boolean showSaveProjectDialog;
	private boolean showAboutDialog;
	private final ImString importPath = new ImString(IMPORT_PATH_CAPACITY);
	private final ImString openProjectPath = new ImString(IMPORT_PATH_CAPACITY);
	private final ImString saveProjectPath = new ImString(IMPORT_PATH_CAPACITY);
	private String projectDialogMessage = "";

	public MenuBarPanel(Runnable onCloseRequest, BeatBlockPanelVisibility panels, Runnable onOpenSmartAutoMap,
			Runnable onGenerateRhythmDrop, Runnable onResetLayout, Runnable onSaveLayout, Runnable onLoadLayout,
			Runnable onOpenQuickStartWizard) {
		this(onCloseRequest, panels, onOpenSmartAutoMap, onGenerateRhythmDrop, onResetLayout, onSaveLayout, onLoadLayout,
			onOpenQuickStartWizard, PresenterFactories.menuBarPresenter());
	}

	MenuBarPanel(Runnable onCloseRequest, BeatBlockPanelVisibility panels, Runnable onOpenSmartAutoMap,
			Runnable onGenerateRhythmDrop, Runnable onResetLayout, Runnable onSaveLayout, Runnable onLoadLayout,
			Runnable onOpenQuickStartWizard, MenuBarPresenter presenter) {
		this.presenter = presenter;
		this.onCloseRequest = onCloseRequest;
		this.panels = panels != null ? panels : new BeatBlockPanelVisibility();
		this.onOpenSmartAutoMap = onOpenSmartAutoMap != null ? onOpenSmartAutoMap : () -> {};
		this.onGenerateRhythmDrop = onGenerateRhythmDrop != null ? onGenerateRhythmDrop : () -> {};
		this.onResetLayout = onResetLayout != null ? onResetLayout : () -> {};
		this.onSaveLayout = onSaveLayout != null ? onSaveLayout : () -> {};
		this.onLoadLayout = onLoadLayout != null ? onLoadLayout : () -> {};
		this.onOpenQuickStartWizard = onOpenQuickStartWizard != null ? onOpenQuickStartWizard : () -> {};
	}

	public void render() {
		if (!ImGui.beginMainMenuBar()) return;
		try {
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.file"))) {
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.open_project"), "Ctrl+Shift+O")) {
					showOpenProjectDialog = true;
					projectDialogMessage = "";
					openProjectPath.set("");
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.save_project"), "Ctrl+S")) {
					showSaveProjectDialog = true;
					projectDialogMessage = "";
					saveProjectPath.set(presenter.defaultSaveProjectPath());
				}
				ImGui.separator();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.import_music"), "Ctrl+O")) {
					showImportDialog = true;
					importPath.set("");
				}
				ImGui.separator();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.close_beatblock"), "Esc")) {
					if (onCloseRequest != null) onCloseRequest.run();
				}
				ImGui.endMenu();
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.edit"))) {
				var undoRedo = presenter.undoRedoState();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.undo"), "Ctrl+Z", false, undoRedo.canUndo())) {
					presenter.undo();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.redo"), "Ctrl+Y", false, undoRedo.canRedo())) {
					presenter.redo();
				}
				ImGui.endMenu();
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.view"))) {
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.close_all_panels"))) {
					panels.closeAll();
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.open_all_panels"))) {
					panels.openAll();
				}
				ImGui.separator();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.reset_layout"))) {
					onResetLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.reset_layout"));
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.save_layout"))) {
					onSaveLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.save_layout"));
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.load_layout"))) {
					onLoadLayout.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.load_layout"));
				}
				ImGui.separator();
				if (ImGui.beginMenu(BBTexts.get("beatblock.menu.panels"))) {
					panelToggleItem(BBTexts.get("beatblock.panel.audio_analysis"), panels.audioAnalysis);
					panelToggleItem(BBTexts.get("beatblock.panel.tool"), panels.tool);
					panelToggleItem(BBTexts.get("beatblock.panel.marker_debug"), panels.marker);
					panelToggleItem(BBTexts.get("beatblock.panel.event_properties"), panels.eventProperties);
					panelToggleItem(BBTexts.get("beatblock.panel.camera_properties"), panels.cameraProperties);
					panelToggleItem(BBTexts.get("beatblock.panel.timeline"), panels.timeline);
					panelToggleItem(BBTexts.get("beatblock.panel.animation_library"), panels.animationLibrary);
					panelToggleItem(BBTexts.get("beatblock.panel.selection_properties"), panels.selectionProperties);
					panelToggleItem(BBTexts.get("beatblock.panel.layer"), panels.layer);
					panelToggleItem(BBTexts.get("beatblock.panel.rhythm_drop"), panels.rhythmDrop);
					ImGui.endMenu();
				}
				ImGui.endMenu();
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.show"))) {
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.smart_auto_map"), BBTexts.get("beatblock.menu.smart_auto_map_shortcut"))) {
					onOpenSmartAutoMap.run();
				}
				if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.tooltip.smart_auto_map"));
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.generate_rhythm_drop"), "Ctrl+Shift+D")) {
					onGenerateRhythmDrop.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.generate_rhythm_drop"));
				}
				ImGui.endMenu();
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.menu.help"))) {
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.quick_start_wizard"))) {
					onOpenQuickStartWizard.run();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.tooltip.quick_start_wizard"));
				}
				ImGui.separator();
				if (ImGui.menuItem(BBTexts.get("beatblock.menu.about"))) {
					showAboutDialog = true;
				}
				ImGui.endMenu();
			}
		} finally {
			ImGui.endMainMenuBar();
		}
		renderImportDialog();
		renderOpenProjectDialog();
		renderSaveProjectDialog();
		renderAboutDialog();
	}

	private static void panelToggleItem(String label, ImBoolean open) {
		boolean v = open.get();
		if (ImGui.menuItem(label, null, v)) {
			open.set(!v);
		}
	}

	private void renderImportDialog() {
		if (!showImportDialog) return;
		ImGui.setNextWindowSize(400, 0);
		if (ImGui.begin(BBTexts.get("beatblock.dialog.import_music"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(BBTexts.get("beatblock.dialog.wav_path"));
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##path", importPath);
			if (ImGui.button(BBTexts.get("beatblock.common.import"))) {
				var result = presenter.importAudio(importPath.get());
				if (result.ok()) {
					showImportDialog = false;
				}
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel"))) {
				showImportDialog = false;
			}
		}
		ImGui.end();
	}

	private void renderOpenProjectDialog() {
		if (!showOpenProjectDialog) return;
		ImGui.setNextWindowSize(460, 0);
		if (ImGui.begin(BBTexts.get("beatblock.dialog.open_project"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(BBTexts.get("beatblock.dialog.project_path"));
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##openOscPath", openProjectPath);
			if (ImGui.button(BBTexts.get("beatblock.common.open"))) {
				var result = presenter.openProject(openProjectPath.get());
				projectDialogMessage = result.messageOrEmpty();
				if (result.ok()) {
					showOpenProjectDialog = false;
				}
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##openOsc")) {
				showOpenProjectDialog = false;
			}
			if (!projectDialogMessage.isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(projectDialogMessage);
			}
		}
		ImGui.end();
	}

	private void renderSaveProjectDialog() {
		if (!showSaveProjectDialog) return;
		ImGui.setNextWindowSize(460, 0);
		if (ImGui.begin(BBTexts.get("beatblock.dialog.save_project"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(BBTexts.get("beatblock.dialog.save_path"));
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##saveOscPath", saveProjectPath);
			if (ImGui.button(BBTexts.get("beatblock.common.save"))) {
				var result = presenter.saveProject(saveProjectPath.get());
				projectDialogMessage = result.messageOrEmpty();
				if (result.ok()) {
					showSaveProjectDialog = false;
				}
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##saveOsc")) {
				showSaveProjectDialog = false;
			}
			if (!projectDialogMessage.isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(projectDialogMessage);
			}
		}
		ImGui.end();
	}

	private void renderAboutDialog() {
		if (!showAboutDialog) return;
		ImGui.setNextWindowSize(360, 0);
		if (ImGui.begin(BBTexts.get("beatblock.dialog.about"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(BBTexts.get("beatblock.common.brand"));
			ImGui.text(BBTexts.get("beatblock.about.tagline"));
			ImGui.spacing();
			ImGui.textWrapped(BBTexts.get("beatblock.about.description"));
			ImGui.spacing();
			ImGui.text(BBTexts.get("beatblock.about.powered_by"));
			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.common.ok"))) {
				showAboutDialog = false;
			}
		}
		ImGui.end();
	}
}
