package com.beatblock.automap.engine;

/**
 * 动画映射：RhythmType + AutoMapStyle → animationTypeId。
 * 与 AnimationPattern 一致，按风格选用不同 Pattern。
 */
public final class AnimationMapper {

	/**
	 * 根据节奏类型与风格返回动画类型 ID。
	 */
	public static String getAnimationTypeId(RhythmType rhythmType, AutoMapStyle style) {
		AnimationPattern pattern = getPatternFor(style);
		return pattern.getAnimationFor(rhythmType);
	}

	/**
	 * 根据节奏事件与风格返回动画类型 ID。
	 */
	public static String getAnimationTypeId(RhythmEvent event, AutoMapStyle style) {
		return getAnimationTypeId(event.getType(), style);
	}

	public static AnimationPattern getPatternFor(AutoMapStyle style) {
		if (style == null) return AnimationPattern.edm();
		return switch (style) {
			case EDM -> AnimationPattern.edm();
			case CINEMATIC -> AnimationPattern.cinematic();
			case AMBIENT -> AnimationPattern.ambient();
			case CHAOS -> AnimationPattern.chaos();
			case MINIMAL -> AnimationPattern.minimal();
		};
	}

	/**
	 * 根据风格返回默认动画时长（秒）。
	 */
	public static double getDurationSeconds(RhythmType type, AutoMapStyle style) {
		if (style == AutoMapStyle.MINIMAL) return 0.35;
		if (style == AutoMapStyle.AMBIENT) return 0.6;
		if (style == AutoMapStyle.CHAOS) return 0.4;
		if (type == RhythmType.KICK) return 0.5;
		if (type == RhythmType.SNARE) return 0.4;
		return 0.3;
	}
}
