package com.beatblock.audio;

import com.beatblock.audio.beatmap.Beatmap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 外部音频分析后端（Python、未来 Java 原生或远程 API 等）。
 * <p>
 * 实现应在调用方线程上<strong>同步</strong>执行；异步调度由 {@link AudioAnalysisService} 负责。
 */
public interface IAudioAnalyzer {

	@NonNull String backendId();

	boolean isAvailable();

	void analyze(
		@NonNull Path audioPath,
		@NonNull AnalysisOptions options,
		@NonNull AnalysisProgressCallback onProgress,
		@NonNull Consumer<Beatmap> onComplete,
		@NonNull Consumer<String> onError,
		@Nullable Consumer<AnalysisSummary> onSummary,
		@NonNull AnalysisCancelControl control
	);
}
