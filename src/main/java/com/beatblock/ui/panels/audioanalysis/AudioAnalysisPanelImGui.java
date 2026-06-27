package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.audio.assets.AudioAnalysisMode;
import com.beatblock.audio.assets.AudioAnalysisPhase;
import com.beatblock.audio.assets.AudioAnalysisStep;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.WaveformPreview;
import com.beatblock.client.imgui.ImGuiFontManager;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.ui.icons.Icons;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;

import java.io.File;
import java.nio.file.Path;

/** 音频解析面板共享常量与 ImGui 辅助方法。 */
final class AudioAnalysisPanelImGui {

	static final int COLOR_DOT_DONE = 0xFF57C45D;
	static final int COLOR_DOT_QUEUED = 0xFF3AB6F5;
	static final int COLOR_DOT_ANALYZING = 0xFF27C4CA;
	static final int COLOR_DOT_FAILED = 0xFF4A4BE2;
	static final int COLOR_DOT_PENDING = 0xFF888888;

	static final ImVec4 COLOR_PROGRESS_BG = new ImVec4(0.18f, 0.18f, 0.22f, 1f);
	static final ImVec4 COLOR_PROGRESS_FG = new ImVec4(0.50f, 0.47f, 0.87f, 1f);
	static final ImVec4 COLOR_SELECTED_BG = new ImVec4(0.22f, 0.20f, 0.32f, 1f);
	static final ImVec4 COLOR_HOVER_BG = new ImVec4(0.18f, 0.17f, 0.24f, 1f);
	static final ImVec4 COLOR_LOW = new ImVec4(0.50f, 0.47f, 0.87f, 1f);
	static final ImVec4 COLOR_MID = new ImVec4(0.36f, 0.79f, 0.65f, 1f);
	static final ImVec4 COLOR_HIGH = new ImVec4(0.94f, 0.62f, 0.16f, 1f);

	static final float ICON_BTN = TimelineLayout.ROW_HEIGHT;
	static final float MIN_LIST_PANEL_WIDTH = 96f;
	static final float MIN_DETAIL_PANEL_WIDTH = 96f;
	static final float PANEL_GAP = 4f;
	static final float PANEL_OUTER_PADDING_X = 4f;
	static final float PANEL_OUTER_PADDING_Y = 6f;
	static final float LIST_PANEL_PADDING = 2f;
	static final float DETAIL_PANEL_PADDING = 6f;
	static final float FOOTER_BUTTON_HEIGHT = 22f;
	static final float FOOTER_RESERVED_HEIGHT = FOOTER_BUTTON_HEIGHT + 12f;
	static final int COLLAPSED_TEXT_MAX_CHARS = 56;

	private AudioAnalysisPanelImGui() {
	}

	static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	static String iconLabel(String icon, String fallback) {
		if (ImGuiFontManager.getIconButtonFont() == null) {
			return fallback;
		}
		return icon;
	}

	static void setTooltipWithDefaultFont() {
		if (ImGuiFontManager.getIconButtonFont() == null) {
			ImGui.setTooltip(BBTexts.get("beatblock.audio.select_file"));
			return;
		}
		ImGui.popFont();
		ImGui.setTooltip(BBTexts.get("beatblock.audio.select_file"));
		ImGui.pushFont(ImGuiFontManager.getIconButtonFont());
	}

	static String decodePayloadText(byte[] payload) {
		if (payload == null || payload.length == 0) return "";
		String raw = new String(payload);
		int zero = raw.indexOf('\0');
		if (zero >= 0) raw = raw.substring(0, zero);
		return raw.trim();
	}

	static void centerText(String text) {
		float textW = ImGui.calcTextSize(text).x;
		float offsetX = (ImGui.getContentRegionAvailX() - textW) * 0.5f;
		if (offsetX > 0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offsetX);
		ImGui.textDisabled(text);
	}

	static void textDisabledWrapped(String text) {
		if (text == null || text.isBlank()) return;
		float wrapPos = ImGui.getCursorPosX() + Math.max(64f, ImGui.getContentRegionAvailX());
		ImGui.pushTextWrapPos(wrapPos);
		ImGui.textDisabled(text);
		ImGui.popTextWrapPos();
	}

