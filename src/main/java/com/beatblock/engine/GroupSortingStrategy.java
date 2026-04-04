package com.beatblock.engine;

import com.beatblock.timeline.binding.SpatialDispatchMode;

/**
 * StageObject 组内排序策略（第 1 阶段）：先映射到现有 SpatialDispatchMode，后续可扩展更丰富路径策略。
 */
public enum GroupSortingStrategy {
	SEQUENTIAL("SEQUENTIAL", SpatialDispatchMode.SEQUENTIAL),
	RADIAL("RADIAL", SpatialDispatchMode.RADIAL),
	SPIRAL("SPIRAL", SpatialDispatchMode.SPIRAL),
	RANDOM("RANDOM", SpatialDispatchMode.RANDOM),
	ALL("ALL", SpatialDispatchMode.ALL);

	private final String code;
	private final SpatialDispatchMode spatialMode;

	GroupSortingStrategy(String code, SpatialDispatchMode spatialMode) {
		this.code = code;
		this.spatialMode = spatialMode;
	}

	public String getCode() {
		return code;
	}

	public SpatialDispatchMode toSpatialDispatchMode() {
		return spatialMode;
	}

	public static GroupSortingStrategy fromValue(Object value) {
		if (value == null) return SEQUENTIAL;
		String s = String.valueOf(value).trim();
		if (s.isEmpty()) return SEQUENTIAL;
		for (GroupSortingStrategy strategy : values()) {
			if (strategy.code.equalsIgnoreCase(s) || strategy.name().equalsIgnoreCase(s)) {
				return strategy;
			}
		}
		SpatialDispatchMode mode = SpatialDispatchMode.fromValue(s);
		return switch (mode) {
			case SEQUENTIAL -> SEQUENTIAL;
			case RADIAL -> RADIAL;
			case SPIRAL -> SPIRAL;
			case RANDOM -> RANDOM;
			case ALL -> ALL;
		};
	}
}