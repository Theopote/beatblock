package com.beatblock.animation;

/**
 * 动画模板：时长、缓动类型、变换类型（位移/旋转/缩放）。
 */
public class AnimationTemplate {

	public enum Easing {
		LINEAR,
		EASE_IN,
		EASE_OUT,
		EASE_IN_OUT
	}

	public enum TransformType {
		TRANSLATE,
		ROTATE,
		SCALE,
		TRANSLATE_AND_SCALE
	}

	private final String id;
	private final double durationSeconds;
	private final Easing easing;
	private final TransformType transformType;

	public AnimationTemplate(String id, double durationSeconds, Easing easing, TransformType transformType) {
		this.id = id != null ? id : "unknown";
		this.durationSeconds = Math.max(0.01, durationSeconds);
		this.easing = easing != null ? easing : Easing.LINEAR;
		this.transformType = transformType != null ? transformType : TransformType.TRANSLATE;
	}

	public String getId() {
		return id;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public Easing getEasing() {
		return easing;
	}

	public TransformType getTransformType() {
		return transformType;
	}

	public static double applyEasing(double t, Easing easing) {
		if (easing == null || easing == Easing.LINEAR) {
			return t;
		}
		return switch (easing) {
			case EASE_IN -> t * t;
			case EASE_OUT -> 1 - (1 - t) * (1 - t);
			case EASE_IN_OUT -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
			default -> t;
		};
	}
}
