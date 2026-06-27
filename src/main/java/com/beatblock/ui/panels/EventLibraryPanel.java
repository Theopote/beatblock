package com.beatblock.ui.panels;

import com.beatblock.ui.eventlibrary.EventTemplate;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.EventLibraryPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.Locale;

/** 事件库：保存/复用动画事件配置模板。 */
public final class EventLibraryPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int NAME_CAPACITY = 128;

	private final EventLibraryPanelPresenter presenter;
	private final ImString saveNameBuffer = new ImString(NAME_CAPACITY);

	public EventLibraryPanel() {
		this(PresenterFactories.eventLibraryPanelPresenter());
	}

	EventLibraryPanel(EventLibraryPanelPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.eventLibraryWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.eventLibraryWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			var state = presenter.viewState();
			ImGui.text(BBTexts.get("beatblock.event_library.title"));
			ImGui.separator();
			ImGui.textWrapped(BBTexts.get("beatblock.event_library.hint"));

			if (!state.editorReady()) {
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
				return;
			}

			renderSaveSection(state);
			ImGui.separator();
			renderTemplateList(state.templates());
			if (!state.statusMessage().isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(state.statusMessage());
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.eventLibraryWindow());
		}
	}

	private void renderSaveSection(EventLibraryPanelPresenter.ViewState state) {
		ImGui.text(BBTexts.get("beatblock.event_library.save_section"));
		if (state.hasSelection()) {
			ImGui.textDisabled(state.selectedEventSummary());
		} else {
			ImGui.textDisabled(BBTexts.get("beatblock.event_library.no_selection"));
		}
		ImGui.setNextItemWidth(-120f);
		ImGui.inputTextWithHint("##eventLibName", BBTexts.get("beatblock.event_library.name_hint"), saveNameBuffer);
		ImGui.sameLine();
		if (!state.hasSelection()) ImGui.beginDisabled();
		if (ImGui.button(BBTexts.get("beatblock.event_library.save") + "##eventLibSave")) {
			var outcome = presenter.saveFromSelection(saveNameBuffer.get());
			notify(outcome);
			if (outcome.success()) {
				saveNameBuffer.set("");
			}
		}
		if (!state.hasSelection()) ImGui.endDisabled();
	}

	private void renderTemplateList(java.util.List<EventTemplate> templates) {
		ImGui.text(BBTexts.get("beatblock.event_library.list_section", templates.size()));
		if (templates.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.event_library.empty"));
			return;
		}
		if (ImGui.beginChild("##eventLibList", 0, 0, true)) {
			for (EventTemplate template : templates) {
				renderTemplateRow(template);
			}
		}
		ImGui.endChild();
	}

	private void renderTemplateRow(EventTemplate template) {
		String label = String.format(Locale.ROOT, "%s · %s (%.2fs, E=%.2f)##eventTpl_%s",
			template.name(),
			template.animationTypeId(),
			template.durationSeconds(),
			template.energy(),
			template.id());
		ImGui.text(label);
		ImGui.sameLine();
		if (ImGui.smallButton(BBTexts.get("beatblock.event_library.apply") + "##apply_" + template.id())) {
			var outcome = presenter.applyTemplate(template.id());
			notify(outcome);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event_library.apply.tooltip"));
		}
		ImGui.sameLine();
		if (ImGui.smallButton(BBTexts.get("beatblock.common.delete") + "##del_" + template.id())) {
			var outcome = presenter.deleteTemplate(template.id());
			notify(outcome);
		}
	}

	private static void notify(EventLibraryPanelPresenter.ApplyOutcome outcome) {
		if (outcome.message() == null || outcome.message().isBlank()) {
			return;
		}
		if (outcome.success()) {
			ToastNotificationSystem.showSuccess(outcome.message());
		} else {
			ToastNotificationSystem.showError(outcome.message());
		}
	}
}
