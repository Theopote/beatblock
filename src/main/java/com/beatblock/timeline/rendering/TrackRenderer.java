package com.beatblock.timeline.rendering;

import com.beatblock.ui.icons.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;

/**
 * 轨道表头：折叠槽 → 类型 → 名称 → 右对齐可见/锁定图标。
 *
 * 关键：所有布局（文字、按钮、裁剪、分段竖线）都使用屏幕坐标锚定，
 * 避免 setCursorPos 的相对坐标受 padding/滚动影响导致的“跑偏/被裁掉”。
 */
public final class TrackRenderer {

	// 紧凑布局参数
	private static final float PAD = 3f;
	private static final float FOLD_BTN = 16f;
	/** 折叠槽总宽（一级有按钮；二级留空但占位） */
	private static final float FOLD_COL_W = 20f;

	private static final float TYPE_COL_RIGHT_PAD = 6f;
	private static final float MIN_TYPE_COL_W = 34f;

	private static final float ICON_BTN = 18f;
	private static final float ICON_GAP = 1.5f;

	private static final int MICRO_SEP_COLOR = 0x55_66_66_66; // ABGR

	/**
	 * @param trackHeaderWidth 左侧轨道头总宽（与可拖动分割线一致）
	 */
	public float drawTrackLabel(
		float rowY,
		int rowIndex,
		String displayName,
		boolean isGroup,
		TimelineTrackListState listState,
		float trackHeaderLeft,
		float trackHeaderWidth
	) {
		ImGui.setCursorPosY(rowY);
		float baseX = trackHeaderLeft;
		float headW = trackHeaderWidth > 0 ? trackHeaderWidth : TimelineLayout.TRACK_LABEL_WIDTH;
		float rowH = TimelineLayout.ROW_HEIGHT;

		// 类型列宽：取四种类型里“最宽”的文字宽度（加右内边距）
		float maxTypeW = 0f;
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			String t = TimelineTrackMeta.getCategoryTypeLabel(i);
			if (t == null || t.isBlank()) continue;
			maxTypeW = Math.max(maxTypeW, ImGui.calcTextSize(t).x);
		}
		float typeColW = Math.max(MIN_TYPE_COL_W, maxTypeW + TYPE_COL_RIGHT_PAD);

		// 列边界（内部坐标：以“轨道头内部局部 X=0”为基准）
		float foldColLeft = PAD;
		float typeStartX = foldColLeft + FOLD_COL_W;
		float nameX = typeStartX + typeColW;

		float iconBlockW = ICON_BTN * 2 + ICON_GAP; // 可见+锁定
		float nameRight = headW - PAD - iconBlockW;   // 名称区右边界（对齐图标块左边）
		float nameW = nameRight - nameX;
		if (nameW < 24f) {
			// 轨道头太窄时，仍保证名称有最小显示/命中宽度
			nameW = 24f;
		}

		// 文本垂直居中偏移
		float textH = ImGui.getTextLineHeightWithSpacing();
		float textOffsetY = Math.max(0f, (rowH - textH) * 0.5f);

		// 屏幕锚点：强制把本行的“内部 X=0”放到已知位置，拿到行的屏幕坐标基准
		ImGui.setCursorPos(0f, rowY);
		float rowOriginScreenY = ImGui.getCursorScreenPosY();
		var dl = ImGui.getWindowDrawList();

		// 分段微竖线：折叠槽右缘 | 类型列右缘（=名称左缘）| 名称区右缘（=图标区左缘）
		float sepFold = baseX + foldColLeft + FOLD_COL_W;
		float sepType = baseX + nameX;
		float sepName = baseX + nameRight;
		dl.addLine(sepFold, rowOriginScreenY, sepFold, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);
		dl.addLine(sepType, rowOriginScreenY, sepType, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);
		dl.addLine(sepName, rowOriginScreenY, sepName, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);

