package com.beatblock.audio;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.Timeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 负责加载音频资源（WAV 路径），解码后分析波形与频段并写入 Timeline，同时更新 MusicPlayer 时长。
 */
public class AudioLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioLoader.class);

	private String loadedPath;

	/**
	 * 当前是否已加载指定资源。
	 */
	public boolean isLoaded(String pathOrId) {
		return pathOrId != null && pathOrId.equals(loadedPath);
	}

	/**
	 * 加载 WAV 文件：解码 → 波形 + 频段分析 → 写入 Timeline，并设置 MusicPlayer 时长。
	 *
	 * @param pathOrId 本地 WAV 文件路径
	 * @return 是否成功
	 */
	public boolean load(String pathOrId) {
		if (pathOrId == null || pathOrId.isEmpty()) return false;
		DecodedAudio audio = WavDecoder.loadFromPath(pathOrId);
		if (audio == null) return false;

		Timeline timeline = BeatBlock.timeline;
		if (timeline != null) {
			AudioAnalyzer.analyzeAndFillTimeline(audio, timeline);
		}
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setDurationSeconds(audio.getDurationSeconds());
			BeatBlock.musicPlayer.setCurrentTimeSeconds(0);
		}
		if (BeatBlock.timelineEditor != null) {
			BeatBlock.timelineEditor.syncClockDuration();
		}
		loadedPath = pathOrId;
		LOGGER.info("BeatBlock: 已导入音乐 {} ({}s)", pathOrId, audio.getDurationSeconds());
		return true;
	}

	/**
	 * 从已解码的音频填充时间线（用于测试或非文件来源）。
	 */
	public void loadFromDecoded(DecodedAudio audio) {
		if (audio == null) return;
		if (BeatBlock.timeline != null) {
			AudioAnalyzer.analyzeAndFillTimeline(audio, BeatBlock.timeline);
		}
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setDurationSeconds(audio.getDurationSeconds());
			BeatBlock.musicPlayer.setCurrentTimeSeconds(0);
		}
		if (BeatBlock.timelineEditor != null) {
			BeatBlock.timelineEditor.syncClockDuration();
		}
		loadedPath = "(decoded)";
	}

	/**
	 * 卸载资源（仅清除加载标记，不清空 Timeline）。
	 */
	public void unload(String pathOrId) {
		if (pathOrId != null && pathOrId.equals(loadedPath)) {
			loadedPath = null;
		}
	}
}
