package com.beatblock.audio;

import net.fabricmc.loader.api.FabricLoader;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 音频转换服务：后台调用 ffmpeg 将不支持格式转换为 MP3。
 */
public final class AudioConversionService {

	private static final Pattern DURATION_PATTERN = Pattern.compile("Duration:\\s*([0-9:.]+)");
	private static final Pattern TIME_PATTERN = Pattern.compile("time=([0-9:.]+)");

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-audio-converter");
		t.setDaemon(true);
		return t;
	});

	public Future<?> convertToMp3Async(
		Path inputAudio,
		ProgressCallback onProgress,
		Consumer<Path> onComplete,
		Consumer<String> onError
	) {
		return executor.submit(() -> convertToMp3(inputAudio, onProgress, onComplete, onError));
	}

	private void convertToMp3(
		Path inputAudio,
		ProgressCallback onProgress,
		Consumer<Path> onComplete,
		Consumer<String> onError
	) {
		if (inputAudio == null || !Files.isRegularFile(inputAudio)) {
			onError.accept("待转换文件不存在。");
			return;
		}

		String fileName = inputAudio.getFileName() != null ? inputAudio.getFileName().toString() : "";
		if (fileName.toLowerCase().endsWith(".mp3")) {
			onProgress.accept("源文件已是 MP3，跳过转换。", 100);
			onComplete.accept(inputAudio);
			return;
		}

		String ffmpeg = resolveFfmpegExecutable();
		if (ffmpeg == null) {
			Path gameDir = FabricLoader.getInstance().getGameDir();
			onError.accept(
				"找不到 ffmpeg。请将 ffmpeg.exe 放到游戏目录（" + gameDir + "）下，"
					+ "或在 config/beatblock/ffmpeg_path.txt 中写入 ffmpeg.exe 的完整路径。"
			);
			return;
		}
		onProgress.accept("已找到 ffmpeg，准备转换...", 2);

		Path output = buildOutputPath(inputAudio);
		List<String> cmd = new ArrayList<>();
		cmd.add(ffmpeg);
		cmd.add("-y");
		cmd.add("-i");
		cmd.add(inputAudio.toAbsolutePath().toString());
		cmd.add("-vn");
		cmd.add("-codec:a");
		cmd.add("libmp3lame");
		cmd.add("-q:a");
		cmd.add("2");
		cmd.add(output.toAbsolutePath().toString());

		Process process;
		try {
			process = new ProcessBuilder(cmd)
				.redirectErrorStream(true)
				.start();
		} catch (IOException e) {
			onError.accept("无法启动 ffmpeg: " + e.getMessage());
			return;
		}

		StringBuilder out = new StringBuilder();
		double[] totalDurationSec = {0.0};
		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = br.readLine()) != null) {
				out.append(line).append('\n');
				parseProgressLine(line, totalDurationSec, onProgress);
			}
		} catch (IOException ignored) {
		}

		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			onError.accept("音频转换被中断。");
			return;
		}

		if (exitCode != 0 || !Files.isRegularFile(output)) {
			onError.accept("ffmpeg 转换失败。" + summarizeProcessOutput(out));
			return;
		}

		onProgress.accept("转换完成。", 100);
		onComplete.accept(output);
	}

	private void parseProgressLine(String line, double[] totalDurationSec, ProgressCallback onProgress) {
		if (line == null || line.isBlank()) return;

		Matcher durationMatcher = DURATION_PATTERN.matcher(line);
		if (durationMatcher.find()) {
			double duration = parseHmsSeconds(durationMatcher.group(1));
			if (duration > 0) {
				totalDurationSec[0] = duration;
				onProgress.accept("读取音频时长...", 4);
			}
		}

		Matcher timeMatcher = TIME_PATTERN.matcher(line);
		if (timeMatcher.find()) {
			double current = parseHmsSeconds(timeMatcher.group(1));
			if (current <= 0) return;
			if (totalDurationSec[0] > 0) {
				int pct = (int) Math.round((current / totalDurationSec[0]) * 100.0);
				pct = Math.max(5, Math.min(98, pct));
				onProgress.accept("FFmpeg 转换中", pct);
			} else {
				onProgress.accept("FFmpeg 转换中（无法估时）", 10);
			}
		}
	}

	private double parseHmsSeconds(String hms) {
		if (hms == null || hms.isBlank()) return 0.0;
		String[] parts = hms.trim().split(":");
		if (parts.length != 3) return 0.0;
		try {
			double h = Double.parseDouble(parts[0]);
			double m = Double.parseDouble(parts[1]);
			double s = Double.parseDouble(parts[2]);
			return (h * 3600.0) + (m * 60.0) + s;
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	private Path buildOutputPath(Path inputAudio) {
		String fileName = inputAudio.getFileName() != null ? inputAudio.getFileName().toString() : "audio";
		String baseName = fileName.replaceAll("\\.[^.]+$", "");
		Path dir = inputAudio.getParent() != null ? inputAudio.getParent() : FabricLoader.getInstance().getGameDir();

		Path output = dir.resolve(baseName + ".mp3");
		if (!Files.exists(output) || output.equals(inputAudio)) {
			return output;
		}

		for (int i = 1; i <= 999; i++) {
			Path candidate = dir.resolve(baseName + "_converted_" + i + ".mp3");
			if (!Files.exists(candidate)) return candidate;
		}
		return dir.resolve(baseName + "_converted.mp3");
	}

	private String resolveFfmpegExecutable() {
		Path configPath = FabricLoader.getInstance().getConfigDir().resolve("beatblock/ffmpeg_path.txt");
		if (Files.exists(configPath)) {
			try {
				String txt = Files.readString(configPath).trim();
				if (!txt.isEmpty() && isExecutable(txt)) return txt;
			} catch (IOException ignored) {
			}
		}

		Path gameDir = FabricLoader.getInstance().getGameDir();
		List<Path> candidates = List.of(
			gameDir.resolve("ffmpeg.exe"),
			gameDir.resolve("ffmpeg"),
			gameDir.resolve("ffmpeg/bin/ffmpeg.exe"),
			gameDir.resolve("ffmpeg/bin/ffmpeg")
		);
		for (Path p : candidates) {
			if (Files.isRegularFile(p) && isExecutable(p.toAbsolutePath().toString())) {
				return p.toAbsolutePath().toString();
			}
		}

		if (isExecutable("ffmpeg")) return "ffmpeg";
		return null;
	}

	private boolean isExecutable(String executable) {
		try {
			Process p = new ProcessBuilder(executable, "-version")
				.redirectErrorStream(true)
				.start();
			boolean finished = p.waitFor(3, TimeUnit.SECONDS);
			return finished && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private String summarizeProcessOutput(StringBuilder output) {
		if (output == null || output.isEmpty()) return "";
		String text = output.toString().trim();
		if (text.length() <= 220) return "\n" + text;
		return "\n" + text.substring(text.length() - 220);
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	@FunctionalInterface
	public interface ProgressCallback {
		void accept(String message, int percent);
	}
}
