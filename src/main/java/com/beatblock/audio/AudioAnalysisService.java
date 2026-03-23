package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapReader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * AudioAnalysisService
 * ─────────────────────────────────────────────────────────────────────────────
 * 在后台线程调用 Python analyze.py，解析进度输出，完成后加载 Beatmap。
 * <p>
 * Python 脚本向 stdout 逐行输出：
 *   PROGRESS <step> <0-100>   — 进度更新
 *   RESULT   <json>           — 完成摘要（单行 JSON）
 *   ERROR    <message>        — 错误信息
 */
public final class AudioAnalysisService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioAnalysisService.class);

	/** 单线程池，串行执行分析任务（避免同时分析多首占用 CPU）*/
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-analyzer");
		t.setDaemon(true);
		return t;
	});
	private final ExecutorService summaryExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-python-summary");
		t.setDaemon(true);
		return t;
	});
	private volatile String cachedPythonSummary = "Python: 检测中...";
	private volatile long nextPythonSummaryRefreshAtMs;
	private volatile boolean pythonSummaryRefreshInFlight;
	private static final long PYTHON_PROBE_CACHE_TTL_MS = 5 * 60 * 1000L;
	private static final long PYTHON_PROBE_TIMEOUT_MS = 3000L;
	private final Map<String, PythonProbeInfo> pythonProbeCache = new ConcurrentHashMap<>();

	/** 是否使用 Demucs 进行茎分离（需要额外 Python 依赖）。UI 可切换。 */
	private volatile boolean useDemucs = true;

	public boolean isUseDemucs() { return useDemucs; }
	public void setUseDemucs(boolean useDemucs) { this.useDemucs = useDemucs; }

	// ── 公共 API ─────────────────────────────────────────────────────────────

	/**
	 * 异步分析音频文件。
	 *
	 * @param audioPath  音频文件路径
	 * @param onProgress 进度回调，在主线程外调用（step, 0~100）
	 * @param onComplete 完成回调，传入解析好的 Beatmap
	 * @param onError    失败回调，传入错误信息
	 * @return Future，可用于取消任务
	 */
	public Future<?> analyze(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError
	) {
		return analyze(audioPath, onProgress, onComplete, onError, null, null);
	}

	public Future<?> analyze(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Runnable onStarted
	) {
		return analyze(audioPath, onProgress, onComplete, onError, null, onStarted);
	}

	public Future<?> analyze(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		Runnable onStarted
	) {
		AnalysisControl control = new AnalysisControl();
		Future<?> delegate = executor.submit(() -> {
			if (onStarted != null) {
				onStarted.run();
			}
			runAnalysis(audioPath, onProgress, onComplete, onError, onSummary, control);
		});
		return wrapCancelableFuture(delegate, control);
	}

	public void shutdown() {
		executor.shutdownNow();
		summaryExecutor.shutdownNow();
	}

	/**
	 * 删除指定音频对应的 basic/demucs 两类 beatmap 缓存。
	 *
	 * @return 删除的缓存文件数量
	 */
	public int clearBeatmapCacheForAudio(Path audioPath) {
		if (audioPath == null) return 0;
		Path outputDir;
		try {
			outputDir = AnalyzerInstaller.getBeatmapOutputDir();
		} catch (Exception e) {
			LOGGER.warn("BeatBlock AudioAnalysis: cannot resolve beatmap output dir for cache clear reason={}", e.toString());
			return 0;
		}

		Path basic = buildBeatmapPath(outputDir, audioPath, false);
		Path demucs = buildBeatmapPath(outputDir, audioPath, true);
		int removed = 0;
		removed += deleteIfExists(basic);
		removed += deleteIfExists(demucs);
		return removed;
	}

	/**
	 * 返回当前 Python 运行时信息（带 5 秒缓存，避免 UI 每帧触发外部进程）。
	 */
	public String getPythonRuntimeSummary() {
		long now = System.currentTimeMillis();
		if (cachedPythonSummary == null || cachedPythonSummary.isBlank()) {
			cachedPythonSummary = "Python: 检测中...";
		}

		if (now >= nextPythonSummaryRefreshAtMs) {
			triggerPythonSummaryRefreshAsync();
		}

		return cachedPythonSummary;
	}

	private void triggerPythonSummaryRefreshAsync() {
		if (pythonSummaryRefreshInFlight) return;

		synchronized (this) {
			if (pythonSummaryRefreshInFlight) return;
			pythonSummaryRefreshInFlight = true;
		}

		summaryExecutor.submit(() -> {
			try {
				String summary = probePythonRuntimeSummary();
				if (!summary.isBlank()) {
					cachedPythonSummary = summary;
				}
			} catch (Exception e) {
				if (cachedPythonSummary == null || cachedPythonSummary.isBlank()) {
					cachedPythonSummary = "Python: 检测失败（" + e.getClass().getSimpleName() + "）";
				}
			} finally {
				nextPythonSummaryRefreshAtMs = System.currentTimeMillis() + 5000L;
				pythonSummaryRefreshInFlight = false;
			}
		});
	}

	// ── 内部分析流程 ──────────────────────────────────────────────────────────

	private void runAnalysis(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		AnalysisControl control
	) {
		boolean taskUseDemucs = useDemucs;
		runAnalysisInternal(audioPath, onProgress, onComplete, onError, onSummary, control, true, taskUseDemucs);
	}

	private void runAnalysisInternal(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError,
		Consumer<AnalysisSummary> onSummary,
		AnalysisControl control,
		boolean allowDemucsFallback,
		boolean analysisUseDemucs
	) {
		// 确保脚本与输出目录已就绪
		Path scriptPath;
		Path outputDir;
		try {
			scriptPath = AnalyzerInstaller.ensureInstalled();
			outputDir = AnalyzerInstaller.getBeatmapOutputDir();
		} catch (AnalyzerInstaller.AnalyzerInstallException e) {
			onError.accept("脚本安装失败：" + e.getMessage());
			return;
		}

		// 输出文件名：带路径指纹，避免不同目录同名音频互相覆盖
		Path beatmapPath = buildBeatmapPath(outputDir, audioPath, analysisUseDemucs);

		// 若 beatmap 文件已存在且可读，直接加载，跳过 Python 分析（避免重复运行耗时分析）
		// 若 analyzerVersion 低于当前最低兼容版本（2.0），则废弃缓存并重新分析
		if (Files.isRegularFile(beatmapPath)) {
			try {
				long fileSize = Files.size(beatmapPath);
				if (fileSize > 16) {
					Beatmap cached = BeatmapReader.read(beatmapPath);
					if (isBeatmapVersionCompatible(cached, analysisUseDemucs)) {
						LOGGER.info("BeatBlock AudioAnalysis: beatmap cache hit, skipping Python path={} beatmap={}",
							audioPath.getFileName(), beatmapPath.getFileName());
						onComplete.accept(cached);
						return;
					} else {
						LOGGER.info("BeatBlock AudioAnalysis: beatmap cache stale (analyzerVersion={}), re-analyzing path={}",
							cached.meta != null ? cached.meta.analyzerVersion() : "null",
							audioPath.getFileName());
						// fall through to Python re-analysis
					}
				}
			} catch (Exception e) {
				LOGGER.warn("BeatBlock AudioAnalysis: existing beatmap unreadable, re-analyzing path={} reason={}",
					audioPath.getFileName(), e.getMessage());
				// fall through to Python re-analysis
			}
		}

		// 解析 Python 可执行文件
		String pythonExe = resolvePythonExe(outputDir.getParent());
		if (pythonExe == null) {
			onError.accept("""
				找不到 Python 解释器。
				请确认已安装 Python，或在 config/beatblock/python_path.txt 中指定完整路径。""");
			return;
		}
		if (control.isCancelled()) {
			onError.accept("分析被取消");
			return;
		}
		PythonProbeInfo pythonProbe = getPythonProbeInfo(pythonExe);
		if (!pythonProbe.probeOk) {
			onError.accept("无法探测 Python 运行环境：" + pythonProbe.detail);
			return;
		}
		if (!pythonProbe.isSupportedVersion()) {
			onError.accept("检测到 Python 版本过新（>=3.13），当前音频分析依赖在该版本上可能无预编译包。\n"
				+ "请在 config/beatblock/python_path.txt 指定 Python 3.10~3.12 路径后重试。");
			return;
		}
		if (!pythonProbe.hasPip) {
			onError.accept("当前 Python 没有可用 pip。请先执行 python -m ensurepip --upgrade。\n"
				+ "当前解释器：" + pythonProbe.executablePath);
			return;
		}

		// 预检并补齐依赖（librosa / numpy / soundfile / scipy）
		onProgress.accept("DEPENDENCY_INSTALL", 0);
		Path requirementsPath = scriptPath.getParent().resolve("requirements.txt");
		String dependencyError = ensurePythonDependencies(pythonExe, requirementsPath, control, onProgress, analysisUseDemucs);
		if (dependencyError != null) {
			if (allowDemucsFallback && analysisUseDemucs && looksLikeDemucsMissing(dependencyError)) {
				LOGGER.warn("BeatBlock AudioAnalysis: Demucs dependencies unavailable, fallback to basic mode path={} reason={}",
					audioPath.getFileName(), sanitizeProcessOutput(dependencyError));
				onProgress.accept("DEMUCS_FALLBACK", 100);
				runAnalysisInternal(audioPath, onProgress, onComplete, onError, onSummary, control, false, false);
				return;
			}
			if (control.isCancelled()) {
				onError.accept("分析被取消");
				return;
			}
			onError.accept(dependencyError);
			return;
		}
		onProgress.accept("DEPENDENCY_INSTALL", 100);

		// 构建 Python 命令
		List<String> cmd = new ArrayList<>();
		cmd.add(pythonExe);
		cmd.add(scriptPath.toAbsolutePath().toString());
		cmd.add(audioPath.toAbsolutePath().toString());
		cmd.add(beatmapPath.toAbsolutePath().toString());
		cmd.add("--waveform"); // 包含波形预览数据
		if (analysisUseDemucs) {
			cmd.add("--demucs");
		}

		Process process;
		try {
			process = new ProcessBuilder(cmd)
				.redirectErrorStream(false)
				.start();
			control.attachProcess(process);
		} catch (IOException e) {
			onError.accept("无法启动 Python：" + e.getMessage()
				+ "\n请确认 Python 已安装，路径：" + pythonExe);
			return;
		}

		StdoutParseResult stdoutResult;
		FutureTask<String> stdoutTask = new FutureTask<>(
			() -> consumeStdout(process.getInputStream(), onProgress)
		);
		FutureTask<String> stderrTask = new FutureTask<>(
			() -> consumeLines(process.getErrorStream())
		);
		Thread stdoutThread = new Thread(stdoutTask, "beatblock-analyzer-stdout");
		stdoutThread.setDaemon(true);
		stdoutThread.start();
		Thread stderrThread = new Thread(stderrTask, "beatblock-analyzer-stderr");
		stderrThread.setDaemon(true);
		stderrThread.start();

		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			stdoutTask.cancel(true);
			stderrTask.cancel(true);
			onError.accept("分析被中断");
			return;
		} finally {
			control.clearProcess(process);
		}

		String stderrText;
		try {
			stdoutResult = parseStdoutResult(stdoutTask.get());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			onError.accept("读取 Python 输出时被中断");
			return;
		} catch (ExecutionException e) {
			onError.accept("读取 Python 输出失败：" + rootMessage(e));
			return;
		}

		try {
			stderrText = stderrTask.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			onError.accept("读取 Python 错误输出时被中断");
			return;
		} catch (ExecutionException e) {
			stderrText = "读取 stderr 失败：" + rootMessage(e);
		}

		if (exitCode != 0) {
			String detail = sanitizeProcessOutput(stderrText);
			if (detail.isEmpty() && stdoutResult.errorText != null && !stdoutResult.errorText.isBlank()) {
				detail = sanitizeProcessOutput(stdoutResult.errorText);
			}
			if (detail.isEmpty() && stdoutResult.resultJson != null && !stdoutResult.resultJson.isBlank()) {
				detail = sanitizeProcessOutput(stdoutResult.resultJson);
			}
			String hint = explainPythonError(detail);
			if (allowDemucsFallback && analysisUseDemucs && looksLikeDemucsMissing(detail + "\n" + hint)) {
				LOGGER.warn("BeatBlock AudioAnalysis: Demucs runtime unavailable, fallback to basic mode path={} detail={}",
					audioPath.getFileName(), detail);
				onProgress.accept("DEMUCS_FALLBACK", 100);
				runAnalysisInternal(audioPath, onProgress, onComplete, onError, onSummary, control, false, false);
				return;
			}
			if (!detail.isEmpty()) {
				if (!hint.isEmpty()) {
					onError.accept("Python 分析脚本退出码：" + exitCode + "\n" + hint + "\n\n" + detail);
				} else {
					onError.accept("Python 分析脚本退出码：" + exitCode + "\n" + detail);
				}
			} else {
				if (!hint.isEmpty()) {
					onError.accept("Python 分析脚本退出码：" + exitCode + "\n" + hint);
				} else {
					onError.accept("Python 分析脚本退出码：" + exitCode);
				}
			}
			return;
		}

		if (onSummary != null && stdoutResult.resultJson != null && !stdoutResult.resultJson.isBlank()) {
			AnalysisSummary summary = parseResultSummary(stdoutResult.resultJson);
			if (summary != null) {
				onSummary.accept(summary);
			}
		}

		try {
			Beatmap beatmap = BeatmapReader.read(beatmapPath);
			onComplete.accept(beatmap);
		} catch (Exception e) {
			onError.accept("读取 beatmap 文件失败：" + e.getMessage());
		}
	}

	/**
	 * 检查缓存的 beatmap 是否与当前分析器版本兼容。
	 * v1.x: low/mid/high 物理频段（已废弃）
	 * v2.x: HPSS + 固定 kick/snare/hihat 三轨道
	 * v3.x: HPSS + 谱聚类自适应轨道（当前）
	 */
	private static boolean isBeatmapVersionCompatible(Beatmap beatmap, boolean expectDemucs) {
		if (beatmap == null || beatmap.meta == null) return false;
		String ver = beatmap.meta.analyzerVersion();
		if (ver == null || ver.isBlank()) return false;
		// 只接受 3.x 及以上的版本（3.0 引入谱聚类自适应轨道）
		boolean versionOk;
		try {
			int major = Integer.parseInt(ver.split("\\.")[0]);
			versionOk = major >= 3;
		} catch (NumberFormatException e) {
			return false;
		}
		if (!versionOk) return false;

		boolean hasStemSeparation = beatmap.meta.hasStemSeparation();
		if (expectDemucs) {
			return hasStemSeparation;
		}
		return !hasStemSeparation;
	}

	private String sanitizeBeatmapBaseName(String baseName) {
		if (baseName == null || baseName.isBlank()) return "audio";
		String sanitized = baseName.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_+", "_");
		if (sanitized.isBlank()) return "audio";
		return sanitized;
	}

	private Path buildBeatmapPath(Path outputDir, Path audioPath, boolean demucsMode) {
		String fileName = audioPath != null && audioPath.getFileName() != null
			? audioPath.getFileName().toString()
			: "audio";
		String baseName = sanitizeBeatmapBaseName(fileName.replaceAll("\\.[^.]+$", ""));
		String normalized = audioPath == null
			? "audio"
			: audioPath.toAbsolutePath().normalize().toString().toLowerCase();
		String audioFingerprint = Integer.toHexString(normalized.hashCode());
		String separationTag = demucsMode ? "demucs" : "basic";
		return outputDir.resolve(baseName + "-" + audioFingerprint + "-" + separationTag + ".beatmap");
	}

	private int deleteIfExists(Path p) {
		if (p == null) return 0;
		try {
			return Files.deleteIfExists(p) ? 1 : 0;
		} catch (IOException e) {
			LOGGER.warn("BeatBlock AudioAnalysis: failed to delete cache file={} reason={}", p.getFileName(), e.toString());
			return 0;
		}
	}

	private AnalysisSummary parseResultSummary(String resultJson) {
		if (resultJson == null || resultJson.isBlank()) return null;
		try {
			JsonObject o = JsonParser.parseString(resultJson).getAsJsonObject();
			float bpm = o.has("bpm") ? o.get("bpm").getAsFloat() : 0f;
			int beatCount = o.has("beat_count") ? o.get("beat_count").getAsInt() : 0;
			int sectionCount = o.has("section_count") ? o.get("section_count").getAsInt() : 0;
			long durationMs = o.has("duration_ms") ? o.get("duration_ms").getAsLong() : 0L;
			return new AnalysisSummary(bpm, beatCount, sectionCount, durationMs);
		} catch (Exception ignored) {
			return null;
		}
	}

	private String consumeStdout(
		InputStream stdout,
		BiConsumer<String, Integer> onProgress
	) throws IOException {
		String resultJson = null;
		StringBuilder errorBuf = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("PROGRESS ")) {
					String[] parts = line.split(" ", 3);
					if (parts.length == 3) {
						try {
							String step = parts[1];
							int pct = Integer.parseInt(parts[2].trim());
							onProgress.accept(step, pct);
						} catch (NumberFormatException ignored) {}
					}
				} else if (line.startsWith("RESULT ")) {
					resultJson = line.substring("RESULT ".length());
				} else if (line.startsWith("ERROR ")) {
					errorBuf.append(line.substring("ERROR ".length())).append('\n');
				}
			}
		}
		return toStdoutResult(resultJson, errorBuf.toString());
	}

	private String toStdoutResult(String resultJson, String errorText) {
		String result = resultJson == null ? "" : resultJson.replace("\u001f", " ");
		String error = errorText == null ? "" : errorText.replace("\u001f", " ");
		return result + "\u001f" + error;
	}

	private StdoutParseResult parseStdoutResult(String raw) {
		if (raw == null) return new StdoutParseResult("", "");
		int sep = raw.indexOf('\u001f');
		if (sep < 0) return new StdoutParseResult(raw, "");
		String result = raw.substring(0, sep);
		String error = sep + 1 < raw.length() ? raw.substring(sep + 1) : "";
		return new StdoutParseResult(result, error);
	}

	private String consumeLines(InputStream input) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	private String rootMessage(Throwable t) {
		Throwable root = t;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		if (root.getMessage() != null && !root.getMessage().isBlank()) {
			return root.getMessage();
		}
		return root.getClass().getSimpleName();
	}

	private record StdoutParseResult(String resultJson, String errorText) {}

	// ── Python 路径解析 ───────────────────────────────────────────────────────

	/**
	 * 查找 Python 可执行文件。
	 * 1) config/beatblock/python_path.txt
	 * 2) PATH 中的 python3
	 * 3) PATH 中的 python
	 */
	private String resolvePythonExe(Path configDir) {
		// 用户自定义
		Path custom = configDir.resolve("python_path.txt");
		if (Files.exists(custom)) {
			try {
				String txt = Files.readString(custom).trim();
				String customExe = normalizePythonCandidate(txt);
				if (!customExe.isEmpty() && isUsablePythonForAnalyzer(customExe)) {
					return customExe;
				}
			} catch (IOException ignored) {}
		}

		List<String> candidates = new ArrayList<>();
		String localAppData = System.getenv("LOCALAPPDATA");
		if (localAppData != null && !localAppData.isBlank()) {
			candidates.add(localAppData + "\\Programs\\Python\\Python312\\python.exe");
			candidates.add(localAppData + "\\Programs\\Python\\Python311\\python.exe");
			candidates.add(localAppData + "\\Programs\\Python\\Python310\\python.exe");
			candidates.add(localAppData + "\\Python\\pythoncore-3.12-64\\python.exe");
			candidates.add(localAppData + "\\Python\\pythoncore-3.11-64\\python.exe");
			candidates.add(localAppData + "\\Python\\pythoncore-3.10-64\\python.exe");
		}
		candidates.addAll(List.of("python", "python3"));

		for (String cand : candidates) {
			String exe = normalizePythonCandidate(cand);
			if (exe.isEmpty()) continue;
			if (!isUsablePythonForAnalyzer(exe)) continue;
			return exe;
		}

		return null;
	}

	private String probePythonRuntimeSummary() {
		Path configDir = AnalyzerInstaller.getScriptPath().getParent();
		if (configDir == null) return "Python: 未检测到配置目录";

		String exe = resolvePythonExe(configDir);
		if (exe == null) {
			return "Python: 未找到（请配置 config/beatblock/python_path.txt）";
		}

		PythonProbeInfo info = getPythonProbeInfo(exe);
		String version = info.versionString();
		if (version.isBlank()) version = "unknown";
		String path = (info.executablePath != null && !info.executablePath.isBlank())
			? info.executablePath
			: exe;
		return "Python: " + version + " · " + path;
	}

	private boolean isUsablePythonForAnalyzer(String pythonExe) {
		PythonProbeInfo info = getPythonProbeInfo(pythonExe);
		if (!info.probeOk) return false;
		if (isBlockedAutoPython(info.executablePath)) return false;
		if (!info.isSupportedVersion()) return false;
		return info.hasPip;
	}

	private PythonProbeInfo getPythonProbeInfo(String pythonExe) {
		String normalizedExe = normalizePythonCandidate(pythonExe);
		if (normalizedExe.isEmpty()) {
			return PythonProbeInfo.failed("Python 路径为空");
		}

		long now = System.currentTimeMillis();
		PythonProbeInfo cached = pythonProbeCache.get(normalizedExe);
		if (cached != null && (now - cached.checkedAtMs) <= PYTHON_PROBE_CACHE_TTL_MS) {
			return cached;
		}

		PythonProbeInfo fresh = probePythonInfoOnce(normalizedExe);
		pythonProbeCache.put(normalizedExe, fresh);
		return fresh;
	}

	private String normalizePythonCandidate(String raw) {
		if (raw == null) return "";
		String s = raw.trim();
		if (s.isEmpty()) return "";

		if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
			s = s.substring(1, s.length() - 1).trim();
		}

		if (s.contains("%")) {
			s = expandWindowsEnv(s);
		}

		return s;
	}

	private String expandWindowsEnv(String text) {
		String out = text;
		int start = out.indexOf('%');
		while (start >= 0) {
			int end = out.indexOf('%', start + 1);
			if (end <= start + 1) break;
			String name = out.substring(start + 1, end);
			String value = System.getenv(name);
			if (value == null) value = "";
			out = out.substring(0, start) + value + out.substring(end + 1);
			start = out.indexOf('%', start + value.length());
		}
		return out;
	}

	private PythonProbeInfo probePythonInfoOnce(String pythonExe) {
		try {
			String probeScript = String.join("\n",
				"import sys",
				"try:",
				"    import pip",
				"    has_pip = 1",
				"except Exception:",
				"    has_pip = 0",
				"print(f'{sys.version_info.major}|{sys.version_info.minor}|{sys.version_info.micro}|{has_pip}|{sys.executable}')"
			);

			Process p = new ProcessBuilder(
				pythonExe,
				"-c",
				probeScript
			).redirectErrorStream(true).start();

			boolean finished = p.waitFor(PYTHON_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			if (!finished) {
				p.destroyForcibly();
				return PythonProbeInfo.failed("探测超时");
			}

			String out = readProcessOutput(p).trim();
			if (p.exitValue() != 0) {
				String detail = sanitizeProcessOutput(out);
				if (detail.isBlank()) detail = "退出码=" + p.exitValue();
				return PythonProbeInfo.failed(detail);
			}

			String[] parts = out.split("\\|", 5);
			if (parts.length < 5) {
				return PythonProbeInfo.failed("探测输出格式异常: " + sanitizeProcessOutput(out));
			}

			int major = Integer.parseInt(parts[0].trim());
			int minor = Integer.parseInt(parts[1].trim());
			int micro = Integer.parseInt(parts[2].trim());
			boolean hasPip = "1".equals(parts[3].trim());
			String executable = parts[4].trim();
			return PythonProbeInfo.ok(major, minor, micro, hasPip, executable);
		} catch (Exception e) {
			return PythonProbeInfo.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private boolean isBlockedAutoPython(String resolvedPath) {
		if (resolvedPath == null || resolvedPath.isBlank()) return false;
		String s = resolvedPath.toLowerCase();
		return s.contains("inkscape") && s.contains("python.exe");
	}

	private String ensurePythonDependencies(
		String pythonExe,
		Path requirementsPath,
		AnalysisControl control,
		BiConsumer<String, Integer> onProgress,
		boolean analysisUseDemucs
	) {
		try {
			if (control.isCancelled()) return "分析被取消";

			Process check = new ProcessBuilder(
				pythonExe,
				"-c",
				"import numpy, librosa, soundfile, scipy"
			).redirectErrorStream(true).start();
			control.attachProcess(check);
			String checkOut = readProcessOutput(check);
			int checkCode = waitProcess(check);
			control.clearProcess(check);
			if (control.isCancelled()) return "分析被取消";
			if (checkCode != 0) {
				if (!Files.isRegularFile(requirementsPath)) {
					return "Python 依赖缺失，且找不到 requirements.txt：" + requirementsPath;
				}

				Process install = new ProcessBuilder(
					pythonExe,
					"-m",
					"pip",
					"install",
					"-r",
					requirementsPath.toAbsolutePath().toString()
				).redirectErrorStream(true).start();
				control.attachProcess(install);
				String installOut = readProcessOutput(install);
				int installCode = waitProcess(install);
				control.clearProcess(install);
				if (control.isCancelled()) return "分析被取消";
				if (installCode != 0) {
					String detail = sanitizeProcessOutput(installOut);
					if (detail.isEmpty()) detail = sanitizeProcessOutput(checkOut);
					String hint = explainPythonError(detail);
					String resolvedExe = getPythonProbeInfo(pythonExe).executablePath;
					String cmdExe = resolvedExe != null && !resolvedExe.isBlank() ? resolvedExe : pythonExe;
					return "Python 依赖安装失败，请手动执行：\n"
						+ "\"" + cmdExe + "\" -m pip install -r \"" + requirementsPath.toAbsolutePath() + "\"\n"
						+ (hint.isEmpty() ? "" : ("\n" + hint + "\n"))
						+ detail;
				}
			}

			if (analysisUseDemucs) {
				onProgress.accept("DEMUCS_DEP_CHECK", 0);
				Process demucsCheck = new ProcessBuilder(
					pythonExe,
					"-c",
					"import demucs.api, torch"
				).redirectErrorStream(true).start();
				control.attachProcess(demucsCheck);
				String demucsCheckOut = readProcessOutput(demucsCheck);
				int demucsCheckCode = waitProcess(demucsCheck);
				control.clearProcess(demucsCheck);
				if (control.isCancelled()) return "分析被取消";

				if (demucsCheckCode != 0) {
					onProgress.accept("DEMUCS_DEP_INSTALL", 0);
					Process demucsInstall = new ProcessBuilder(
						pythonExe,
						"-m",
						"pip",
						"install",
						"demucs",
						"torch"
					).redirectErrorStream(true).start();
					control.attachProcess(demucsInstall);
					String demucsInstallOut = readProcessOutput(demucsInstall);
					int demucsInstallCode = waitProcess(demucsInstall);
					control.clearProcess(demucsInstall);
					if (control.isCancelled()) return "分析被取消";

					if (demucsInstallCode != 0) {
						String detail = sanitizeProcessOutput(demucsInstallOut);
						if (detail.isEmpty()) detail = sanitizeProcessOutput(demucsCheckOut);
						onProgress.accept(demucsInstallFailureStep(detail), 100);
						onProgress.accept("DEMUCS_DEP_INSTALL_FAILED", 100);
						String hint = explainPythonError(detail);
						String resolvedExe = getPythonProbeInfo(pythonExe).executablePath;
						String cmdExe = resolvedExe != null && !resolvedExe.isBlank() ? resolvedExe : pythonExe;
						return "Demucs 依赖安装失败，请手动执行：\n"
							+ "\"" + cmdExe + "\" -m pip install demucs torch\n"
							+ (hint.isEmpty() ? "" : ("\n" + hint + "\n"))
							+ detail;
					}
					onProgress.accept("DEMUCS_DEP_INSTALL", 100);
					onProgress.accept("DEMUCS_DEP_INSTALL_SUCCESS", 100);
				} else {
					onProgress.accept("DEMUCS_DEP_CHECK", 100);
					onProgress.accept("DEMUCS_DEP_INSTALL_SUCCESS", 100);
				}
			}

			return null;
		} catch (IOException e) {
			if (control.isCancelled()) return "分析被取消";
			return "检查 Python 依赖失败：" + e.getMessage();
		}
	}


	private Future<?> wrapCancelableFuture(Future<?> delegate, AnalysisControl control) {
		return new Future<>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				control.cancelRunningProcess();
				return delegate.cancel(true);
			}

			@Override
			public boolean isCancelled() {
				return delegate.isCancelled();
			}

			@Override
			public boolean isDone() {
				return delegate.isDone();
			}

			@Override
			public Object get() throws InterruptedException, ExecutionException {
				return delegate.get();
			}

			@Override
			public Object get(long timeout, @NonNull TimeUnit unit)
				throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
				return delegate.get(timeout, unit);
			}
		};
	}

	private String readProcessOutput(Process process) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = br.readLine()) != null) sb.append(line).append('\n');
		}
		return sb.toString();
	}

	private int waitProcess(Process process) {
		try {
			return process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return -1;
		}
	}

	private String sanitizeProcessOutput(String raw) {
		if (raw == null) return "";
		String text = raw.trim();
		if (text.isEmpty()) return "";
		if (text.length() <= 1200) return text;
		return text.substring(text.length() - 1200);
	}

	private String explainPythonError(String detail) {
		if (detail == null || detail.isBlank()) return "";
		String s = detail.toLowerCase();

		if ((s.contains("no module named demucs") || s.contains("no module named torch")
			|| s.contains("modulenotfounderror: demucs") || s.contains("modulenotfounderror: torch"))) {
			return "检测到 Demucs 依赖缺失。请安装 demucs 与 torch，建议使用 Python 3.10~3.12。";
		}

		if (s.contains("no module named") || s.contains("modulenotfounderror")) {
			return "检测到 Python 依赖缺失。请确认当前 Python 环境已安装 librosa/numpy/soundfile/scipy。";
		}
		if (s.contains("dll load failed") || s.contains("winerror 126") || s.contains("winerror 193")) {
			return "检测到 Python 二进制依赖加载失败。请检查 Python 位数与系统匹配，并安装 Microsoft Visual C++ Redistributable。";
		}
		if (s.contains("permission denied") || s.contains("access is denied") || s.contains("errno 13")) {
			return "检测到权限不足。请用有权限的目录运行，或检查防病毒软件是否拦截 Python/pip 写入。";
		}
		if (s.contains("could not find a version that satisfies") || s.contains("no matching distribution found")) {
			return "pip 未找到可安装版本。请检查 Python 版本是否过旧，或切换可用的 pip 镜像源。";
		}
		if (s.contains("ssl") || s.contains("certificate verify failed")) {
			return "检测到网络证书/SSL 问题。请检查网络代理与证书环境，必要时更换 pip 源。";
		}
		if (s.contains("pip is not recognized") || s.contains("no module named pip")) {
			if (s.contains("inkscape") && s.contains("python.exe")) {
				return "当前自动选择到了 Inkscape 自带 Python（不含 pip），不能用于音频分析。"
					+ "请在 config/beatblock/python_path.txt 指定 Python 3.10~3.12 的 python.exe。";
			}
			return "当前 Python 没有可用 pip。请先执行 python -m ensurepip --upgrade。";
		}
		if (s.contains("ffmpeg") && (s.contains("not found") || s.contains("no such file"))) {
			return "检测到 ffmpeg 不可用。请将 ffmpeg.exe 放在 Minecraft 目录，或在 config/beatblock/ffmpeg_path.txt 指定路径。";
		}
		return "";
	}

	private boolean looksLikeDemucsMissing(String detail) {
		if (detail == null || detail.isBlank()) return false;
		String s = detail.toLowerCase();
		return s.contains("demucs") || s.contains("torch") || s.contains("demucs.api");
	}

	private String demucsInstallFailureStep(String detail) {
		if (detail == null || detail.isBlank()) return "DEMUCS_DEP_INSTALL_FAILED_UNKNOWN";
		String s = detail.toLowerCase();
		if (s.contains("permission denied") || s.contains("access is denied") || s.contains("errno 13")) {
			return "DEMUCS_DEP_INSTALL_FAILED_PERMISSION";
		}
		if (s.contains("ssl") || s.contains("certificate verify failed")
			|| s.contains("timed out") || s.contains("connection") || s.contains("proxy")) {
			return "DEMUCS_DEP_INSTALL_FAILED_NETWORK";
		}
		if (s.contains("could not find a version that satisfies") || s.contains("no matching distribution found")) {
			return "DEMUCS_DEP_INSTALL_FAILED_VERSION";
		}
		if (s.contains("pip is not recognized") || s.contains("no module named pip")) {
			return "DEMUCS_DEP_INSTALL_FAILED_PIP";
		}
		if (s.contains("dll load failed") || s.contains("winerror 126") || s.contains("winerror 193")) {
			return "DEMUCS_DEP_INSTALL_FAILED_DLL";
		}
		return "DEMUCS_DEP_INSTALL_FAILED_UNKNOWN";
	}

	@FunctionalInterface
	public interface BiConsumer<A, B> {
		void accept(A a, B b);
	}

	public record AnalysisSummary(float bpm, int beatCount, int sectionCount, long durationMs) {}

	private static final class PythonProbeInfo {
		private final boolean probeOk;
		private final int major;
		private final int minor;
		private final int micro;
		private final boolean hasPip;
		private final String executablePath;
		private final String detail;
		private final long checkedAtMs;

		private PythonProbeInfo(
			boolean probeOk,
			int major,
			int minor,
			int micro,
			boolean hasPip,
			String executablePath,
			String detail,
			long checkedAtMs
		) {
			this.probeOk = probeOk;
			this.major = major;
			this.minor = minor;
			this.micro = micro;
			this.hasPip = hasPip;
			this.executablePath = executablePath;
			this.detail = detail;
			this.checkedAtMs = checkedAtMs;
		}

		private static PythonProbeInfo ok(int major, int minor, int micro, boolean hasPip, String executablePath) {
			return new PythonProbeInfo(true, major, minor, micro, hasPip, executablePath, "", System.currentTimeMillis());
		}

		private static PythonProbeInfo failed(String detail) {
			return new PythonProbeInfo(false, 0, 0, 0, false, "", detail == null ? "" : detail, System.currentTimeMillis());
		}

		private boolean isSupportedVersion() {
			return major == 3 && minor <= 12;
		}

		private String versionString() {
			if (!probeOk) return "unknown";
			return major + "." + minor + "." + micro;
		}
	}

	private static final class AnalysisControl {
		private final AtomicReference<Process> activeProcess = new AtomicReference<>();
		private volatile boolean cancelled;

		private void attachProcess(Process process) {
			if (process == null) return;
			activeProcess.set(process);
			if (cancelled) {
				process.destroyForcibly();
			}
		}

		private void clearProcess(Process process) {
			activeProcess.compareAndSet(process, null);
		}

		private void cancelRunningProcess() {
			cancelled = true;
			Process p = activeProcess.getAndSet(null);
			if (p != null) {
				p.destroyForcibly();
			}
		}

		private boolean isCancelled() {
			return cancelled;
		}
	}
}

