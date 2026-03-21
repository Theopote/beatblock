package com.beatblock.timeline.editor;

/**
 * 交互状态机：当前正在进行的操作。
 */
public enum InteractionMode {
	NONE,
	DRAG_EVENT,
	DRAG_CLIP,
	RESIZE_CLIP,
	PAN_VIEW,
	BOX_SELECT,
	SCRUB_TIME,
	MARKER_DRAG,
	LOOP_IN_DRAG,
	LOOP_OUT_DRAG,
	/** 拖动轨道头与内容区之间的分割线 */
	RESIZE_HEADER
}
