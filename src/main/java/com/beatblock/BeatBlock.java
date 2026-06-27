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
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.video.VideoExportService;
import net.fabricmc.api.ModInitializer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeatBlock implements ModInitializer {
	public static final String MOD_ID = "beatblock";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Runnable openUICallback;

	public static net.minecraft.util.Identifier id(String path) {
		return net.minecraft.util.Identifier.of(MOD_ID, path);
	}

	private static BeatBlockContext context;

	/** 仅供单元测试安装 Context；生产代码在 {@link #onInitialize()} 中初始化。 */
	public static void installContext(@NonNull BeatBlockContext ctx) {
		context = ctx;
	}

	/** 仅供单元测试清理 Context。 */
	public static void resetContext() {
		context = null;
	}

	public static @NonNull BeatBlockContext getContext() {
		if (context == null) {
			throw new IllegalStateException("BeatBlock mod has not been initialized yet");
		}
		return context;
	}

	public static IAudioPlayer getActiveAudioPlayer() {
		return getContext().activeAudioPlayer();
	}

	@Override
	public void onInitialize() {
		initializeMod();
		registerAssetConversionHandler();
		BeatBlockItems.initialize();
		LOGGER.info("BeatBlock 模组已加载 — 音乐可视化创作工具");
	}

	private static void initializeMod() {
		AudioLoader loader = new AudioLoader();
		MusicPlayer player = new MusicPlayer();
		StemMixer mixer = new StemMixer();
		StageManager stage = new StageManager();
		Timeline timelineModel = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timelineModel, player);
		BlockAnimationEngine animationEngine = new BlockAnimationEngine();
		AudioAnalysisEngine analysisEngine = new AudioAnalysisEngine();
		AudioAnalysisService analyzer = new AudioAnalysisService();
		AudioConversionService conversionService = new AudioConversionService();
		VideoExportService videoExportService = new VideoExportService(com.beatblock.client.export.ClientThreadExecutor::run);
		context = new BeatBlockContext(
			loader,
			player,
			mixer,
			stage,
			timelineModel,
			editor,
			animationEngine,
			analysisEngine,
			analyzer,
			conversionService,
			videoExportService
		);
	}

	private static void registerAssetConversionHandler() {
		AudioAssetManager.getInstance().setConversionRequestHandler((asset, targetFormat) -> {
			if (asset == null || asset.getPath() == null) return;
			asset.setStatus(com.beatblock.audio.assets.AudioAssetStatus.ANALYZING);
			asset.setAnalysisProgressPercent(3);
			asset.setProcessingStatusText(BBTexts.get("beatblock.audio.ffmpeg_converting"));
			asset.setErrorMessage(null);
			getContext().audioConversionService().convertToMp3Async(
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
					asset.setInfoMessage(BBTexts.get("beatblock.audio.converted_to", outName));
					asset.setStatus(com.beatblock.audio.assets.AudioAssetStatus.PENDING);
					asset.setAnalysisProgressPercent(0);
					asset.setProcessingStatusText(BBTexts.get("beatblock.audio.convert_done_parsing"));
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
	}
}
