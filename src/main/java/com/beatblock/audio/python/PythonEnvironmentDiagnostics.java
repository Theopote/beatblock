package com.beatblock.audio.python;

import com.beatblock.audio.AnalysisCancelControl;
import com.beatblock.audio.AnalysisProgressCallback;
import com.beatblock.audio.AnalyzerInstaller;
import com.beatblock.audio.ffmpeg.FfmpegService;
import com.beatblock.audio.process.ProcessIo;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Python / ffmpeg 环境探测、运行时健康检查、依赖安装与错误归类。
 */
public final class PythonEnvironmentDiagnostics {

	private static final Logger LOGGER = LoggerFactory.getLogger(PythonEnvironmentDiagnostics.class);
	private static final long PROBE_CACHE_TTL_MS = 5 * 60 * 1000L;
	private static final long PROBE_TIMEOUT_MS = 3000L;

	private final Map<String, PythonProbeInfo> probeCache = new ConcurrentHashMap<>();

	public record HealthItem(String state, String detail) {}

	public record RuntimeHealthSnapshot(
		HealthItem python,
		HealthItem pip,
		HealthItem librosa,
		HealthItem demucs,
		HealthItem torch,
		HealthItem ffmpeg
	) {
		public static RuntimeHealthSnapshot empty() {
			HealthItem unknown = new HealthItem("unknown", "检查中");
			return new RuntimeHealthSnapshot(unknown, unknown, unknown, unknown, unknown, unknown);
		}
	}

	public PythonProbeInfo getProbeInfo(String pythonExe) {
		String normalizedExe = normalizePythonCandidate(pythonExe);
		if (normalizedExe.isEmpty()) {
			return PythonProbeInfo.failed("Python 路径为空");
		}

		long now = System.currentTimeMillis();
		PythonProbeInfo cached = probeCache.get(normalizedExe);
		if (cached != null && (now - cached.checkedAtMs()) <= PROBE_CACHE_TTL_MS) {
			return cached;
		}

		PythonProbeInfo fresh = probePythonInfoOnce(normalizedExe);
		probeCache.put(normalizedExe, fresh);
		return fresh;
	}

	public boolean isUsablePythonForAnalyzer(String pythonExe) {
		PythonProbeInfo info = getProbeInfo(pythonExe);
		if (!info.probeOk()) return false;
		if (isBlockedAutoPython(info.executablePath())) return false;
		if (!info.isSupportedVersion()) return false;
		return info.hasPip();
	}

