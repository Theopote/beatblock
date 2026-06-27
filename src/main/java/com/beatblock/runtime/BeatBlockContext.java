package com.beatblock.runtime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import com.beatblock.BeatBlock;
import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.AudioConversionService;
import com.beatblock.audio.AudioLoader;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.audio.StemMixer;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.stage.StageManager;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CommandManager;

/**
 * 运行时核心服务容器：构造器注入入口，替代散落的 {@link BeatBlock} 静态字段访问。
 * 生产环境在 {@link BeatBlock#onInitialize()} 中构建；测试可通过 {@link Builder} 注入 mock。
 */
public final class BeatBlockContext {

	private final AudioLoader audioLoader;
	private final MusicPlayer musicPlayer;
	private final StemMixer stemMixer;
	private final StageManager stageManager;
	private final Timeline timeline;
	private final TimelineEditor timelineEditor;
	private final BlockAnimationEngine blockAnimationEngine;
	private final AudioAnalysisEngine audioAnalysisEngine;
	private final AudioAnalysisService externalAudioAnalyzer;
	private final AudioConversionService audioConversionService;

	public BeatBlockContext(
		AudioLoader audioLoader,
		MusicPlayer musicPlayer,
		StemMixer stemMixer,
		StageManager stageManager,
		Timeline timeline,
		TimelineEditor timelineEditor,
		BlockAnimationEngine blockAnimationEngine,
		AudioAnalysisEngine audioAnalysisEngine,
		AudioAnalysisService externalAudioAnalyzer,
		AudioConversionService audioConversionService
	) {
		this.audioLoader = audioLoader;
		this.musicPlayer = musicPlayer;
		this.stemMixer = stemMixer;
		this.stageManager = stageManager;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.blockAnimationEngine = blockAnimationEngine;
		this.audioAnalysisEngine = audioAnalysisEngine;
		this.externalAudioAnalyzer = externalAudioAnalyzer;
		this.audioConversionService = audioConversionService;
	}

	public static Builder builder() {
		return new Builder();
	}

	/** 从 legacy 静态字段快照构建，供测试或未显式 bind 时使用。 */
	public static BeatBlockContext fromLegacyStatics() {
		return new BeatBlockContext(
			BeatBlock.audioLoader,
			BeatBlock.musicPlayer,
			BeatBlock.stemMixer,
			BeatBlock.stageManager,
			BeatBlock.timeline,
			BeatBlock.timelineEditor,
			BeatBlock.blockAnimationEngine,
			BeatBlock.audioAnalysisEngine,
			BeatBlock.externalAudioAnalyzer,
			BeatBlock.audioConversionService
		);
	}

	public @NonNull AudioLoader audioLoader() {
		return audioLoader;
	}

	public @NonNull MusicPlayer musicPlayer() {
		return musicPlayer;
	}

	public @NonNull StemMixer stemMixer() {
		return stemMixer;
	}

	public @NonNull StageManager stageManager() {
		return stageManager;
	}

	public @NonNull Timeline timeline() {
		return timeline;
	}

	public @NonNull TimelineEditor timelineEditor() {
		return timelineEditor;
	}

	public @NonNull BlockAnimationEngine blockAnimationEngine() {
		return blockAnimationEngine;
	}

	public @NonNull AudioAnalysisEngine audioAnalysisEngine() {
		return audioAnalysisEngine;
	}

	public @NonNull AudioAnalysisService externalAudioAnalyzer() {
		return externalAudioAnalyzer;
	}

	public @NonNull AudioConversionService audioConversionService() {
		return audioConversionService;
	}

	public @NonNull IAudioPlayer activeAudioPlayer() {
		if (stemMixer != null && stemMixer.hasStems()) {
			return stemMixer;
		}
		return musicPlayer;
	}

	public @Nullable CommandManager commandManager() {
		return timelineEditor != null ? timelineEditor.getCommandManager() : null;
	}

	public @Nullable BuildLayerManager buildLayerManager() {
		return blockAnimationEngine != null ? blockAnimationEngine.getBuildLayerManager() : null;
	}

	public static final class Builder {
		private AudioLoader audioLoader;
		private MusicPlayer musicPlayer;
		private StemMixer stemMixer;
		private StageManager stageManager;
		private Timeline timeline;
		private TimelineEditor timelineEditor;
		private BlockAnimationEngine blockAnimationEngine;
		private AudioAnalysisEngine audioAnalysisEngine;
		private AudioAnalysisService externalAudioAnalyzer;
		private AudioConversionService audioConversionService;

		public Builder audioLoader(AudioLoader audioLoader) {
			this.audioLoader = audioLoader;
			return this;
		}

		public Builder musicPlayer(MusicPlayer musicPlayer) {
			this.musicPlayer = musicPlayer;
			return this;
		}

		public Builder stemMixer(StemMixer stemMixer) {
			this.stemMixer = stemMixer;
			return this;
		}

		public Builder stageManager(StageManager stageManager) {
			this.stageManager = stageManager;
			return this;
		}

		public Builder timeline(Timeline timeline) {
			this.timeline = timeline;
			return this;
		}

		public Builder timelineEditor(TimelineEditor timelineEditor) {
			this.timelineEditor = timelineEditor;
			return this;
		}

		public Builder blockAnimationEngine(BlockAnimationEngine blockAnimationEngine) {
			this.blockAnimationEngine = blockAnimationEngine;
			return this;
		}

		public Builder audioAnalysisEngine(AudioAnalysisEngine audioAnalysisEngine) {
			this.audioAnalysisEngine = audioAnalysisEngine;
			return this;
		}

		public Builder externalAudioAnalyzer(AudioAnalysisService externalAudioAnalyzer) {
			this.externalAudioAnalyzer = externalAudioAnalyzer;
			return this;
		}

		public Builder audioConversionService(AudioConversionService audioConversionService) {
			this.audioConversionService = audioConversionService;
			return this;
		}

		public BeatBlockContext build() {
			return new BeatBlockContext(
				audioLoader,
				musicPlayer,
				stemMixer,
				stageManager,
				timeline,
				timelineEditor,
				blockAnimationEngine,
				audioAnalysisEngine,
				externalAudioAnalyzer,
				audioConversionService
			);
		}
	}
}
