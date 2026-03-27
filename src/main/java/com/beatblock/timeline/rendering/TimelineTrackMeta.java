package com.beatblock.timeline.rendering;

/**
 * 轨道行元数据：默认名称、层级（一级轨道 / 子轨道），与 TimelineLayout.CONTENT_ROW_COUNT 对应。
 *
 * <p>音频子轨支持最多 {@link #MAX_AUDIO_SUB_ROWS} 条动态轨道（由 TrackRegistry 填充）。
 * 动画组及其他固定轨道行索引从 {@link #ROW_ANIMATION_GROUP} 开始，固定偏移在 MAX_AUDIO_SUB_ROWS 之后。</p>
 */
public final class TimelineTrackMeta {

	public static final int NO_PARENT = -1;

	/** 音频子轨最大槽数（动态分配，实际使用数由 TrackRegistry 决定）。 */
	public static final int MAX_AUDIO_SUB_ROWS = 16;

	// ── 固定行索引 ──────────────────────────────────────────────────────────
	public static final int ROW_AUDIO_GROUP     = 0;
	/** 动态音频子轨占据行 1 … 1+MAX_AUDIO_SUB_ROWS-1 */
	public static final int ROW_AUDIO_SUBS_START = 1;
	public static final int ROW_AUDIO_SUBS_END   = ROW_AUDIO_SUBS_START + MAX_AUDIO_SUB_ROWS - 1;

	public static final int ROW_ANIMATION_GROUP = ROW_AUDIO_SUBS_END + 1;  // = 9
	public static final int ROW_ANIM_BLOCK      = ROW_ANIMATION_GROUP + 1;
	public static final int MAX_ANIMATION_SUB_ROWS = 16;
	public static final int ROW_ANIM_FEATURES_START = ROW_ANIM_BLOCK + 1;
	public static final int ROW_ANIM_FEATURES_END = ROW_ANIM_FEATURES_START + MAX_ANIMATION_SUB_ROWS - 1;
	public static final int ROW_ANIM_AUTO       = ROW_ANIM_FEATURES_END + 1;
	public static final int ROW_CAMERA          = ROW_ANIM_AUTO + 1;
	public static final int ROW_GLOBAL_EVENT    = ROW_CAMERA + 1;

	/** 总行槽数（最大值，含所有音频子轨槽位）。 */
	private static final int ROW_COUNT = ROW_GLOBAL_EVENT + 1;             // = 14

	// ── 遗留常量（向后兼容，指向动态区的前三个槽）────────────────────────
	/** @deprecated 使用 TrackRegistry 动态轨道，不要依赖固定槽索引。 */
	@Deprecated
	public static final int ROW_WAVEFORM  = ROW_AUDIO_SUBS_START;     // 槽 0 = 行 1
	/** @deprecated */
	@Deprecated
	public static final int ROW_FREQ_LOW  = ROW_AUDIO_SUBS_START + 1; // 槽 1 = 行 2
	/** @deprecated */
	@Deprecated
	public static final int ROW_FREQ_MID  = ROW_AUDIO_SUBS_START + 2; // 槽 2 = 行 3
	/** @deprecated */
	@Deprecated
	public static final int ROW_FREQ_HIGH = ROW_AUDIO_SUBS_START + 3; // 槽 3 = 行 4

	private static final String[] DEFAULT_NAMES = new String[ROW_COUNT];
	/** 父行索引，NO_PARENT 表示一级轨道（或组标题） */
	private static final int[] PARENT_ROW = new int[ROW_COUNT];

