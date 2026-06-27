package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.timeline.*;
import com.beatblock.timeline.editor.SelectionBox;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
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
	/** 轨道槽交替背景（深色），使轨道行更明显 */
	private static final int ROW_BG_EVEN = 0xFF_28_28_2A;
	private static final int ROW_BG_ODD = 0xFF_1E_1E_20;
	/** 组头行专用背景与分段分隔线（增强三段式结构可读性） */
	private static final int GROUP_ROW_BG = 0xFF_2E_2B_33;
	private static final int GROUP_SEPARATOR_COLOR = 0x99_6E_7D_96;
	/** 左侧轨道列表与右侧内容区的竖线分隔（ABGR，供面板贯通绘制） */
	public static final int TIMELINE_DIVIDER_COLOR = 0x66_88_88_88;

	private final GridRenderer gridRenderer = new GridRenderer();
	private final TrackRenderer trackRenderer = new TrackRenderer();
	private final EventRenderer eventRenderer = new EventRenderer();
	private final WaveformRenderer waveformRenderer = new WaveformRenderer();
	private final TimelineDenseFeatureApplier denseFeatureApplier = new TimelineDenseFeatureApplier();
	private final TimelineRowContentRenderer rowContentRenderer =
		new TimelineRowContentRenderer(eventRenderer, waveformRenderer);
	private final TimelinePairedFeatureLaneSync pairedFeatureLaneSync = new TimelinePairedFeatureLaneSync();

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
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.time_label"));
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
			trackListState.setMuteChangeListener(() ->
				TimelineStemMuteSync.syncStemMuteState(ctx(), trackListState, currentAudioSubTracks));
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
		pairedFeatureLaneSync.sync(trackListState, currentAudioSubTracks, currentAnimationSubTracks);
		layout.setActiveAnimationSubRowCount(currentAnimationSubTracks.size());
		if (trackListState != null) {
			TimelineStemMuteSync.syncPrimaryPlayerMuteState(ctx(), trackListState, currentAudioSubTracks);
		}
		if (trackListState != null && ctx().stemMixer() != null && ctx().stemMixer().hasStems()) {
			TimelineStemMuteSync.syncStemMuteState(ctx(), trackListState, currentAudioSubTracks);
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
		TimelineRowHoverHighlighter.drawPairedFeatureHoverHighlight(
			layout, currentAudioSubTracks, currentAnimationSubTracks);
		TimelineRowHoverHighlighter.drawActionCameraHoverHighlight(layout);

		// 每帧重置音频组拖放高亮标记
		audioGroupDropHighlight = false;

		// 轨道名 + 内容区（仅可见行）；组可折叠，折叠后子轨道不绘制
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			if (!layout.isRowVisible(i)) continue;
			float rowY = layout.getRowCursorY(i);
			float rowHeight = layout.getRowHeight(i);
			boolean isGroup = TimelineTrackMeta.isGroupRow(i);
			String displayName = TimelineRowLabelResolver.resolveDisplayName(
				i, trackListState, currentAudioSubTracks, currentAnimationSubTracks);
			boolean canControlPlayback = TimelineStemMuteSync.isPlayableAudioControlRow(i, currentAudioSubTracks);
			String rowTypeLabel = TimelineRowLabelResolver.resolveTypeLabel(
				i, currentAudioSubTracks, currentAnimationSubTracks);
			trackRenderer.drawTrackLabel(rowY, rowHeight, i, displayName, isGroup, trackListState,
				layout.trackHeaderLeft, layout.trackHeaderWidth, canControlPlayback, rowTypeLabel,
				timeline, clock);
			rowContentRenderer.drawRowContent(
				this, i, rowY, timeline, viewState, selectionState, layout, trackListState,
				currentAudioSubTracks, currentAnimationSubTracks);
		}

		// 音频组拖放高亮（在所有行内容绘制后叠加边框）
		TimelineAudioGroupDropHighlight.drawIfActive(layout, audioGroupDropHighlight);
		audioGroupDropHighlight = false;

		// 分割线：位于轨道背景/内容之上，但低于对齐辅助线与框选。
		drawDivider(layout, layout.contentTop, layout.contentTop + layout.contentHeight);

		drawAlignmentGuides(viewState, layout, interactionState);

		// 框选矩形
		if (selectionBox != null && selectionBox.isActive()) {
			ImGui.getWindowDrawList().addRect(selectionBox.getMinX(), selectionBox.getMinY(), selectionBox.getMaxX(), selectionBox.getMaxY(), SELECTED_BORDER_COLOR, 0f, 0, 1.5f);
		}

		// 播放头须在轨道内容最上层；子窗口 draw list 会盖住父窗口 overlay，故在轨道区末尾绘制。
		if (clock != null) {
			drawPlayheadLine(
				layout,
				viewState,
				clock.getCurrentTimeSeconds(),
				layout.contentTop,
				layout.contentTop + layout.contentHeight
			);
		}
	}

	/** 在指定屏幕 Y 范围内绘制播放头竖线；超出可见时间范围时不绘制。 */
	public static void drawPlayheadLine(
		TimelineLayout layout,
		TimelineViewState viewState,
		double currentTimeSeconds,
		float y0,
		float y1
	) {
		if (layout == null || viewState == null || y1 <= y0) return;
		float playheadX = layout.contentLeft + viewState.timeToScreen(currentTimeSeconds);
		if (playheadX < layout.contentLeft - 2 || playheadX > layout.contentLeft + layout.contentWidth + 2) {
			return;
		}
		ImGui.getWindowDrawList().addLine(playheadX, y0, playheadX, y1, PLAYHEAD_COLOR, 2f);
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
}
