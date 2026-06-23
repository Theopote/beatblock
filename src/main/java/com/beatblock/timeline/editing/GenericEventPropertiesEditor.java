package com.beatblock.timeline.editing;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用事件属性编辑（时间 + 任意键值参数），供时间线内联属性弹窗使用。
 */
public final class GenericEventPropertiesEditor {

	private GenericEventPropertiesEditor() {}

	public sealed interface Result {
		record Ok(AnimationEventSnapshot snapshot) implements Result {}
		record Err(String message) implements Result {}
	}

	public static Result buildUpdatedSnapshot(
		double timeSeconds,
		Map<String, String> paramRawValues,
		Map<String, Boolean> paramAsNumber,
		double clipStartSeconds,
		double clipEndSeconds
	) {
		Map<String, Object> parameters = new HashMap<>();
		if (paramRawValues != null) {
			for (Map.Entry<String, String> entry : paramRawValues.entrySet()) {
				String key = entry.getKey();
				if (key == null || key.isBlank()) continue;
				String raw = entry.getValue();
				if (raw == null) continue;
				boolean asNumber = paramAsNumber != null && paramAsNumber.getOrDefault(key, false);
				try {
					if (asNumber) {
						parameters.put(key, Double.parseDouble(raw.trim()));
					} else {
						parameters.put(key, raw);
					}
				} catch (NumberFormatException ex) {
					return new Result.Err("Invalid number for parameter: " + key);
				}
			}
		}
		return new Result.Ok(new AnimationEventSnapshot(
			Math.max(0.0, timeSeconds),
			parameters,
			clipStartSeconds,
			clipEndSeconds
		));
	}
}