	static {
		DEFAULT_NAMES[ROW_AUDIO_GROUP]     = "音频片段";
		// 动态子轨槽：默认名称为空（由 TrackRegistry / TrackListState 提供）
		for (int i = ROW_AUDIO_SUBS_START; i <= ROW_AUDIO_SUBS_END; i++) {
			DEFAULT_NAMES[i] = "";
		}
		DEFAULT_NAMES[ROW_ANIMATION_GROUP] = "动画";
		DEFAULT_NAMES[ROW_ANIM_BLOCK]      = "方块动画";
		for (int i = ROW_ANIM_FEATURES_START; i <= ROW_ANIM_FEATURES_END; i++) {
			DEFAULT_NAMES[i] = "";
		}
		DEFAULT_NAMES[ROW_ANIM_AUTO]       = "自动动画";
		DEFAULT_NAMES[ROW_CAMERA]          = "摄像机";
		DEFAULT_NAMES[ROW_GLOBAL_EVENT]    = "事件";

		PARENT_ROW[ROW_AUDIO_GROUP] = NO_PARENT;
		for (int i = ROW_AUDIO_SUBS_START; i <= ROW_AUDIO_SUBS_END; i++) {
			PARENT_ROW[i] = ROW_AUDIO_GROUP;
		}
		PARENT_ROW[ROW_ANIMATION_GROUP] = NO_PARENT;
		PARENT_ROW[ROW_ANIM_BLOCK]      = ROW_ANIMATION_GROUP;
		for (int i = ROW_ANIM_FEATURES_START; i <= ROW_ANIM_FEATURES_END; i++) {
			PARENT_ROW[i] = ROW_ANIMATION_GROUP;
		}
		PARENT_ROW[ROW_ANIM_AUTO]       = ROW_ANIMATION_GROUP;
		PARENT_ROW[ROW_CAMERA]          = NO_PARENT;
		PARENT_ROW[ROW_GLOBAL_EVENT]    = NO_PARENT;
	}

	public static String getDefaultName(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= DEFAULT_NAMES.length) return "";
		return DEFAULT_NAMES[rowIndex];
	}

	public static boolean isGroupRow(int rowIndex) {
		return rowIndex == ROW_AUDIO_GROUP || rowIndex == ROW_ANIMATION_GROUP;
	}

	/** 是否是动态音频子轨槽位（行 ROW_AUDIO_SUBS_START … ROW_AUDIO_SUBS_END）。 */
	public static boolean isAudioSubRow(int rowIndex) {
		return rowIndex >= ROW_AUDIO_SUBS_START && rowIndex <= ROW_AUDIO_SUBS_END;
	}

	/** 音频子轨的槽序号（0-based），非音频子轨返回 -1。 */
	public static int audioSubRowSlot(int rowIndex) {
		if (!isAudioSubRow(rowIndex)) return -1;
		return rowIndex - ROW_AUDIO_SUBS_START;
	}

	public static boolean isAnimationFeatureSubRow(int rowIndex) {
		return rowIndex >= ROW_ANIM_FEATURES_START && rowIndex <= ROW_ANIM_FEATURES_END;
	}

	public static int animationFeatureSubRowSlot(int rowIndex) {
		if (!isAnimationFeatureSubRow(rowIndex)) return -1;
		return rowIndex - ROW_ANIM_FEATURES_START;
	}

	/** 是否有父轨道（是否为子轨道，需要缩进显示） */
	public static boolean hasParent(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= PARENT_ROW.length) return false;
		return PARENT_ROW[rowIndex] != NO_PARENT;
	}

	public static int getParentRowIndex(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= PARENT_ROW.length) return NO_PARENT;
		return PARENT_ROW[rowIndex];
	}

	/**
	 * 轨道「类型」列文案：音频组与子轨、动画组与子轨、摄像机、事件（用于时间线左侧表头）。
	 */
	public static String getCategoryTypeLabel(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= DEFAULT_NAMES.length) return "";
		if (rowIndex == ROW_AUDIO_GROUP) return "音频片段";
		if (isAudioSubRow(rowIndex)) return "节奏特征";
		if (rowIndex == ROW_ANIMATION_GROUP || rowIndex == ROW_ANIM_BLOCK || rowIndex == ROW_ANIM_AUTO) return "动画";
		if (isAnimationFeatureSubRow(rowIndex)) return "动画控制";
		if (rowIndex == ROW_CAMERA) return "摄像机";
		if (rowIndex == ROW_GLOBAL_EVENT) return "事件";
		return "";
	}
}
