package com.beatblock.client.export;

import com.beatblock.BeatBlock;
import com.beatblock.audio.ffmpeg.FfmpegService;
import com.beatblock.audio.ffmpeg.FfmpegTranscodeOutcome;
import com.beatblock.audio.ffmpeg.FfmpegVideoEncoder;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.client.camera.TimelineCameraController;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.video.VideoExportProgress;
import com.beatblock.video.VideoExportService;
import com.beatblock.video.VideoExportSettings;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端视频导出协调器：逐帧 seek 时间线、捕获纯 Minecraft 场景（不含 UI）、写入 ffmpeg pipe。
 */
public final class VideoExportCoordinator {

	private static final Logger LOGGER = LoggerFactory.getLogger(VideoExportCoordinator.class);
	private static final VideoExportCoordinator INSTANCE = new VideoExportCoordinator();

	private enum Phase {
		IDLE,
		PREPARING,
		WAITING_FRAME,
		FINALIZING
	}

	private Phase phase = Phase.IDLE;
	private VideoExportSettings settings;
	private VideoExportService service;
	private FfmpegVideoEncoder encoder;
	private Path outputPath;
	private int nextFrameIndex;
	private int captureWidth;
	private int captureHeight;
	private boolean cancelRequested;
	private int pendingWarmupFrames;

	private VideoExportCoordinator() {}

	public static VideoExportCoordinator getInstance() {
		return INSTANCE;
	}

	public boolean isActive() {
		return phase != Phase.IDLE;
	}

	/** 导出进行时隐藏编辑器面板，仅保留导出对话框等屏幕反馈。 */
	public boolean shouldHideEditorChrome() {
		return isActive();
	}

	public void start(VideoExportSettings exportSettings, VideoExportService exportService) {
		if (exportSettings == null || exportService == null || phase != Phase.IDLE) {
			return;
		}
		String ffmpeg = FfmpegService.resolveExecutable();
		if (ffmpeg == null) {
			failImmediately(exportSettings, exportService, BBTexts.get("beatblock.export.error.ffmpeg_missing"));
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getWindow() == null) {
			failImmediately(exportSettings, exportService, BBTexts.get("beatblock.export.error.window_unavailable"));
			return;
		}

		this.settings = exportSettings;
		this.outputPath = exportSettings.outputPath();
		this.service = exportService;
		this.nextFrameIndex = 0;
		this.cancelRequested = false;
		this.pendingWarmupFrames = 2;
		this.phase = Phase.PREPARING;

		int nativeWidth = Math.max(1, client.getWindow().getFramebufferWidth());
		int nativeHeight = Math.max(1, client.getWindow().getFramebufferHeight());
		int[] target = FfmpegVideoEncoder.resolveTargetSize(
			exportSettings.width(),
			exportSettings.height(),
			nativeWidth,
			nativeHeight
		);
		this.captureWidth = target[0];
		this.captureHeight = target[1];

		updateProgress(VideoExportProgress.State.STARTING, BBTexts.get("beatblock.export.progress.preparing"), 0);
		try {
			Path audioPath = resolveAudioPath(exportSettings);
			this.encoder = new FfmpegVideoEncoder(
				ffmpeg,
				exportSettings.outputPath(),
				captureWidth,
				captureHeight,
				exportSettings.fps(),
				exportSettings.totalFrames(),
				exportSettings.includeAudio() ? audioPath : null,
				(message, percent) -> updateProgress(VideoExportProgress.State.RUNNING, message, percent)
			);
			BeatBlockClientDriver.stopPlayback();
			scheduleNextFrame();
		} catch (IOException e) {
			cleanup();
			failImmediately(exportSettings, exportService, BBTexts.get("beatblock.export.error.start_ffmpeg", e.getMessage()));
		}
	}

	public void cancel() {
		cancelRequested = true;
	}

	public void onClientTick() {
		if (phase == Phase.IDLE || cancelRequested) {
			if (cancelRequested && phase != Phase.IDLE) {
				abort(BBTexts.get("beatblock.export.cancelled"));
			}
		}
	}

	public void onBeforeFlipFrame() {
		if (phase != Phase.WAITING_FRAME || cancelRequested) {
			return;
		}
		if (pendingWarmupFrames > 0) {
			pendingWarmupFrames--;
			return;
		}
		try {
			byte[] rgba = VideoFrameCapturer.captureRgbaTopDown(captureWidth, captureHeight);
			encoder.writeFrame(rgba);
			nextFrameIndex++;
			updateProgress(
				VideoExportProgress.State.RUNNING,
				BBTexts.get("beatblock.export.progress.rendering_frame", nextFrameIndex, settings.totalFrames()),
				Math.min(99, (int) Math.round((nextFrameIndex * 100.0) / settings.totalFrames()))
			);
			if (nextFrameIndex >= settings.totalFrames()) {
				finishEncoding();
			} else {
				scheduleNextFrame();
			}
		} catch (IOException e) {
			abort(BBTexts.get("beatblock.export.error.write_frame", e.getMessage()));
		}
	}

