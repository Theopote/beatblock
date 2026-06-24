package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.timeline.*;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.editor.SelectionBox;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 时间线渲染入口：按 4 区域绘制（1.时间尺 2.轨道名 3.网格 4.内容/事件/播放头/框选）。
 */
public final class TimelineRenderer implements TimelineAudioDropHost {
	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineRenderer.class);

	public static final int PLAYHEAD_COLOR = 0xFF_FF_66_66;
	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;
	private static final int ALIGNMENT_GUIDE_COLOR = 0xAA_FF_CC_44;
	private static final int FREQ_MID_COLOR = 0xFF_57_C4_A0;
	private static final int FREQ_HIGH_COLOR = 0xFF_27_A0_EF;
	private static final int ANIMATION_CLIP_DEFAULT_COLOR = 0xFF_FF_CC_66;
	private static final int AUDIO_CLIP_FILL_COLOR = 0xAA_57_C4_A0;
	private static final int AUDIO_CLIP_BORDER_COLOR = 0xFF_7F_D9_BB;
	/** 轨道槽交替背景（深色），使轨道行更明显 */
	private static final int ROW_BG_EVEN = 0xFF_28_28_2A;
	private static final int ROW_BG_ODD = 0xFF_1E_1E_20;
	/** 组头行专用背景与分段分隔线（增强三段式结构可读性） */
	private static final int GROUP_ROW_BG = 0xFF_2E_2B_33;
	private static final int GROUP_SEPARATOR_COLOR = 0x99_6E_7D_96;
	/** 左侧轨道列表与右侧内容区的竖线分隔（ABGR，供面板贯通绘制） */
	public static final int TIMELINE_DIVIDER_COLOR = 0x66_88_88_88;
	private static final int PAIRED_ROW_HOVER_FILL = 0x33_8A_BA_FF;
	private static final int PAIRED_ROW_HOVER_BORDER = 0x99_A0_D0_FF;

	/** 音频组高亮颜色（紫色半透明边框，ABGR） */
	private static final int AUDIO_GROUP_DROP_HIGHLIGHT_COLOR = 0x55_7F_77_DD;

	private final GridRenderer gridRenderer = new GridRenderer();
	private final TrackRenderer trackRenderer = new TrackRenderer();
	private final EventRenderer eventRenderer = new EventRenderer();
	private final WaveformRenderer waveformRenderer = new WaveformRenderer();
	private final TimelineDenseFeatureApplier denseFeatureApplier = new TimelineDenseFeatureApplier();

	/**
	 * 本帧计算出的音频子轨定义列表（由 TrackRegistry.buildAudioSubTracks 生成）。
	 * 在 renderTrackArea 开始时更新，drawRowContent 中按槽索引查找。
	 * 只在 featureTracks keySet 发生变化时重建，避免每帧分配新对象。
	 */
	private List<TrackDefinition> currentAudioSubTracks = Collections.emptyList();
	private List<TrackDefinition> currentAnimationSubTracks = Collections.emptyList();
	/** 上次构建 currentAudioSubTracks 时的 featureTracks key 快照，用于脏检测。 */
	private AudioSubTrackCacheKey lastAudioSubTrackCacheKey = AudioSubTrackCacheKey.empty();
	private Set<String> lastAnimationTrackIds = Set.of();
	private final Map<String, PairVisibilitySnapshot> pairedFeatureVisibility = new HashMap<>();

	/** 当前帧音频组是否有拖拽悬停高亮（任意 row 0~4 悬停且有 audio payload 时置 true） */
	private boolean audioGroupDropHighlight;
	/** 已注册静音回调的 TrackListState 对象（避免重复注册）。 */
	private TimelineTrackListState registeredMuteListenerFor;
	private final Supplier<BeatBlockContext> contextSource;

	public TimelineRenderer() {
		this(BeatBlock::getContext);
	}

	TimelineRenderer(Supplier<BeatBlockContext> contextSource) {
		this.contextSource = contextSource != null ? contextSource : BeatBlock::getContext;
	}

	private BeatBlockContext ctx() {
		return contextSource.get();
	}

	private record PairVisibilitySnapshot(boolean audioVisible, boolean controlVisible) {}

	private record AudioSubTrackCacheKey(
		boolean hasWaveform,
		boolean hasStemWaveforms,
		Set<String> featureKeys
	) {
		private static AudioSubTrackCacheKey empty() {
			return new AudioSubTrackCacheKey(false, false, Set.of());
		}
	}

	/**
	 * 固定区域：只绘制时间刻度行（左侧「时间」标签 + 标尺），分界线与轨道区对齐，并占位。
	 *
	 * @param bpm 来自 Timeline.getBpm()；传 0 表示无 BPM 信息
	 */
	public void renderRulerRow(TimelineLayout layout, TimelineViewState viewState, double bpm, TimelineToolbarState toolbarState, Timeline timeline) {
		if (viewState == null || layout == null) return;
		ImGui.setCursorScreenPos(layout.trackHeaderLeft + 4f, layout.rulerTop + 4f);
		ImGui.textDisabled("时间");
		gridRenderer.renderRuler(layout.startY, viewState, layout, bpm, toolbarState, timeline);
		drawDivider(layout, layout.rulerTop, layout.rulerTop + layout.rulerHeight);
		// 竖向分割线在 TimelinePanel 中自标尺顶贯通画到子窗口底，避免与滚动区重复/断层
		ImGui.setCursorPosY(layout.startY + TimelineLayout.RULER_HEIGHT);
	}

	/** 可滚动区域：轨道区（左侧轨道列表 + 竖线分隔 + 网格 + 一行一行轨道 + 播放头 + 框选）。 */
	public void renderTrackArea(
		Timeline timeline,
		TimelineViewState viewState,
		SelectionState selectionState,
		TimelineClock clock,
		SelectionBox selectionBox,
		InteractionState interactionState,
		TimelineTrackListState trackListState,
		TimelineLayout layout
	) {
		if (timeline == null || viewState == null || layout == null) return;

		// 首次（或 trackListState 更换后）注册静音/独奏变更回调
		if (registeredMuteListenerFor != trackListState) {
			registeredMuteListenerFor = trackListState;
			trackListState.setMuteChangeListener(() -> syncStemMuteState(trackListState));
		}

		denseFeatureApplier.applyPendingUpdates(ctx(), timeline);
		denseFeatureApplier.tryAutoApplyAnalyzedBeatmap(this, timeline);

		// ── 音频子轨定义列表：仅在 featureTracks keySet 变化时重建 ─────────────
		// TrackRegistry.buildAudioSubTracks 内部每次都分配新 ArrayList + TrackDefinition 对象，
		// 60fps 下产生持续 GC 压力。轨道定义只在 featureTracks 内容发生变化时才需要重建。
		AudioSubTrackCacheKey currentAudioCacheKey = new AudioSubTrackCacheKey(
			timeline.getWaveform() != null,
			timeline.hasStemWaveforms(),
			Set.copyOf(timeline.getFeatureTracks().keySet())
		);
		if (!lastAudioSubTrackCacheKey.equals(currentAudioCacheKey)) {
			lastAudioSubTrackCacheKey = currentAudioCacheKey;
			currentAudioSubTracks  = TrackRegistry.buildAudioSubTracks(timeline);
		}
		layout.setActiveAudioSubRowCount(currentAudioSubTracks.size());
		Set<String> currentAnimationIds = timeline.getTracks().stream()
			.map(Track::getId)
			.filter(Timeline::isBlockAnimationFeatureTrackId)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		if (!lastAnimationTrackIds.equals(currentAnimationIds)) {
			lastAnimationTrackIds = currentAnimationIds;
			currentAnimationSubTracks = TrackRegistry.buildBlockAnimationControlTracks(timeline);
		}
		syncPairedFeatureLaneState(trackListState);
		layout.setActiveAnimationSubRowCount(currentAnimationSubTracks.size());
		if (trackListState != null) {
			syncPrimaryPlayerMuteState(trackListState);
		}
		if (trackListState != null && ctx().stemMixer() != null && ctx().stemMixer().hasStems()) {
			syncStemMuteState(trackListState);
		}

		// 预留轨道区总高度，使子窗口滚动范围正确
		ImGui.dummy(0, layout.contentHeight);

		// 轨道槽交替背景（仅可见行），轨道与轨道之间靠 ROW_GAP 留白
		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			if (!layout.isRowVisible(i)) continue;
			float rowScreenY = layout.getRowScreenY(i);
			float rowH = layout.getRowHeight(i);
			int bg;
			if (TimelineTrackMeta.isGroupRow(i)) {
				bg = GROUP_ROW_BG;
			} else {
				int vi = layout.getVisibleIndex(i);
				bg = (vi % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
			}
			ImGui.getWindowDrawList().addRectFilled(x0, rowScreenY, x1, rowScreenY + rowH, bg);
		}
		drawGroupSectionSeparators(layout, x0, x1);

		// 网格竖线（仅时间轴方向，不画行间线）
		gridRenderer.render(viewState, layout, layout.contentHeight);
		drawPairedFeatureHoverHighlight(layout);
		drawActionCameraHoverHighlight(layout);

		// 每帧重置音频组拖放高亮标记
		audioGroupDropHighlight = false;

		// 轨道名 + 内容区（仅可见行）；组可折叠，折叠后子轨道不绘制
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			if (!layout.isRowVisible(i)) continue;
			float rowY = layout.getRowCursorY(i);
			float rowHeight = layout.getRowHeight(i);
			boolean isGroup = TimelineTrackMeta.isGroupRow(i);
			String displayName = resolveDisplayName(i, trackListState);
			boolean canControlPlayback = isPlaybackControlRow(i);
			String rowTypeLabel = resolveTypeLabel(i);
			trackRenderer.drawTrackLabel(rowY, rowHeight, i, displayName, isGroup, trackListState,
				layout.trackHeaderLeft, layout.trackHeaderWidth, canControlPlayback, rowTypeLabel,
				timeline, clock);
			drawRowContent(i, rowY, timeline, viewState, selectionState, layout, trackListState);
		}

		// 音频组拖放高亮（在所有行内容绘制后叠加边框）
		drawAudioGroupDropHighlight(layout);

		// 分割线：位于轨道背景/内容之上，但低于对齐辅助线与框选。
		drawDivider(layout, layout.contentTop, layout.contentTop + layout.contentHeight);

		drawAlignmentGuides(viewState, layout, interactionState);

		// 框选矩形
		if (selectionBox != null && selectionBox.isActive()) {
			ImGui.getWindowDrawList().addRect(selectionBox.getMinX(), selectionBox.getMinY(), selectionBox.getMaxX(), selectionBox.getMaxY(), SELECTED_BORDER_COLOR, 0f, 0, 1.5f);
		}
	}

	private void drawDivider(TimelineLayout layout, float y0, float y1) {
		if (layout == null || y1 <= y0) return;
		ImGui.getWindowDrawList().addLine(layout.contentLeft, y0, layout.contentLeft, y1, TIMELINE_DIVIDER_COLOR, 1f);
	}

	private void drawGroupSectionSeparators(TimelineLayout layout, float x0, float x1) {
		if (layout == null) return;
		int[] groupRows = {
			TimelineTrackMeta.ROW_AUDIO_GROUP,
			TimelineTrackMeta.ROW_ANIMATION_GROUP,
			TimelineTrackMeta.ROW_ACTION_GROUP
		};
		for (int idx = 1; idx < groupRows.length; idx++) {
			int row = groupRows[idx];
			if (!layout.isRowVisible(row)) continue;
			float y = layout.getRowScreenY(row) - Math.max(1f, TimelineLayout.ROW_GAP * 0.5f);
			ImGui.getWindowDrawList().addLine(x0, y, x1, y, GROUP_SEPARATOR_COLOR, 1.2f);
		}
	}

	private void drawPairedFeatureHoverHighlight(TimelineLayout layout) {
		if (layout == null) return;
		if (!ImGui.isWindowHovered()) return;

		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		if (mx < x0 || mx > x1 || my < layout.contentTop || my > layout.contentTop + layout.contentHeight) {
			return;
		}

		int hoveredRow = layout.findRowAtScreenY(my);
		if (hoveredRow < 0) return;

		Map<String, Integer> audioFeatureRows = new HashMap<>();
		for (int slot = 0; slot < currentAudioSubTracks.size(); slot++) {
			TrackDefinition td = currentAudioSubTracks.get(slot);
			if (td.getVisualType() != TrackDefinition.VisualType.IMPULSE) continue;
			String key = td.getKey();
			if (key == null || key.isBlank()) continue;
			audioFeatureRows.put(key, TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot);
		}

		Map<String, Integer> controlFeatureRows = new HashMap<>();
		for (int slot = 0; slot < currentAnimationSubTracks.size(); slot++) {
			String key = Timeline.blockAnimationFeatureKeyFromTrackId(currentAnimationSubTracks.get(slot).getKey());
			if (key.isBlank()) continue;
			controlFeatureRows.put(key, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
		}

		String hoveredFeature = null;
		if (TimelineTrackMeta.isAudioSubRow(hoveredRow)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(hoveredRow);
			if (slot >= 0 && slot < currentAudioSubTracks.size()) {
				TrackDefinition td = currentAudioSubTracks.get(slot);
				if (td.getVisualType() == TrackDefinition.VisualType.IMPULSE) {
					hoveredFeature = td.getKey();
				}
			}
		} else if (TimelineTrackMeta.isAnimationFeatureSubRow(hoveredRow)) {
			int slot = TimelineTrackMeta.animationFeatureSubRowSlot(hoveredRow);
			if (slot >= 0 && slot < currentAnimationSubTracks.size()) {
				hoveredFeature = Timeline.blockAnimationFeatureKeyFromTrackId(currentAnimationSubTracks.get(slot).getKey());
			}
		}

		if (hoveredFeature == null || hoveredFeature.isBlank()) return;
		Integer audioRow = audioFeatureRows.get(hoveredFeature);
		Integer controlRow = controlFeatureRows.get(hoveredFeature);
		if (audioRow != null) drawHoverRowHighlight(layout, audioRow, x0, x1);
		if (controlRow != null) drawHoverRowHighlight(layout, controlRow, x0, x1);
	}

	private void drawHoverRowHighlight(TimelineLayout layout, int rowIndex, float x0, float x1) {
		if (layout == null || !layout.isRowVisible(rowIndex)) return;
		float y0 = layout.getRowScreenY(rowIndex);
		float y1 = y0 + layout.getRowHeight(rowIndex);
		if (y0 < 0 || y1 <= y0) return;
		ImGui.getWindowDrawList().addRectFilled(x0, y0, x1, y1, PAIRED_ROW_HOVER_FILL, 2f);
		ImGui.getWindowDrawList().addRect(x0, y0, x1, y1, PAIRED_ROW_HOVER_BORDER, 2f, 0, 1f);
	}

	private void drawActionCameraHoverHighlight(TimelineLayout layout) {
		if (layout == null || !ImGui.isWindowHovered()) return;

		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		if (mx < x0 || mx > x1 || my < layout.contentTop || my > layout.contentTop + layout.contentHeight) {
			return;
		}

		int hoveredRow = layout.findRowAtScreenY(my);
		if (hoveredRow < 0) return;

		int[] linkedRows;
		if (hoveredRow == TimelineTrackMeta.ROW_CAMERA) {
			linkedRows = new int[]{
				TimelineTrackMeta.ROW_ANIM_BLOCK,
				TimelineTrackMeta.ROW_ANIM_AUTO,
				TimelineTrackMeta.ROW_BUILD_REVERSE
			};
		} else if (hoveredRow == TimelineTrackMeta.ROW_ANIM_BLOCK
			|| hoveredRow == TimelineTrackMeta.ROW_ANIM_AUTO
			|| hoveredRow == TimelineTrackMeta.ROW_BUILD_REVERSE) {
			linkedRows = new int[]{TimelineTrackMeta.ROW_CAMERA};
		} else {
			return;
		}

		drawHoverRowHighlight(layout, hoveredRow, x0, x1);
		for (int row : linkedRows) {
			drawHoverRowHighlight(layout, row, x0, x1);
		}
	}

	private void drawAlignmentGuides(TimelineViewState viewState, TimelineLayout layout, InteractionState interactionState) {
		if (viewState == null || layout == null || interactionState == null) return;
		double[] guides = interactionState.getAlignmentGuideTimes();
		if (guides.length == 0) return;

		float y0 = layout.contentTop;
		float y1 = layout.contentTop + layout.contentHeight;
		float xLeft = layout.contentLeft;
		float xRight = layout.contentLeft + layout.contentWidth;
		for (double t : guides) {
			float x = xLeft + viewState.timeToScreen(t);
			if (x < xLeft - 1 || x > xRight + 1) continue;
			ImGui.getWindowDrawList().addLine(x, y0, x, y1, ALIGNMENT_GUIDE_COLOR, 1.5f);
		}
	}

	private void drawRowContent(int rowIndex, float rowY, Timeline timeline, TimelineViewState viewState,
	                             SelectionState selectionState, TimelineLayout layout,
	                             TimelineTrackListState trackListState) {
		float rowHeight = layout.getRowHeight(rowIndex);
		float rowScreenY = layout.getRowScreenY(rowIndex);

		// 可见性只控制内容区（波形/事件），不隐藏左侧轨道头，便于再次点击恢复显示。
		if (trackListState != null && !trackListState.isVisible(rowIndex)) {
			return;
		}

		// ── 音频组标题行 ──────────────────────────────────────────────────────
		if (rowIndex == TimelineTrackMeta.ROW_AUDIO_GROUP) {
			TimelineAudioDropHandler.renderAudioGroupDropTarget(this, rowIndex, rowHeight, timeline, layout, trackListState);
			ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
				rowScreenY + rowHeight, true);
			renderAudioRootTrackClips(rowY, rowHeight, timeline, layout, viewState, selectionState);
			ImGui.popClipRect();
			return;
		}

		// ── 动态音频子轨（TrackRegistry 驱动） ───────────────────────────────
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
			if (slot < 0 || slot >= currentAudioSubTracks.size()) return;
			TrackDefinition td = currentAudioSubTracks.get(slot);
			TimelineAudioDropHandler.renderAudioGroupDropTarget(this, rowIndex, rowHeight, timeline, layout, trackListState);
			ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
				rowScreenY + rowHeight, true);
			renderAudioSubTrack(td, rowY, rowHeight, timeline, layout, viewState);
			ImGui.popClipRect();
			return;
		}
		if (TimelineTrackMeta.isAnimationFeatureSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.animationFeatureSubRowSlot(rowIndex);
			if (slot < 0 || slot >= currentAnimationSubTracks.size()) {
				return;
			}
			ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
				rowScreenY + rowHeight, true);
			TrackDefinition td = currentAnimationSubTracks.get(slot);
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

		// ── 固定非音频轨道 ────────────────────────────────────────────────────
		ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
			rowScreenY + rowHeight, true);
		if (rowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK) {
			TimelineAudioDropHandler.renderAnimationTrackDropTarget(this, rowIndex, rowHeight, timeline, layout);
			eventRenderer.renderAnimationEventBlocks(rowY, timeline.getBlockAnimationEvents(), layout, viewState, selectionState);
		} else if (rowIndex == TimelineTrackMeta.ROW_ANIM_AUTO) {
			TimelineAudioDropHandler.renderAnimationTrackDropTarget(this, rowIndex, rowHeight, timeline, layout);
			eventRenderer.renderAnimationEventBlocks(rowY, timeline.getAutoAnimationEvents(), layout, viewState, selectionState);
		} else if (rowIndex == TimelineTrackMeta.ROW_BUILD_REVERSE) {
			renderBuildReverseTrackDropTarget(rowY, rowHeight, timeline, layout, viewState);
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

	/**
	 * 根据 {@link TrackDefinition} 渲染单条音频子轨内容（波形 / 冲击柱）。
	 */
	private void renderAudioSubTrack(TrackDefinition td, float rowY, float rowHeight,
	                                 Timeline timeline, TimelineLayout layout, TimelineViewState viewState) {
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
		}
	}

	private WaveformData resolveClipWaveformData(Timeline timeline, Clip clip, String stemKey) {
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

	/**
	 * 在顶部音频组轨道渲染可交互的音频片段条（来自 Timeline.TRACK_ID_AUDIO 的 Clip）。
	 */
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

			// 在片段条上显示音频文件名（仅当宽度足够时）
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

	/** 将文本裁剪到指定宽度，超出时追加省略号。 */
	private static String fitLabelWithEllipsis(String raw, float maxWidth) {
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

	/**
	 * 解析行的显示名称：音频子轨优先使用 TrackDefinition.displayName，
	 * 其次是用户自定义名，最后是 TimelineTrackMeta 默认名。
	 */
	private String resolveDisplayName(int rowIndex, TimelineTrackListState trackListState) {
		// 用户自定义名（非空）优先
		if (trackListState != null) {
			String custom = trackListState.getDisplayName(rowIndex);
			String fallback = TimelineTrackMeta.getDefaultName(rowIndex);
			boolean isCustom = !custom.equals(fallback) && !custom.isEmpty();
			if (isCustom) return custom;
		}
		// 动态音频子轨用 TrackDefinition 的 displayName
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
			if (slot >= 0 && slot < currentAudioSubTracks.size()) {
				TrackDefinition td = currentAudioSubTracks.get(slot);
				if (td.getVisualType() == TrackDefinition.VisualType.IMPULSE) {
					String key = td.getKey();
					return TrackRegistry.localizedName(key) + " 特征";
				}
				return td.getDisplayName();
			}
		}
		if (TimelineTrackMeta.isAnimationFeatureSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.animationFeatureSubRowSlot(rowIndex);
			if (slot >= 0 && slot < currentAnimationSubTracks.size()) {
				String featureKey = Timeline.blockAnimationFeatureKeyFromTrackId(currentAnimationSubTracks.get(slot).getKey());
				String base = !featureKey.isBlank()
					? TrackRegistry.localizedName(featureKey)
					: currentAnimationSubTracks.get(slot).getDisplayName();
				return base + " 控制";
			}
		}
		return trackListState != null ? trackListState.getDisplayName(rowIndex) : TimelineTrackMeta.getDefaultName(rowIndex);
	}

	/**
	 * 解析行类型标签：Demucs 茎波形轨显示「音频」，librosa 特征轨显示「节奏特征」，
	 * 音频组行显示「音频片段」，其他行由 TimelineTrackMeta.getCategoryTypeLabel 决定。
	 */
	private String resolveTypeLabel(int rowIndex) {
		if (rowIndex == TimelineTrackMeta.ROW_AUDIO_GROUP) return "音频片段";
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
			if (slot >= 0 && slot < currentAudioSubTracks.size()) {
				String key = currentAudioSubTracks.get(slot).getKey();
				// Demucs 茎波形行（key = "waveform" 或 "stem_wf_*"）→「音频」
				if ("waveform".equals(key) || (key != null && key.startsWith("stem_wf_"))) {
					return "音频";
				}
				// librosa 特征轨 → 「节奏特征」
				return "节奏特征";
			}
		}
		if (TimelineTrackMeta.isAnimationFeatureSubRow(rowIndex)) {
			return "动画控制";
		}
		return TimelineTrackMeta.getCategoryTypeLabel(rowIndex);
	}

	private void syncPairedFeatureLaneState(TimelineTrackListState trackListState) {
		if (trackListState == null) return;

		Map<String, Integer> audioRowsByFeature = new HashMap<>();
		for (int slot = 0; slot < currentAudioSubTracks.size(); slot++) {
			TrackDefinition td = currentAudioSubTracks.get(slot);
			if (td.getVisualType() != TrackDefinition.VisualType.IMPULSE) continue;
			String key = td.getKey();
			if (key == null || key.isBlank()) continue;
			audioRowsByFeature.put(key, TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot);
		}

		Map<String, Integer> controlRowsByFeature = new HashMap<>();
		for (int slot = 0; slot < currentAnimationSubTracks.size(); slot++) {
			TrackDefinition td = currentAnimationSubTracks.get(slot);
			String key = Timeline.blockAnimationFeatureKeyFromTrackId(td.getKey());
			if (key.isBlank()) continue;
			controlRowsByFeature.put(key, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
		}

		Set<String> pairedKeys = new HashSet<>(audioRowsByFeature.keySet());
		pairedKeys.retainAll(controlRowsByFeature.keySet());
		pairedFeatureVisibility.keySet().retainAll(pairedKeys);

		for (String key : pairedKeys) {
			int audioRow = audioRowsByFeature.get(key);
			int controlRow = controlRowsByFeature.get(key);
			boolean audioVisible = trackListState.isVisible(audioRow);
			boolean controlVisible = trackListState.isVisible(controlRow);
			PairVisibilitySnapshot previous = pairedFeatureVisibility.get(key);

			if (audioVisible != controlVisible) {
				if (previous == null) {
					trackListState.setVisible(controlRow, audioVisible);
				} else {
					boolean audioChanged = previous.audioVisible() != audioVisible;
					boolean controlChanged = previous.controlVisible() != controlVisible;
					if (audioChanged && !controlChanged) {
						trackListState.setVisible(controlRow, audioVisible);
					} else if (controlChanged && !audioChanged) {
						trackListState.setVisible(audioRow, controlVisible);
					} else {
						trackListState.setVisible(controlRow, audioVisible);
					}
				}
			}

			pairedFeatureVisibility.put(key, new PairVisibilitySnapshot(
				trackListState.isVisible(audioRow),
				trackListState.isVisible(controlRow)
			));
		}
	}

	/**
	 * 仅主混音与 Demucs 茎波形轨是可真实发声轨道，才显示静音/独奏控制。
	 */
	private boolean isPlaybackControlRow(int rowIndex) {
		if (!TimelineTrackMeta.isAudioSubRow(rowIndex)) return false;
		int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
		if (slot < 0 || slot >= currentAudioSubTracks.size()) return false;
		String key = currentAudioSubTracks.get(slot).getKey();
		if (key == null || key.isBlank()) return false;
		return "waveform".equals(key) || key.startsWith("stem_wf_");
	}

	private void renderBuildReverseTrackDropTarget(
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
			if (payload != null && ctx().blockAnimationEngine() != null && ctx().timelineEditor() != null) {
				String layerId = new String(payload, StandardCharsets.UTF_8).trim();
				double dropTime = viewState.screenToTime(ImGui.getMousePosX() - layout.contentLeft);
				dropTime = Math.max(0, dropTime);
				var cmd = new com.beatblock.timeline.command.layer.BindLayerToTrackCommand(
					timeline,
					ctx().blockAnimationEngine().getBuildLayerManager(),
					ctx().timelineEditor().getCommandManager(),
					layerId,
					dropTime,
					com.beatblock.timeline.command.layer.BindLayerToTrackCommand.DEFAULT_CLIP_DURATION_SECONDS
				);
				ctx().timelineEditor().getCommandManager().execute(cmd);
			}
			ImGui.endDragDropTarget();
		}
	}

	@Override
	public BeatBlockContext context() {
		return ctx();
	}

	@Override
	public void setAudioGroupDropHighlight(boolean highlight) {
		audioGroupDropHighlight = highlight;
	}

	@Override
	public void resetBeatmapAutoApplySignature() {
		denseFeatureApplier.resetAutoApplySignature();
	}

	@Override
	public void requestDenseFeatureEnrichment(Timeline timeline, AudioAsset asset) {
		denseFeatureApplier.requestEnrichment(ctx(), timeline, asset);
	}

	@Override
	public void bindStemAudioIfDemucs(com.beatblock.audio.beatmap.Beatmap beatmap) {
		bindStemAudioIfDemucsInternal(beatmap);
	}

	@Override
	public String resolveDefaultTargetObjectId() {
		if (ctx().blockAnimationEngine() != null) {
			var sys = ctx().blockAnimationEngine().getStageObjectSystem();
			var all = sys.getAll();
			if (!all.isEmpty()) {
				return all.iterator().next().getId();
			}
		}
		return "default";
	}

	@Override
	public void syncClockDuration() {
		if (ctx().timelineEditor() != null) {
			ctx().timelineEditor().syncClockDuration();
		}
	}

	/** 释放后台 dense 特征分析线程。 */
	public void shutdown() {
		denseFeatureApplier.shutdown();
	}

	/**
	 * 将 trackListState 的茎轨道静音/独奏状态同步到 {@link BeatBlock#stemMixer}。
	 * 仅影响 key 为 "stem_wf_*" 的音频子轨对应的茎。
	 */
	private void syncStemMuteState(TimelineTrackListState trackListState) {
		if (ctx().stemMixer() == null || !ctx().stemMixer().hasStems()) return;
		syncOneStemMute(trackListState, "drums");
		syncOneStemMute(trackListState, "bass");
		syncOneStemMute(trackListState, "vocals");
		syncOneStemMute(trackListState, "other");
	}

	private void syncOneStemMute(TimelineTrackListState trackListState, String stemName) {
		boolean hasMappedRow = false;
		boolean anyAudibleMappedRow = false;
		for (int slot = 0; slot < currentAudioSubTracks.size(); slot++) {
			TrackDefinition td = currentAudioSubTracks.get(slot);
			String key = td.getKey();
			if (!mapsToStemAudioControl(key, stemName)) continue;
			hasMappedRow = true;
			int rowIndex = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (!isPlayableAudioRowEffectivelyMuted(trackListState, rowIndex)) {
				anyAudibleMappedRow = true;
			}
		}

		// 语义：仅当存在映射行且这些行都被静音/独奏抑制时，才静音该茎
		boolean muted = hasMappedRow && !anyAudibleMappedRow;
		ctx().stemMixer().setStemMuted(stemName, muted);
	}

	private boolean mapsToStemAudioControl(String trackKey, String stemName) {
		if (trackKey == null || stemName == null) return false;
		if (trackKey.startsWith("stem_wf_")) {
			return stemName.equals(trackKey.substring("stem_wf_".length()));
		}
		return false;
	}

	private boolean isPlayableAudioControlRow(int rowIndex) {
		if (!TimelineTrackMeta.isAudioSubRow(rowIndex)) return false;
		int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
		if (slot < 0 || slot >= currentAudioSubTracks.size()) return false;
		String key = currentAudioSubTracks.get(slot).getKey();
		return "waveform".equals(key) || (key != null && key.startsWith("stem_wf_"));
	}

	private boolean isPlayableAudioRowEffectivelyMuted(TimelineTrackListState trackListState, int rowIndex) {
		if (trackListState == null) return false;
		if (trackListState.isMuted(rowIndex)) return true;

		boolean groupSoloed = trackListState.isSoloed(TimelineTrackMeta.ROW_AUDIO_GROUP);
		boolean anyPlayableSolo = groupSoloed;
		for (int slot = 0; slot < currentAudioSubTracks.size(); slot++) {
			int candidateRowIndex = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (!isPlayableAudioControlRow(candidateRowIndex)) continue;
			if (trackListState.isSoloed(candidateRowIndex)) {
				anyPlayableSolo = true;
				break;
			}
		}

		if (!anyPlayableSolo) return false;
		if (groupSoloed) return false;
		return !trackListState.isSoloed(rowIndex);
	}

	private void syncPrimaryPlayerMuteState(TimelineTrackListState trackListState) {
		if (ctx().musicPlayer() == null) return;

		boolean anyAudioRowAudible = false;
		for (int slot = 0; slot < currentAudioSubTracks.size(); slot++) {
			TrackDefinition td = currentAudioSubTracks.get(slot);
			if (!"waveform".equals(td.getKey())) continue;
			int rowIndex = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (!isPlayableAudioRowEffectivelyMuted(trackListState, rowIndex)) {
				anyAudioRowAudible = true;
				break;
			}
		}

		// 没有任何可听音频子轨时静音主播放器。
		ctx().musicPlayer().setMuted(!anyAudioRowAudible);
	}

	/**
	 * 若 beatmap 为 Demucs 茎分离模式，将各茎 WAV 加载进 {@link BeatBlock#stemMixer}，
	 * 若非 Demucs 模式则清空 stemMixer。
	 */
	private void bindStemAudioIfDemucsInternal(com.beatblock.audio.beatmap.Beatmap beatmap) {
		if (ctx().stemMixer() == null) return;
		ctx().stemMixer().clearStems();

		if (beatmap == null || beatmap.meta == null || !beatmap.meta.hasStemSeparation()) return;
		if (beatmap.beatmapFilePath == null || beatmap.meta.stems() == null) return;

		java.nio.file.Path beatmapDir = beatmap.beatmapFilePath.getParent();
		if (beatmapDir == null) return;

		boolean anyLoaded = false;
		int loadedCount = 0;
		for (java.util.Map.Entry<String, String> entry : beatmap.meta.stems().entrySet()) {
			String stemKey = entry.getKey();
			String relativePath = entry.getValue();
			if (relativePath == null || relativePath.isBlank()) continue;
			java.nio.file.Path stemPath = beatmapDir.resolve(relativePath).normalize();
			if (java.nio.file.Files.isRegularFile(stemPath)) {
				if (ctx().stemMixer().loadStem(stemKey, stemPath)) {
					anyLoaded = true;
					loadedCount++;
				}
			} else {
				LOGGER.warn("BeatBlock TimelineRenderer: stem WAV not found key={} path={}", stemKey, stemPath);
			}
		}

		if (anyLoaded) {
			LOGGER.info("BeatBlock TimelineRenderer: StemMixer loaded {} stems", loadedCount);
		} else {
			LOGGER.warn("BeatBlock TimelineRenderer: stem metadata present but no stems were loaded successfully");
		}
	}


	/**
	 * 绘制音频组拖放高亮边框（row 0~4 内容区外围），并在帧末重置标记。
	 * 应在所有行内容绘制完毕后调用。
	 */
	private void drawAudioGroupDropHighlight(TimelineLayout layout) {
		if (!audioGroupDropHighlight) return;
		// 寻找音频组内第一个和最后一个可见行（组头 + 所有活跃子轨）。
		// 注意：行顺序可能为「特征/控制交错」，因此不能依赖连续行号区间。
		float y0 = -1f, y1 = -1f;
		float groupY = layout.getRowScreenY(TimelineTrackMeta.ROW_AUDIO_GROUP);
		if (groupY >= 0f) {
			y0 = groupY;
			y1 = groupY + layout.getRowHeight(TimelineTrackMeta.ROW_AUDIO_GROUP);
		}
		for (int slot = 0; slot < layout.getActiveAudioSubRowCount(); slot++) {
			int r = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			float ry = layout.getRowScreenY(r);
			if (ry < 0f) continue;
			if (y0 < 0f || ry < y0) y0 = ry;
			float bottom = ry + layout.getRowHeight(r);
			if (bottom > y1) y1 = bottom;
		}
		if (y0 >= 0 && y1 > y0) {
			ImGui.getWindowDrawList().addRect(
				layout.contentLeft, y0,
				layout.contentLeft + layout.contentWidth, y1,
				AUDIO_GROUP_DROP_HIGHLIGHT_COLOR, 3f, 0, 1.5f);
		}
		audioGroupDropHighlight = false;
	}
}
