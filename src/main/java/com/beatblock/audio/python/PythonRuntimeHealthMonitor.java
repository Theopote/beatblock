package com.beatblock.audio.python;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Python 运行时摘要与健康快照的 TTL 缓存与异步刷新。
 */
public final class PythonRuntimeHealthMonitor {

	private static final Logger LOGGER = LoggerFactory.getLogger(PythonRuntimeHealthMonitor.class);
	private static final long REFRESH_INTERVAL_MS = 5000L;

	private final PythonEnvironmentDiagnostics pythonDiagnostics;
	private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-python-summary");
		t.setDaemon(true);
		return t;
	});

	private volatile String cachedPythonSummary = "Python: 检测中...";
	private volatile long nextPythonSummaryRefreshAtMs;
	private volatile boolean pythonSummaryRefreshInFlight;
	private volatile PythonEnvironmentDiagnostics.RuntimeHealthSnapshot cachedRuntimeHealthSnapshot =
		PythonEnvironmentDiagnostics.RuntimeHealthSnapshot.empty();
	private volatile long nextRuntimeHealthRefreshAtMs;
	private final AtomicBoolean runtimeHealthRefreshInFlight = new AtomicBoolean();

	public PythonRuntimeHealthMonitor(PythonEnvironmentDiagnostics pythonDiagnostics) {
		this.pythonDiagnostics = pythonDiagnostics;
	}

	public String getRuntimeSummary() {
		long now = System.currentTimeMillis();
		if (cachedPythonSummary == null || cachedPythonSummary.isBlank()) {
			cachedPythonSummary = "Python: 检测中...";
		}
		if (now >= nextPythonSummaryRefreshAtMs) {
			triggerPythonSummaryRefreshAsync();
		}
		return cachedPythonSummary;
	}

	public PythonEnvironmentDiagnostics.RuntimeHealthSnapshot getRuntimeHealthSnapshot() {
		long now = System.currentTimeMillis();
		if (now >= nextRuntimeHealthRefreshAtMs) {
			triggerRuntimeHealthRefreshAsync();
		}
		return cachedRuntimeHealthSnapshot;
	}

	public void shutdown() {
		refreshExecutor.shutdownNow();
	}

	private void triggerPythonSummaryRefreshAsync() {
		if (pythonSummaryRefreshInFlight) return;
		synchronized (this) {
			if (pythonSummaryRefreshInFlight) return;
			pythonSummaryRefreshInFlight = true;
		}

		refreshExecutor.submit(() -> {
			try {
				Path configDir = pythonDiagnostics.configDirOrNull();
				String summary = configDir != null
					? pythonDiagnostics.probeRuntimeSummary(configDir)
					: "Python: 未检测到配置目录";
				if (!summary.isBlank()) {
					cachedPythonSummary = summary;
				}
			} catch (Exception e) {
				if (cachedPythonSummary == null || cachedPythonSummary.isBlank()) {
					cachedPythonSummary = "Python: 检测失败（" + e.getClass().getSimpleName() + "）";
				}
			} finally {
				nextPythonSummaryRefreshAtMs = System.currentTimeMillis() + REFRESH_INTERVAL_MS;
				pythonSummaryRefreshInFlight = false;
			}
		});
	}

	private void triggerRuntimeHealthRefreshAsync() {
		if (!runtimeHealthRefreshInFlight.compareAndSet(false, true)) return;
		refreshExecutor.submit(() -> {
			try {
				Path configDir = pythonDiagnostics.configDirOrNull();
				cachedRuntimeHealthSnapshot = configDir != null
					? pythonDiagnostics.probeRuntimeHealth(configDir)
					: PythonEnvironmentDiagnostics.RuntimeHealthSnapshot.empty();
			} catch (Exception e) {
				LOGGER.debug("BeatBlock PythonRuntimeHealth: probe failed: {}", e.toString());
			} finally {
				nextRuntimeHealthRefreshAtMs = System.currentTimeMillis() + REFRESH_INTERVAL_MS;
				runtimeHealthRefreshInFlight.set(false);
			}
		});
	}
}
