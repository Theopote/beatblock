package com.beatblock.timeline.rendering;

import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.WaveformData;
import com.beatblock.timeline.command.layer.BindLayerToTrackCommand;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 时间线各行内容区绘制（波形/片段条/动画事件/拖放目标）。 */
public final class TimelineRowContentRenderer {

	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;
	private static final int FREQ_MID_COLOR = 0xFF_57_C4_A0;
	private static final int ANIMATION_CLIP_DEFAULT_COLOR = 0xFF_FF_CC_66;
	private static final int AUDIO_CLIP_FILL_COLOR = 0xAA_57_C4_A0;
	private static final int AUDIO_CLIP_BORDER_COLOR = 0xFF_7F_D9_BB;

	private final EventRenderer eventRenderer;
	private final WaveformRenderer waveformRenderer;

	public TimelineRowContentRenderer(EventRenderer eventRenderer, WaveformRenderer waveformRenderer) {
		this.eventRenderer = eventRenderer;
		this.waveformRenderer = waveformRenderer;
	}

	public void drawRowContent(
		TimelineAudioDropHost dropHost,
		int rowIndex,
		float rowY,
		Timeline timeline,
		TimelineViewState viewState,
		SelectionState selectionState,
		TimelineLayout layout,
		TimelineTrackListState trackListState,
		List<TrackDefinition> audioSubTracks,
		List<TrackDefinition> animationSubTracks
	) {
		float rowHeight = layout.getRowHeight(rowIndex);
		float rowScreenY = layout.getRowScreenY(rowIndex);

		if (trackListState != null && !trackListState.isVisible(rowIndex)) {
			return;
		}

		if (rowIndex == TimelineTrackMeta.ROW_AUDIO_GROUP) {
			TimelineAudioDropHandler.renderAudioGroupDropTarget(
				dropHost, rowIndex, rowHeight, timeline, layout, trackListState);
			ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
				rowScreenY + rowHeight, true);
			renderAudioRootTrackClips(rowY, rowHeight, timeline, layout, viewState, selectionState);
			ImGui.popClipRect();
			return;
		}

		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
			if (slot < 0 || slot >= audioSubTracks.size()) return;
			TrackDefinition td = audioSubTracks.get(slot);
			TimelineAudioDropHandler.renderAudioGroupDropTarget(
				dropHost, rowIndex, rowHeight, timeline, layout, trackListState);
			ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
				rowScreenY + rowHeight, true);
			renderAudioSubTrack(td, rowY, rowHeight, timeline, layout, viewState);
			ImGui.popClipRect();
			return;
		}

		if (TimelineTrackMeta.isAnimationFeatureSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.animationFeatureSubRowSlot(rowIndex);
			if (slot < 0 || slot >= animationSubTracks.size()) {
				return;
			}
			ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
				rowScreenY + rowHeight, true);
			TrackDefinition td = animationSubTracks.get(slot);
			eventRenderer.renderAnimationEventBlocks(
				rowY,
				timeline.getAnimationEvents(td.getKey()),
				layout,
				viewState,
				selectionState,
				td.hasCustomColor() ? td.getColor() : ANIMATION_CLIP_DEFAULT_COLOR
			);
			ImGui.popClipRect();
			return;
		}

		ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
			rowScreenY + rowHeight, true);
		if (rowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK) {
			TimelineAudioDropHandler.renderAnimationTrackDropTarget(dropHost, rowIndex, rowHeight, timeline, layout);
			eventRenderer.renderAnimationEventBlocks(
				rowY, timeline.getBlockAnimationEvents(), layout, viewState, selectionState);
		} else if (rowIndex == TimelineTrackMeta.ROW_ANIM_AUTO) {
			TimelineAudioDropHandler.renderAnimationTrackDropTarget(dropHost, rowIndex, rowHeight, timeline, layout);
			eventRenderer.renderAnimationEventBlocks(
				rowY, timeline.getAutoAnimationEvents(), layout, viewState, selectionState);
		} else if (rowIndex == TimelineTrackMeta.ROW_BUILD_REVERSE) {
			renderBuildReverseTrackDropTarget(dropHost.context(), rowY, rowHeight, timeline, layout, viewState);
			eventRenderer.renderAnimationEventBlocks(
				rowY,
				timeline.getBuildReverseEvents(),
				layout,
				viewState,
				selectionState,
				0xFF_66_CC_88
			);
		} else if (rowIndex == TimelineTrackMeta.ROW_CAMERA) {
			eventRenderer.renderCameraTrackRow(rowY, timeline, layout, viewState, selectionState);
		} else if (rowIndex == TimelineTrackMeta.ROW_GLOBAL_EVENT) {
			eventRenderer.renderGlobalEventRow(rowY, timeline.getGlobalEvents(), layout, viewState);
		}
		ImGui.popClipRect();
	}

	static void renderBuildReverseTrackDropTarget(
		BeatBlockContext context,
		float rowY,
		float rowHeight,
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState viewState
	) {
		float screenY = layout.getRowScreenY(TimelineTrackMeta.ROW_BUILD_REVERSE);
		if (screenY < 0) return;
		ImGui.setCursorScreenPos(layout.contentLeft, screenY);
		ImGui.invisibleButton("##BuildReverseDropTarget", layout.contentWidth, rowHeight);
		if (ImGui.beginDragDropTarget()) {
			byte[] payload = ImGui.acceptDragDropPayload("BB_BUILD_LAYER_ID");
			if (payload != null && context != null
				&& context.blockAnimationEngine() != null && context.timelineEditor() != null) {
				String layerId = new String(payload, StandardCharsets.UTF_8).trim();
				double dropTime = viewState.screenToTime(ImGui.getMousePosX() - layout.contentLeft);
				dropTime = Math.max(0, dropTime);
				var cmd = new BindLayerToTrackCommand(
					timeline,
					context.blockAnimationEngine().getBuildLayerManager(),
					context.timelineEditor().getCommandManager(),
					layerId,
					dropTime,
					BindLayerToTrackCommand.DEFAULT_CLIP_DURATION_SECONDS
				);
				context.timelineEditor().getCommandManager().execute(cmd);
			}
			ImGui.endDragDropTarget();
		}
	}

	private void renderAudioSubTrack(
		TrackDefinition td,
		float rowY,
		float rowHeight,
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState viewState
	) {
		switch (td.getVisualType()) {
			case WAVEFORM -> {
				String key = td.getKey();
				String stemKey = key != null && key.startsWith("stem_wf_")
					? key.substring("stem_wf_".length())
					: null;
				int color = td.hasCustomColor() ? td.getColor() : 0xFF_66_AA_FF;
				Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
				boolean renderedSegment = false;
				if (audioTrack != null && !audioTrack.getClips().isEmpty()) {
					for (Clip clip : audioTrack.getClips()) {
						if (clip == null) continue;
						WaveformData clipWaveform = resolveClipWaveformData(timeline, clip, stemKey);
						if (clipWaveform == null) continue;
						waveformRenderer.renderWaveformSegment(
							rowY,
							rowHeight,
							clipWaveform,
							color,
							timeline,
							layout,
							viewState,
							clip.getStartTimeSeconds(),
							clip.getEndTimeSeconds()
						);
						renderedSegment = true;
					}
				}
				if (!renderedSegment) {
					if (stemKey != null) {
						WaveformData stemWf = timeline.getStemWaveform(stemKey);
						waveformRenderer.renderStemWaveform(rowY, rowHeight, stemWf, color, timeline, layout, viewState);
					} else {
						waveformRenderer.render(rowY, rowHeight, timeline, layout, viewState);
					}
				} else {
					ImGui.setCursorPosY(rowY + rowHeight);
				}
			}
			case IMPULSE -> {
				int color = td.hasCustomColor() ? td.getColor() : FREQ_MID_COLOR;
				List<FeatureEvent> events = timeline.getFeatureEvents(td.getKey());
				eventRenderer.renderFeatureBars(rowY, rowHeight, events, layout, viewState, color,
					timeline.getBpm(), 0.20f, 0.9f);
			}
			default -> { }
		}
	}

	static WaveformData resolveClipWaveformData(Timeline timeline, Clip clip, String stemKey) {
		if (timeline == null || clip == null) return null;
		Object clipPathObj = timeline.getMetadata("clipAudioPath_" + clip.getId());
		if (clipPathObj == null) return null;
		String clipAudioKey = TimelineAudioFeatureFillSupport.normalizeAudioPath(clipPathObj.toString());
		if (clipAudioKey == null) return null;

		AudioAsset asset = TimelineAudioFeatureFillSupport.findAssetByAudioKey(clipAudioKey);
		if (asset == null || asset.getBeatmap() == null) return null;
		com.beatblock.audio.beatmap.Beatmap beatmap = asset.getBeatmap();
		com.beatblock.audio.beatmap.WaveformPreview preview;
		if (stemKey == null) {
			preview = beatmap.waveformPreview;
		} else {
			preview = beatmap.stemWaveforms.get(stemKey);
		}
		if (preview == null || preview.data() == null || preview.data().length == 0) return null;

		float[] peaks = preview.data().clone();
		float max = 0f;
		for (float p : peaks) if (p > max) max = p;
		if (max > 1e-6f && max != 1f) {
			for (int i = 0; i < peaks.length; i++) peaks[i] /= max;
		}
		double duration = TimelineAudioDropHandler.resolveAssetDurationSeconds(asset, timeline);
		int sampleRate = beatmap.meta != null ? beatmap.meta.sampleRate() : 44100;
		return new WaveformData(peaks, duration, sampleRate);
	}

	private void renderAudioRootTrackClips(
		float rowY,
		float rowHeight,
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState viewState,
		SelectionState selectionState
	) {
		if (timeline == null || layout == null || viewState == null) return;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null || audioTrack.getClips().isEmpty()) return;

		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY();
		double vs = viewState.getViewStartTimeSeconds();
		double ve = viewState.getViewEndTimeSeconds();

		for (Clip clip : audioTrack.getClips()) {
			if (clip == null) continue;
			double start = clip.getStartTimeSeconds();
			double end = clip.getEndTimeSeconds();
			if (end < vs || start > ve) continue;

			float x0 = baseX + viewState.timeToScreen(start);
			float x1 = baseX + viewState.timeToScreen(end);
			if (x1 < layout.contentLeft || x0 > layout.contentLeft + layout.contentWidth) continue;
			x0 = Math.max(x0, layout.contentLeft);
			x1 = Math.min(x1, layout.contentLeft + layout.contentWidth);
			if (x1 <= x0) continue;

			float y0 = baseY + 2f;
			float y1 = baseY + Math.max(6f, rowHeight - 2f);
			ImGui.getWindowDrawList().addRectFilled(x0, y0, x1, y1, AUDIO_CLIP_FILL_COLOR, 3f);
			ImGui.getWindowDrawList().addRect(x0, y0, x1, y1, AUDIO_CLIP_BORDER_COLOR, 3f, 0, 1.2f);

			if (selectionState != null && selectionState.isClipSelected(clip.getId())) {
				ImGui.getWindowDrawList().addRect(x0 - 1f, y0 - 1f, x1 + 1f, y1 + 1f, SELECTED_BORDER_COLOR, 3f, 0, 2f);
			}

			Object labelObj = timeline.getMetadata("clipLabel_" + clip.getId());
			if (labelObj != null && x1 - x0 > 16f) {
				float textY = y0 + Math.max(2f, (y1 - y0 - 13f) / 2f);
				float maxTextWidth = Math.max(0f, x1 - x0 - 10f);
				String label = fitLabelWithEllipsis(labelObj.toString(), maxTextWidth);
				if (!label.isEmpty()) {
					ImGui.getWindowDrawList().addText(x0 + 5f, textY, 0xFF_FF_FF_BB, label);
				}
			}
		}

		ImGui.setCursorPosY(rowY + rowHeight);
	}

	static String fitLabelWithEllipsis(String raw, float maxWidth) {
		if (raw == null || raw.isBlank() || maxWidth <= 4f) return "";
		if (ImGui.calcTextSize(raw).x <= maxWidth) return raw;
		final String ellipsis = "...";
		float ellipsisW = ImGui.calcTextSize(ellipsis).x;
		if (ellipsisW >= maxWidth) return "";
		int keep = raw.length();
		while (keep > 0) {
			String candidate = raw.substring(0, keep) + ellipsis;
			if (ImGui.calcTextSize(candidate).x <= maxWidth) return candidate;
			keep--;
		}
		return "";
	}
}
