package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.cache.BeatmapAnalysisCache;
import com.beatblock.audio.python.PythonAudioAnalyzer;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import com.beatblock.audio.python.PythonRuntimeHealthMonitor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * 音频分析对外入口：任务调度见 {@link AudioAnalysisOrchestrator}，
 * Python 环境健康见 {@link PythonRuntimeHealthMonitor}。
 */
public final class AudioAnalysisService {

	private final AudioAnalysisOrchestrator orchestrator;
	private final PythonRuntimeHealthMonitor runtimeHealthMonitor;

	private volatile boolean useDemucs = true;

	public AudioAnalysisService() {
		this(new PythonEnvironmentDiagnostics());
	}

	public AudioAnalysisService(PythonEnvironmentDiagnostics pythonDiagnostics) {
		this.orchestrator = new AudioAnalysisOrchestrator(new PythonAudioAnalyzer(pythonDiagnostics));
		this.runtimeHealthMonitor = new PythonRuntimeHealthMonitor(pythonDiagnostics);
	}

	AudioAnalysisService(IAudioAnalyzer analyzer, PythonEnvironmentDiagnostics pythonDiagnostics) {
		this.orchestrator = new AudioAnalysisOrchestrator(analyzer);
		this.runtimeHealthMonitor = new PythonRuntimeHealthMonitor(pythonDiagnostics);
	}

	AudioAnalysisService(
		AudioAnalysisOrchestrator orchestrator,
		PythonRuntimeHealthMonitor runtimeHealthMonitor
	) {
		this.orchestrator = orchestrator;
		this.runtimeHealthMonitor = runtimeHealthMonitor;
	}

	public @NonNull IAudioAnalyzer getAnalyzer() {
		return orchestrator.getAnalyzer();
	}

	public boolean isUseDemucs() { return useDemucs; }
	public void setUseDemucs(boolean useDemucs) { this.useDemucs = useDemucs; }

	public @NonNull Future<?> analyze(
		@NonNull Path audioPath,
		@NonNull AnalysisProgressCallback onProgress,
		@NonNull Consumer<Beatmap> onComplete,
		@NonNull Consumer<String> onError
	) {
		return submitAnalysis(null, audioPath, onProgress, onComplete, onError, null, null, useDemucs);
	}

	public @NonNull Future<?> analyze(
		@NonNull Path audioPath,
		@NonNull AnalysisProgressCallback onProgress,
		@NonNull Consumer<Beatmap> onComplete,
		@NonNull Consumer<String> onError,
		@Nullable Runnable onStarted
	) {
		return submitAnalysis(null, audioPath, onProgress, onComplete, onError, null, onStarted, useDemucs);
	}

	public @NonNull Future<?> analyze(
		@NonNull Path audioPath,
		@NonNull AnalysisProgressCallback onProgress,
		@NonNull Consumer<Beatmap> onComplete,
		@NonNull Consumer<String> onError,
		@Nullable Consumer<AnalysisSummary> onSummary,
		@Nullable Runnable onStarted
	) {
		return submitAnalysis(null, audioPath, onProgress, onComplete, onError, onSummary, onStarted, useDemucs);
	}

	public @NonNull Future<?> analyze(
		@NonNull Path audioPath,
		@NonNull AnalysisProgressCallback onProgress,
		@NonNull Consumer<Beatmap> onComplete,
		@NonNull Consumer<String> onError,
		@Nullable Consumer<AnalysisSummary> onSummary,
		@Nullable Runnable onStarted,
		boolean requestedDemucs
	) {
		return submitAnalysis(null, audioPath, onProgress, onComplete, onError, onSummary, onStarted, requestedDemucs);
	}

	public @NonNull Future<?> analyze(
		@Nullable String taskId,
		@NonNull Path audioPath,
		@NonNull AnalysisProgressCallback onProgress,
		@NonNull Consumer<Beatmap> onComplete,
		@NonNull Consumer<String> onError,
		@Nullable Consumer<AnalysisSummary> onSummary,
		@Nullable Runnable onStarted,
		boolean requestedDemucs
	) {
		return submitAnalysis(taskId, audioPath, onProgress, onComplete, onError, onSummary, onStarted, requestedDemucs);
	}

	private @NonNull Future<?> submitAnalysis(
		@Nullable String taskId,
		@NonNull Path audioPath,
		@Nullable AnalysisProgressCallback onProgress,
		@Nullable Consumer<Beatmap> onComplete,
		@Nullable Consumer<String> onError,
		@Nullable Consumer<AnalysisSummary> onSummary,
		@Nullable Runnable onStarted,
		boolean requestedDemucs
	) {
		return orchestrator.submit(
			taskId,
			audioPath,
			AnalysisOptions.withDemucs(requestedDemucs),
			onProgress,
			onComplete,
			onError,
			onSummary,
			onStarted
		);
	}

	public boolean cancelAnalysis(@Nullable String taskId) {
		return orchestrator.cancel(taskId);
	}

	public int getActiveAnalysisCount() {
		return orchestrator.activeTaskCount();
	}

	public void shutdown() {
		orchestrator.shutdown();
		runtimeHealthMonitor.shutdown();
	}

	public int clearBeatmapCacheForAudio(@NonNull Path audioPath) {
		return BeatmapAnalysisCache.clearBeatmapCacheForAudio(audioPath);
	}

	public int clearAllAnalysisCacheForAudio(@NonNull Path audioPath) {
		return BeatmapAnalysisCache.clearAllAnalysisCacheForAudio(audioPath);
	}

	public @NonNull String getPythonRuntimeSummary() {
		return runtimeHealthMonitor.getRuntimeSummary();
	}

	public PythonEnvironmentDiagnostics.@NonNull RuntimeHealthSnapshot getRuntimeHealthSnapshot() {
		return runtimeHealthMonitor.getRuntimeHealthSnapshot();
	}
}
