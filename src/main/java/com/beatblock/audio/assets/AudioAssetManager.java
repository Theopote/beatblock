package com.beatblock.audio.assets;

import com.beatblock.BeatBlock;
import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.BeatmapMeta;
import com.beatblock.audio.beatmap.FrequencyBand;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
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
	private ConversionRequestHandler conversionRequestHandler;
	private static final String[] SUPPORTED_AUDIO_EXTENSIONS = {"mp3", "wav", "ogg", "flac"};

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

	public AudioAsset findById(String id) {
		if (id == null || id.isBlank()) return null;
		for (AudioAsset asset : assets) {
			if (id.equals(asset.getId())) return asset;
		}
		return null;
	}

	public void setConversionRequestHandler(ConversionRequestHandler conversionRequestHandler) {
		this.conversionRequestHandler = conversionRequestHandler;
	}

	public boolean requestConvertToMp3(AudioAsset asset) {
		if (asset == null || asset.getPath() == null) return false;
		if (conversionRequestHandler == null) {
			LOGGER.info("BeatBlock AudioAssetManager: 转换请求已记录（尚未接入转换器） file={}", asset.getPath());
			return false;
		}
		conversionRequestHandler.requestConversion(asset, "mp3");
		return true;
	}

	public AudioAsset addFromPath(String pathStr) {
		if (pathStr == null || pathStr.isEmpty()) return null;
		Path path = normalizeAudioPath(pathStr);
		if (path == null) {
			LOGGER.warn("BeatBlock AudioAssetManager: 无法解析路径: {}", pathStr);
			return null;
		}
		if (!isSupportedAudioFile(path)) {
			LOGGER.warn("BeatBlock AudioAssetManager: 不支持的音频格式: {}", path);
			return null;
		}
		if (!Files.isRegularFile(path)) {
			LOGGER.warn("BeatBlock AudioAssetManager: 非文件或不存在: {}", pathStr);
			return null;
		}
		AudioAsset asset = new AudioAsset(path);
		assets.add(asset);
		return asset;
	}

	public boolean isSupportedAudioPath(String rawPath) {
		Path path = normalizeAudioPath(rawPath);
		return path != null && isSupportedAudioFile(path);
	}

	public String getSupportedAudioExtensionsLabel() {
		return "MP3/WAV/OGG/FLAC";
	}

	private Path normalizeAudioPath(String raw) {
		if (raw == null) return null;
		String v = raw.trim();
		if (v.isEmpty()) return null;

		if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
			v = v.substring(1, v.length() - 1).trim();
		}

		try {
			if (v.startsWith("file:/")) {
				Path p = Paths.get(URI.create(v));
				return p.toAbsolutePath().normalize();
			}
		} catch (Exception e) {
			LOGGER.debug("BeatBlock AudioAssetManager: URI 路径解析失败: {}", e.getMessage());
		}

		try {
			Path p = Paths.get(v);
			if (!p.isAbsolute()) {
				Path gameDir = FabricLoader.getInstance().getGameDir();
				p = gameDir.resolve(p);
			}
			return p.toAbsolutePath().normalize();
		} catch (Exception e) {
			LOGGER.debug("BeatBlock AudioAssetManager: 普通路径解析失败: {}", e.getMessage());
			return null;
		}
	}

	private boolean isSupportedAudioFile(Path path) {
		if (path == null || path.getFileName() == null) return false;
		String name = path.getFileName().toString();
		int idx = name.lastIndexOf('.');
		if (idx < 0 || idx == name.length() - 1) return false;
		String ext = name.substring(idx + 1).toLowerCase(Locale.ROOT);
		for (String e : SUPPORTED_AUDIO_EXTENSIONS) {
			if (e.equals(ext)) return true;
		}
		return false;
	}

	public void remove(String id) {
		if (id == null) return;
		assets.removeIf(a -> id.equals(a.getId()));
	}

	/**
	 * 异步执行完整音频解析（Python + librosa），更新 asset 状态与统计信息。
	 */
	public void startAnalysis(AudioAsset asset) {
		if (asset == null) return;
		Path path = asset.getPath();
		if (path == null) return;
		asset.setStatus(AudioAssetStatus.ANALYZING);
		asset.setAnalysisProgressPercent(0);
		asset.setProcessingStatusText(null);
		asset.getFinishedSteps().clear();
		asset.setErrorMessage(null);

		AudioAnalysisService service = BeatBlock.externalAudioAnalyzer;
		if (service == null) {
			asset.setStatus(AudioAssetStatus.FAILED);
			asset.setErrorMessage("外部音频分析器未初始化");
			return;
		}

		service.analyze(
			path,
			(step, pct) -> {
				asset.setAnalysisProgressPercent(pct);
				// 解析 Python 步骤名映射到 UI 步骤
				switch (step) {
					case "BPM_DETECTION" -> asset.markStepFinished(AudioAnalysisStep.BPM_DETECTION);
					case "BEAT_DETECTION" -> {
						asset.markStepFinished(AudioAnalysisStep.BEAT_DETECTION);
						asset.markStepFinished(AudioAnalysisStep.BAND_SPLIT);
					}
					case "SECTION_DETECTION" -> asset.markStepFinished(AudioAnalysisStep.SECTION_DETECTION);
					case "WRITE_BEATMAP" -> asset.markStepFinished(AudioAnalysisStep.WRITE_BEATMAP);
					default -> {
					}
				}
			},
			(Beatmap beatmap) -> {
				asset.setBeatmap(beatmap);
				asset.setAnalysisProgressPercent(100);
				asset.setProcessingStatusText(null);
				BeatmapMeta meta = beatmap.meta;
				asset.setDurationSeconds(meta.durationMs() / 1000.0);
				asset.setSampleRate(meta.sampleRate());
				asset.setBpm((float) meta.bpm());
				asset.setBeatCount(beatmap.beats.size());
				asset.setSectionCount(beatmap.sections.size());

				int low = 0, mid = 0, high = 0;
				for (BeatEvent e : beatmap.beats) {
					if (e.band() == FrequencyBand.LOW) low++;
					else if (e.band() == FrequencyBand.MID) mid++;
					else if (e.band() == FrequencyBand.HIGH) high++;
				}
				asset.setLowCount(low);
				asset.setMidCount(mid);
				asset.setHighCount(high);

				asset.setStatus(AudioAssetStatus.COMPLETED);
			},
			err -> {
				LOGGER.warn("BeatBlock AudioAssetManager: 外部解析失败: {}", err);
				asset.setStatus(AudioAssetStatus.FAILED);
				asset.setAnalysisProgressPercent(0);
				asset.setProcessingStatusText(null);
				asset.setErrorMessage(normalizeErrorMessage(path, err));
			}
		);
	}

	private String normalizeErrorMessage(Path audioPath, String raw) {
		String safe = raw == null ? "未知错误" : raw.trim();
		String lower = safe.toLowerCase();
		String ext = "";
		if (audioPath != null && audioPath.getFileName() != null) {
			String name = audioPath.getFileName().toString();
			int idx = name.lastIndexOf('.');
			if (idx >= 0 && idx < name.length() - 1) {
				ext = name.substring(idx + 1).toLowerCase();
			}
		}

		if ("wma".equals(ext)
			|| lower.contains("wma")
			|| lower.contains("format")
			|| lower.contains("unsupported")
			|| lower.contains("could not")
			|| lower.contains("can't")
			|| lower.contains("cannot load audio")) {
			return "当前格式可能不受支持。建议先转换为 MP3 或 WAV，再重新解析。";
		}
		return safe;
	}

	@FunctionalInterface
	public interface ConversionRequestHandler {
		void requestConversion(AudioAsset asset, String targetFormat);
	}
}

