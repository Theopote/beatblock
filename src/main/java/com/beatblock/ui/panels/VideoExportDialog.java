package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.VideoExportPanelPresenter;
import com.beatblock.video.VideoExportPreferences;
import com.beatblock.video.VideoExportProgress;
import com.beatblock.video.VideoExportService;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * 视频导出设置弹窗：输出路径、分辨率、帧率、时间范围、FFmpeg 状态与导出进度。
 */
public final class VideoExportDialog {

	private static final int PATH_CAPACITY = 512;

	private final VideoExportPanelPresenter presenter;
	private final ImString outputPath = new ImString(PATH_CAPACITY);
	private final ImInt resolutionIndex = new ImInt(VideoExportPreferences.resolutionPresetIndex());
	private final ImInt fpsIndex = new ImInt(VideoExportPreferences.fpsPresetIndex());
	private final ImDouble startSeconds = new ImDouble(0.0);
	private final ImDouble endSeconds = new ImDouble(60.0);
	private final ImBoolean includeAudio = new ImBoolean(VideoExportPreferences.includeAudio());
	private String statusMessage = "";
	private boolean open;

	public VideoExportDialog() {
		this(PresenterFactories.videoExportPanelPresenter());
	}

	VideoExportDialog(VideoExportPanelPresenter presenter) {
		this.presenter = presenter;
	}

	public void open() {
		open = true;
		statusMessage = "";
		var service = presenter.activeService();
		if (service != null) {
			service.clearLastResult();
		}
		var state = presenter.dialogState();
		outputPath.set(state.defaultOutputPath());
		startSeconds.set(state.defaultStartSeconds());
		endSeconds.set(state.defaultEndSeconds());
		resolutionIndex.set(VideoExportPreferences.resolutionPresetIndex());
		fpsIndex.set(VideoExportPreferences.fpsPresetIndex());
		includeAudio.set(VideoExportPreferences.includeAudio());
	}

