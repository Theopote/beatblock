package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * AudioAnalysisService
 * ─────────────────────────────────────────────────────────────────────────────
 * 在后台线程调用 Python analyze.py，解析进度输出，完成后加载 Beatmap。
 *
 * Python 脚本向 stdout 逐行输出：
 *   PROGRESS <step> <0-100>   — 进度更新
 *   RESULT   <json>           — 完成摘要（单行 JSON）
 *   ERROR    <message>        — 错误信息
 */
public final class AudioAnalysisService {

	/** 单线程池，串行执行分析任务（避免同时分析多首占用 CPU）*/
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-analyzer");
		t.setDaemon(true);
		return t;
	});
	private volatile String cachedPythonSummary = "Python: 检测中...";
	private volatile long nextPythonSummaryRefreshAtMs;

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
		return executor.submit(() -> runAnalysis(audioPath, onProgress, onComplete, onError));
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	/**
	 * 返回当前 Python 运行时信息（带 5 秒缓存，避免 UI 每帧触发外部进程）。
	 */
	public String getPythonRuntimeSummary() {
		long now = System.currentTimeMillis();
		if (now < nextPythonSummaryRefreshAtMs && cachedPythonSummary != null) {
			return cachedPythonSummary;
		}

		synchronized (this) {
			now = System.currentTimeMillis();
			if (now < nextPythonSummaryRefreshAtMs && cachedPythonSummary != null) {
				return cachedPythonSummary;
			}

			String summary = probePythonRuntimeSummary();
			cachedPythonSummary = summary;
			nextPythonSummaryRefreshAtMs = now + 5000L;
			return summary;
		}
	}

	// ── 内部分析流程 ──────────────────────────────────────────────────────────

	private void runAnalysis(
		Path audioPath,
		BiConsumer<String, Integer> onProgress,
		Consumer<Beatmap> onComplete,
		Consumer<String> onError
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

		// 输出文件名：将音频扩展名替换为 .beatmap
		String baseName = audioPath.getFileName().toString()
			.replaceAll("\\.[^.]+$", "");
		Path beatmapPath = outputDir.resolve(baseName + ".beatmap");

		// 解析 Python 可执行文件
		String pythonExe = resolvePythonExe(outputDir.getParent());
		if (pythonExe == null) {
			onError.accept("""
				找不到 Python 解释器。
				请确认已安装 Python，或在 config/beatblock/python_path.txt 中指定完整路径。""");
			return;
		}
		if (!isSupportedPythonVersion(pythonExe)) {
			onError.accept("检测到 Python 版本过新（>=3.13），当前音频分析依赖在该版本上可能无预编译包。\n"
				+ "请在 config/beatblock/python_path.txt 指定 Python 3.10~3.12 路径后重试。");
			return;
		}

		// 预检并补齐依赖（librosa / numpy / soundfile / scipy）
		Path requirementsPath = scriptPath.getParent().resolve("requirements.txt");
		String dependencyError = ensurePythonDependencies(pythonExe, requirementsPath);
		if (dependencyError != null) {
			onError.accept(dependencyError);
			return;
		}

		// 构建 Python 命令
		List<String> cmd = new ArrayList<>();
		cmd.add(pythonExe);
		cmd.add(scriptPath.toAbsolutePath().toString());
		cmd.add(audioPath.toAbsolutePath().toString());
		cmd.add(beatmapPath.toAbsolutePath().toString());
		cmd.add("--waveform"); // 包含波形预览数据

		Process process;
		try {
			process = new ProcessBuilder(cmd)
				.redirectErrorStream(false)
				.start();
		} catch (IOException e) {
			onError.accept("无法启动 Python：" + e.getMessage()
				+ "\n请确认 Python 已安装，路径：" + pythonExe);
			return;
		}

		String resultJson = null;
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(process.getInputStream()))) {

			String line;
			while ((line = reader.readLine()) != null) {
				resultJson = parseLine(line, onProgress, onError, resultJson);
			}
		} catch (IOException e) {
			onError.accept("读取 Python 输出失败：" + e.getMessage());
			return;
		}

		// 读取 stderr（异常栈 / 依赖缺失等）
		String stderrText = "";
		try (BufferedReader errReader = new BufferedReader(
			new InputStreamReader(process.getErrorStream()))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = errReader.readLine()) != null) sb.append(line).append('\n');
			stderrText = sb.toString();
		} catch (IOException ignored) {}

		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			onError.accept("分析被中断");
			return;
		}

		if (exitCode != 0) {
			String detail = sanitizeProcessOutput(stderrText);
			if (detail.isEmpty() && resultJson != null && !resultJson.isBlank()) {
				detail = sanitizeProcessOutput(resultJson);
			}
			String hint = explainPythonError(detail);
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

		try {
			Beatmap beatmap = BeatmapReader.read(beatmapPath);
			onComplete.accept(beatmap);
		} catch (Exception e) {
			onError.accept("读取 beatmap 文件失败：" + e.getMessage());
		}
	}

	/**
	 * 解析 Python stdout 的一行输出。
	 * 返回更新后的 resultJson（如果本行是 RESULT 行）。
	 */
	private String parseLine(
		String line,
		BiConsumer<String, Integer> onProgress,
		Consumer<String> onError,
		String currentResultJson
	) {
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
			currentResultJson = line.substring("RESULT ".length());
		} else if (line.startsWith("ERROR ")) {
			onError.accept(line.substring("ERROR ".length()));
		}
		return currentResultJson;
	}

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
				if (!txt.isEmpty() && isExecutable(txt) && isUsablePythonForAnalyzer(txt)) {
					return txt;
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
			if (!isExecutable(cand)) continue;
			if (!isUsablePythonForAnalyzer(cand)) continue;
			return cand;
		}

		// 最后兜底：即使不满足 pip 条件，也返回一个可执行 python，后续会给出更具体错误。
		for (String cand : candidates) {
			if (isExecutable(cand)) return cand;
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

		String version = readPythonVersion(exe);
		if (version == null || version.isBlank()) version = "unknown";
		return "Python: " + version + " · " + exe;
	}

	private String readPythonVersion(String pythonExe) {
		try {
			Process p = new ProcessBuilder(
				pythonExe,
				"-c",
				"import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}')"
			).redirectErrorStream(true).start();
			String out = readProcessOutput(p).trim();
			int code = waitProcess(p);
			if (code != 0) return "unknown";
			return out;
		} catch (Exception e) {
			return "unknown";
		}
	}

	private boolean isExecutable(String exe) {
		try {
			Process p = new ProcessBuilder(exe, "--version")
				.redirectErrorStream(true)
				.start();
			p.waitFor(3, TimeUnit.SECONDS);
			return p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isSupportedPythonVersion(String pythonExe) {
		try {
			Process p = new ProcessBuilder(
				pythonExe,
				"-c",
				"import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"
			).redirectErrorStream(true).start();
			String out = readProcessOutput(p).trim();
			int code = waitProcess(p);
			if (code != 0 || out.isBlank()) return true;

			String[] parts = out.split("\\.");
			if (parts.length < 2) return true;
			int major = Integer.parseInt(parts[0]);
			int minor = Integer.parseInt(parts[1]);
			if (major != 3) return false;
			return minor <= 12;
		} catch (Exception e) {
			return true;
		}
	}

	private boolean hasPipModule(String pythonExe) {
		try {
			Process p = new ProcessBuilder(
				pythonExe,
				"-c",
				"import pip"
			).redirectErrorStream(true).start();
			waitProcess(p);
			return p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isUsablePythonForAnalyzer(String pythonExe) {
		String resolvedPath = readPythonExecutablePath(pythonExe);
		if (isBlockedAutoPython(resolvedPath)) return false;
		if (!isSupportedPythonVersion(pythonExe)) return false;
		return hasPipModule(pythonExe);
	}

	private String readPythonExecutablePath(String pythonExe) {
		try {
			Process p = new ProcessBuilder(
				pythonExe,
				"-c",
				"import sys; print(sys.executable)"
			).redirectErrorStream(true).start();
			String out = readProcessOutput(p).trim();
			waitProcess(p);
			return out;
		} catch (Exception e) {
			return "";
		}
	}

	private boolean isBlockedAutoPython(String resolvedPath) {
		if (resolvedPath == null || resolvedPath.isBlank()) return false;
		String s = resolvedPath.toLowerCase();
		return s.contains("inkscape") && s.contains("python.exe");
	}

	private String ensurePythonDependencies(String pythonExe, Path requirementsPath) {
		try {
			Process check = new ProcessBuilder(
				pythonExe,
				"-c",
				"import numpy, librosa, soundfile, scipy"
			).redirectErrorStream(true).start();
			String checkOut = readProcessOutput(check);
			int checkCode = waitProcess(check);
			if (checkCode == 0) return null;

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
			String installOut = readProcessOutput(install);
			int installCode = waitProcess(install);
			if (installCode == 0) return null;

			String detail = sanitizeProcessOutput(installOut);
			if (detail.isEmpty()) detail = sanitizeProcessOutput(checkOut);
			String hint = explainPythonError(detail);
			String resolvedExe = readPythonExecutablePath(pythonExe);
			String cmdExe = resolvedExe != null && !resolvedExe.isBlank() ? resolvedExe : pythonExe;
			return "Python 依赖安装失败，请手动执行：\n"
				+ "\"" + cmdExe + "\" -m pip install -r \"" + requirementsPath.toAbsolutePath() + "\"\n"
				+ (hint.isEmpty() ? "" : ("\n" + hint + "\n"))
				+ detail;
		} catch (IOException e) {
			return "检查 Python 依赖失败：" + e.getMessage();
		}
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

	@FunctionalInterface
	public interface BiConsumer<A, B> {
		void accept(A a, B b);
	}
}

