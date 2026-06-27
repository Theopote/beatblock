package com.beatblock.timeline.camera;

import com.beatblock.BeatBlock;

import java.util.Locale;

/**
 * 摄像机轨道片段类型：与 {@link com.beatblock.timeline.EventType#CAMERA_SEGMENT} 的参数字段 {@code kind} 对应。
 */
public enum CameraSegmentKind {
	PATH,
	DOLLY,
	ORBIT,
	CRANE,
	SHAKE;

	public static CameraSegmentKind fromParam(Object raw) {
		if (raw == null) return PATH;
		String s = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
		if (s.isEmpty()) return PATH;
		try {
			return valueOf(s);
		} catch (IllegalArgumentException e) {
			BeatBlock.LOGGER.debug("Unknown camera segment kind '{}', using PATH", s, e);
			return PATH;
		}
	}
}
