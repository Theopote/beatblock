package com.beatblock.engine.influence;

import com.beatblock.engine.EngineAnimationInstance;

public final class InfluenceInstanceKeys {

	private InfluenceInstanceKeys() {}

	public static String key(EngineAnimationInstance instance) {
		if (instance == null || instance.getTarget() == null || instance.getDefinition() == null) {
			return "unknown";
		}
		return instance.getTarget().getId()
			+ "@" + instance.getStartTimeSeconds()
			+ "#" + instance.getDefinition().getId();
	}
}
