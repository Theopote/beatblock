package com.beatblock;

import com.beatblock.item.BeatBlockItems;
import com.beatblock.audio.AudioLoader;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.audio.StemMixer;
import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.audio.AudioConversionService;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.stage.StageManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeatBlock implements ModInitializer {
	public static final String MOD_ID = "beatblock";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Runnable openUICallback;

	public static net.minecraft.util.Identifier id(String path) {
		return net.minecraft.util.Identifier.of(MOD_ID, path);
	}

	public static AudioLoader audioLoader;
	public static MusicPlayer musicPlayer;
	public static StageManager stageManager;
	public static Timeline timeline;
	public static TimelineEditor timelineEditor;
	public static BlockAnimationEngine blockAnimationEngine;
	public static AudioAnalysisEngine audioAnalysisEngine;
	public static AudioAnalysisService externalAudioAnalyzer;
	public static AudioConversionService audioConversionService;
	public static StemMixer stemMixer;

	public static IAudioPlayer getActiveAudioPlayer() {
		if (stemMixer != null && stemMixer.hasStems()) return stemMixer;
		return musicPlayer;
	}

	@Override
	public void onInitialize() {
		audioLoader = new AudioLoader();
		musicPlayer = new MusicPlayer();
		stemMixer = new StemMixer();
		stageManager = new StageManager();
		timeline = Timeline.createDefault();
		timelineEditor = new TimelineEditor(timeline, musicPlayer);
		blockAnimationEngine = new BlockAnimationEngine();
		audioAnalysisEngine = new AudioAnalysisEngine();
		externalAudioAnalyzer = new AudioAnalysisService();
		audioConversionService = new AudioConversionService();
		AudioAssetManager.getInstance().setConversionRequestHandler((asset, targetFormat) -> {
			if (asset == null || asset.getPath() == null) return;
			asset.setStatus(com.beatblock.audio.assets.AudioAssetStatus.ANALYZING);
			asset.setAnalysisProgressPercent(3);
			asset.setProcessingStatusText("FFmpeg 转换中（目标格式：MP3）");
			asset.setErrorMessage(null);
			audioConversionService.convertToMp3Async(
				asset.getPath(),
				(message, percent) -> {
					asset.setStatus(com.beatblock.audio.assets.AudioAssetStatus.ANALYZING);
					asset.setProcessingStatusText(message + "（" + percent + "%）");
					asset.setAnalysisProgressPercent(percent);
				},
				convertedPath -> {
					asset.setPath(convertedPath);
					String outName = convertedPath.getFileName() != null
						? convertedPath.getFileName().toString()
						: convertedPath.toString();
					asset.setInfoMessage("已转换为: " + outName);
					asset.setStatus(com.beatblock.audio.assets.AudioAssetStatus.PENDING);
					asset.setAnalysisProgressPercent(0);
					asset.setProcessingStatusText("转换完成，开始解析...");
					asset.setErrorMessage(null);
					AudioAssetManager.getInstance().startAnalysis(asset);
				},
				err -> {
					asset.setStatus(com.beatblock.audio.assets.AudioAssetStatus.FAILED);
					asset.setProcessingStatusText(null);
					asset.setInfoMessage(null);
					asset.setErrorMessage(err);
				}
			);
		});

		BeatBlockItems.initialize();

		LOGGER.info("BeatBlock 模组已加载 — 音乐可视化创作工具");
	}
}