	public void render() {
		if (!open) {
			return;
		}
		ImGui.setNextWindowSize(520, 0);
		if (!ImGui.begin(BBTexts.get("beatblock.export.title"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.end();
			return;
		}

		var state = presenter.dialogState();
		var service = presenter.activeService();
		boolean exporting = service != null && service.isExporting();

		renderFfmpegStatus(state.ffmpegStatus());
		renderBlockedReason(state, exporting);
		ImGui.separator();

		if (exporting) {
			ImGui.beginDisabled();
		}
		renderExportSettings(state);
		if (exporting) {
			ImGui.endDisabled();
		}

		renderExportProgress(service, exporting);
		renderLastResult(service, exporting);

		if (!statusMessage.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(0.95f, 0.35f, 0.35f, 1f, statusMessage);
		}

		ImGui.separator();
		renderActionButtons(service, exporting, state.canExport());

		ImGui.end();
	}

	private void renderExportSettings(VideoExportPanelPresenter.ExportDialogState state) {
		ImGui.text(BBTexts.get("beatblock.export.output_path"));
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("##exportOutputPath", outputPath);

		ImGui.text(BBTexts.get("beatblock.export.resolution"));
		ImGui.setNextItemWidth(-1);
		if (ImGui.combo("##exportResolution", resolutionIndex, resolutionLabels())) {
			VideoExportPreferences.setResolutionPresetIndex(resolutionIndex.get());
		}

		ImGui.text(BBTexts.get("beatblock.export.fps"));
		ImGui.setNextItemWidth(-1);
		if (ImGui.combo("##exportFps", fpsIndex, fpsLabels())) {
			VideoExportPreferences.setFpsPresetIndex(fpsIndex.get());
		}

		ImGui.text(BBTexts.get("beatblock.export.time_range"));
		ImGui.setNextItemWidth(120);
		ImGui.inputDouble(BBTexts.get("beatblock.export.start_time") + "##start", startSeconds, 0.1, 1.0, "%.2f s");
		ImGui.sameLine();
		ImGui.setNextItemWidth(120);
		ImGui.inputDouble(BBTexts.get("beatblock.export.end") + "##end", endSeconds, 0.1, 1.0, "%.2f s");
		ImGui.sameLine();
		ImGui.textDisabled(BBTexts.get("beatblock.export.duration_hint", state.timelineDurationSeconds()));

		ImGui.spacing();
		ImGui.textWrapped(BBTexts.get("beatblock.export.scene_only_hint"));

		if (ImGui.checkbox(BBTexts.get("beatblock.export.include_audio"), includeAudio)) {
			VideoExportPreferences.setIncludeAudio(includeAudio.get());
		}
	}

	private void renderFfmpegStatus(VideoExportPanelPresenter.FfmpegStatus status) {
		ImGui.text(BBTexts.get("beatblock.export.ffmpeg.section"));
		if (status.available()) {
			ImGui.textColored(0.2f, 0.85f, 0.35f, 1f, BBTexts.get("beatblock.export.ffmpeg.ok"));
			if (status.executablePath() != null && !status.executablePath().isBlank()) {
				ImGui.textWrapped(BBTexts.get("beatblock.export.ffmpeg.path", status.executablePath()));
			}
		} else {
			ImGui.textColored(0.95f, 0.35f, 0.35f, 1f, BBTexts.get("beatblock.export.ffmpeg.missing"));
			ImGui.textWrapped(BBTexts.get("beatblock.export.ffmpeg.search_hint"));
			if (status.searchSummary() != null && !status.searchSummary().isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(status.searchSummary());
			}
		}
	}

	private void renderBlockedReason(VideoExportPanelPresenter.ExportDialogState state, boolean exporting) {
		if (exporting || state.canExport() || state.blockedReason() == null || state.blockedReason().isBlank()) {
			return;
		}
		ImGui.spacing();
		ImGui.textColored(0.95f, 0.55f, 0.25f, 1f, BBTexts.get("beatblock.export.blocked_hint"));
		ImGui.textWrapped(state.blockedReason());
	}

	private void renderExportProgress(VideoExportService service, boolean exporting) {
		if (service == null || !exporting) {
			return;
		}
		VideoExportProgress progress = service.activeProgress();
		if (progress == null) {
			return;
		}
		ImGui.spacing();
		ImGui.separator();
		ImGui.text(BBTexts.get("beatblock.export.progress.section"));
		ImGui.progressBar(progress.percent() / 100f, -1, 0, progress.message());
		ImGui.textDisabled(BBTexts.get("beatblock.export.progress.percent", progress.percent()));
		if (progress.totalFrames() > 0) {
			ImGui.sameLine();
			ImGui.textDisabled(BBTexts.get(
				"beatblock.export.progress.frames",
				progress.currentFrame(),
				progress.totalFrames()
			));
		}
	}

	private void renderLastResult(VideoExportService service, boolean exporting) {
		if (service == null || exporting) {
			return;
		}
		VideoExportService.VideoExportResult lastResult = service.lastResult();
		if (lastResult == null || lastResult.message() == null || lastResult.message().isBlank()) {
			return;
		}
		ImGui.spacing();
		ImGui.separator();
		if (lastResult.success()) {
			ImGui.textColored(0.2f, 0.85f, 0.35f, 1f, lastResult.message());
		} else {
			ImGui.textColored(0.95f, 0.35f, 0.35f, 1f, lastResult.message());
		}
	}

	private void renderActionButtons(VideoExportService service, boolean exporting, boolean canExport) {
		if (exporting) {
			if (ImGui.button(BBTexts.get("beatblock.export.cancel_export"), 120, 0)) {
				presenter.cancelExport();
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.export.close") + "##exportVideo")) {
				open = false;
			}
			return;
		}

		if (!canExport) {
			ImGui.beginDisabled();
		}
		if (ImGui.button(BBTexts.get("beatblock.export.start_button"), 120, 0)) {
			var result = presenter.startExport(
				outputPath.get(),
				resolutionIndex.get(),
				fpsIndex.get(),
				startSeconds.get(),
				endSeconds.get(),
				includeAudio.get()
			);
			statusMessage = result.ok() ? "" : result.messageOrEmpty();
		}
		if (!canExport) {
			ImGui.endDisabled();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.export.close") + "##exportVideo")) {
			open = false;
		}
	}

	private static String[] resolutionLabels() {
		return BBTexts.labels(
			"beatblock.export.resolution.native",
			"beatblock.export.resolution.720p",
			"beatblock.export.resolution.1080p",
			"beatblock.export.resolution.1440p"
		);
	}

	private static String[] fpsLabels() {
		return BBTexts.labels(
			"beatblock.export.fps.24",
			"beatblock.export.fps.30",
			"beatblock.export.fps.60"
		);
	}
}
