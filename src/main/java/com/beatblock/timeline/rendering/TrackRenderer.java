package com.beatblock.timeline.rendering;

import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * 绘制轨道列表左侧：可见 👁、锁定 🔒、轨道名称；与右侧内容区有分界线。
 */
public final class TrackRenderer {

	/** 可见、锁定图标（与其他软件时间线一致） */
	private static final String ICON_VISIBLE = "\uD83D\uDC41";
	private static final String ICON_LOCK = "\uD83D\uDD12";

	/**
	 * 绘制一行：左侧 [👁 可见][🔒 锁定] 轨道名。
	 * @param listState 可为 null，为 null 时不绘制可见/锁定控件。
	 */
	public float drawTrackLabel(float rowY, int rowIndex, String label, boolean isGroup, TimelineTrackListState listState) {
		ImGui.setCursorPosY(rowY);
		ImGui.setCursorPosX(4);

		if (listState != null) {
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
			ImGui.sameLine();
		}

		if (isGroup) {
			ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.85f, 0.7f, 1f);
		}
		ImGui.text(label);
		if (isGroup) {
			ImGui.popStyleColor();
		}
		return isGroup ? rowY + 22f : rowY;
	}
}
