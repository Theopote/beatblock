package com.beatblock.automap.engine;

/**
 * 节奏→动画映射规则：某节奏类型触发某动画。
 */
public final class PatternRule {

	private final RhythmType trigger;
	private final String animationTypeId;

	public PatternRule(RhythmType trigger, String animationTypeId) {
		this.trigger = trigger != null ? trigger : RhythmType.KICK;
		this.animationTypeId = animationTypeId != null ? animationTypeId : "jump";
	}

	public RhythmType getTrigger() { return trigger; }
	public String getAnimationTypeId() { return animationTypeId; }
}
