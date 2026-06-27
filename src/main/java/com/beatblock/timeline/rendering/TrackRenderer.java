package com.beatblock.timeline.rendering;

import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
/**
 * 轨道表头：折叠槽 → 类型 → 名称 → 右对齐可见/锁定图标。
 * <p>
 * 关键：所有布局（文字、按钮、裁剪、分段竖线）都使用屏幕坐标锚定，
 * 避免 setCursorPos 的相对坐标受 padding/滚动影响导致的“跑偏/被裁掉”。
 */
public final class TrackRenderer {

	// 紧凑布局参数（图标按钮边长 = 轨道行高，与行对齐）
	/** 轨道头右侧留白（可见/锁定与右缘）；保持紧凑，播放区越界由内容裁剪负责。 */
	private static final float PAD = 3f;

	private static final float TYPE_COL_RIGHT_PAD = 6f;
	private static final float MIN_TYPE_COL_W = 34f;

	/** 可见/锁定 两枚按钮之间的间隙 */
	private static final float ICON_GAP = 2f;
	/**
	 * 左侧额外缩进。
	 * 轨道头本身已经处在 ImGui 内容区内，再加额外 inset 会产生“二次边距”，
	 * 让折叠按钮、类型列、名称列整体向右偏移。
	 */
	private static final float LEFT_INSET = 0f;
	private static final float TEXT_INSET = 4f;
	private static final String[] TYPE_LABEL_KEYS = {
		"beatblock.track.type.audio",
		"beatblock.track.type.feature",
		"beatblock.track.type.animation",
		"beatblock.track.type.camera",
		"beatblock.track.type.event"
	};

