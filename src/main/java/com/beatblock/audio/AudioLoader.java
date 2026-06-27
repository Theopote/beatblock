package com.beatblock.audio;

import com.beatblock.BeatBlock;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.audio.analysis.AudioBuffer;
import com.beatblock.audio.analysis.AudioDecoder;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 负责加载音频资源（WAV 路径），解码后分析波形与频段并写入 Timeline，同时更新 MusicPlayer 时长。
 */
public class AudioLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioLoader.class);

	private final Supplier<BeatBlockContext> contextSource;
	private String loadedPath;

	public AudioLoader() {
		this(BeatBlock::getContext);
	}

	AudioLoader(Supplier<BeatBlockContext> contextSource) {
		this.contextSource = contextSource != null ? contextSource : BeatBlock::getContext;
	}

	private BeatBlockContext ctx() {
		return contextSource.get();
	}

	/**
	 * 当前是否已加载指定资源。
	 */
	public boolean isLoaded(@Nullable String pathOrId) {
		return pathOrId != null && pathOrId.equals(loadedPath);
	}

	/**
	 * 加载 WAV 文件：解码 → 波形 + 频段分析 → 写入 Timeline，并设置 MusicPlayer 时长。
	 *
	 * @param pathOrId 本地 WAV 文件路径
	 * @return 是否成功
	 */
	public boolean load(@Nullable String pathOrId) {
		if (pathOrId == null || pathOrId.isEmpty()) return false;
		DecodedAudio audio = WavDecoder.loadFromPath(pathOrId);
		if (audio == null) return false;

		Timeline timeline = ctx().timeline();
		if (timeline != null) {
			timeline.setMetadata("audioPath", pathOrId);
			Object existingProjectPath = timeline.getMetadata("projectPath");
			if (existingProjectPath == null || String.valueOf(existingProjectPath).isBlank()) {
				timeline.setMetadata("projectPath", pathOrId);
			}
			AudioAnalysisEngine engine = ctx().audioAnalysisEngine();
			if (engine != null) {
				AudioBuffer buffer = AudioDecoder.fromDecodedAudio(audio);
				if (buffer != null && engine.analyzeBuffer(buffer) != null) {
					engine.fillTimeline(timeline);
				} else {
					AudioAnalyzer.analyzeAndFillTimeline(audio, timeline);
				}
			} else {
				AudioAnalyzer.analyzeAndFillTimeline(audio, timeline);
			}
		}
		var musicPlayer = ctx().musicPlayer();
		if (musicPlayer != null) {
			musicPlayer.setDurationSeconds(audio.getDurationSeconds());
			musicPlayer.setCurrentTimeSeconds(0);
			musicPlayer.loadAudio(pathOrId);
		}
		var editor = ctx().timelineEditor();
		if (editor != null) {
			editor.syncClockDuration();
		}
		loadedPath = pathOrId;
		LOGGER.info("BeatBlock: 已导入音乐 {} ({}s)", pathOrId, audio.getDurationSeconds());
		return true;
	}

	/**
	 * 从已解码的音频填充时间线（用于测试或非文件来源）。
	 */
	public void loadFromDecoded(@Nullable DecodedAudio audio) {
		if (audio == null) return;
		Timeline timeline = ctx().timeline();
		if (timeline != null) {
			timeline.setMetadata("audioPath", "(decoded)");
			timeline.setMetadata("projectId", "decoded");
			AudioAnalyzer.analyzeAndFillTimeline(audio, timeline);
		}
		var musicPlayer = ctx().musicPlayer();
		if (musicPlayer != null) {
			musicPlayer.setDurationSeconds(audio.getDurationSeconds());
			musicPlayer.setCurrentTimeSeconds(0);
			musicPlayer.loadAudio(null);
		}
		var editor = ctx().timelineEditor();
		if (editor != null) {
			editor.syncClockDuration();
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
