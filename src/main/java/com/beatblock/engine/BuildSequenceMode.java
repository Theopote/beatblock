package com.beatblock.engine;

import java.util.Locale;

/**
 * 建造序列的空间排列模式，决定方块出现的顺序。
 */
public enum BuildSequenceMode {
	/** 从底部向上逐层建造 */
	WALL,
	/** 从一端到另一端水平延伸 */
	BRIDGE,
	/** 从中心向外扩散的柱状堆叠 */
	TOWER,
	/** 随机顺序逐块出现（或消失） */
	DISSOLVE;

	public static BuildSequenceMode fromValue(Object value) {
		if (value == null) return WALL;
		try {
			return BuildSequenceMode.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return WALL;
		}
	}
}
