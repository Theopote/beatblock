package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/** 时间线工具栏：复制 / 粘贴 / 删除。 */
final class TimelineToolbarEditControls {

	void renderInline(TimelineEditor editor) {
		if (editor == null) return;

		boolean hasSelection = editor.hasTimelineSelection();
		boolean hasClipboard = editor.hasClipboardContent();
		boolean canDelete = editor.hasDeletableSelection();

		if (!hasSelection) ImGui.beginDisabled();
		if (ImGui.button(BBTexts.get("beatblock.common.copy") + "##tlCopy")) {
			editor.copySelectedEvents();
		}
		if (!hasSelection) ImGui.endDisabled();
		if (ImGui.isItemHovered()) ImGui.setTooltip("Ctrl+C");

		TimelineToolbarImGui.nextItemInGroup();

		if (!hasClipboard) ImGui.beginDisabled();
		if (ImGui.button(BBTexts.get("beatblock.common.paste") + "##tlPaste")) {
			editor.pasteClipboardAtPlayhead();
		}
		if (!hasClipboard) ImGui.endDisabled();
		if (ImGui.isItemHovered()) ImGui.setTooltip("Ctrl+V");

		TimelineToolbarImGui.nextItemInGroup();

		if (!canDelete) ImGui.beginDisabled();
		if (ImGui.button(BBTexts.get("beatblock.common.delete") + "##tlDelete")) {
			editor.deleteSelectedEntries();
		}
		if (!canDelete) ImGui.endDisabled();
		if (ImGui.isItemHovered()) ImGui.setTooltip("Delete");
	}

	void renderCompact(TimelineEditor editor) {
		if (editor == null) return;
		ImGui.separator();
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.edit_tools"));
		renderInline(editor);
	}
}