	static void compactGap() {
		ImGui.dummy(0f, 4f);
	}

	static boolean beginDetailSection(String id, String title, boolean defaultOpen) {
		int flags = ImGuiTreeNodeFlags.SpanAvailWidth
			| ImGuiTreeNodeFlags.Framed
			| ImGuiTreeNodeFlags.FramePadding
			| ImGuiTreeNodeFlags.NoTreePushOnOpen;
		if (defaultOpen) {
			flags |= ImGuiTreeNodeFlags.DefaultOpen;
		}
		boolean open = ImGui.treeNodeEx(title + "##" + id, flags);
		if (open) {
			ImGui.indent(6f);
			compactGap();
		}
		return open;
	}

	static void endDetailSection() {
		ImGui.unindent(6f);
		compactGap();
	}

	static void detailRowCompact(AudioAnalysisPanelUiState state, String key, String value) {
		detailRowCompact(state, key, value, null);
	}

	static void detailRowCompact(AudioAnalysisPanelUiState state, String key, String value, ImVec4 color) {
		ImGui.textDisabled(key + "：");
		ImGui.sameLine();
		ImGui.setCursorPosX(ImGui.getCursorPosX() + 4f);
		String rowId = "##detailCompact_" + Integer.toHexString((key + "|" + value).hashCode());
		renderCollapsedInlineValue(state, value, rowId, color);
	}

	static void renderCollapsedInlineValue(AudioAnalysisPanelUiState state, String text, String rowId, ImVec4 color) {
		String normalized = text != null ? text : "-";
		boolean expandable = shouldCollapseValue(normalized);
		boolean expanded = expandable && state.expandedDetailRows().contains(rowId);
		String display = expanded ? normalized : collapseText(normalized);
		ImVec4 resolvedColor = color != null ? color : new ImVec4(1f, 1f, 1f, 1f);

		ImGui.pushStyleColor(ImGuiCol.Text, resolvedColor.x, resolvedColor.y, resolvedColor.z, resolvedColor.w);
		if (expanded) {
			float wrapPos = ImGui.getCursorPosX() + Math.max(64f, ImGui.getContentRegionAvailX() - 52f);
			ImGui.pushTextWrapPos(wrapPos);
			ImGui.textWrapped(display);
			ImGui.popTextWrapPos();
		} else {
			ImGui.text(display);
		}
		ImGui.popStyleColor();

		if (expandable && !expanded && ImGui.isItemHovered()) {
			ImGui.setTooltip(normalized);
		}
		if (expandable) {
			ImGui.sameLine();
			if (ImGui.smallButton((expanded ? BBTexts.get("beatblock.common.collapse") : BBTexts.get("beatblock.common.expand")) + rowId)) {
				if (expanded) {
					state.expandedDetailRows().remove(rowId);
				} else {
					state.expandedDetailRows().add(rowId);
				}
			}
		}
	}

	static boolean shouldCollapseValue(String text) {
		if (text == null || text.isBlank()) return false;
		return text.length() > COLLAPSED_TEXT_MAX_CHARS
			|| text.contains("\\")
			|| text.contains("/")
			|| text.contains(":");
	}

	static String collapseText(String text) {
		if (text == null || text.length() <= COLLAPSED_TEXT_MAX_CHARS) {
			return text;
		}
		if (COLLAPSED_TEXT_MAX_CHARS < 8) {
			return text.substring(0, Math.max(1, COLLAPSED_TEXT_MAX_CHARS - 1)) + "…";
		}
		int head = COLLAPSED_TEXT_MAX_CHARS / 2 - 1;
		int tail = COLLAPSED_TEXT_MAX_CHARS - head - 1;
		return text.substring(0, head) + "…" + text.substring(text.length() - tail);
	}

	static float computeProgress(AudioAsset asset) {
		if (asset.getAnalysisProgressPercent() > 0) {
			return Math.max(0f, Math.min(1f, asset.getAnalysisProgressPercent() / 100f));
		}
		int total = AudioAnalysisStep.values().length;
		if (total == 0) return 0f;
		return (float) asset.getFinishedSteps().size() / total;
	}

