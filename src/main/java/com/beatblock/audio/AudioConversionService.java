package com.beatblock.audio;

import com.beatblock.audio.ffmpeg.FfmpegService;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * 音频转换服务：后台调用 {@link FfmpegService} 将不支持格式转换为 MP3。
 */
public final class AudioConversionService {

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
		Path fallbackDir = FabricLoader.getInstance().getGameDir();
		var outcome = FfmpegService.transcodeToMp3(
			inputAudio,
			fallbackDir,
			onProgress != null ? onProgress::accept : null
		);

		if (outcome instanceof FfmpegTranscoder.Outcome.AlreadyMp3 already) {
			if (onProgress != null) {
				onProgress.accept("源文件已是 MP3，跳过转换。", 100);
			}
			onComplete.accept(already.path());
		} else if (outcome instanceof FfmpegTranscoder.Outcome.Success success) {
			if (onProgress != null) {
				onProgress.accept("转换完成。", 100);
			}
			onComplete.accept(success.outputPath());
		} else if (outcome instanceof FfmpegTranscoder.Outcome.Failure failure) {
			onError.accept(failure.message());
		}
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	@FunctionalInterface
	public interface ProgressCallback {
		void accept(String message, int percent);
	}
}
