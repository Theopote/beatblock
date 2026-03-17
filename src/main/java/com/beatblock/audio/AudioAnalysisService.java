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

		// 读取 stderr（异常栈）
		try (BufferedReader errReader = new BufferedReader(
			new InputStreamReader(process.getErrorStream()))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = errReader.readLine()) != null) sb.append(line).append('\n');
			if (!sb.isEmpty()) {
				// 这里暂不处理，必要时可以写入日志
			}
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
			onError.accept("Python 分析脚本退出码：" + exitCode);
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
				if (!txt.isEmpty() && isExecutable(txt)) return txt;
			} catch (IOException ignored) {}
		}
		for (String cand : List.of("python3", "python")) {
			if (isExecutable(cand)) return cand;
		}
		return null;
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

	@FunctionalInterface
	public interface BiConsumer<A, B> {
		void accept(A a, B b);
	}
}

