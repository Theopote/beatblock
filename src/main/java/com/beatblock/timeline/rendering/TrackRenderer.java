package com.beatblock.timeline.rendering;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;

/**
 * 绘制轨道列表左侧：轨道名称（开头、可自定义）、子轨道缩进；可见 👁、锁定 🔒；与右侧内容区有分界线。
 */
public final class TrackRenderer {

	private static final float CHILD_INDENT_PX = 14f;

	/** 可见、锁定图标（与其他软件时间线一致） */
	private static final String ICON_VISIBLE = "\uD83D\uDC41";
	private static final String ICON_LOCK = "\uD83D\uDD12";

	/**
	 * 绘制一行：开头为轨道名称（可自定义，子轨道缩进），然后 [👁][🔒]。
	 * @param listState 可为 null，为 null 时不绘制可见/锁定及自定义名。
	 */
	public float drawTrackLabel(float rowY, int rowIndex, String displayName, boolean isGroup, TimelineTrackListState listState) {
		ImGui.setCursorPosY(rowY);
		float nameStartX = 4f;
		// 组轨道：左侧折叠/展开箭头，点击可折叠子轨道
		if (isGroup && listState != null) {
			boolean collapsed = listState.isGroupCollapsed(rowIndex);
			ImGui.setCursorPosX(4f);
			ImGui.text(collapsed ? "\u25B6" : "\u25BC"); // ▶ / ▼
			if (ImGui.isItemClicked(0)) {
				listState.toggleGroupCollapsed(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(collapsed ? "展开子轨道" : "折叠子轨道");
			}
			ImGui.sameLine();
			nameStartX = ImGui.getCursorPosX();
		} else if (TimelineTrackMeta.hasParent(rowIndex)) {
			nameStartX += CHILD_INDENT_PX;
		}
		ImGui.setCursorPosX(nameStartX);

		boolean isEditing = listState != null && listState.getEditingRowIndex() == rowIndex;
		if (isEditing && listState != null) {
			ImGui.setNextItemWidth(-1);
			if (ImGui.inputText("##name" + rowIndex, listState.getRenameBuffer(), ImGuiInputTextFlags.EnterReturnsTrue)) {
				listState.finishEditing(true);
			}
			if (ImGui.isItemDeactivatedAfterEdit()) {
				listState.finishEditing(true);
			}
		} else {
			if (isGroup) {
				ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.85f, 0.7f, 1f);
			}
			ImGui.text(displayName);
			if (isGroup) {
				ImGui.popStyleColor();
			}
			if (listState != null && ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
				listState.startEditing(rowIndex);
			}
			if (listState != null && ImGui.isItemHovered()) {
				ImGui.setTooltip("双击可修改轨道名称");
			}
		}

		// 可见、锁定在名称右侧
		if (listState != null && !isEditing) {
			ImGui.sameLine();
			boolean vis = listState.isVisible(rowIndex);
			ImGui.pushStyleColor(ImGuiCol.Text, vis ? 0.9f : 0.45f, vis ? 0.9f : 0.45f, vis ? 0.9f : 0.45f, 1f);
			if (ImGui.checkbox(ICON_VISIBLE + "##vis" + rowIndex, vis)) {
				listState.toggleVisible(rowIndex);
			}
			ImGui.popStyleColor();
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(vis ? "可见 (点击隐藏)" : "隐藏 (点击显示)");
			}
			ImGui.sameLine();
			boolean lock = listState.isLocked(rowIndex);
			ImGui.pushStyleColor(ImGuiCol.Text, lock ? 0.95f : 0.5f, lock ? 0.6f : 0.5f, lock ? 0.6f : 0.5f, 1f);
			if (ImGui.checkbox(ICON_LOCK + "##lock" + rowIndex, lock)) {
				listState.toggleLocked(rowIndex);
			}
			ImGui.popStyleColor();
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(lock ? "已锁定 (点击解锁)" : "未锁定 (点击锁定)");
			}
		}
		return isGroup ? rowY + 22f : rowY;
	}
}
