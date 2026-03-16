package com.beatblock.audio.assets;

/**
 * 音频解析流水线中的步骤，用于 UI 显示「BPM 检测 / 踩点检测 / 频段分离 / 段落识别 / 写入文件」打勾进度。
 */
public enum AudioAnalysisStep {
	BPM_DETECTION,
	BEAT_DETECTION,
	BAND_SPLIT,
	SECTION_DETECTION,
	WRITE_BEATMAP
}

