package com.beatblock;

import com.beatblock.item.BeatBlockItems;
import com.beatblock.animation.AnimationManager;
import com.beatblock.animation.AnimationRegistry;
import com.beatblock.animation.AnimationTemplate;
import com.beatblock.audio.AudioLoader;
import com.beatblock.audio.BeatmapGenerator;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.AnalyzerInstaller;
import com.beatblock.beat.BeatEvent;
import com.beatblock.beat.BeatScheduler;
import com.beatblock.stage.StageManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.visual.BlockDisplayPool;
import com.beatblock.visual.BlockSpawner;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.visual.TransformUpdater;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeatBlock implements ModInitializer {
	public static final String MOD_ID = "beatblock";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 客户端设置：手持 BeatBlock 物品右键时调用，用于打开 UI 界面 */
	public static Runnable openUICallback;

	/** 资源 ID 辅助（参考 ChronoBlocks），用于物品、包等注册 */
	public static net.minecraft.util.Identifier id(String path) {
		return net.minecraft.util.Identifier.of(MOD_ID, path);
	}

	// 各系统单例，供 Client 与网络等使用
	public static AudioLoader audioLoader;
	public static BeatmapGenerator beatmapGenerator;
	public static MusicPlayer musicPlayer;
	public static BeatScheduler beatScheduler;
	public static AnimationRegistry animationRegistry;
	public static AnimationManager animationManager;
	public static BlockDisplayPool blockDisplayPool;
	public static BlockSpawner blockSpawner;
	public static TransformUpdater transformUpdater;
	public static StageManager stageManager;
	public static Timeline timeline;
	public static TimelineEditor timelineEditor;
	public static BlockAnimationEngine blockAnimationEngine;
	public static AudioAnalysisEngine audioAnalysisEngine;
	public static AudioAnalysisService externalAudioAnalyzer;

	@Override
	public void onInitialize() {
		audioLoader = new AudioLoader();
		beatmapGenerator = new BeatmapGenerator();
		musicPlayer = new MusicPlayer();
		beatScheduler = new BeatScheduler();
		animationRegistry = new AnimationRegistry();
		animationManager = new AnimationManager();
		blockDisplayPool = new BlockDisplayPool();
		blockSpawner = new BlockSpawner();
		transformUpdater = new TransformUpdater();
		stageManager = new StageManager();
		timeline = Timeline.createDefault();
		timelineEditor = new TimelineEditor(timeline);
		blockAnimationEngine = new BlockAnimationEngine();
		audioAnalysisEngine = new AudioAnalysisEngine();
		// 外部 Python 音频分析器（librosa），脚本由 AnalyzerInstaller 从资源解压到 config 目录
		externalAudioAnalyzer = new AudioAnalysisService();

		// 注册默认动画模板
		animationRegistry.register(new AnimationTemplate("bounce", 0.5, AnimationTemplate.Easing.EASE_OUT, AnimationTemplate.TransformType.SCALE));
		animationRegistry.register(new AnimationTemplate("slide", 0.4, AnimationTemplate.Easing.LINEAR, AnimationTemplate.TransformType.TRANSLATE));
		animationRegistry.register(new AnimationTemplate("pulse", 0.3, AnimationTemplate.Easing.EASE_IN_OUT, AnimationTemplate.TransformType.TRANSLATE_AND_SCALE));

		// 将 BeatScheduler 与 AnimationManager 连接（具体根据 BeatEvent 创建实例的逻辑可在客户端/游戏层实现）
		animationManager.setBeatScheduler(beatScheduler);

		BeatBlockItems.initialize();

		LOGGER.info("BeatBlock 模组已加载 — 音乐驱动方块动画引擎");
	}
}