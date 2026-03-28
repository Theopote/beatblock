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
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

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

	private final List<AudioAsset> assets = new CopyOnWriteArrayList<>();
	private final Map<String, Future<?>> analysisTasks = new ConcurrentHashMap<>();
	private AudioAsset currentDragAsset;
	private ConversionRequestHandler conversionRequestHandler;
	private long nextQueueTicket = 1L;
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
		return isSupportedAudioFile(path);
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
		Future<?> task = analysisTasks.remove(id);
		if (task != null) {
			task.cancel(true);
		}
		assets.removeIf(a -> id.equals(a.getId()));
	}

	/**
	 * 删除当前音频对应的 basic/demucs 缓存后重新分析。
	 *
	 * @return 用户可读结果信息
	 */
	public String clearCacheAndReanalyze(AudioAsset asset) {
		AudioAnalysisMode mode = BeatBlock.externalAudioAnalyzer != null && BeatBlock.externalAudioAnalyzer.isUseDemucs()
			? AudioAnalysisMode.DEMUCS
			: AudioAnalysisMode.BASIC;
		return clearCacheAndReanalyze(asset, mode);
	}

	public String clearCacheAndReanalyze(AudioAsset asset, AudioAnalysisMode mode) {
		if (asset == null || asset.getPath() == null) return "无效音频资产";
		AudioAnalysisService service = BeatBlock.externalAudioAnalyzer;
		if (service == null) return "外部音频分析器未初始化";

		AudioAnalysisMode resolvedMode = mode != null ? mode : AudioAnalysisMode.BASIC;
		int removed = service.clearAllAnalysisCacheForAudio(asset.getPath());
		startAnalysis(asset, resolvedMode);
		return "已清理 " + removed + " 个缓存文件（含 stem 缓存），开始以 " + resolvedMode.label() + " 重新解析";
	}

	public int getQueuePosition(String assetId) {
		if (assetId == null || assetId.isBlank()) return -1;
		AudioAsset target = findById(assetId);
		if (target == null || target.getStatus() != AudioAssetStatus.QUEUED) return -1;
		long targetTicket = target.getQueueTicket();
		if (targetTicket < 0) return -1;

		int position = 1;
		for (AudioAsset asset : assets) {
			if (asset == target) continue;
			if (asset.getStatus() != AudioAssetStatus.QUEUED) continue;
			long ticket = asset.getQueueTicket();
			if (ticket >= 0 && ticket < targetTicket) {
				position++;
			}
		}
		return position;
	}

	public void moveQueueUp(String assetId) {
		moveQueueBy(assetId, -1);
	}

	public void moveQueueDown(String assetId) {
		moveQueueBy(assetId, 1);
	}

	public boolean canMoveQueueUp(String assetId) {
		int pos = getQueuePosition(assetId);
		return pos > 1;
	}

	public boolean canMoveQueueDown(String assetId) {
		int pos = getQueuePosition(assetId);
		return pos > 0 && pos < getQueuedCount();
	}

	public void moveQueueBefore(String movingAssetId, String targetAssetId) {
		if (movingAssetId == null || targetAssetId == null) return;
		if (movingAssetId.isBlank() || targetAssetId.isBlank()) return;
		if (movingAssetId.equals(targetAssetId)) return;

		List<AudioAsset> queued = getQueuedAssetsSorted();
		int movingIdx = -1;
		int targetIdx = -1;
		for (int i = 0; i < queued.size(); i++) {
			String id = queued.get(i).getId();
			if (movingAssetId.equals(id)) movingIdx = i;
			if (targetAssetId.equals(id)) targetIdx = i;
		}
		if (movingIdx < 0 || targetIdx < 0 || movingIdx == targetIdx) return;

		AudioAsset moving = queued.remove(movingIdx);
		if (movingIdx < targetIdx) {
			targetIdx--;
		}
		queued.add(targetIdx, moving);

		long t = 1L;
		for (AudioAsset asset : queued) {
			asset.setQueueTicket(t++);
		}
		nextQueueTicket = t;
	}

	public int getQueuedCount() {
		int count = 0;
		for (AudioAsset asset : assets) {
			if (asset.getStatus() == AudioAssetStatus.QUEUED) count++;
		}
		return count;
	}

	private void moveQueueBy(String assetId, int delta) {
		if (assetId == null || assetId.isBlank() || delta == 0) return;
		List<AudioAsset> queued = getQueuedAssetsSorted();
		int idx = -1;
		for (int i = 0; i < queued.size(); i++) {
			if (assetId.equals(queued.get(i).getId())) {
				idx = i;
				break;
			}
		}
		if (idx < 0) return;
		int newIdx = idx + delta;
		if (newIdx < 0 || newIdx >= queued.size()) return;

		AudioAsset a = queued.get(idx);
		AudioAsset b = queued.get(newIdx);
		long t = a.getQueueTicket();
		a.setQueueTicket(b.getQueueTicket());
		b.setQueueTicket(t);
		normalizeQueueTickets();
	}

	private List<AudioAsset> getQueuedAssetsSorted() {
		List<AudioAsset> queued = new ArrayList<>();
		for (AudioAsset asset : assets) {
			if (asset.getStatus() == AudioAssetStatus.QUEUED && asset.getQueueTicket() >= 0) {
				queued.add(asset);
			}
		}
		queued.sort(Comparator.comparingLong(AudioAsset::getQueueTicket));
		return queued;
	}

	private void normalizeQueueTickets() {
		List<AudioAsset> queued = getQueuedAssetsSorted();
		long t = 1L;
		for (AudioAsset asset : queued) {
			asset.setQueueTicket(t++);
		}
		nextQueueTicket = t;
	}

	/**
	 * 异步执行完整音频解析（Python + librosa），更新 asset 状态与统计信息。
	 */
	public void startAnalysis(AudioAsset asset) {
		AudioAnalysisMode mode = BeatBlock.externalAudioAnalyzer != null && BeatBlock.externalAudioAnalyzer.isUseDemucs()
			? AudioAnalysisMode.DEMUCS
			: AudioAnalysisMode.BASIC;
		startAnalysis(asset, mode);
	}

	public void startAnalysis(AudioAsset asset, AudioAnalysisMode requestedMode) {
		if (asset == null) return;
		Path path = asset.getPath();
		if (path == null) return;
		if (asset.getStatus() == AudioAssetStatus.QUEUED || analysisTasks.containsKey(asset.getId())) {
			return;
		}
		asset.setStatus(AudioAssetStatus.QUEUED);
		asset.setQueueTicket(nextQueueTicket++);
		asset.setAnalysisProgressPercent(0);
		asset.setProcessingStatusText("排队中");
		asset.setBeatmap(null); // 新一轮解析期间避免继续使用旧 beatmap/stem
		asset.getFinishedSteps().clear();
		asset.setErrorMessage(null);
		asset.setInfoMessage(null);
		asset.setRequestedAnalysisMode(requestedMode != null ? requestedMode : AudioAnalysisMode.BASIC);
		asset.setResolvedAnalysisMode(null);
		asset.setCacheSource("");

		AudioAnalysisService service = BeatBlock.externalAudioAnalyzer;
		if (service == null) {
			asset.setStatus(AudioAssetStatus.FAILED);
			asset.setQueueTicket(-1L);
			asset.setErrorMessage("外部音频分析器未初始化");
			return;
		}

		Future<?> task = service.analyze(
			path,
			(step, pct) -> {
				asset.setAnalysisProgressPercent(pct);
				asset.setProcessingStatusText(stepDisplayName(step));
				// 解析 Python 步骤名映射到 UI 步骤
				switch (step) {
					case "DEPENDENCY_INSTALL" -> {
						// 依赖安装是前置步骤，不映射到 beatmap 步骤枚举
					}
					case "DEMUCS_DEP_CHECK" -> {
						asset.setInfoMessage("正在检查 Demucs 依赖...");
					}
					case "DEMUCS_DEP_INSTALL" -> {
						asset.setInfoMessage("正在自动安装 Demucs 依赖，请稍候...");
					}
					case "DEMUCS_DEP_INSTALL_SUCCESS" -> {
						asset.setInfoMessage("Demucs 依赖已就绪，继续进行分轨分析");
					}
					case "DEMUCS_DEP_INSTALL_FAILED_NETWORK" -> {
						asset.setInfoMessage("Demucs 自动安装失败：网络或证书问题");
					}
					case "DEMUCS_DEP_INSTALL_FAILED_PERMISSION" -> {
						asset.setInfoMessage("Demucs 自动安装失败：权限不足");
					}
					case "DEMUCS_DEP_INSTALL_FAILED_VERSION" -> {
						asset.setInfoMessage("Demucs 自动安装失败：Python 版本/平台不匹配");
					}
					case "DEMUCS_DEP_INSTALL_FAILED_PIP" -> {
						asset.setInfoMessage("Demucs 自动安装失败：当前 Python 缺少 pip");
					}
					case "DEMUCS_DEP_INSTALL_FAILED_DLL" -> {
						asset.setInfoMessage("Demucs 自动安装失败：二进制依赖加载失败");
					}
					case "DEMUCS_DEP_INSTALL_FAILED_UNKNOWN" -> {
						asset.setInfoMessage("Demucs 自动安装失败：未知原因");
					}
					case "DEMUCS_DEP_INSTALL_FAILED" -> {
						if (asset.getInfoMessage() == null || asset.getInfoMessage().isBlank()) {
							asset.setInfoMessage("Demucs 依赖自动安装失败");
						}
					}
					case "BPM_DETECTION" -> asset.markStepFinished(AudioAnalysisStep.BPM_DETECTION);
					case "BEAT_DETECTION" -> {
						asset.markStepFinished(AudioAnalysisStep.BEAT_DETECTION);
						asset.markStepFinished(AudioAnalysisStep.BAND_SPLIT);
					}
					case "SECTION_DETECTION" -> asset.markStepFinished(AudioAnalysisStep.SECTION_DETECTION);
					case "DEMUCS_SEPARATE" -> {
						// Demucs 模型分离中，不单独计入已完成步骤
					}
					case "DEMUCS_FALLBACK" -> {
						String info = asset.getInfoMessage();
						if (info == null || info.isBlank()) {
							asset.setInfoMessage("Demucs 不可用，已自动切换为基础分析模式（Basic）");
						} else if (!info.contains("已自动切换为基础分析模式")) {
							asset.setInfoMessage(info + "；已自动切换为基础分析模式（Basic）");
						}
					}
					case "STEM_ANALYSIS" -> asset.markStepFinished(AudioAnalysisStep.STEM_SEPARATION);
					case "WRITE_BEATMAP" -> asset.markStepFinished(AudioAnalysisStep.WRITE_BEATMAP);
					default -> {
					}
				}
			},
			(Beatmap beatmap) -> {
				asset.setBeatmap(beatmap);
				asset.setAnalysisProgressPercent(100);
				asset.setProcessingStatusText(null);
				asset.setQueueTicket(-1L);
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
				asset.setResolvedAnalysisMode(meta.hasStemSeparation() ? AudioAnalysisMode.DEMUCS : AudioAnalysisMode.BASIC);
				if (asset.getCacheSource() == null || asset.getCacheSource().isBlank()) {
					asset.setCacheSource("unknown");
				}

				asset.setStatus(AudioAssetStatus.COMPLETED);
				analysisTasks.remove(asset.getId());
			},
			err -> {
				LOGGER.warn("BeatBlock AudioAssetManager: 外部解析失败: {}", err);
				asset.setStatus(AudioAssetStatus.FAILED);
				asset.setAnalysisProgressPercent(0);
				asset.setProcessingStatusText(null);
				asset.setQueueTicket(-1L);
				asset.setErrorMessage(normalizeErrorMessage(path, err));
				analysisTasks.remove(asset.getId());
			},
			summary -> {
				if (summary.durationMs() > 0) {
					asset.setDurationSeconds(summary.durationMs() / 1000.0);
				}
				if (summary.bpm() > 0) {
					asset.setBpm(summary.bpm());
				}
				if (summary.beatCount() >= 0) {
					asset.setBeatCount(summary.beatCount());
				}
				if (summary.sectionCount() >= 0) {
					asset.setSectionCount(summary.sectionCount());
				}
				asset.setResolvedAnalysisMode("demucs".equalsIgnoreCase(summary.separationMode()) ? AudioAnalysisMode.DEMUCS : AudioAnalysisMode.BASIC);
				asset.setCacheSource(summary.cacheSource());
			},
			() -> {
				asset.setStatus(AudioAssetStatus.ANALYZING);
				asset.setQueueTicket(-1L);
				asset.setProcessingStatusText("正在分析");
			},
			asset.getRequestedAnalysisMode() == AudioAnalysisMode.DEMUCS
		);
		analysisTasks.put(asset.getId(), task);
	}

	private String stepDisplayName(String step) {
		if (step == null || step.isBlank()) return "处理中";
		return switch (step) {
			case "DEPENDENCY_INSTALL" -> "正在安装依赖";
			case "DEMUCS_DEP_CHECK" -> "检查 Demucs 依赖";
			case "DEMUCS_DEP_INSTALL" -> "自动安装 Demucs 依赖";
			case "DEMUCS_DEP_INSTALL_SUCCESS" -> "Demucs 依赖就绪";
			case "DEMUCS_DEP_INSTALL_FAILED_NETWORK" -> "Demucs 安装失败（网络/证书）";
			case "DEMUCS_DEP_INSTALL_FAILED_PERMISSION" -> "Demucs 安装失败（权限不足）";
			case "DEMUCS_DEP_INSTALL_FAILED_VERSION" -> "Demucs 安装失败（版本不匹配）";
			case "DEMUCS_DEP_INSTALL_FAILED_PIP" -> "Demucs 安装失败（缺少 pip）";
			case "DEMUCS_DEP_INSTALL_FAILED_DLL" -> "Demucs 安装失败（二进制依赖）";
			case "DEMUCS_DEP_INSTALL_FAILED_UNKNOWN" -> "Demucs 安装失败（未知原因）";
			case "DEMUCS_DEP_INSTALL_FAILED" -> "Demucs 依赖安装失败";
			case "BPM_DETECTION" -> "BPM 检测";
			case "BEAT_DETECTION" -> "踩点检测";
			case "DEMUCS_SEPARATE" -> "Demucs 茎分离";
			case "DEMUCS_FALLBACK" -> "Demucs 不可用，切换基础分析";
			case "STEM_ANALYSIS" -> "茎轨特征分析";
			case "SECTION_DETECTION" -> "段落识别";
			case "WAVEFORM" -> "生成波形预览";
			case "WRITE_BEATMAP" -> "写入 Beatmap";
			default -> step;
		};
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
