package com.beatblock.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 动画库中的模板：id、名称、时长、可组合的多个效果。
 */
public final class AnimationDefinition {

	private final String id;
	private final String name;
	private final float durationSeconds;
	private final List<AnimationEffect> effects;

	public AnimationDefinition(String id, String name, float durationSeconds, List<AnimationEffect> effects) {
		this.id = id != null ? id : "unknown";
		this.name = name != null ? name : id;
		this.durationSeconds = Math.max(0.01f, durationSeconds);
		this.effects = effects != null ? new ArrayList<>(effects) : new ArrayList<>();
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public float getDurationSeconds() {
		return durationSeconds;
	}

	public List<AnimationEffect> getEffects() {
		return Collections.unmodifiableList(effects);
	}
}
