package com.beatblock.audio.assets;

import com.beatblock.BeatBlock;
import com.beatblock.audio.DecodedAudio;
import com.beatblock.audio.WavDecoder;
import com.beatblock.audio.analysis.AudioAnalysisEngine;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.audio.analysis.WaveformExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 音频资产管理器：维护「音频解析」面板的数据源，并串联 AudioAnalysisEngine。
 * 目前为同步实现，后续可引入后台线程。
 */
public final class AudioAssetManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioAssetManager.class);

	private static final AudioAssetManager INSTANCE = new AudioAssetManager();

	public static AudioAssetManager getInstance() {
		return INSTANCE;
	}

	private final List<AudioAsset> assets = new ArrayList<>();
	private AudioAsset currentDragAsset;

	private AudioAssetManager() {
	}

	public List<AudioAsset> getAssets() {
		return Collections.unmodifiableList(assets);
	}

	/** 当前正在作为拖拽源的资产（由 UI 设置，仅在一次拖拽操作期间有效）。 */
	public AudioAsset getCurrentDragAsset() {
		return currentDragAsset;
	}

	public void setCurrentDragAsset(AudioAsset asset) {
		this.currentDragAsset = asset;
	}

	public AudioAsset addFromPath(String pathStr) {
		if (pathStr == null || pathStr.isEmpty()) return null;
		Path path = Paths.get(pathStr);
		if (!Files.isRegularFile(path)) {
			LOGGER.warn("BeatBlock AudioAssetManager: 非文件或不存在: {}", pathStr);
			return null;
		}
		AudioAsset asset = new AudioAsset(path);
		assets.add(asset);
		return asset;
	}

	public void remove(String id) {
		if (id == null) return;
		assets.removeIf(a -> id.equals(a.getId()));
	}

	/**
	 * 同步执行完整音频解析，更新 asset 状态与统计信息。
	 * 目前仅支持 WAV（通过 WavDecoder + AudioAnalysisEngine）；后续可扩展 MP3/OGG。
	 */
	public void startAnalysis(AudioAsset asset) {
		if (asset == null) return;
		Path path = asset.getPath();
		if (path == null) return;
		asset.setStatus(AudioAssetStatus.ANALYZING);
		asset.getFinishedSteps().clear();
		asset.setErrorMessage(null);

		try {
			// 先用 WavDecoder 获取基础信息
			DecodedAudio decoded = WavDecoder.loadFromPath(path.toString());
			if (decoded == null) {
				asset.setStatus(AudioAssetStatus.FAILED);
				asset.setErrorMessage("无法解码音频（目前仅支持 WAV）");
				return;
			}
			asset.setDurationSeconds(decoded.getDurationSeconds());
			asset.setSampleRate(decoded.getSampleRate());

			// BPM + 节拍 + 频段分离 + 段落识别 + Beatmap 写入 全部由 AudioAnalysisEngine 负责
			AudioAnalysisEngine engine = BeatBlock.audioAnalysisEngine;
			if (engine == null) {
				engine = new AudioAnalysisEngine();
				BeatBlock.audioAnalysisEngine = engine;
			}

			// AudioBuffer 构造与 engine.analyzeBuffer 已经执行了 FFT/Band/Energy/Beat/BPM
			asset.markStepFinished(AudioAnalysisStep.BPM_DETECTION);

			AudioFeatureTimeline ft = engine.analyze(Paths.get(path.toString()));
			if (ft == null) {
				asset.setStatus(AudioAssetStatus.FAILED);
				asset.setErrorMessage("分析失败");
				return;
			}
			asset.setFeatureTimeline(ft);

			// 解析结果统计
			List<DetectedBeat> beats = ft.getBeats();
			List<FrequencyBands> bands = ft.getBands();
			WaveformExtractor.WaveformFrame[] wf = ft.getWaveformFrames();

			asset.markStepFinished(AudioAnalysisStep.BEAT_DETECTION);
			asset.markStepFinished(AudioAnalysisStep.BAND_SPLIT);
			asset.markStepFinished(AudioAnalysisStep.SECTION_DETECTION);
			asset.markStepFinished(AudioAnalysisStep.WRITE_BEATMAP);

			asset.setBpm(ft.getBpm());
			asset.setBeatCount(beats.size());
			asset.setDurationSeconds(ft.getDurationSeconds());

			int low = 0, mid = 0, high = 0;
			for (FrequencyBands fb : bands) {
				if (fb.getLow() > 0) low++;
				if (fb.getMid() > 0) mid++;
				if (fb.getHigh() > 0) high++;
			}
			asset.setLowCount(low);
			asset.setMidCount(mid);
			asset.setHighCount(high);

			// 简单用 waveform 数量推断段落数（后续可接 MusicStructureAnalyzer 结果）
			if (wf != null) {
				asset.setSectionCount(Math.max(1, wf.length / 256));
			}

			asset.setStatus(AudioAssetStatus.COMPLETED);
		} catch (Exception e) {
			LOGGER.warn("BeatBlock AudioAssetManager: 解析音频失败: {}", path, e);
			asset.setStatus(AudioAssetStatus.FAILED);
			asset.setErrorMessage("解析过程中发生异常");
		}
	}
}