	public String resolvePythonExe(Path configDir) {
		Path custom = configDir.resolve("python_path.txt");
		if (Files.exists(custom)) {
			try {
				String txt = Files.readString(custom).trim();
				String customExe = normalizePythonCandidate(txt);
				if (!customExe.isEmpty() && isUsablePythonForAnalyzer(customExe)) {
					return customExe;
				}
			} catch (IOException e) {
				LOGGER.debug("Unable to read python_path.txt from config", e);
			}
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

	public String probeRuntimeSummary(Path configDir) {
		if (configDir == null) return "Python: 未检测到配置目录";

		String exe = resolvePythonExe(configDir);
		if (exe == null) {
			return "Python: 未找到（请配置 config/beatblock/python_path.txt）";
		}

		PythonProbeInfo info = getProbeInfo(exe);
		String version = info.versionString();
		if (version.isBlank()) version = "unknown";
		String path = (info.executablePath() != null && !info.executablePath().isBlank())
			? info.executablePath()
			: exe;
		return "Python: " + version + " · " + path;
	}

	public RuntimeHealthSnapshot probeRuntimeHealth(Path configDir) {
		String pythonExe = configDir != null ? resolvePythonExe(configDir) : null;
		HealthItem ffmpeg = probeFfmpegHealth();
		if (pythonExe == null || pythonExe.isBlank()) {
			HealthItem missing = new HealthItem("missing", "未找到");
			return new RuntimeHealthSnapshot(
				new HealthItem("missing", "未配置"),
				missing,
				new HealthItem("unknown", "未知"),
				new HealthItem("unknown", "未知"),
				new HealthItem("unknown", "未知"),
				ffmpeg
			);
		}

		PythonProbeInfo info = getProbeInfo(pythonExe);
		HealthItem python = !info.probeOk()
			? new HealthItem("error", detailOrDefault(info.detail(), "探测失败"))
			: !info.isSupportedVersion()
				? new HealthItem("warn", "版本 " + info.versionString())
				: new HealthItem("ok", info.versionString());
		HealthItem pip = !info.probeOk()
			? new HealthItem("unknown", "未知")
			: info.hasPip()
				? new HealthItem("ok", "可用")
				: new HealthItem("missing", "缺少");

		String script = String.join("\n",
			"import importlib.util",
			"mods = ['librosa','soundfile','demucs','torch']",
			"print('|'.join('1' if importlib.util.find_spec(m) else '0' for m in mods))"
		);
		try {
			Process p = new ProcessBuilder(pythonExe, "-c", script)
				.redirectErrorStream(true)
				.start();
			boolean finished = p.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			if (!finished) {
				p.destroyForcibly();
				HealthItem unknown = new HealthItem("unknown", "探测超时");
				return new RuntimeHealthSnapshot(python, pip, unknown, unknown, unknown, ffmpeg);
			}
			String out = ProcessIo.readProcessOutput(p).trim();
			if (p.exitValue() != 0) {
				HealthItem unknown = new HealthItem("unknown", ProcessIo.sanitizeProcessOutput(out));
				return new RuntimeHealthSnapshot(python, pip, unknown, unknown, unknown, ffmpeg);
			}
			String[] parts = out.split("\\|");
			return new RuntimeHealthSnapshot(
				python,
				pip,
				moduleHealth(parts, 0, "librosa"),
				moduleHealth(parts, 2, "demucs"),
				moduleHealth(parts, 3, "torch"),
				ffmpeg
			);
		} catch (Exception e) {
			HealthItem unknown = new HealthItem("unknown", e.getClass().getSimpleName());
			return new RuntimeHealthSnapshot(python, pip, unknown, unknown, unknown, ffmpeg);
		}
	}

	public String ensurePythonDependencies(
		String pythonExe,
		Path requirementsPath,
		AnalysisCancelControl control,
		AnalysisProgressCallback onProgress,
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
			String checkOut = ProcessIo.readProcessOutput(check);
			int checkCode = ProcessIo.waitProcess(check);
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
				String installOut = ProcessIo.readProcessOutput(install);
				int installCode = ProcessIo.waitProcess(install);
				control.clearProcess(install);
				if (control.isCancelled()) return "分析被取消";
				if (installCode != 0) {
					String detail = ProcessIo.sanitizeProcessOutput(installOut);
					if (detail.isEmpty()) detail = ProcessIo.sanitizeProcessOutput(checkOut);
					String hint = explainPythonError(detail);
					String resolvedExe = getProbeInfo(pythonExe).executablePath();
					String cmdExe = resolvedExe != null && !resolvedExe.isBlank() ? resolvedExe : pythonExe;
					return "Python 依赖安装失败，请手动执行：\n"
						+ "\"" + cmdExe + "\" -m pip install -r \"" + requirementsPath.toAbsolutePath() + "\"\n"
						+ (hint.isEmpty() ? "" : ("\n" + hint + "\n"))
						+ detail;
				}
			}

			if (analysisUseDemucs) {
				onProgress.onProgress("DEMUCS_DEP_CHECK", 0);
				Process demucsCheck = new ProcessBuilder(
					pythonExe,
					"-c",
					"import demucs.api, torch"
				).redirectErrorStream(true).start();
				control.attachProcess(demucsCheck);
				String demucsCheckOut = ProcessIo.readProcessOutput(demucsCheck);
				int demucsCheckCode = ProcessIo.waitProcess(demucsCheck);
				control.clearProcess(demucsCheck);
				if (control.isCancelled()) return "分析被取消";

				if (demucsCheckCode != 0) {
					onProgress.onProgress("DEMUCS_DEP_INSTALL", 0);
					Path demucsRequirementsPath = requirementsPath.getParent().resolve("requirements-demucs.txt");
					List<String> demucsInstallCmd = new ArrayList<>();
					demucsInstallCmd.add(pythonExe);
					demucsInstallCmd.add("-m");
					demucsInstallCmd.add("pip");
					demucsInstallCmd.add("install");
					if (Files.isRegularFile(demucsRequirementsPath)) {
						demucsInstallCmd.add("-r");
						demucsInstallCmd.add(demucsRequirementsPath.toAbsolutePath().toString());
					} else {
						demucsInstallCmd.add("demucs");
						demucsInstallCmd.add("torch");
					}
					Process demucsInstall = new ProcessBuilder(demucsInstallCmd).redirectErrorStream(true).start();
					control.attachProcess(demucsInstall);
					String demucsInstallOut = ProcessIo.readProcessOutput(demucsInstall);
					int demucsInstallCode = ProcessIo.waitProcess(demucsInstall);
					control.clearProcess(demucsInstall);
					if (control.isCancelled()) return "分析被取消";

					if (demucsInstallCode != 0) {
						String detail = ProcessIo.sanitizeProcessOutput(demucsInstallOut);
						if (detail.isEmpty()) detail = ProcessIo.sanitizeProcessOutput(demucsCheckOut);
						onProgress.onProgress(demucsInstallFailureStep(detail), 100);
						onProgress.onProgress("DEMUCS_DEP_INSTALL_FAILED", 100);
						String hint = explainPythonError(detail);
						String resolvedExe = getProbeInfo(pythonExe).executablePath();
						String cmdExe = resolvedExe != null && !resolvedExe.isBlank() ? resolvedExe : pythonExe;
						String demucsInstallHint = Files.isRegularFile(demucsRequirementsPath)
							? "\"" + cmdExe + "\" -m pip install -r \"" + demucsRequirementsPath.toAbsolutePath() + "\""
							: "\"" + cmdExe + "\" -m pip install demucs torch";
						return "Demucs 依赖安装失败，请手动执行：\n"
							+ demucsInstallHint + "\n"
							+ (hint.isEmpty() ? "" : ("\n" + hint + "\n"))
							+ detail;
					}
					onProgress.onProgress("DEMUCS_DEP_INSTALL", 100);
					onProgress.onProgress("DEMUCS_DEP_INSTALL_SUCCESS", 100);
				} else {
					onProgress.onProgress("DEMUCS_DEP_CHECK", 100);
					onProgress.onProgress("DEMUCS_DEP_INSTALL_SUCCESS", 100);
				}
			}

			return null;
		} catch (IOException e) {
			if (control.isCancelled()) return "分析被取消";
			return "检查 Python 依赖失败：" + e.getMessage();
		}
	}

