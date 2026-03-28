package com.beatblock.timeline.binding;

/**
 * 方块动作的空间调度模式。
 */
public enum SpatialDispatchMode {
	ALL,
	SEQUENTIAL,
	RADIAL,
	RANDOM,
	SPIRAL;

	public static SpatialDispatchMode fromValue(Object value) {
		if (value == null) return ALL;
		try {
			return SpatialDispatchMode.valueOf(String.valueOf(value).trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return ALL;
		}
	}
}
