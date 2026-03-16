package com.beatblock.audio.assets;

/**
 * 音频资产当前状态：未解析 / 解析中 / 已完成 / 失败。
 */
public enum AudioAssetStatus {
	PENDING,
	ANALYZING,
	COMPLETED,
	FAILED
}

