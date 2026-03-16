package com.beatblock.ui.panels;

import com.beatblock.audio.assets.AudioAnalysisStep;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.List;

/**
 * 音频解析 / 媒体箱面板：管理已导入音频、执行分析、展示进度与预览，并作为拖拽源。
 */
public final class AudioAnalysisPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final ImString importPath = new ImString(512);
	private AudioAsset selectedAsset;
	private boolean previewExpanded = true;

	public void render() {
		if (!ImGui.begin("音频解析###AudioAnalysisPanel", WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		renderHeader();
		List<AudioAsset> assets = AudioAssetManager.getInstance().getAssets();

		float listWidth = ImGui.getContentRegionAvailX();
		float listHeight = ImGui.getContentRegionAvailY();
		float previewWidth = previewExpanded ? listWidth * 0.45f : 0f;
		float leftWidth = listWidth - previewWidth - (previewExpanded ? 8f : 0f);

		// 左侧：列表
		ImGui.beginChild("##AudioList", leftWidth, listHeight, false);
		renderImportDropZone();
		renderAssetList(assets);
		ImGui.endChild();

		// 右侧：预览
		if (previewExpanded) {
			ImGui.sameLine();
			ImGui.beginChild("##AudioPreview", previewWidth, listHeight, false);
			renderPreviewHeader();
			renderPreview(selectedAsset);
			ImGui.endChild();
		} else {
			ImGui.sameLine();
			if (ImGui.button("▶")) {
				previewExpanded = true;
			}
		}

		ImGui.end();
	}

	private void renderHeader() {
		ImGui.text("音频解析");
		ImGui.sameLine();
		if (ImGui.button("+")) {
			ImGui.openPopup("##AddAudioPath");
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("添加本地音频文件路径（目前支持 WAV）");
		}

		if (ImGui.beginPopup("##AddAudioPath")) {
			ImGui.text("音频文件路径：");
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##AudioPath", importPath);
			if (ImGui.button("添加并解析")) {
				String path = importPath.get().trim();
				if (!path.isEmpty()) {
					AudioAsset asset = AudioAssetManager.getInstance().addFromPath(path);
					if (asset != null) {
						selectedAsset = asset;
						AudioAssetManager.getInstance().startAnalysis(asset);
					}
				}
				ImGui.closeCurrentPopup();
			}
			ImGui.sameLine();
			if (ImGui.button("取消")) {
				ImGui.closeCurrentPopup();
			}
			ImGui.endPopup();
		}
		ImGui.separator();
	}

	private void renderImportDropZone() {
		ImGui.beginChild("##DropZone", 0, 120, true);
		float availX = ImGui.getContentRegionAvailX();
		float startX = ImGui.getCursorPosX() + availX * 0.5f - 80f;
		ImGui.setCursorPosX(startX);
		ImGui.textDisabled("拖入音频文件");
		ImGui.setCursorPosX(startX);
		ImGui.textDisabled("或点击 + 添加");
		ImGui.setCursorPosX(startX);
		ImGui.textDisabled("MP3 · WAV · OGG · FLAC");
		ImGui.endChild();
	}

	private void renderAssetList(List<AudioAsset> assets) {
		if (assets.isEmpty()) {
			ImGui.textDisabled("尚未添加音频文件。");
			return;
		}
		for (AudioAsset asset : assets) {
			boolean selected = selectedAsset != null && selectedAsset.getId().equals(asset.getId());
			if (selected) {
				ImGui.pushStyleColor(imgui.flag.ImGuiCol.ChildBg, 0.25f, 0.25f, 0.3f, 1f);
			}
			ImGui.beginChild("##asset_" + asset.getId(), 0, 80, true);
			if (selected) ImGui.popStyleColor();

			if (ImGui.isItemClicked()) {
				selectedAsset = asset;
			}

			// 基本信息行
			ImGui.text(asset.getFileName());
			ImGui.sameLine();
			ImGui.textDisabled(String.format(" %.1fs @ %dHz", asset.getDurationSeconds(), asset.getSampleRate()));

			// 状态点
			ImGui.sameLine(ImGui.getWindowWidth() - 24);
			int color = switch (asset.getStatus()) {
				case COMPLETED -> 0xFF00FF00;
				case ANALYZING -> 0xFFFFFF00;
				case FAILED -> 0xFFFF0000;
				default -> 0xFF888888;
			};
			float cx = ImGui.getCursorScreenPosX() + 8;
			float cy = ImGui.getCursorScreenPosY() + 8;
			ImGui.getWindowDrawList().addCircleFilled(cx, cy, 4f, color);

			ImGui.setCursorPosY(ImGui.getCursorPosY() + 6);

			switch (asset.getStatus()) {
				case PENDING, FAILED -> renderPendingRow(asset);
				case ANALYZING -> renderAnalyzingRow(asset);
				case COMPLETED -> renderCompletedRow(asset);
			}

			ImGui.endChild();
		}
	}

	private void renderPendingRow(AudioAsset asset) {
		if (ImGui.button("解析")) {
			AudioAssetManager.getInstance().startAnalysis(asset);
		}
		ImGui.sameLine();
		if (ImGui.button("移除")) {
			AudioAssetManager.getInstance().remove(asset.getId());
		}
		if (asset.getStatus() == AudioAssetStatus.FAILED && asset.getErrorMessage() != null) {
			ImGui.textDisabled(asset.getErrorMessage());
		}
	}

	private void renderAnalyzingRow(AudioAsset asset) {
		ImGui.textDisabled("解析中...");
		for (AudioAnalysisStep step : AudioAnalysisStep.values()) {
			boolean done = asset.getFinishedSteps().contains(step);
			String label = switch (step) {
				case BPM_DETECTION -> "BPM 检测";
				case BEAT_DETECTION -> "踩点检测";
				case BAND_SPLIT -> "频段分离";
				case SECTION_DETECTION -> "段落识别";
				case WRITE_BEATMAP -> "写入 Beatmap";
			};
			ImGui.bullet();
			ImGui.sameLine();
			String prefix = done ? "✔" : "·";
			ImGui.text(prefix + " " + label);
		}
	}

	private void renderCompletedRow(AudioAsset asset) {
		ImGui.textDisabled("拖动到时间线音频轨道");
		// 拖拽源
		if (ImGui.beginDragDropSource()) {
			AudioAssetManager.getInstance().setCurrentDragAsset(asset);
			ImGui.setDragDropPayload("BB_AUDIO_ASSET_ID", asset.getId().getBytes(), ImGuiCond.Once);
			ImGui.text(asset.getFileName());
			ImGui.endDragDropSource();
		}
	}

	private void renderPreviewHeader() {
		ImGui.setCursorPosY(ImGui.getCursorPosY() + 2);
		if (ImGui.button(previewExpanded ? "◀" : "▶")) {
			previewExpanded = !previewExpanded;
		}
		ImGui.sameLine();
		ImGui.text("预览");
		ImGui.separator();
	}

	private void renderPreview(AudioAsset asset) {
		if (asset == null) {
			ImGui.textDisabled("点击左侧列表中的音频以查看解析详情。");
			return;
		}
		ImGui.text(asset.getFileName());
		ImGui.textDisabled(String.format("时长: %.1fs  采样率: %dHz", asset.getDurationSeconds(), asset.getSampleRate()));
		ImGui.spacing();
		ImGui.text("解析结果");
		ImGui.separator();
		ImGui.textDisabled(String.format("BPM: %.1f", asset.getBpm()));
		ImGui.textDisabled("拍号: 4/4");
		ImGui.textDisabled(String.format("踩点数量: %d", asset.getBeatCount()));
		ImGui.textDisabled(String.format("识别段落: %d", asset.getSectionCount()));
		ImGui.spacing();
		ImGui.text("频段爆点分布");
		ImGui.separator();
		ImGui.textDisabled(String.format("低频（鼓点）: %d", asset.getLowCount()));
		ImGui.textDisabled(String.format("中频（旋律）: %d", asset.getMidCount()));
		ImGui.textDisabled(String.format("高频（打击）: %d", asset.getHighCount()));
	}
}