	private static final int MICRO_SEP_COLOR = 0x55_66_66_66; // ABGR
	private static final float STATE_ACTIVE_GREEN_R = 0.20f;
	private static final float STATE_ACTIVE_GREEN_G = 0.95f;
	private static final float STATE_ACTIVE_GREEN_B = 0.40f;
	private static final float STATE_ALERT_RED_R = 0.95f;
	private static final float STATE_ALERT_RED_G = 0.35f;
	private static final float STATE_ALERT_RED_B = 0.35f;
	private static final float STATE_BG_ALPHA = 0.22f;
	private static final float STATE_BG_HOVER_ALPHA = 0.30f;
	private static final float STATE_BG_ACTIVE_ALPHA = 0.36f;
	private static final float[] GROUP_AUDIO_TEXT = {0.72f, 0.90f, 0.82f};
	private static final float[] GROUP_FEATURE_TEXT = {0.74f, 0.84f, 0.97f};
	private static final float[] GROUP_ACTION_TEXT = {0.95f, 0.84f, 0.66f};
	private float cachedTypeColumnWidth = -1f;

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
		float trackHeaderWidth,
		boolean showMuteSolo,
		String typeLabel,
		Timeline timeline,
		TimelineClock playheadClock
	) {
		ImGui.setCursorPosY(rowY);
		float baseX = trackHeaderLeft;
		float headW = trackHeaderWidth > 0 ? trackHeaderWidth : TimelineLayout.TRACK_LABEL_WIDTH;
		float rowH = Math.max(TimelineLayout.ROW_HEIGHT, rowHeight);
		/** 折叠槽固定宽度：与默认行高一致，避免音频轨增高时左侧布局被撑宽 */
		float foldColW = TimelineLayout.ROW_HEIGHT;
		float iconBtn = TimelineLayout.ROW_HEIGHT;
		float iconOffsetY = Math.max(0f, (rowH - iconBtn) * 0.5f);
		float foldIconOffsetX = Math.max(0f, (foldColW - iconBtn) * 0.5f);

		// 类型列宽：取四种类型里“最宽”的文字宽度（加右内边距）
		float typeColW = resolveTypeColumnWidth();

		// 列边界（内部坐标：以“轨道头内部局部 X=0”为基准）
		float foldColLeft = LEFT_INSET;
		float typeStartX = foldColLeft + foldColW;
		float nameX = typeStartX + typeColW;

		int iconCount = showMuteSolo ? 4 : 2;
		if (!showMuteSolo && rowIndex == TimelineTrackMeta.ROW_CAMERA) {
			iconCount = 3;
		}
		float iconBlockW = iconBtn * iconCount + ICON_GAP * (iconCount - 1);
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
			float btnX = baseX + foldColLeft + foldIconOffsetX;
			float btnY = rowOriginScreenY + iconOffsetY;
			String foldTooltip = null;

			IconButtonStyle.pushBeatBlockIconButton();
			ImGui.setCursorScreenPos(btnX, btnY);
			boolean collapsed = listState.isGroupCollapsed(rowIndex);
			// 折叠态：track-collapse（侧向）与字体一致；展开态：track-expand 在当前字体内易错配，改用通用 collapse（向下收起）
			if (ImGui.button((collapsed ? Icons.Timeline.TRACK_COLLAPSE : Icons.Action.COLLAPSE) + "##fold" + rowIndex, rowH, rowH)) {
				listState.toggleGroupCollapsed(rowIndex);
			}
			if (ImGui.isItemHovered()) {
				foldTooltip = collapsed
					? BBTexts.get("beatblock.track.tooltip.expand")
					: BBTexts.get("beatblock.track.tooltip.collapse");
			}
			IconButtonStyle.popBeatBlockIconButton();
			if (foldTooltip != null) {
				ImGui.setTooltip(foldTooltip);
			}
		}

		// —— 类型：左缘对齐（上下一致）—— //
		String resolvedTypeLabel = (typeLabel != null && !typeLabel.isBlank())
			? typeLabel : TimelineTrackMeta.getCategoryTypeLabel(rowIndex);
		ImGui.setCursorScreenPos(baseX + typeStartX + TEXT_INSET, rowOriginScreenY + textOffsetY);
		ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.52f, 0.62f, 1f);
		ImGui.text(resolvedTypeLabel);
		ImGui.popStyleColor();

		boolean isEditing = listState != null && listState.getEditingRowIndex() == rowIndex;

		// —— 名称：左右边界固定（上下用文本居中偏移）—— //
		float nameTextX = baseX + nameX + TEXT_INSET;
		float nameTextW = Math.max(8f, nameW - TEXT_INSET);
        ImGui.setCursorScreenPos(nameTextX, rowOriginScreenY);
        if (isEditing) {
            ImGui.setNextItemWidth(nameTextW);
			boolean commitByEnter = ImGui.inputText("##name" + rowIndex, listState.getRenameBuffer(), ImGuiInputTextFlags.EnterReturnsTrue);
			boolean nameItemHovered = ImGui.isItemHovered();
			boolean nameItemActive = ImGui.isItemActive();
			if (commitByEnter) {
				listState.finishEditing(true);
			}
			if (!commitByEnter && ImGui.isItemDeactivated()) {
				listState.finishEditing(false);
			}
			if (!commitByEnter && !nameItemActive && !nameItemHovered && ImGui.isMouseClicked(0)) {
				listState.finishEditing(false);
			}
			if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
				listState.finishEditing(false);
			}
		} else {
			// 命中区：名称区内全区域可双击改名
            ImGui.invisibleButton("##nameHit" + rowIndex, nameTextW, rowH);
			boolean nameHovered = ImGui.isItemHovered();

			// 裁剪：确保文字在名称区内展示，不会“跑出去”
			float clipX1 = nameTextX;
			float clipY1 = rowOriginScreenY;
			ImGui.pushClipRect(clipX1, clipY1, clipX1 + nameTextW, clipY1 + rowH, true);

			ImGui.setCursorScreenPos(clipX1, rowOriginScreenY + textOffsetY);
			boolean isAudioControlLane = TimelineTrackMeta.isAudioSubRow(rowIndex);
			boolean isReferenceLane = rowIndex == TimelineTrackMeta.ROW_ANIMATION_GROUP;
			float[] groupColor = resolveGroupTitleColor(rowIndex);
			if (isGroup) {
				if (groupColor != null) {
					ImGui.pushStyleColor(ImGuiCol.Text, groupColor[0], groupColor[1], groupColor[2], 1f);
				} else {
					ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.85f, 0.7f, 1f);
				}
			} else if (isAudioControlLane) {
				ImGui.pushStyleColor(ImGuiCol.Text, 0.70f, 0.86f, 0.78f, 1f);
			} else if (isReferenceLane) {
				ImGui.pushStyleColor(ImGuiCol.Text, 0.66f, 0.76f, 0.92f, 1f);
			}
			ImGui.text(displayName);
			if (isGroup || isAudioControlLane || isReferenceLane) {
				ImGui.popStyleColor();
			}
			ImGui.popClipRect();

			if (listState != null && nameHovered && ImGui.isMouseDoubleClicked(0)) {
				listState.startEditing(rowIndex);
			}
			if (listState != null && nameHovered) {
				if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
					ImGui.setTooltip(BBTexts.get("beatblock.track.tooltip.rename_audio"));
				} else {
					ImGui.setTooltip(BBTexts.get("beatblock.track.tooltip.rename"));
				}
			}
		}

		// —— 静音 / 独奏 / 可见 / 锁定：右对齐，纯图标按钮 —— //
		if (listState != null && !isEditing) {
			float lockX = headW - PAD - iconBtn;
			float visX  = lockX - ICON_GAP - iconBtn;
			float camKfX = visX - ICON_GAP - iconBtn;
			float soloX = visX  - ICON_GAP - iconBtn;
			float muteX = soloX - ICON_GAP - iconBtn;
			String muteTooltip = null;
			String soloTooltip = null;
			String visTooltip = null;
			String camKfTooltip = null;
			String lockTooltip = null;

			IconButtonStyle.pushBeatBlockIconButton();

			if (showMuteSolo) {
				// ── 静音按钮 ────────────────────────────────────────────
				boolean muted = listState.isMuted(rowIndex);
				if (muted) pushStateButtonStyle(STATE_ALERT_RED_R, STATE_ALERT_RED_G, STATE_ALERT_RED_B);
				ImGui.setCursorScreenPos(baseX + muteX, rowOriginScreenY + iconOffsetY);
				if (ImGui.button((muted ? Icons.Timeline.MUTE : Icons.Audio.VOLUME) + "##mute" + rowIndex, iconBtn, iconBtn)) {
					listState.toggleMuted(rowIndex);
				}
				if (muted) popStateButtonStyle();
				if (ImGui.isItemHovered()) {
					muteTooltip = muted
						? BBTexts.get("beatblock.track.tooltip.mute_on")
						: BBTexts.get("beatblock.track.tooltip.mute_off");
				}

				// ── 独奏按钮 ────────────────────────────────────────────
				boolean solo = listState.isSoloed(rowIndex);
				if (solo) pushStateButtonStyle(STATE_ACTIVE_GREEN_R, STATE_ACTIVE_GREEN_G, STATE_ACTIVE_GREEN_B);
				ImGui.setCursorScreenPos(baseX + soloX, rowOriginScreenY + iconOffsetY);
				if (ImGui.button(Icons.Timeline.SOLO + "##solo" + rowIndex, iconBtn, iconBtn)) {
					listState.toggleSoloed(rowIndex);
				}
				if (solo) popStateButtonStyle();
				if (ImGui.isItemHovered()) {
					soloTooltip = solo
						? BBTexts.get("beatblock.track.tooltip.solo_on")
						: BBTexts.get("beatblock.track.tooltip.solo_off");
				}
			}

			// ── 摄像机轨：在播放头处向当前路径片段添加关键帧（位于可见按钮左侧）──────
			if (!showMuteSolo && rowIndex == TimelineTrackMeta.ROW_CAMERA) {
				ImGui.setCursorScreenPos(baseX + camKfX, rowOriginScreenY + iconOffsetY);
				if (ImGui.button(Icons.Timeline.KEYFRAME + "##camKf" + rowIndex, iconBtn, iconBtn)) {
					if (timeline != null && playheadClock != null) {
						CameraKeyframeActions.addKeyframeAtPlayhead(timeline, playheadClock);
					}
				}
				if (ImGui.isItemHovered()) {
					camKfTooltip = BBTexts.get("beatblock.track.tooltip.cam_keyframe");
				}
			}

			// ── 可见按钮 ────────────────────────────────────────────
			boolean vis = listState.isVisible(rowIndex);
			// 用快照标记决定 push/pop，不受按钮点击后 vis 修改影响
			boolean visStylePushed = !vis;
			ImGui.setCursorScreenPos(baseX + visX, rowOriginScreenY + iconOffsetY);
			if (visStylePushed) pushStateButtonStyle(STATE_ALERT_RED_R, STATE_ALERT_RED_G, STATE_ALERT_RED_B);
			if (ImGui.button((vis ? Icons.EYE : Icons.Action.HIDDEN) + "##vis" + rowIndex, iconBtn, iconBtn)) {
				listState.toggleVisible(rowIndex);
				vis = listState.isVisible(rowIndex);
			}
			if (visStylePushed) popStateButtonStyle();
			if (ImGui.isItemHovered()) {
				visTooltip = vis
					? BBTexts.get("beatblock.track.tooltip.visible_on")
					: BBTexts.get("beatblock.track.tooltip.visible_off");
			}

			boolean lock = listState.isLocked(rowIndex);
			ImGui.setCursorScreenPos(baseX + lockX, rowOriginScreenY + iconOffsetY);
			if (lock) pushStateButtonStyle(STATE_ALERT_RED_R, STATE_ALERT_RED_G, STATE_ALERT_RED_B);
			if (ImGui.button((lock ? Icons.Action.LOCK : Icons.Action.UNLOCK) + "##lock" + rowIndex, iconBtn, iconBtn)) {
				listState.toggleLocked(rowIndex);
			}
			if (lock) popStateButtonStyle();
			if (ImGui.isItemHovered()) {
				lockTooltip = lock
					? BBTexts.get("beatblock.track.tooltip.lock_on")
					: BBTexts.get("beatblock.track.tooltip.lock_off");
			}

			IconButtonStyle.popBeatBlockIconButton();
			if (muteTooltip != null) ImGui.setTooltip(muteTooltip);
			if (soloTooltip != null) ImGui.setTooltip(soloTooltip);
			if (visTooltip != null) ImGui.setTooltip(visTooltip);
			if (camKfTooltip != null) ImGui.setTooltip(camKfTooltip);
			if (lockTooltip != null) ImGui.setTooltip(lockTooltip);
		}

		return rowY + rowH;
	}

	private static void pushStateButtonStyle(float r, float g, float b) {
		ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, 1f);
		ImGui.pushStyleColor(ImGuiCol.Button, r, g, b, STATE_BG_ALPHA);
		ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r, g, b, STATE_BG_HOVER_ALPHA);
		ImGui.pushStyleColor(ImGuiCol.ButtonActive, r, g, b, STATE_BG_ACTIVE_ALPHA);
	}

	private static void popStateButtonStyle() {
		ImGui.popStyleColor(4);
	}

	private float resolveTypeColumnWidth() {
		if (cachedTypeColumnWidth > 0f) return cachedTypeColumnWidth;
		float maxTypeW = 0f;
		for (String key : TYPE_LABEL_KEYS) {
			String label = BBTexts.get(key);
			if (label.isBlank()) continue;
			maxTypeW = Math.max(maxTypeW, ImGui.calcTextSize(label).x);
		}
		cachedTypeColumnWidth = Math.max(MIN_TYPE_COL_W, maxTypeW + TYPE_COL_RIGHT_PAD);
		return cachedTypeColumnWidth;
	}

	private static float[] resolveGroupTitleColor(int rowIndex) {
		if (rowIndex == TimelineTrackMeta.ROW_AUDIO_GROUP) return GROUP_AUDIO_TEXT;
		if (rowIndex == TimelineTrackMeta.ROW_ANIMATION_GROUP) return GROUP_FEATURE_TEXT;
		if (rowIndex == TimelineTrackMeta.ROW_ACTION_GROUP) return GROUP_ACTION_TEXT;
		return null;
	}
}

