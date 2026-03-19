package com.beatblock.timeline.rendering;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;

import com.beatblock.ui.icons.Icons;

/**
 * 轨道表头：固定宽度的折叠槽 + 树缩进槽（保证类型列左对齐）→ 类型 → 名称（左右边界对齐）→ 微竖线分隔 → 可见/锁定图标按钮。
 */
public final class TrackRenderer {

	/** 与二级轨道缩进同宽，一级组轨道在此槽留空，使类型列竖向对齐 */
	private static final float TREE_INDENT_W = 14f;
	private static final float PAD = 4f;
	private static final float FOLD_BTN = 18f;
	/** 折叠列总宽（无折叠时留空，宽度不变） */
	private static final float FOLD_COL_W = 22f;
	/** 「音频/动画/摄像机/事件」等类型列宽 */
	private static final float TYPE_COL_W = 50f;
	private static final float ICON_BTN = 20f;
	private static final float ICON_GAP = 2f;
	/** 列间微竖线（ABGR） */
	private static final int MICRO_SEP_COLOR = 0x55_66_66_66;
	private static final int TREE_GUIDE_COLOR = 0x44_55_55_55;

	/**
	 * @param trackHeaderWidth 左侧轨道头总宽（与可拖动分割线一致）
	 */
	public float drawTrackLabel(float rowY, int rowIndex, String displayName, boolean isGroup, TimelineTrackListState listState, float trackHeaderWidth) {
		ImGui.setCursorPosY(rowY);
		float headW = trackHeaderWidth > 0 ? trackHeaderWidth : TimelineLayout.TRACK_LABEL_WIDTH;

		// 类型列左缘：折叠槽 + 树缩进槽（组轨道缩进槽留空，与二级轨「空折叠槽+缩进」总宽一致）
		final float typeStartX = PAD + FOLD_COL_W + TREE_INDENT_W;
		final float nameX = typeStartX + TYPE_COL_W;

		final float iconBlockW = ICON_BTN * 2 + ICON_GAP;
		final float nameRight = headW - PAD - iconBlockW;
		float nameW = nameRight - nameX;
		if (nameW < 40f) {
			nameW = Math.max(24f, headW - nameX - PAD);
		}

		ImGui.setCursorPos(0f, rowY);
		final float rowOriginScreenX = ImGui.getCursorScreenPosX();
		final float rowOriginScreenY = ImGui.getCursorScreenPosY();
		final float rowH = TimelineLayout.ROW_HEIGHT;
		var dl = ImGui.getWindowDrawList();

		// —— 微竖线：折叠槽右缘 | 类型列右缘（名称左缘）| 名称区右缘（图标左）——
		float sep1 = rowOriginScreenX + PAD + FOLD_COL_W;
		float sep2 = rowOriginScreenX + nameX;
		float sep3 = rowOriginScreenX + nameRight;
		dl.addLine(sep1, rowOriginScreenY, sep1, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);
		dl.addLine(sep2, rowOriginScreenY, sep2, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);
		dl.addLine(sep3, rowOriginScreenY, sep3, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);

		// 二级轨道：树引导竖线（折叠槽内靠左）
		if (TimelineTrackMeta.hasParent(rowIndex)) {
			float gx = rowOriginScreenX + PAD + FOLD_COL_W * 0.5f;
			dl.addLine(gx, rowOriginScreenY + 3f, gx, rowOriginScreenY + rowH - 3f, TREE_GUIDE_COLOR, 1f);
		}

		float vbtn = (rowH - FOLD_BTN) * 0.5f;

		// —— 折叠槽：组显示按钮，其余留空 ——
		if (isGroup && listState != null) {
			float foldX = PAD + Math.max(0f, (FOLD_COL_W - FOLD_BTN) * 0.5f);
			ImGui.setCursorPos(foldX, rowY + vbtn);
			boolean collapsed = listState.isGroupCollapsed(rowIndex);
			ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f);
			if (ImGui.button((collapsed ? Icons.Timeline.TRACK_COLLAPSE : Icons.Timeline.TRACK_EXPAND) + "##fold" + rowIndex, FOLD_BTN, FOLD_BTN)) {
				listState.toggleGroupCollapsed(rowIndex);
			}
			ImGui.popStyleVar();
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(collapsed ? "Expand sub-tracks" : "Collapse sub-tracks");
			}
		}

		// —— 类型（左缘对齐）——
		ImGui.setCursorPos(typeStartX, rowY);
		ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.52f, 0.62f, 1f);
		ImGui.text(TimelineTrackMeta.getCategoryTypeLabel(rowIndex));
		ImGui.popStyleColor();

		boolean isEditing = listState != null && listState.getEditingRowIndex() == rowIndex;

		// —— 名称：固定左 nameX、右 nameRight ——
		ImGui.setCursorPos(nameX, rowY);
		if (isEditing && listState != null) {
			ImGui.setNextItemWidth(nameW);
			if (ImGui.inputText("##name" + rowIndex, listState.getRenameBuffer(), ImGuiInputTextFlags.EnterReturnsTrue)) {
				listState.finishEditing(true);
			}
			if (ImGui.isItemDeactivatedAfterEdit()) {
				listState.finishEditing(true);
			}
		} else {
			ImGui.invisibleButton("##nameHit" + rowIndex, nameW, rowH);
			boolean nameHovered = ImGui.isItemHovered();
			ImGui.setCursorPos(nameX, rowY);
			float clipX1 = ImGui.getCursorScreenPosX();
			float clipY1 = ImGui.getCursorScreenPosY();
			ImGui.pushClipRect(clipX1, clipY1, clipX1 + nameW, clipY1 + rowH, true);
			if (isGroup) {
				ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.85f, 0.7f, 1f);
			}
			ImGui.text(displayName);
			if (isGroup) {
				ImGui.popStyleColor();
			}
			ImGui.popClipRect();
			if (listState != null && nameHovered && ImGui.isMouseDoubleClicked(0)) {
				listState.startEditing(rowIndex);
			}
			if (listState != null && nameHovered) {
				ImGui.setTooltip("双击可修改轨道名称");
			}
		}

		// —— 可见 / 锁定：右对齐在轨道头右缘 ——
		if (listState != null && !isEditing) {
			float lockRight = headW - PAD;
			float lockX = lockRight - ICON_BTN;
			float visX = lockX - ICON_GAP - ICON_BTN;

			ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f);

			ImGui.setCursorPos(visX, rowY + vbtn);
			boolean vis = listState.isVisible(rowIndex);
			if (ImGui.button((vis ? Icons.EYE : Icons.Action.HIDDEN) + "##vis" + rowIndex, ICON_BTN, ICON_BTN)) {
				listState.toggleVisible(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(vis ? "可见 (点击隐藏)" : "隐藏 (点击显示)");
			}

			ImGui.setCursorPos(lockX, rowY + vbtn);
			boolean lock = listState.isLocked(rowIndex);
			if (ImGui.button((lock ? Icons.Action.LOCK : Icons.Action.UNLOCK) + "##lock" + rowIndex, ICON_BTN, ICON_BTN)) {
				listState.toggleLocked(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(lock ? "已锁定 (点击解锁)" : "未锁定 (点击锁定)");
			}

			ImGui.popStyleVar();
		}

		return isGroup ? rowY + 22f : rowY;
	}
}
