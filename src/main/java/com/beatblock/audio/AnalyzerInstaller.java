package com.beatblock.audio;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * AnalyzerInstaller
 * ─────────────────────────────────────────────────────────────────────────────
 * 启动时，将打包在 jar 内 `resources/beatblock/analyzer/` 下的
 * Python 脚本和 `requirements.txt` 提取到
 * `config/beatblock/analyzer/` 目录，供 ProcessBuilder 调用。
 *
 * <p>Python 无法直接执行 jar 内部资源，只能执行文件系统路径，
 * 因此必须先把脚本从 jar 解压到 config 目录。</p>
 */
public final class AnalyzerInstaller {

	private static final String RESOURCE_PREFIX = "/beatblock/analyzer/";

	private static final String[] FILES = {
		"analyze.py",
		"requirements.txt"
	};

	private static final String REL_TARGET_DIR = "beatblock/analyzer";

	private AnalyzerInstaller() {}

	/**
	 * 确保脚本已提取到 config/beatblock/analyzer/。
	 *
	 * @return analyze.py 的绝对路径
	 */
	public static Path ensureInstalled() throws AnalyzerInstallException {
		Path configRoot = FabricLoader.getInstance().getConfigDir();
		Path targetDir = configRoot.resolve(REL_TARGET_DIR);
		try {
			Files.createDirectories(targetDir);
		} catch (IOException e) {
			throw new AnalyzerInstallException("无法创建目录: " + targetDir, e);
		}

		for (String fileName : FILES) {
			extractFile(fileName, targetDir);
		}
		return targetDir.resolve("analyze.py");
	}

	/** 返回 analyze.py 预期所在路径（不强制提取）。 */
	public static Path getScriptPath() {
		Path configRoot = FabricLoader.getInstance().getConfigDir();
		return configRoot.resolve(REL_TARGET_DIR).resolve("analyze.py");
	}

	/** 返回 beatmap 输出目录（config/beatblock/beatmaps），若不存在则创建。 */
	public static Path getBeatmapOutputDir() throws AnalyzerInstallException {
		Path configRoot = FabricLoader.getInstance().getConfigDir();
		Path dir = configRoot.resolve("beatblock/beatmaps");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new AnalyzerInstallException("无法创建 beatmap 目录: " + dir, e);
		}
		return dir;
	}

	private static void extractFile(String fileName, Path targetDir) throws AnalyzerInstallException {
		String resourcePath = RESOURCE_PREFIX + fileName;
		Path targetFile = targetDir.resolve(fileName);

		URL resourceUrl = AnalyzerInstaller.class.getResource(resourcePath);
		if (resourceUrl == null) {
			throw new AnalyzerInstallException("jar 内找不到资源: " + resourcePath +
				"，请确认文件已放在 src/main/resources/beatblock/analyzer/ 下");
		}

		try (InputStream in = resourceUrl.openStream()) {
			Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new AnalyzerInstallException("提取文件失败: " + fileName + " → " + targetFile, e);
		}
	}

	public static final class AnalyzerInstallException extends Exception {
		public AnalyzerInstallException(String msg, Throwable cause) { super(msg, cause); }
		public AnalyzerInstallException(String msg) { super(msg); }
	}
}