		// —— 折叠槽：仅组轨道显示按钮；二级轨占位但无按钮 —— //
		if (isGroup && listState != null) {
			float vbtn = (rowH - FOLD_BTN) * 0.5f;
			float foldX = foldColLeft + (FOLD_COL_W - FOLD_BTN) * 0.5f;
			float btnX = baseX + foldX;
			float btnY = rowOriginScreenY + vbtn;

			ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f);
			ImGui.setCursorScreenPos(btnX, btnY);
			boolean collapsed = listState.isGroupCollapsed(rowIndex);
			// 折叠态：track-collapse（侧向）与字体一致；展开态：track-expand 在当前字体内易错配，改用通用 collapse（向下收起）
			if (ImGui.button((collapsed ? Icons.Timeline.TRACK_COLLAPSE : Icons.Action.COLLAPSE) + "##fold" + rowIndex, FOLD_BTN, FOLD_BTN)) {
				listState.toggleGroupCollapsed(rowIndex);
			}
			ImGui.popStyleVar();

			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(collapsed ? "Expand sub-tracks" : "Collapse sub-tracks");
			}
		}

		// —— 类型：左缘对齐（上下一致）—— //
		ImGui.setCursorScreenPos(baseX + typeStartX, rowOriginScreenY + textOffsetY);
		ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.52f, 0.62f, 1f);
		ImGui.text(TimelineTrackMeta.getCategoryTypeLabel(rowIndex));
		ImGui.popStyleColor();

		boolean isEditing = listState != null && listState.getEditingRowIndex() == rowIndex;

		// —— 名称：左右边界固定（上下用文本居中偏移）—— //
		if (isEditing && listState != null) {
			ImGui.setCursorScreenPos(baseX + nameX, rowOriginScreenY);
			ImGui.setNextItemWidth(nameW);
			if (ImGui.inputText("##name" + rowIndex, listState.getRenameBuffer(), ImGuiInputTextFlags.EnterReturnsTrue)) {
				listState.finishEditing(true);
			}
			if (ImGui.isItemDeactivatedAfterEdit()) {
				listState.finishEditing(true);
			}
		} else {
			// 命中区：名称区内全区域可双击改名
			ImGui.setCursorScreenPos(baseX + nameX, rowOriginScreenY);
			ImGui.invisibleButton("##nameHit" + rowIndex, nameW, rowH);
			boolean nameHovered = ImGui.isItemHovered();

			// 裁剪：确保文字在名称区内展示，不会“跑出去”
			float clipX1 = baseX + nameX;
			float clipY1 = rowOriginScreenY;
			ImGui.pushClipRect(clipX1, clipY1, clipX1 + nameW, clipY1 + rowH, true);

			ImGui.setCursorScreenPos(clipX1, rowOriginScreenY + textOffsetY);
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

		// —— 可见 / 锁定：右对齐，纯图标按钮 —— //
		if (listState != null && !isEditing) {
			float vbtn = (rowH - ICON_BTN) * 0.5f;

			float lockRight = headW - PAD;
			float lockX = lockRight - ICON_BTN;
			float visX = lockX - ICON_GAP - ICON_BTN;

			ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2f, 2f);

			boolean vis = listState.isVisible(rowIndex);
			ImGui.setCursorScreenPos(baseX + visX, rowOriginScreenY + vbtn);
			if (ImGui.button((vis ? Icons.EYE : Icons.Action.HIDDEN) + "##vis" + rowIndex, ICON_BTN, ICON_BTN)) {
				listState.toggleVisible(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(vis ? "可见 (点击隐藏)" : "隐藏 (点击显示)");
			}

			boolean lock = listState.isLocked(rowIndex);
			ImGui.setCursorScreenPos(baseX + lockX, rowOriginScreenY + vbtn);
			if (ImGui.button((lock ? Icons.Action.LOCK : Icons.Action.UNLOCK) + "##lock" + rowIndex, ICON_BTN, ICON_BTN)) {
				listState.toggleLocked(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(lock ? "已锁定 (点击解锁)" : "未锁定 (点击锁定)");
			}

			ImGui.popStyleVar();
		}

		return rowY + rowH;
	}
}