	private void scheduleNextFrame() {
		double frameTime = settings.startTimeSeconds() + (nextFrameIndex / (double) settings.fps());
		BeatBlockClientDriver.prepareExportFrame(frameTime);
		TimelineCameraController.getInstance().sampleAtExportTime(frameTime);
		phase = Phase.WAITING_FRAME;
		pendingWarmupFrames = nextFrameIndex == 0 ? 2 : 1;
	}

	private void finishEncoding() {
		phase = Phase.FINALIZING;
		updateProgress(VideoExportProgress.State.FINALIZING, BBTexts.get("beatblock.export.progress.finalizing"), 99);
		Thread finisher = new Thread(() -> {
			FfmpegTranscodeOutcome outcome = encoder.finishAndAwait();
			MinecraftClient.getInstance().execute(() -> {
				if (outcome instanceof FfmpegTranscodeOutcome.Success) {
					completeSuccess(outputPath);
				} else if (outcome instanceof FfmpegTranscodeOutcome.Failure(String message)) {
					abort(message);
				} else {
					abort(BBTexts.get("beatblock.export.error.failed"));
				}
			});
		}, "beatblock-video-export-finalize");
		finisher.setDaemon(true);
		finisher.start();
	}

	private void completeSuccess(Path output) {
		String message = BBTexts.get("beatblock.export.success", output.toAbsolutePath());
		updateProgress(VideoExportProgress.State.SUCCEEDED, message, 100);
		if (service != null) {
			service.onCompleted(new VideoExportService.VideoExportResult(
				true,
				output,
				message,
				currentProgress(VideoExportProgress.State.SUCCEEDED, message, 100)
			));
		}
		ToastNotificationSystem.showSuccess(message);
		cleanup();
	}

	private void abort(String message) {
		LOGGER.warn("Video export aborted: {}", message);
		if (encoder != null) {
			encoder.close();
		}
		updateProgress(VideoExportProgress.State.CANCELLED, message, 0);
		if (service != null) {
			service.onCompleted(new VideoExportService.VideoExportResult(
				false,
				null,
				message,
				currentProgress(VideoExportProgress.State.CANCELLED, message, 0)
			));
		}
		ToastNotificationSystem.showError(message);
		cleanup();
	}

	private void failImmediately(VideoExportSettings exportSettings, VideoExportService exportService, String message) {
		VideoExportProgress progress = new VideoExportProgress(
			VideoExportProgress.State.FAILED,
			exportSettings,
			0,
			message,
			0,
			exportSettings != null ? exportSettings.totalFrames() : 0
		);
		exportService.onCompleted(new VideoExportService.VideoExportResult(false, null, message, progress));
		ToastNotificationSystem.showError(message);
	}

	private void cleanup() {
		phase = Phase.IDLE;
		settings = null;
		service = null;
		encoder = null;
		nextFrameIndex = 0;
		cancelRequested = false;
	}

	private void updateProgress(VideoExportProgress.State state, String message, int percent) {
		if (service != null) {
			service.onProgressUpdated(currentProgress(state, message, percent));
		}
	}

	private VideoExportProgress currentProgress(VideoExportProgress.State state, String message, int percent) {
		return new VideoExportProgress(
			state,
			settings,
			percent,
			message,
			nextFrameIndex,
			settings != null ? settings.totalFrames() : 0
		);
	}

	private static Path resolveAudioPath(VideoExportSettings exportSettings) {
		if (!exportSettings.includeAudio()) {
			return null;
		}
		var ctx = BeatBlock.getContext();
		var musicPlayer = ctx.musicPlayer();
		if (musicPlayer != null) {
			String loaded = musicPlayer.getLoadedAudioPath();
			if (loaded != null && !loaded.isBlank()) {
				Path path = Path.of(loaded);
				if (Files.isRegularFile(path)) {
					return path;
				}
			}
		}
		var timeline = ctx.timeline();
		if (timeline != null) {
			Object audioPath = timeline.getMetadata("audioPath");
			if (audioPath != null) {
				Path path = Path.of(String.valueOf(audioPath));
				if (Files.isRegularFile(path)) {
					return path;
				}
			}
		}
		return null;
	}

}
