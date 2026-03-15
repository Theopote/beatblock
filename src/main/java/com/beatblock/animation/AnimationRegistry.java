package com.beatblock.animation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 按 ID 注册与查找 AnimationTemplate。
 */
public class AnimationRegistry {

	private final Map<String, AnimationTemplate> templates = new HashMap<>();

	public void register(AnimationTemplate template) {
		if (template != null) {
			templates.put(template.getId(), template);
		}
	}

	public AnimationTemplate get(String id) {
		return templates.get(id);
	}

	public Map<String, AnimationTemplate> getAll() {
		return Collections.unmodifiableMap(templates);
	}

	public void clear() {
		templates.clear();
	}
}