	static boolean isActiveStep(AudioAsset asset, AudioAnalysisStep step) {
		for (AudioAnalysisStep s : AudioAnalysisStep.values()) {
			if (!asset.getFinishedSteps().contains(s)) {
				return s == step;
			}
		}
		return false;
	}

	static String stepLabel(AudioAnalysisStep step) {
		return switch (step) {
			case BPM_DETECTION -> BBTexts.get("beatblock.audio.step.bpm");
			case BEAT_DETECTION -> BBTexts.get("beatblock.audio.step.beat");
			case BAND_SPLIT -> BBTexts.get("beatblock.audio.step.band");
			case SECTION_DETECTION -> BBTexts.get("beatblock.audio.step.section");
			case STEM_SEPARATION -> BBTexts.get("beatblock.audio.step.stem");
			case WRITE_BEATMAP -> BBTexts.get("beatblock.audio.step.write");
		};
	}

	static String stemStateLabel(Beatmap bm, String stemKey) {
		if (bm == null || bm.meta == null || bm.meta.stems() == null) return BBTexts.get("beatblock.audio.not_generated");
		String path = bm.meta.stems().get(stemKey);
		return (path != null && !path.isBlank()) ? BBTexts.get("beatblock.audio.generated") : BBTexts.get("beatblock.audio.not_generated");
	}

	static String analysisModeLabel(AudioAnalysisMode mode) {
		if (mode == null) return "-";
		return mode == AudioAnalysisMode.DEMUCS ? BBTexts.get("beatblock.audio.mode.demucs") : BBTexts.get("beatblock.audio.mode.basic");
	}

	static String cacheSourceLabel(String cacheSource) {
		if (cacheSource == null || cacheSource.isBlank()) return "-";
		return switch (cacheSource) {
			case "beatmap-cache" -> BBTexts.get("beatblock.audio.cache.beatmap");
			case "stem-cache-reuse" -> BBTexts.get("beatblock.audio.cache.stem_reuse");
			case "fresh" -> BBTexts.get("beatblock.audio.cache.fresh");
			case "unknown" -> BBTexts.get("beatblock.audio.cache.unknown");
			default -> cacheSource;
		};
	}

	static String analysisPhaseLabel(AudioAsset asset) {
		if (asset == null) return "-";
		AudioAnalysisPhase phase = asset.getAnalysisPhase();
		if (phase == null) return BBTexts.get("beatblock.audio.phase.analyzing");
		return switch (phase) {
			case PENDING -> BBTexts.get("beatblock.audio.phase.pending");
			case QUEUED -> BBTexts.get("beatblock.audio.phase.queued");
			case ENVIRONMENT -> BBTexts.get("beatblock.audio.phase.environment");
			case STEM_SEPARATION -> BBTexts.get("beatblock.audio.phase.stem_separation");
			case RHYTHM -> BBTexts.get("beatblock.audio.phase.rhythm");
			case STRUCTURE -> BBTexts.get("beatblock.audio.phase.structure");
			case WAVEFORM -> BBTexts.get("beatblock.audio.phase.waveform");
			case WRITE_RESULT -> BBTexts.get("beatblock.audio.phase.write_result");
			case COMPLETED -> BBTexts.get("beatblock.audio.phase.completed");
			case FAILED -> BBTexts.get("beatblock.audio.phase.failed");
		};
	}

	static String queueStageLabel(AudioAsset asset) {
		if (asset == null) return "-";
		return switch (asset.getStatus()) {
			case QUEUED -> BBTexts.get("beatblock.audio.phase.queued");
			case ANALYZING -> analysisPhaseLabel(asset);
			case COMPLETED -> BBTexts.get("beatblock.audio.phase.completed");
			case FAILED -> BBTexts.get("beatblock.audio.phase.failed");
			default -> BBTexts.get("beatblock.audio.phase.pending");
		};
	}

	static void renderModeBadge(AudioAsset asset) {
		AudioAnalysisMode mode = asset.getRequestedAnalysisMode();
		ImVec4 color = mode == AudioAnalysisMode.DEMUCS
			? new ImVec4(0.22f, 0.78f, 0.82f, 1f)
			: new ImVec4(0.94f, 0.62f, 0.16f, 1f);
		ImGui.pushStyleColor(ImGuiCol.Text, color.x, color.y, color.z, color.w);
		ImGui.textDisabled("[" + analysisModeLabel(mode) + "]");
		ImGui.popStyleColor();
	}