	public static String explainPythonError(String detail) {
		if (detail == null || detail.isBlank()) return "";
		String s = detail.toLowerCase();

		if ((s.contains("no module named demucs") || s.contains("no module named torch")
			|| s.contains("modulenotfounderror: demucs") || s.contains("modulenotfounderror: torch"))) {
			return "检测到 Demucs 依赖缺失。请执行 pip install -r requirements-demucs.txt（或 pip install demucs torch），建议使用 Python 3.10~3.12。";
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

	public static boolean looksLikeDemucsMissing(String detail) {
		if (detail == null || detail.isBlank()) return false;
		String s = detail.toLowerCase();
		return s.contains("demucs") || s.contains("torch") || s.contains("demucs.api");
	}

	public static String demucsInstallFailureStep(String detail) {
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

	public Path configDirOrNull() {
		Path scriptPath = AnalyzerInstaller.getScriptPath();
		return scriptPath != null ? scriptPath.getParent() : null;
	}

	private HealthItem moduleHealth(String[] parts, int index, String label) {
		boolean ok = parts != null && index >= 0 && index < parts.length && "1".equals(parts[index].trim());
		return ok ? new HealthItem("ok", "已安装") : new HealthItem("missing", "缺少 " + label);
	}

	private HealthItem probeFfmpegHealth() {
		String executable = FfmpegService.resolveExecutable();
		if (executable == null || executable.isBlank()) {
			return new HealthItem("missing", "未找到");
		}
		return new HealthItem("ok", executable);
	}

	private static String detailOrDefault(String detail, String fallback) {
		return detail == null || detail.isBlank() ? fallback : detail;
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

			Process p = new ProcessBuilder(pythonExe, "-c", probeScript)
				.redirectErrorStream(true)
				.start();

			boolean finished = p.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			if (!finished) {
				p.destroyForcibly();
				return PythonProbeInfo.failed("探测超时");
			}

			String out = ProcessIo.readProcessOutput(p).trim();
			if (p.exitValue() != 0) {
				String detail = ProcessIo.sanitizeProcessOutput(out);
				if (detail.isBlank()) detail = "退出码=" + p.exitValue();
				return PythonProbeInfo.failed(detail);
			}

			String[] parts = out.split("\\|", 5);
			if (parts.length < 5) {
				return PythonProbeInfo.failed("探测输出格式异常: " + ProcessIo.sanitizeProcessOutput(out));
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

	private static boolean isBlockedAutoPython(String resolvedPath) {
		if (resolvedPath == null || resolvedPath.isBlank()) return false;
		String s = resolvedPath.toLowerCase();
		return s.contains("inkscape") && s.contains("python.exe");
	}

	private static String normalizePythonCandidate(String raw) {
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

	private static String expandWindowsEnv(String text) {
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
}
