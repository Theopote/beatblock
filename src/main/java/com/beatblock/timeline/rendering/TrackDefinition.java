package com.beatblock.timeline.rendering;

/**
 * 单条动态轨道的定义：由 {@link TrackRegistry} 生成，供渲染器遍历。
 * 代替 TimelineTrackMeta 中针对音频子轨的硬编码。
 */
public final class TrackDefinition {

	/** 渲染器支持的可视类型 */
	public enum VisualType {
		/** 波形图 */
		WAVEFORM,
		/** 冲击柱（impulse bar），用于 kick/snare/hihat 等打击类 */
		IMPULSE,
		/** 动画片段 */
		ANIMATION_CLIP,
	}

	/** 分组 ID（同组在轨道头有缩进视觉、折叠联动） */
	public static final int GROUP_NONE     = -1;
	public static final int GROUP_RHYTHM   =  0;
	public static final int GROUP_EXTENDED =  1;
	public static final int GROUP_STEMS    =  2;

	/** 特征 map 的 key（"waveform" 固定，其余对应 FeatureTrack.key） */
	private final String key;
	/** 显示名称 */
	private final String displayName;
	/** 渲染类型 */
	private final VisualType visualType;
	/** 分组 */
	private final int group;
	/** ABGR 颜色 */
	private final int color;

	public TrackDefinition(String key, String displayName, VisualType visualType, int group, int color) {
		this.key         = key;
		this.displayName = displayName;
		this.visualType  = visualType;
		this.group       = group;
		this.color       = color;
	}

	/** Convenience: 无颜色覆盖（使用渲染器默认） */
	public TrackDefinition(String key, String displayName, VisualType visualType, int group) {
		this(key, displayName, visualType, group, 0);
	}

	public String     getKey()         { return key;         }
	public String     getDisplayName() { return displayName; }
	public VisualType getVisualType()  { return visualType;  }
	public int        getGroup()       { return group;       }
	/** 0 表示未指定，渲染器使用内建默认色 */
	public int        getColor()       { return color;       }
	public boolean    hasCustomColor() { return color != 0;  }
}