	static void renderQueueBadge(AudioAsset asset) {
		String label = analysisModeLabel(asset.getRequestedAnalysisMode()) + " / " + queueStageLabel(asset);
		ImVec4 color = asset.getRequestedAnalysisMode() == AudioAnalysisMode.DEMUCS
			? new ImVec4(0.22f, 0.78f, 0.82f, 1f)
			: new ImVec4(0.94f, 0.62f, 0.16f, 1f);
		ImGui.pushStyleColor(ImGuiCol.Text, color.x, color.y, color.z, color.w);
		ImGui.textDisabled("[" + label + "]");
		ImGui.popStyleColor();
	}

	static void renderCacheBadge(String cacheSource) {
		if (cacheSource == null || cacheSource.isBlank()) return;
		ImVec4 color = switch (cacheSource) {
			case "beatmap-cache" -> new ImVec4(0.58f, 0.72f, 0.30f, 1f);
			case "stem-cache-reuse" -> new ImVec4(0.22f, 0.78f, 0.82f, 1f);
			case "fresh" -> new ImVec4(0.62f, 0.64f, 0.70f, 1f);
			default -> new ImVec4(0.80f, 0.80f, 0.80f, 1f);
		};
		ImGui.pushStyleColor(ImGuiCol.Text, color.x, color.y, color.z, color.w);
		ImGui.textDisabled("[" + cacheSourceLabel(cacheSource) + "]");
		ImGui.popStyleColor();
	}

	static void renderWarningBanner() {
		ImGui.pushStyleColor(ImGuiCol.Text, 0.94f, 0.62f, 0.16f, 1f);
		ImGui.textWrapped(Icons.Action.WARNING + " " + BBTexts.get("beatblock.audio.demucs_warning"));
		ImGui.popStyleColor();
	}

	static String resolveStemDisplayPath(Beatmap bm, String relativePath) {
		if (bm == null || relativePath == null || relativePath.isBlank()) return relativePath;
		try {
			Path p = Path.of(relativePath);
			if (p.isAbsolute()) return p.normalize().toString();
			if (bm.beatmapFilePath != null && bm.beatmapFilePath.getParent() != null) {
				return bm.beatmapFilePath.getParent().resolve(relativePath).normalize().toString();
			}
		} catch (RuntimeException e) {
			com.beatblock.BeatBlock.LOGGER.debug("Unable to resolve stem path '{}', using raw value", relativePath, e);
		}
		return relativePath;
	}

	static void renderStemDetailRow(AudioAnalysisPanelUiState state, AudioAnalysisPanelHost host,
		Beatmap bm, String stemKey, String label, ImVec4 color) {
		if (bm == null || bm.meta == null || bm.meta.stems() == null) {
			detailRowCompact(state, label, BBTexts.get("beatblock.audio.not_generated"), color);
			return;
		}
		String relativePath = bm.meta.stems().get(stemKey);
		if (relativePath == null || relativePath.isBlank()) {
			detailRowCompact(state, label, BBTexts.get("beatblock.audio.not_generated"), color);
			return;
		}
		String path = resolveStemDisplayPath(bm, relativePath);
		boolean fileExists = new File(path).isFile();
		detailRowCompact(state, label, fileExists ? BBTexts.get("beatblock.audio.generated") : BBTexts.get("beatblock.audio.path_missing"), color);
		detailRowCompact(state, BBTexts.get("beatblock.audio.path_for", label), path);
		renderCopyPathAction(host, path, stemKey, label);
	}

	static void renderCopyPathAction(AudioAnalysisPanelHost host, String path, String stemKey, String label) {
		if (path == null || path.isBlank()) return;
		ImGui.setCursorPosX(ImGui.getCursorPosX() + 14f);
		if (ImGui.smallButton(Icons.Action.COPY + " " + BBTexts.get("beatblock.audio.copy_path") + "##copyStemPath_" + stemKey)) {
			if (AudioAnalysisClipboard.copy(path)) {
				host.uiState().setPanelHint(BBTexts.get("beatblock.audio.copied_path", label), false);
			} else {
				host.uiState().setPanelHint(BBTexts.get("beatblock.audio.copy_failed"), true);
			}
		}
	}

