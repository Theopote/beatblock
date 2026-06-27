package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.preferences.BeatBlockShortcutId;
import com.beatblock.ui.preferences.UiPreferences;
import com.beatblock.ui.preferences.UiTheme;
import com.beatblock.ui.presenter.ProjectTemplatePresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.EnumMap;

/** 偏好设置：主题、快捷键、工程模板。 */
public final class PreferencesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int CHORD_CAPACITY = 64;

	private final ProjectTemplatePresenter templatePresenter;
	private final ImInt themeIndex = new ImInt(0);
	private final EnumMap<BeatBlockShortcutId, ImString> shortcutBuffers = new EnumMap<>(BeatBlockShortcutId.class);
	private boolean buffersInitialized;

	public PreferencesPanel() {
		this(PresenterFactories.projectTemplatePresenter());
	}

	PreferencesPanel(ProjectTemplatePresenter templatePresenter) {
		this.templatePresenter = templatePresenter;
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			shortcutBuffers.put(id, new ImString(CHORD_CAPACITY));
		}
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.preferencesWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.preferencesWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ensureBuffers();
			ImGui.text(BBTexts.get("beatblock.preferences.title"));
			ImGui.separator();
			if (ImGui.beginTabBar("##preferencesTabs")) {
				if (ImGui.beginTabItem(BBTexts.get("beatblock.preferences.tab.theme"))) {
					renderThemeTab();
					ImGui.endTabItem();
				}
				if (ImGui.beginTabItem(BBTexts.get("beatblock.preferences.tab.shortcuts"))) {
					renderShortcutsTab();
					ImGui.endTabItem();
				}
				if (ImGui.beginTabItem(BBTexts.get("beatblock.preferences.tab.templates"))) {
					renderTemplatesTab();
					ImGui.endTabItem();
				}
				ImGui.endTabBar();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.preferencesWindow());
		}
	}

	private void ensureBuffers() {
		if (buffersInitialized) {
			return;
		}
		buffersInitialized = true;
		themeIndex.set(themeToIndex(UiPreferences.theme()));
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			shortcutBuffers.get(id).set(UiPreferences.shortcut(id));
		}
	}

	private void renderThemeTab() {
		ImGui.textWrapped(BBTexts.get("beatblock.preferences.theme.desc"));
		ImGui.spacing();
		String[] labels = BBTexts.labels(
			"beatblock.preferences.theme.dark",
			"beatblock.preferences.theme.light",
			"beatblock.preferences.theme.high_contrast"
		);
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo(BBTexts.get("beatblock.preferences.theme.label") + "##uiTheme", themeIndex, labels)) {
			UiPreferences.setTheme(indexToTheme(themeIndex.get()));
			ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.preferences.theme.applied"));
		}
	}

	private void renderShortcutsTab() {
		ImGui.textWrapped(BBTexts.get("beatblock.preferences.shortcuts.desc"));
		ImGui.spacing();
		if (ImGui.beginChild("##shortcutList", 0, -40f, true)) {
			for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
				ImGui.text(BBTexts.get("beatblock.preferences.shortcut." + id.id()));
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("##shortcut_" + id.id(), shortcutBuffers.get(id));
			}
		}
		ImGui.endChild();
		if (ImGui.button(BBTexts.get("beatblock.preferences.shortcuts.save") + "##saveShortcuts", -1f, 0f)) {
			for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
				UiPreferences.setShortcut(id, shortcutBuffers.get(id).get());
			}
			ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.preferences.shortcuts.saved"));
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.preferences.shortcuts.reset") + "##resetShortcuts")) {
			UiPreferences.resetShortcuts();
			for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
				shortcutBuffers.get(id).set(UiPreferences.shortcut(id));
			}
			ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.preferences.shortcuts.reset_done"));
		}
	}

	private void renderTemplatesTab() {
		ImGui.textWrapped(BBTexts.get("beatblock.preferences.templates.desc"));
		ImGui.spacing();
		for (ProjectTemplatePresenter.TemplateId templateId : ProjectTemplatePresenter.TemplateId.values()) {
			ImGui.separator();
			ImGui.text(BBTexts.get(ProjectTemplatePresenter.labelKey(templateId)));
			ImGui.textWrapped(BBTexts.get(ProjectTemplatePresenter.descriptionKey(templateId)));
			if (ImGui.button(BBTexts.get("beatblock.preferences.templates.apply") + "##tpl_" + templateId.name())) {
				var outcome = templatePresenter.apply(templateId);
				if (outcome.success()) {
					ToastNotificationSystem.showSuccess(outcome.message());
				} else {
					ToastNotificationSystem.showError(outcome.message());
				}
			}
		}
	}

	private static int themeToIndex(UiTheme theme) {
		return switch (theme) {
			case LIGHT -> 1;
			case HIGH_CONTRAST -> 2;
			default -> 0;
		};
	}

	private static UiTheme indexToTheme(int index) {
		return switch (index) {
			case 1 -> UiTheme.LIGHT;
			case 2 -> UiTheme.HIGH_CONTRAST;
			default -> UiTheme.DARK;
		};
	}
}
