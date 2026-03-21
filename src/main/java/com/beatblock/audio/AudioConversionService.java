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

/**
 * 音频转换服务：后台调用 ffmpeg 将不支持格式转换为 MP3。
 */
public final class AudioConversionService {

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-audio-converter");
		t.setDaemon(true);
		return t;
	});

	public Future<?> convertToMp3Async(Path inputAudio, Consumer<Path> onComplete, Consumer<String> onError) {
		return executor.submit(() -> convertToMp3(inputAudio, onComplete, onError));
	}

	private void convertToMp3(Path inputAudio, Consumer<Path> onComplete, Consumer<String> onError) {
		if (inputAudio == null || !Files.isRegularFile(inputAudio)) {
			onError.accept("待转换文件不存在。");
			return;
		}

		String fileName = inputAudio.getFileName() != null ? inputAudio.getFileName().toString() : "";
		if (fileName.toLowerCase().endsWith(".mp3")) {
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
		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = br.readLine()) != null) {
				out.append(line).append('\n');
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

		onComplete.accept(output);
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
}
