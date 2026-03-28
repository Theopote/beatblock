package com.beatblock.selection;

/**
 * 方块选择工具模式（与 ChronoBlocks 类工具栏对齐的扩展集）。
 */
public enum SelectionMode {
	OFF,
	CLICK,
	BOX,
	LINE,
	SPHERE,
	/** 全图六邻域连通魔棒 */
	CONNECTED,
	COLUMN,
	/**
	 * 按击中面做法平面：与「当前选区包围盒 ∩ 平面」相交；若无选区则用「当前区块 ∩ 平面」。
	 */
	PLANE_SLICE,
	/**
	 * 仅在当前选区 AABB 内做连通魔棒（需已有选区，且点击在盒内）。
	 */
	SELECTION_WAND,
	/** 场景区按住左键连续盖章（半径与形状见选择属性） */
	BRUSH
}