	static void renderWaveformPreview(WaveformPreview preview) {
		if (preview == null || preview.data() == null || preview.data().length == 0) {
			ImGui.textDisabled(BBTexts.get("beatblock.audio.waveform_unavailable"));
			return;
		}
		float barW = Math.max(64f, ImGui.getContentRegionAvailX());
		float barH = 48f;
		float x0 = ImGui.getCursorScreenPosX();
		float y0 = ImGui.getCursorScreenPosY();
		ImGui.dummy(barW, barH + 4f);

		float[] data = preview.data();
		int samples = data.length;
		int columns = Math.max(1, (int) barW);
		var dl = ImGui.getWindowDrawList();
		float midY = y0 + barH * 0.5f;
		int waveformColor = 0xFF7777D0;
		int bgColor = 0xFF2A2A32;
		dl.addRectFilled(x0, y0, x0 + barW, y0 + barH, bgColor, 3f);
		for (int col = 0; col < columns; col++) {
			int start = (int) ((long) col * samples / columns);
			int end = (int) ((long) (col + 1) * samples / columns);
			if (end <= start) {
				end = Math.min(samples, start + 1);
			}
			float peak = 0f;
			for (int i = start; i < end; i++) {
				peak = Math.max(peak, Math.abs(data[i]));
			}
			float halfH = peak * (barH * 0.45f);
			float x = x0 + col;
			dl.addLine(x, midY - halfH, x, midY + halfH, waveformColor, 1f);
		}
		if (preview.samplesPerSecond() > 0) {
			float duration = samples / (float) preview.samplesPerSecond();
			ImGui.textDisabled(BBTexts.get("beatblock.audio.waveform_duration", duration));
		}
	}

	static void renderBandBar(AudioAsset asset) {
		int total = asset.getLowCount() + asset.getMidCount() + asset.getHighCount();
		if (total == 0) return;

		float barW = ImGui.getContentRegionAvailX();
		float barH = 8f;
		float x0 = ImGui.getCursorScreenPosX();
		float y0 = ImGui.getCursorScreenPosY();

		float lowW = barW * (asset.getLowCount() / (float) total);
		float midW = barW * (asset.getMidCount() / (float) total);

		ImGui.dummy(barW, barH + 2f);

		var dl = ImGui.getWindowDrawList();
		float r = 3f;
		dl.addRectFilled(x0, y0, x0 + lowW, y0 + barH, 0xFF7777D0, r);
		dl.addRectFilled(x0 + lowW, y0, x0 + lowW + midW, y0 + barH, 0xFF57C4A0);
		dl.addRectFilled(x0 + lowW + midW, y0, x0 + barW, y0 + barH, 0xFF27A0EF, r);

		ImGui.pushStyleColor(ImGuiCol.Text, COLOR_LOW.x, COLOR_LOW.y, COLOR_LOW.z, COLOR_LOW.w);
		ImGui.text(BBTexts.get("beatblock.audio.band_low"));
		ImGui.popStyleColor();
		ImGui.sameLine();
		ImGui.pushStyleColor(ImGuiCol.Text, COLOR_MID.x, COLOR_MID.y, COLOR_MID.z, COLOR_MID.w);
		ImGui.text(BBTexts.get("beatblock.audio.band_mid"));
		ImGui.popStyleColor();
		ImGui.sameLine();
		ImGui.pushStyleColor(ImGuiCol.Text, COLOR_HIGH.x, COLOR_HIGH.y, COLOR_HIGH.z, COLOR_HIGH.w);
		ImGui.text(BBTexts.get("beatblock.audio.band_high"));
		ImGui.popStyleColor();
	}

	static int statusDotColor(com.beatblock.audio.assets.AudioAssetStatus status) {
		return switch (status) {
			case COMPLETED -> COLOR_DOT_DONE;
			case QUEUED -> COLOR_DOT_QUEUED;
			case ANALYZING -> COLOR_DOT_ANALYZING;
			case FAILED -> COLOR_DOT_FAILED;
			default -> COLOR_DOT_PENDING;
		};
	}
}
