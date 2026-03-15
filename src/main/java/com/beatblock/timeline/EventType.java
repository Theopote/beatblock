package com.beatblock.timeline;

/**
 * 时间线事件类型：与 UI 展示、序列化、插件扩展一致。
 */
public enum EventType {
	BEAT,
	ANIMATION,
	CAMERA_KEYFRAME,
	PARTICLE,
	LIGHTING,
	GLOBAL
}
