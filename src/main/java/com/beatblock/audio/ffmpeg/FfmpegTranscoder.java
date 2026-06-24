package com.beatblock.audio.ffmpeg;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 通过 ffmpeg 将音频文件转码为 MP3（libmp3lame）。
 */
public final class FfmpegTranscoder {

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(String message, int percent);
	}

	private FfmpegTranscoder() {}

	public static FfmpegTranscodeOutcome transcodeToMp3(
		Path inputAudio,
		Path fallbackOutputDir,
		ProgressListener onProgress
	) {
		return transcodeToMp3(inputAudio, fallbackOutputDir, FfmpegLocator::resolveExecutable, onProgress);
	}

	static FfmpegTranscodeOutcome transcodeToMp3(
		Path inputAudio,
		Path fallbackOutputDir,
		ExecutableResolver executableResolver,
		ProgressListener onProgress
	) {
		if (inputAudio == null || !Files.isRegularFile(inputAudio)) {
			return new FfmpegTranscodeOutcome.Failure("待转换文件不存在。");
		}

		String fileName = inputAudio.getFileName() != null ? inputAudio.getFileName().toString() : "";
		if (fileName.toLowerCase().endsWith(".mp3")) {
			return new FfmpegTranscodeOutcome.AlreadyMp3(inputAudio);
		}

		String ffmpeg = executableResolver != null ? executableResolver.resolve() : null;
		if (ffmpeg == null) {
			Path gameDir = fallbackOutputDir != null
				? fallbackOutputDir
				: FabricLoader.getInstance().getGameDir();
			return new FfmpegTranscodeOutcome.Failure(
				"找不到 ffmpeg。请将 ffmpeg.exe 放到游戏目录（" + gameDir + "）下，"
					+ "或在 config/beatblock/ffmpeg_path.txt 中写入 ffmpeg.exe 的完整路径。"
			);
		}

		if (onProgress != null) {
			onProgress.onProgress("已找到 ffmpeg，准备转换...", 2);
		}

		Path output = resolveMp3OutputPath(inputAudio, fallbackOutputDir);
		Process process;
		try {
			process = new ProcessBuilder(buildMp3Command(ffmpeg, inputAudio, output))
				.redirectErrorStream(true)
				.start();
		} catch (IOException e) {
			return new FfmpegTranscodeOutcome.Failure("无法启动 ffmpeg: " + e.getMessage());
		}

		StringBuilder out = new StringBuilder();
		double[] totalDurationSec = {0.0};
		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = br.readLine()) != null) {
				out.append(line).append('\n');
				if (onProgress != null) {
					FfmpegProgressParser.parseLine(line, totalDurationSec, onProgress::onProgress);
				}
			}
		} catch (IOException ignored) {
		}

		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return new FfmpegTranscodeOutcome.Failure("音频转换被中断。");
		}

		if (exitCode != 0 || !Files.isRegularFile(output)) {
			return new FfmpegTranscodeOutcome.Failure("ffmpeg 转换失败。" + summarizeProcessOutput(out));
		}

		return new FfmpegTranscodeOutcome.Success(output);
	}

	static List<String> buildMp3Command(String ffmpegExecutable, Path inputAudio, Path outputAudio) {
		List<String> cmd = new ArrayList<>();
		cmd.add(ffmpegExecutable);
		cmd.add("-y");
		cmd.add("-i");
		cmd.add(inputAudio.toAbsolutePath().toString());
		cmd.add("-vn");
		cmd.add("-codec:a");
		cmd.add("libmp3lame");
		cmd.add("-q:a");
		cmd.add("2");
		cmd.add(outputAudio.toAbsolutePath().toString());
		return List.copyOf(cmd);
	}

	static Path resolveMp3OutputPath(Path inputAudio, Path fallbackOutputDir) {
		String fileName = inputAudio.getFileName() != null ? inputAudio.getFileName().toString() : "audio";
		String baseName = fileName.replaceAll("\\.[^.]+$", "");
		Path dir = inputAudio.getParent();
		if (dir == null) {
			dir = fallbackOutputDir != null ? fallbackOutputDir : FabricLoader.getInstance().getGameDir();
		}

		Path output = dir.resolve(baseName + ".mp3");
		if (!Files.exists(output) || output.equals(inputAudio)) {
			return output;
		}

		for (int i = 1; i <= 999; i++) {
			Path candidate = dir.resolve(baseName + "_converted_" + i + ".mp3");
			if (!Files.exists(candidate)) {
				return candidate;
			}
		}
		return dir.resolve(baseName + "_converted.mp3");
	}

	@FunctionalInterface
	interface ExecutableResolver {
		String resolve();
	}

	private static String summarizeProcessOutput(StringBuilder output) {
		if (output == null || output.isEmpty()) {
			return "";
		}
		String text = output.toString().trim();
		if (text.length() <= 220) {
			return "\n" + text;
		}
		return "\n" + text.substring(text.length() - 220);
	}
}
