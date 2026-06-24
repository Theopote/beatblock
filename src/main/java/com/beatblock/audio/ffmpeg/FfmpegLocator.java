package com.beatblock.audio.ffmpeg;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 统一 ffmpeg 可执行文件路径解析与版本探测。
 * <p>
 * 查找顺序：{@code config/beatblock/ffmpeg_path.txt} → 游戏目录候选 → PATH 上的 {@code ffmpeg}。
 */
public final class FfmpegLocator {

	private static final int VERSION_PROBE_TIMEOUT_SEC = 3;

	@FunctionalInterface
	interface ExecutableProbe {
		boolean isExecutable(String executable);
	}

	private FfmpegLocator() {}

	public static String resolveExecutable() {
		return resolveExecutable(
			FabricLoader.getInstance().getConfigDir(),
			FabricLoader.getInstance().getGameDir(),
			FfmpegLocator::isExecutable
		);
	}

	static String resolveExecutable(Path configDir, Path gameDir, ExecutableProbe probe) {
		Path configPath = configDir.resolve("beatblock/ffmpeg_path.txt");
		if (Files.exists(configPath)) {
			try {
				String txt = Files.readString(configPath).trim();
				if (!txt.isEmpty() && probe.isExecutable(txt)) {
					return txt;
				}
			} catch (IOException ignored) {
			}
		}

		List<Path> candidates = List.of(
			gameDir.resolve("ffmpeg.exe"),
			gameDir.resolve("ffmpeg"),
			gameDir.resolve("ffmpeg/bin/ffmpeg.exe"),
			gameDir.resolve("ffmpeg/bin/ffmpeg")
		);
		for (Path candidate : candidates) {
			if (Files.isRegularFile(candidate)) {
				String absolute = candidate.toAbsolutePath().toString();
				if (probe.isExecutable(absolute)) {
					return absolute;
				}
			}
		}

		if (probe.isExecutable("ffmpeg")) {
			return "ffmpeg";
		}
		return null;
	}

	public static boolean isAvailable() {
		return resolveExecutable() != null;
	}

	public static boolean isExecutable(String executable) {
		try {
			Process process = new ProcessBuilder(executable, "-version")
				.redirectErrorStream(true)
				.start();
			boolean finished = process.waitFor(VERSION_PROBE_TIMEOUT_SEC, TimeUnit.SECONDS);
			return finished && process.exitValue() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}
}
