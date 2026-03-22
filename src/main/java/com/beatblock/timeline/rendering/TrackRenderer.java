package com.beatblock.timeline.rendering;

import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
/**
 * 轨道表头：折叠槽 → 类型 → 名称 → 右对齐可见/锁定图标。
 *
 * 关键：所有布局（文字、按钮、裁剪、分段竖线）都使用屏幕坐标锚定，
 * 避免 setCursorPos 的相对坐标受 padding/滚动影响导致的“跑偏/被裁掉”。
 */
public final class TrackRenderer {

	// 紧凑布局参数（图标按钮边长 = 轨道行高，与行对齐）
	/** 轨道头右侧留白（可见/锁定与右缘）；折叠列左侧为 0，与内容区左缘对齐 */
	private static final float PAD = 3f;

	private static final float TYPE_COL_RIGHT_PAD = 6f;
	private static final float MIN_TYPE_COL_W = 34f;

	/** 可见/锁定 两枚按钮之间的间隙 */
	private static final float ICON_GAP = 2f;
	/** 左侧安全内边距，避免折叠按钮贴边被窗口裁剪。 */
	private static final float LEFT_INSET = 8f;

	private static final int MICRO_SEP_COLOR = 0x55_66_66_66; // ABGR

	/**
	 * @param trackHeaderWidth 左侧轨道头总宽（与可拖动分割线一致）
	 */
	public float drawTrackLabel(
		float rowY,
		float rowHeight,
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
		float rowH = Math.max(14f, rowHeight);
		/** 折叠槽宽 = 行高，折叠按钮与轨道行同高同宽 */
		float foldColW = rowH;
		float iconBtn = rowH;

		// 类型列宽：取四种类型里“最宽”的文字宽度（加右内边距）
		float maxTypeW = 0f;
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			String t = TimelineTrackMeta.getCategoryTypeLabel(i);
			if (t == null || t.isBlank()) continue;
			maxTypeW = Math.max(maxTypeW, ImGui.calcTextSize(t).x);
		}
		float typeColW = Math.max(MIN_TYPE_COL_W, maxTypeW + TYPE_COL_RIGHT_PAD);

		// 列边界（内部坐标：以“轨道头内部局部 X=0”为基准）
		float foldColLeft = LEFT_INSET;
		float typeStartX = foldColLeft + foldColW;
		float nameX = typeStartX + typeColW;

		float iconBlockW = iconBtn * 2 + ICON_GAP; // 可见+锁定
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
		float sepFold = baseX + foldColLeft + foldColW;
		float sepType = baseX + nameX;
		float sepName = baseX + nameRight;
		dl.addLine(sepFold, rowOriginScreenY, sepFold, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);
		dl.addLine(sepType, rowOriginScreenY, sepType, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);
		dl.addLine(sepName, rowOriginScreenY, sepName, rowOriginScreenY + rowH, MICRO_SEP_COLOR, 1f);

		// —— 折叠槽：仅组轨道显示按钮；二级轨占位但无按钮 —— //
		if (isGroup && listState != null) {
			float btnX = baseX + foldColLeft;
			float btnY = rowOriginScreenY;
			String foldTooltip = null;

			IconButtonStyle.pushBeatBlockIconButton();
			ImGui.setCursorScreenPos(btnX, btnY);
			boolean collapsed = listState.isGroupCollapsed(rowIndex);
			// 折叠态：track-collapse（侧向）与字体一致；展开态：track-expand 在当前字体内易错配，改用通用 collapse（向下收起）
			if (ImGui.button((collapsed ? Icons.Timeline.TRACK_COLLAPSE : Icons.Action.COLLAPSE) + "##fold" + rowIndex, rowH, rowH)) {
				listState.toggleGroupCollapsed(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				foldTooltip = collapsed ? "Expand sub-tracks" : "Collapse sub-tracks";
			}
			IconButtonStyle.popBeatBlockIconButton();
			if (foldTooltip != null) {
				ImGui.setTooltip(foldTooltip);
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
				if (rowIndex >= TimelineTrackMeta.ROW_WAVEFORM && rowIndex <= TimelineTrackMeta.ROW_FREQ_HIGH) {
					ImGui.setTooltip("双击可修改轨道名称\nAlt+滚轮：调整音频轨高度\nAlt+中键：重置音频轨高度");
				} else {
					ImGui.setTooltip("双击可修改轨道名称");
				}
			}
		}

		// —— 可见 / 锁定：右对齐，纯图标按钮 —— //
		if (listState != null && !isEditing) {
			float lockRight = headW - PAD - iconBtn;
			float visX = lockRight - ICON_GAP - iconBtn;
			String visTooltip = null;
			String lockTooltip = null;

			IconButtonStyle.pushBeatBlockIconButton();

			boolean vis = listState.isVisible(rowIndex);
			ImGui.setCursorScreenPos(baseX + visX, rowOriginScreenY);
			if (ImGui.button((vis ? Icons.EYE : Icons.Action.HIDDEN) + "##vis" + rowIndex, iconBtn, iconBtn)) {
				listState.toggleVisible(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				visTooltip = vis ? "可见 (点击隐藏)" : "隐藏 (点击显示)";
			}

			boolean lock = listState.isLocked(rowIndex);
			ImGui.setCursorScreenPos(baseX + lockRight, rowOriginScreenY);
			if (ImGui.button((lock ? Icons.Action.LOCK : Icons.Action.UNLOCK) + "##lock" + rowIndex, iconBtn, iconBtn)) {
				listState.toggleLocked(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				lockTooltip = lock ? "已锁定 (点击解锁)" : "未锁定 (点击锁定)";
			}

			IconButtonStyle.popBeatBlockIconButton();
			if (visTooltip != null) ImGui.setTooltip(visTooltip);
			if (lockTooltip != null) ImGui.setTooltip(lockTooltip);
		}

		return rowY + rowH;
	}
}

