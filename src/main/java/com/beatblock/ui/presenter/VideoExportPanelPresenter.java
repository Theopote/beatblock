package com.beatblock.ui.presenter;

import com.beatblock.audio.ffmpeg.FfmpegLocator;
import com.beatblock.audio.ffmpeg.FfmpegService;
import com.beatblock.audio.ffmpeg.FfmpegVideoEncoder;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.video.VideoExportPreferences;
import com.beatblock.video.VideoExportService;
import com.beatblock.video.VideoExportSettings;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Supplier;

/** 视频导出弹窗业务逻辑。 */
public final class VideoExportPanelPresenter {

	public record FfmpegStatus(boolean available, @Nullable String executablePath, String searchSummary) {}

	public record ExportDialogState(
		String defaultOutputPath,
		double timelineDurationSeconds,
		double defaultStartSeconds,
		double defaultEndSeconds,
		FfmpegStatus ffmpegStatus,
		boolean canExport,
		@Nullable String blockedReason
	) {}

	private static final int[][] RESOLUTION_PRESETS = {
		{ 0, 0 },
		{ 1280, 720 },
		{ 1920, 1080 },
		{ 2560, 1440 }
	};
	private static final int[] FPS_PRESETS = { 24, 30, 60 };

	private final Supplier<BeatBlockContext> contextSource;
	private final Supplier<VideoExportService> exportService;

	public VideoExportPanelPresenter(
		Supplier<BeatBlockContext> contextSource,
		Supplier<VideoExportService> exportService
	) {
		this.contextSource = contextSource != null ? contextSource : () -> null;
		this.exportService = exportService;
	}

	public static int[][] resolutionPresets() {
		return RESOLUTION_PRESETS;
	}

	public static int[] fpsPresets() {
		return FPS_PRESETS;
	}

	public ExportDialogState dialogState() {
		BeatBlockContext ctx = contextSource.get();
		Timeline timeline = ctx != null ? ctx.timeline() : null;
		double duration = timeline != null ? Math.max(0.0, timeline.getDurationSeconds()) : 0.0;
		FfmpegStatus ffmpegStatus = probeFfmpeg();
		String blockedReason = exportBlockedReason(duration, ffmpegStatus);
		return new ExportDialogState(
			defaultOutputPath(timeline),
			duration,
			0.0,
			duration > 0 ? duration : 60.0,
			ffmpegStatus,
			blockedReason == null,
			blockedReason
		);
	}

	public PresenterResult startExport(
		String rawOutputPath,
		int resolutionPresetIndex,
		int fpsPresetIndex,
		double startSeconds,
		double endSeconds,
		boolean includeAudio
	) {
		ExportDialogState state = dialogState();
		if (!state.canExport()) {
			return PresenterResult.failure(state.blockedReason() != null
				? state.blockedReason()
				: BBTexts.get("beatblock.export.blocked.generic"));
		}
		String output = rawOutputPath != null ? rawOutputPath.trim() : "";
		if (output.isBlank()) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.output_empty"));
		}
		if (endSeconds <= startSeconds) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.invalid_range"));
		}

		int presetIndex = Math.max(0, Math.min(resolutionPresetIndex, RESOLUTION_PRESETS.length - 1));
		int fpsIndex = Math.max(0, Math.min(fpsPresetIndex, FPS_PRESETS.length - 1));
		int[] resolution = RESOLUTION_PRESETS[presetIndex];
		int fps = FPS_PRESETS[fpsIndex];

		VideoExportPreferences.setResolutionPresetIndex(presetIndex);
		VideoExportPreferences.setFpsPresetIndex(fpsIndex);
		VideoExportPreferences.setIncludeAudio(includeAudio);
		Path outputPath = Path.of(output);
		Path parent = outputPath.getParent();
		if (parent != null) {
			VideoExportPreferences.setLastOutputDirectory(parent.toString());
		}

		VideoExportSettings settings = new VideoExportSettings(
			outputPath,
			resolution[0],
			resolution[1],
			fps,
			startSeconds,
			endSeconds,
			includeAudio
		);

		VideoExportService service = exportService.get();
		if (service == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.service_unavailable"));
		}
		if (service.isExporting()) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.already_running"));
		}
		if (!service.startExport(settings)) {
			return PresenterResult.failure(BBTexts.get("beatblock.export.error.start_failed"));
		}
		return PresenterResult.success(BBTexts.get("beatblock.export.started"));
	}

	public void cancelExport() {
		VideoExportService service = exportService.get();
		if (service != null) {
			service.cancelExport();
		}
	}

	public @Nullable VideoExportService activeService() {
		return exportService.get();
	}

	private static FfmpegStatus probeFfmpeg() {
		String executable = FfmpegService.resolveExecutable();
		boolean available = executable != null;
		String summary = String.join("\n", FfmpegLocator.describeSearchLocations(FabricLoader.getInstance().getGameDir()));
		return new FfmpegStatus(available, executable, summary);
	}

	private @Nullable String exportBlockedReason(double duration, FfmpegStatus ffmpegStatus) {
		if (!ffmpegStatus.available()) {
			return BBTexts.get("beatblock.export.error.ffmpeg_missing");
		}
		if (duration <= 0.0) {
			return BBTexts.get("beatblock.export.error.no_timeline_duration");
		}
		VideoExportService service = exportService.get();
		if (service != null && service.isExporting()) {
			return BBTexts.get("beatblock.export.error.already_running");
		}
		return null;
	}

	private static String defaultOutputPath(@Nullable Timeline timeline) {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path exportsDir = gameDir.resolve("exports");
		String lastDir = VideoExportPreferences.lastOutputDirectory();
		Path baseDir = !lastDir.isBlank() ? Path.of(lastDir) : exportsDir;
		String hint = "";
		if (timeline != null) {
			Object projectPath = timeline.getMetadata("projectPath");
			if (projectPath != null) {
				hint = String.valueOf(projectPath);
			} else {
				Object audioPath = timeline.getMetadata("audioPath");
				if (audioPath != null) {
					hint = String.valueOf(audioPath);
				}
			}
		}
		return baseDir.resolve(FfmpegVideoEncoder.defaultOutputFileName(hint)).toString();
	}
}
