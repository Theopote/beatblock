package com.beatblock.automap.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 动画模式：一组节奏→动画规则，可按段落或风格切换。
 */
public final class AnimationPattern {

	private final List<PatternRule> rules;

	public AnimationPattern(List<PatternRule> rules) {
		this.rules = rules != null ? List.copyOf(rules) : List.of();
	}

	public List<PatternRule> getRules() { return rules; }

	public String getAnimationFor(RhythmType type) {
		for (PatternRule r : rules) {
			if (r.getTrigger() == type) return r.getAnimationTypeId();
		}
		return "jump";
	}

	/** 默认 EDM：Kick→jump, Snare→explosion, HiHat→pulse */
	public static AnimationPattern edm() {
		List<PatternRule> r = new ArrayList<>();
		r.add(new PatternRule(RhythmType.KICK, "jump"));
		r.add(new PatternRule(RhythmType.SNARE, "explosion"));
		r.add(new PatternRule(RhythmType.HIHAT, "pulse"));
		return new AnimationPattern(r);
	}

	public static AnimationPattern cinematic() {
		List<PatternRule> r = new ArrayList<>();
		r.add(new PatternRule(RhythmType.KICK, "rise"));
		r.add(new PatternRule(RhythmType.SNARE, "wave"));
		r.add(new PatternRule(RhythmType.HIHAT, "pulse"));
		return new AnimationPattern(r);
	}

	public static AnimationPattern ambient() {
		List<PatternRule> r = new ArrayList<>();
		r.add(new PatternRule(RhythmType.KICK, "wave"));
		r.add(new PatternRule(RhythmType.SNARE, "pulse"));
		r.add(new PatternRule(RhythmType.HIHAT, "pulse"));
		return new AnimationPattern(r);
	}

	public static AnimationPattern chaos() {
		List<PatternRule> r = new ArrayList<>();
		r.add(new PatternRule(RhythmType.KICK, "explosion"));
		r.add(new PatternRule(RhythmType.SNARE, "spiral"));
		r.add(new PatternRule(RhythmType.HIHAT, "orbit"));
		return new AnimationPattern(r);
	}

	public static AnimationPattern minimal() {
		List<PatternRule> r = new ArrayList<>();
		r.add(new PatternRule(RhythmType.KICK, "jump"));
		r.add(new PatternRule(RhythmType.SNARE, "pulse"));
		r.add(new PatternRule(RhythmType.HIHAT, "pulse"));
		return new AnimationPattern(r);
	}
}
