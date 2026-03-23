package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.BeatBlockRuntime;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.FrequencyBand;
import com.beatblock.timeline.FrequencyEvent;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.WaveformData;
import com.beatblock.timeline.editor.SelectionBox;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 时间线渲染入口：按 4 区域绘制（1.时间尺 2.轨道名 3.网格 4.内容/事件/播放头/框选）。
 */
public final class TimelineRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineRenderer.class);
	private static final long DENSE_FAILURE_COOLDOWN_MS = 30_000L;
	private static final long PENDING_DENSE_PAYLOAD_TTL_MS = 120_000L;

	private static final int PLAYHEAD_COLOR = 0xFF_FF_66_66;
	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;
	private static final int FREQ_LOW_COLOR = 0xFF_77_77_DD;
	private static final int FREQ_MID_COLOR = 0xFF_57_C4_A0;
	private static final int FREQ_HIGH_COLOR = 0xFF_27_A0_EF;
	/** 轨道槽交替背景（深色），使轨道行更明显 */
	private static final int ROW_BG_EVEN = 0xFF_28_28_2A;
	private static final int ROW_BG_ODD = 0xFF_1E_1E_20;
	/** 左侧轨道列表与右侧内容区的竖线分隔（ABGR，供面板贯通绘制） */
	public static final int TIMELINE_DIVIDER_COLOR = 0x66_88_88_88;

	/** 音频组高亮颜色（紫色半透明边框，ABGR） */
	private static final int AUDIO_GROUP_DROP_HIGHLIGHT_COLOR = 0x55_7F_77_DD;

	private final GridRenderer gridRenderer = new GridRenderer();
	private final TrackRenderer trackRenderer = new TrackRenderer();
	private final EventRenderer eventRenderer = new EventRenderer();
	private final WaveformRenderer waveformRenderer = new WaveformRenderer();
	private final ExecutorService denseFeatureExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-dense-feature");
		t.setDaemon(true);
		return t;
	});
	private volatile boolean denseFeatureExecutorShutdown;
	private final ConcurrentMap<String, DenseApplyPayload> pendingDenseApplies = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Boolean> denseAnalysisInFlight = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Long> denseAnalysisFailureUntilMs = new ConcurrentHashMap<>();

	/**
	 * 本帧计算出的音频子轨定义列表（由 TrackRegistry.buildAudioSubTracks 生成）。
	 * 在 renderTrackArea 开始时更新，drawRowContent 中按槽索引查找。
	 * 只在 featureTracks keySet 发生变化时重建，避免每帧分配新对象。
	 */
	private List<TrackDefinition> currentAudioSubTracks = Collections.emptyList();
	/** 上次构建 currentAudioSubTracks 时的 featureTracks key 快照，用于脏检测。 */
	private Set<String> lastFeatureTrackKeys = Set.of();

	/** 当前帧音频组是否有拖拽悬停高亮（任意 row 0~4 悬停且有 audio payload 时置 true） */
	private boolean audioGroupDropHighlight;
	/** 已注册静音回调的 TrackListState 对象（避免重复注册）。 */
	private TimelineTrackListState registeredMuteListenerFor;

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

	public void renderRulerRow(TimelineLayout layout, TimelineViewState viewState, double bpm) {
		renderRulerRow(layout, viewState, bpm, null, null);
	}

	/** 可滚动区域：轨道区（左侧轨道列表 + 竖线分隔 + 网格 + 一行一行轨道 + 播放头 + 框选）。 */
	public void renderTrackArea(
		Timeline timeline,
		TimelineViewState viewState,
		SelectionState selectionState,
		TimelineClock clock,
		SelectionBox selectionBox,
		TimelineTrackListState trackListState,
		TimelineLayout layout
	) {
		if (timeline == null || viewState == null || layout == null) return;

		// 首次（或 trackListState 更换后）注册静音/独奏变更回调
		if (registeredMuteListenerFor != trackListState) {
			registeredMuteListenerFor = trackListState;
			trackListState.setMuteChangeListener(() -> syncStemMuteState(trackListState));
		}

		applyPendingDenseUpdates(timeline);

		// ── 音频子轨定义列表：仅在 featureTracks keySet 变化时重建 ─────────────
		// TrackRegistry.buildAudioSubTracks 内部每次都分配新 ArrayList + TrackDefinition 对象，
		// 60fps 下产生持续 GC 压力。轨道定义只在 featureTracks 内容发生变化时才需要重建。
		Set<String> currentKeys = timeline.getFeatureTracks().keySet();
		if (!lastFeatureTrackKeys.equals(currentKeys)) {
			lastFeatureTrackKeys   = Set.copyOf(currentKeys);
			currentAudioSubTracks  = TrackRegistry.buildAudioSubTracks(timeline);
		}
		layout.setActiveAudioSubRowCount(currentAudioSubTracks.size());
		if (trackListState != null) {
			syncPrimaryPlayerMuteState(trackListState);
		}
		if (trackListState != null && BeatBlock.stemMixer != null && BeatBlock.stemMixer.hasStems()) {
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
			int vi = layout.getVisibleIndex(i);
			int bg = (vi % 2 == 0) ? ROW_BG_EVEN : ROW_BG_ODD;
			ImGui.getWindowDrawList().addRectFilled(x0, rowScreenY, x1, rowScreenY + rowH, bg);
		}

		// 网格竖线（仅时间轴方向，不画行间线）
		gridRenderer.render(viewState, layout, layout.contentHeight);

		// 每帧重置音频组拖放高亮标记
		audioGroupDropHighlight = false;

		// 轨道名 + 内容区（仅可见行）；组可折叠，折叠后子轨道不绘制
		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			if (!layout.isRowVisible(i)) continue;
			float rowY = layout.getRowCursorY(i);
			float rowHeight = layout.getRowHeight(i);
			boolean isGroup = TimelineTrackMeta.isGroupRow(i);
			String displayName = resolveDisplayName(i, trackListState);
			trackRenderer.drawTrackLabel(rowY, rowHeight, i, displayName, isGroup, trackListState, layout.trackHeaderLeft, layout.trackHeaderWidth);
			drawRowContent(i, rowY, timeline, viewState, selectionState, layout, trackListState);
		}

		// 音频组拖放高亮（在所有行内容绘制后叠加边框）
		drawAudioGroupDropHighlight(layout);

		// 分割线：位于轨道背景/内容之上，但低于播放头。
		drawDivider(layout, layout.contentTop, layout.contentTop + layout.contentHeight);

		// 播放头（仅限轨道区高度）
		if (clock != null) {
			double currentTime = clock.getCurrentTimeSeconds();
			float playheadX = viewState.timeToScreen(currentTime);
			if (playheadX >= -2 && playheadX <= layout.contentWidth + 2) {
				float px = layout.contentLeft + playheadX;
				float py0 = layout.contentTop;
				float py1 = layout.contentTop + layout.contentHeight;
				ImGui.getWindowDrawList().addLine(px, py0, px, py1, PLAYHEAD_COLOR, 2f);
			}
		}

		// 框选矩形
		if (selectionBox != null && selectionBox.isActive()) {
			ImGui.getWindowDrawList().addRect(selectionBox.getMinX(), selectionBox.getMinY(), selectionBox.getMaxX(), selectionBox.getMaxY(), SELECTED_BORDER_COLOR, 0f, 0, 1.5f);
		}
	}

	private void drawDivider(TimelineLayout layout, float y0, float y1) {
		if (layout == null || y1 <= y0) return;
		ImGui.getWindowDrawList().addLine(layout.contentLeft, y0, layout.contentLeft, y1, TIMELINE_DIVIDER_COLOR, 1f);
	}

	private void drawRowContent(int rowIndex, float rowY, Timeline timeline, TimelineViewState viewState,
	                             SelectionState selectionState, TimelineLayout layout,
	                             TimelineTrackListState trackListState) {
		float rowHeight = layout.getRowHeight(rowIndex);
		float rowScreenY = layout.getRowScreenY(rowIndex);

		// ── 音频组标题行 ──────────────────────────────────────────────────────
		if (rowIndex == TimelineTrackMeta.ROW_AUDIO_GROUP) {
			renderAudioGroupDropTarget(rowIndex, rowY, rowHeight, timeline, layout);
			return;
		}

		// ── 动态音频子轨（TrackRegistry 驱动） ───────────────────────────────
		if (TimelineTrackMeta.isAudioSubRow(rowIndex)) {
			int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
			if (slot < 0 || slot >= currentAudioSubTracks.size()) return;
			TrackDefinition td = currentAudioSubTracks.get(slot);
			renderAudioGroupDropTarget(rowIndex, rowY, rowHeight, timeline, layout);
			// 静音/独奏：有效静音时跳过内容渲染（保留拖放目标区）
			if (isAudioRowEffectivelyMuted(trackListState, rowIndex)) return;
			ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
				rowScreenY + rowHeight, true);
			renderAudioSubTrack(td, rowY, rowHeight, timeline, layout, viewState);
			ImGui.popClipRect();
			return;
		}

		// ── 固定非音频轨道 ────────────────────────────────────────────────────
		ImGui.pushClipRect(layout.contentLeft, rowScreenY, layout.contentLeft + layout.contentWidth,
			rowScreenY + rowHeight, true);
		if (rowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK) {
			renderAnimationTrackDropTarget(rowIndex, rowHeight, timeline, layout);
			eventRenderer.renderAnimationEventBlocks(rowY, timeline.getBlockAnimationEvents(), layout, viewState, selectionState);
		} else if (rowIndex == TimelineTrackMeta.ROW_ANIM_AUTO) {
			renderAnimationTrackDropTarget(rowIndex, rowHeight, timeline, layout);
			eventRenderer.renderAnimationEventBlocks(rowY, timeline.getAutoAnimationEvents(), layout, viewState, selectionState);
		} else if (rowIndex == TimelineTrackMeta.ROW_CAMERA) {
			eventRenderer.renderCameraKeyframeRow(rowY, timeline.getCameraKeyframes(), layout, viewState);
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
				if (key.startsWith("stem_wf_")) {
					// 茎波形轨：从 Timeline 的 stemWaveforms 获取对应数据
					String stemKey = key.substring("stem_wf_".length());
					WaveformData stemWf = timeline.getStemWaveform(stemKey);
					int color = td.hasCustomColor() ? td.getColor() : 0xFF_66_AA_FF;
					waveformRenderer.renderStemWaveform(rowY, rowHeight, stemWf, color, timeline, layout, viewState);
				} else {
					waveformRenderer.render(rowY, rowHeight, timeline, layout, viewState);
				}
			}
			case IMPULSE -> {
				int color = td.hasCustomColor() ? td.getColor() : FREQ_MID_COLOR;
				List<FeatureEvent> events = timeline.getFeatureEvents(td.getKey());
				if (!events.isEmpty()) {
					eventRenderer.renderFeatureBars(rowY, rowHeight, events, layout, viewState, color,
						timeline.getBpm(), 0.20f, 0.9f);
				} else {
					// 遗留回退：若命名特征轨道无数据但有遗留频段数据，则用遗留数据渲染
					renderLegacyBandFallback(td.getKey(), rowY, rowHeight, timeline, layout, viewState, color);
				}
			}
		}
	}

	/** 遗留频段回退：只在 featureTracks 为空且遗留列表有数据时触发。 */
	private void renderLegacyBandFallback(String key, float rowY, float rowHeight,
	                                      Timeline timeline, TimelineLayout layout,
	                                      TimelineViewState viewState, int color) {
		switch (key) {
			case "low" -> eventRenderer.renderFrequencyBars(rowY, rowHeight,
				timeline.getFrequencyEventsByBand(FrequencyBand.LOW),
				layout, viewState, FREQ_LOW_COLOR, timeline.getBpm(), 0.35f, 1.0f);
			case "mid" -> eventRenderer.renderFrequencyBars(rowY, rowHeight,
				timeline.getFrequencyEventsByBand(FrequencyBand.MID),
				layout, viewState, FREQ_MID_COLOR, timeline.getBpm(), 0.24f, 0.9f);
			case "high" -> eventRenderer.renderFrequencyBars(rowY, rowHeight,
				timeline.getFrequencyEventsByBand(FrequencyBand.HIGH),
				layout, viewState, FREQ_HIGH_COLOR, timeline.getBpm(), 0.16f, 0.8f);
		}
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
				return currentAudioSubTracks.get(slot).getDisplayName();
			}
		}
		return trackListState != null ? trackListState.getDisplayName(rowIndex) : TimelineTrackMeta.getDefaultName(rowIndex);
	}

	/**
	 * 在指定行放置一个不可见按钮作为拖放目标（内容区），接受音频资产拖放。
	 * 行 0~4（音频组/波形/低中高频）均调用此方法；松手后自动填充整组数据。
	 */
	private void renderAudioGroupDropTarget(int rowIndex, float rowY, float rowHeight, Timeline timeline, TimelineLayout layout) {
		float screenY = layout.getRowScreenY(rowIndex);
		if (screenY < 0) return;
		ImGui.setCursorScreenPos(layout.contentLeft, screenY);
		ImGui.invisibleButton("##AudioDropTarget_" + rowIndex, layout.contentWidth, rowHeight);

		if (ImGui.isItemHovered()) {
			audioGroupDropHighlight = true;
		}

		acceptAudioAssetDrop(timeline);
	}

	/**
	 * 共用的音频资产拖放接受逻辑。在 invisibleButton 之后调用。
	 */
	private void acceptAudioAssetDrop(Timeline timeline) {
		if (ImGui.beginDragDropTarget()) {
			byte[] payload = ImGui.acceptDragDropPayload("BB_AUDIO_ASSET_ID");
			if (payload != null) {
				String assetId = new String(payload, StandardCharsets.UTF_8).trim();
				AudioAsset asset = AudioAssetManager.getInstance().findById(assetId);
				if (asset == null) {
					asset = AudioAssetManager.getInstance().getCurrentDragAsset();
				}
				handleDroppedAudioAsset(timeline, asset, -1);
			}
			ImGui.endDragDropTarget();
		}
	}

	private void renderAnimationTrackDropTarget(int rowIndex, float rowHeight, Timeline timeline, TimelineLayout layout) {
		float screenY = layout.getRowScreenY(rowIndex);
		if (screenY < 0) return;
		ImGui.setCursorScreenPos(layout.contentLeft, screenY);
		ImGui.invisibleButton("##AnimDropTarget_" + rowIndex, layout.contentWidth, rowHeight);
		acceptAudioAssetDropForAnimationTrack(timeline, rowIndex);
	}

	private void acceptAudioAssetDropForAnimationTrack(Timeline timeline, int targetRowIndex) {
		if (ImGui.beginDragDropTarget()) {
			byte[] payload = ImGui.acceptDragDropPayload("BB_AUDIO_ASSET_ID");
			if (payload != null) {
				String assetId = new String(payload, StandardCharsets.UTF_8).trim();
				AudioAsset asset = AudioAssetManager.getInstance().findById(assetId);
				if (asset == null) {
					asset = AudioAssetManager.getInstance().getCurrentDragAsset();
				}
				handleDroppedAudioAsset(timeline, asset, targetRowIndex);
			}
			ImGui.endDragDropTarget();
		}
	}

	private void handleDroppedAudioAsset(Timeline timeline, AudioAsset asset, int dropTargetRowIndex) {
		if (asset == null || BeatBlock.audioAnalysisEngine == null) return;

		bindDroppedAudioToPlayback(timeline, asset);
		if (asset.getBeatmap() != null) {
			BeatBlock.audioAnalysisEngine.fillTimelineFromBeatmap(timeline, asset.getBeatmap());
			requestDenseFeatureEnrichment(timeline, asset);
			BeatBlockRuntime.getInstance().loadBeatmap(asset.getBeatmap());
			// 若是 Demucs 模式，加载茎音频到 StemMixer
			bindStemAudioIfDemucs(asset.getBeatmap());
		} else if (asset.getFeatureTimeline() != null) {
			BeatBlock.audioAnalysisEngine.fillTimelineFromFeature(timeline, asset.getFeatureTimeline(), asset.getSampleRate());
		} else {
			requestDenseFeatureEnrichment(timeline, asset);
		}

		if (dropTargetRowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK
			|| dropTargetRowIndex == TimelineTrackMeta.ROW_ANIM_AUTO) {
			populateAnimationTrackFromAudioFeatures(timeline, dropTargetRowIndex);
		}

		if (BeatBlock.timelineEditor != null) {
			BeatBlock.timelineEditor.syncClockDuration();
		}
	}

	private void populateAnimationTrackFromAudioFeatures(Timeline timeline, int targetRowIndex) {
		if (timeline == null) return;
		boolean toBlockTrack = targetRowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK;
		boolean toAutoTrack = targetRowIndex == TimelineTrackMeta.ROW_ANIM_AUTO;
		if (!toBlockTrack && !toAutoTrack) return;
		boolean demucsSeparated = isDemucsSeparatedTimeline(timeline);
		String mappingPreset = resolveDemucsMappingPreset(timeline, toBlockTrack);
		double durationScale = durationScaleForPreset(mappingPreset);
		float energyThresholdScale = energyThresholdScaleForPreset(mappingPreset);
		double minGapScale = minGapScaleForPreset(mappingPreset);
		durationScale *= readScaleMetadata(timeline, "demucsMapDurationScale", 1.0, 0.5, 2.0);
		energyThresholdScale *= (float) readScaleMetadata(timeline, "demucsMapEnergyScale", 1.0, 0.6, 1.6);
		minGapScale *= readScaleMetadata(timeline, "demucsMapGapScale", 1.0, 0.5, 2.0);

		if (toBlockTrack) {
			timeline.clearBlockAnimationEvents();
		} else {
			timeline.clearAutoAnimationEvents();
		}

		String targetObjectId = resolveDefaultTargetObjectId();
		int added = 0;
		Map<String, Double> lastAcceptedTimeByFeature = new HashMap<>();

		for (Map.Entry<String, FeatureTrack> entry : timeline.getFeatureTracks().entrySet()) {
			String featureKey = entry.getKey();
			FeatureTrack track = entry.getValue();
			if (track == null || track.getEvents().isEmpty()) continue;

			AnimationMappingRule rule = selectAnimationRule(featureKey, demucsSeparated, toBlockTrack);
			if (rule == null) continue;
			for (FeatureEvent event : track.getEvents()) {
				added += addAnimationEventFromSource(
					timeline, toBlockTrack, event.getTimeSeconds(),
					event.getEnergy(), rule,
					targetObjectId, featureKey, lastAcceptedTimeByFeature,
					durationScale, energyThresholdScale, minGapScale
				);
			}
		}

		if (added == 0) {
			for (FrequencyEvent fe : timeline.getFrequencyEvents()) {
				String featureKey = switch (fe.getBand()) {
					case LOW -> "kick";
					case MID -> "snare";
					case HIGH -> "hihat";
				};
				AnimationMappingRule rule = selectAnimationRule(featureKey, false, toBlockTrack);
				if (rule == null) continue;
				added += addAnimationEventFromSource(
					timeline, toBlockTrack, fe.getTimeSeconds(),
					fe.getEnergy(), rule, targetObjectId, featureKey, lastAcceptedTimeByFeature,
					durationScale, energyThresholdScale, minGapScale
				);
			}
		}

		timeline.sortAll();
		LOGGER.info("BeatBlock Timeline: mapped dropped audio into {} animation events on {} track (preset={})",
			added, toBlockTrack ? "block" : "auto", mappingPreset);
	}

	private int addAnimationEventFromSource(
		Timeline timeline,
		boolean toBlockTrack,
		double timeSeconds,
		float rawEnergy,
		AnimationMappingRule rule,
		String targetObjectId,
		String sourceFeature,
		Map<String, Double> lastAcceptedTimeByFeature,
		double durationScale,
		float energyThresholdScale,
		double minGapScale
	) {
		if (rule == null) return 0;
		double featureDurationScale = readFeatureScaleMetadata(timeline, sourceFeature, "duration", 1.0, 0.5, 2.0);
		float featureEnergyScale = (float) readFeatureScaleMetadata(timeline, sourceFeature, "energy", 1.0, 0.6, 1.6);
		double featureGapScale = readFeatureScaleMetadata(timeline, sourceFeature, "gap", 1.0, 0.5, 2.0);

		double effectiveDurationScale = durationScale * featureDurationScale;
		float effectiveEnergyThresholdScale = energyThresholdScale * featureEnergyScale;
		double effectiveMinGapScale = minGapScale * featureGapScale;

		float energy = Math.max(0f, Math.min(1f, rawEnergy));
		float minEnergy = Math.max(0f, Math.min(1f, rule.minEnergy() * effectiveEnergyThresholdScale));
		if (energy < minEnergy) return 0;
		double minGap = Math.max(0.02, rule.minGapSeconds() * effectiveMinGapScale);

		Double lastAccepted = lastAcceptedTimeByFeature.get(sourceFeature);
		if (lastAccepted != null && timeSeconds < lastAccepted + minGap) {
			return 0;
		}

		double duration = Math.max(0.05, rule.baseDurationSeconds() * effectiveDurationScale * (0.70 + energy * 0.75));
		Map<String, Object> params = new HashMap<>();
		params.put("energy", energy);
		params.put("sourceFeature", sourceFeature);
		params.put("sourceStem", rule.sourceStem());
		params.put("mappingProfile", "demucs-aware");
		params.put("mappingPreset", resolvePresetLabel(durationScale, energyThresholdScale, minGapScale));
		params.put("featureDurationScale", featureDurationScale);
		params.put("featureEnergyScale", featureEnergyScale);
		params.put("featureGapScale", featureGapScale);
		params.put("generatedBy", "audio-asset-drop");

		TimelineAnimationEvent ev = new TimelineAnimationEvent(
			"",
			timeSeconds,
			duration,
			rule.animationType(),
			targetObjectId,
			energy,
			params
		);
		if (toBlockTrack) {
			timeline.addBlockAnimationEvent(ev);
		} else {
			timeline.addAutoAnimationEvent(ev);
		}
		lastAcceptedTimeByFeature.put(sourceFeature, timeSeconds);
		return 1;
	}

	private String resolveDefaultTargetObjectId() {
		if (BeatBlock.blockAnimationEngine != null) {
			var sys = BeatBlock.blockAnimationEngine.getStageObjectSystem();
			var all = sys != null ? sys.getAll() : null;
			if (all != null && !all.isEmpty()) {
				return all.iterator().next().getId();
			}
		}
		return "default";
	}

	private AnimationMappingRule selectAnimationRule(String featureKey, boolean demucsSeparated, boolean toBlockTrack) {
		if (featureKey == null || featureKey.isBlank()) return null;
		String key = featureKey.toLowerCase();

		if (demucsSeparated) {
			if (toBlockTrack) {
				return switch (key) {
					case "kick" -> new AnimationMappingRule("bounce", 0.46, 0.18f, 0.18, "drums");
					case "snare" -> new AnimationMappingRule("slide", 0.34, 0.16f, 0.24, "drums");
					case "bass" -> new AnimationMappingRule("bounce", 0.58, 0.20f, 0.32, "bass");
					default -> null;
				};
			}
			return switch (key) {
				case "hihat" -> new AnimationMappingRule("pulse", 0.20, 0.12f, 0.10, "drums");
				case "hihat_open" -> new AnimationMappingRule("pulse", 0.28, 0.16f, 0.16, "drums");
				case "snare_hi" -> new AnimationMappingRule("slide", 0.26, 0.16f, 0.20, "drums");
				case "vocals" -> new AnimationMappingRule("slide", 0.52, 0.20f, 0.48, "vocals");
				case "other" -> new AnimationMappingRule("pulse", 0.34, 0.18f, 0.30, "other");
				case "bass" -> new AnimationMappingRule("pulse", 0.40, 0.24f, 0.42, "bass");
				default -> null;
			};
		}

		if (toBlockTrack) {
			return switch (key) {
				case "kick", "low" -> new AnimationMappingRule("bounce", 0.48, 0.18f, 0.20, "mix");
				case "snare", "mid" -> new AnimationMappingRule("slide", 0.36, 0.16f, 0.24, "mix");
				default -> null;
			};
		}
		return switch (key) {
			case "hihat", "high" -> new AnimationMappingRule("pulse", 0.24, 0.12f, 0.12, "mix");
			case "snare_hi", "mid" -> new AnimationMappingRule("slide", 0.30, 0.16f, 0.22, "mix");
			default -> null;
		};
	}

	private boolean isDemucsSeparatedTimeline(Timeline timeline) {
		if (timeline == null) return false;
		Object value = timeline.getMetadata("separationMode");
		return value != null && "demucs".equalsIgnoreCase(value.toString().trim());
	}

	private String resolveDemucsMappingPreset(Timeline timeline, boolean toBlockTrack) {
		if (timeline != null) {
			Object configured = timeline.getMetadata("demucsMappingPreset");
			if (configured != null) {
				String v = configured.toString().trim().toLowerCase();
				if ("drive".equals(v) || "detail".equals(v) || "balanced".equals(v)) {
					return v;
				}
			}
		}
		return toBlockTrack ? "drive" : "detail";
	}

	private double readScaleMetadata(Timeline timeline, String key, double defaultValue, double min, double max) {
		if (timeline == null || key == null || key.isBlank()) return defaultValue;
		Object raw = timeline.getMetadata(key);
		if (raw == null) return defaultValue;
		double v;
		if (raw instanceof Number n) {
			v = n.doubleValue();
		} else {
			try {
				v = Double.parseDouble(raw.toString().trim());
			} catch (Exception e) {
				return defaultValue;
			}
		}
		if (Double.isNaN(v) || Double.isInfinite(v)) return defaultValue;
		return Math.max(min, Math.min(max, v));
	}

	private double durationScaleForPreset(String preset) {
		return switch (preset) {
			case "drive" -> 1.18;
			case "detail" -> 0.92;
			default -> 1.0;
		};
	}

	private float energyThresholdScaleForPreset(String preset) {
		return switch (preset) {
			case "drive" -> 0.88f;
			case "detail" -> 1.10f;
			default -> 1.0f;
		};
	}

	private double minGapScaleForPreset(String preset) {
		return switch (preset) {
			case "drive" -> 0.85;
			case "detail" -> 1.18;
			default -> 1.0;
		};
	}

	private String resolvePresetLabel(double durationScale, float energyThresholdScale, double minGapScale) {
		if (durationScale > 1.05 && energyThresholdScale < 0.95f && minGapScale < 0.95) return "drive";
		if (durationScale < 0.97 && energyThresholdScale > 1.05f && minGapScale > 1.05) return "detail";
		return "balanced";
	}

	private double readFeatureScaleMetadata(
		Timeline timeline,
		String featureKey,
		String scaleType,
		double defaultValue,
		double min,
		double max
	) {
		if (timeline == null || featureKey == null || featureKey.isBlank() || scaleType == null || scaleType.isBlank()) {
			return defaultValue;
		}
		String normalizedFeature = featureKey.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
		String normalizedScale = scaleType.trim().toLowerCase();
		String metadataKey = "demucsFeat" + capitalize(normalizedScale) + "_" + normalizedFeature;
		return readScaleMetadata(timeline, metadataKey, defaultValue, min, max);
	}

	private String capitalize(String s) {
		if (s == null || s.isBlank()) return "";
		if (s.length() == 1) return s.toUpperCase();
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/**
	 * 触发高分辨率频段数据补全：已有特征时立即应用；否则在后台分析，主线程按当前时间线音频路径安全回填。
	 */
	private void requestDenseFeatureEnrichment(Timeline timeline, AudioAsset asset) {
		if (timeline == null || asset == null || BeatBlock.audioAnalysisEngine == null) return;
		if (denseFeatureExecutorShutdown) return;

		String audioKey = buildAudioAssetKey(asset);
		if (audioKey == null) return;
		long now = System.currentTimeMillis();
		Long failureUntil = denseAnalysisFailureUntilMs.get(audioKey);
		if (failureUntil != null && failureUntil > now) return;

		AudioFeatureTimeline cachedFeature = asset.getFeatureTimeline();
		if (cachedFeature != null) {
			denseAnalysisFailureUntilMs.remove(audioKey);
			applyDenseFeatureData(timeline, asset, cachedFeature, audioKey);
			return;
		}

		Path audioPath = asset.getPath();
		if (audioPath == null) return;
		if (denseAnalysisInFlight.putIfAbsent(audioKey, Boolean.TRUE) != null) return;

		denseFeatureExecutor.submit(() -> {
			try {
				AudioFeatureTimeline analyzed = BeatBlock.audioAnalysisEngine.analyze(audioPath);
				if (analyzed == null) {
					denseAnalysisFailureUntilMs.put(audioKey, System.currentTimeMillis() + DENSE_FAILURE_COOLDOWN_MS);
					LOGGER.warn("BeatBlock Timeline: dense feature enrichment returned null path={} (cooldown={}ms)",
						audioPath, DENSE_FAILURE_COOLDOWN_MS);
					return;
				}
				asset.setFeatureTimeline(analyzed);
				denseAnalysisFailureUntilMs.remove(audioKey);
				pendingDenseApplies.put(audioKey, new DenseApplyPayload(asset, analyzed, System.currentTimeMillis()));
			} catch (Exception e) {
				denseAnalysisFailureUntilMs.put(audioKey, System.currentTimeMillis() + DENSE_FAILURE_COOLDOWN_MS);
				LOGGER.warn("BeatBlock Timeline: dense feature enrichment failed path={} reason={}", audioPath, e.toString());
			} finally {
				denseAnalysisInFlight.remove(audioKey);
			}
		});
	}

	/**
	 * 释放后台资源，避免客户端退出后残留分析线程。
	 */
	public void shutdown() {
		if (denseFeatureExecutorShutdown) {
			LOGGER.debug("BeatBlock Timeline: dense feature executor already shut down");
			return;
		}
		int pendingCount = pendingDenseApplies.size();
		int inflightCount = denseAnalysisInFlight.size();
		int cooldownCount = denseAnalysisFailureUntilMs.size();
		denseFeatureExecutorShutdown = true;
		pendingDenseApplies.clear();
		denseAnalysisInFlight.clear();
		denseAnalysisFailureUntilMs.clear();
		denseFeatureExecutor.shutdownNow();
		LOGGER.info(
			"BeatBlock Timeline: dense feature executor shutdown (pending={}, inflight={}, cooldown={})",
			pendingCount,
			inflightCount,
			cooldownCount
		);
	}

	private void applyPendingDenseUpdates(Timeline timeline) {
		if (pendingDenseApplies.isEmpty()) return;
		pruneStalePendingDenseApplies();
		if (timeline == null || pendingDenseApplies.isEmpty()) return;
		String timelineAudioKey = getTimelineAudioPathKey(timeline);
		if (timelineAudioKey == null) return;
		DenseApplyPayload payload = pendingDenseApplies.remove(timelineAudioKey);
		if (payload != null) {
			applyDenseFeatureData(timeline, payload.asset(), payload.feature(), timelineAudioKey);
		}
	}

	private void pruneStalePendingDenseApplies() {
		long now = System.currentTimeMillis();
		pendingDenseApplies.entrySet().removeIf(e -> now - e.getValue().createdAtMs() > PENDING_DENSE_PAYLOAD_TTL_MS);
	}

	private void applyDenseFeatureData(Timeline timeline, AudioAsset asset, AudioFeatureTimeline feature, String expectedAudioKey) {
		if (timeline == null || asset == null || feature == null || BeatBlock.audioAnalysisEngine == null) return;
		String timelineAudioKey = getTimelineAudioPathKey(timeline);
		if (!Objects.equals(timelineAudioKey, expectedAudioKey)) return;

		BeatBlock.audioAnalysisEngine.fillTimelineFromFeature(timeline, feature, asset.getSampleRate());
		if (asset.getBeatmap() != null && asset.getBeatmap().meta != null) {
			timeline.setMetadata("bpm", asset.getBeatmap().meta.bpm());
			timeline.setMetadata("beatCount", asset.getBeatmap().beats.size());
		}
		if (BeatBlock.timelineEditor != null) {
			BeatBlock.timelineEditor.syncClockDuration();
		}
	}

	private String buildAudioAssetKey(AudioAsset asset) {
		if (asset == null || asset.getPath() == null) return null;
		return normalizeAudioPath(asset.getPath().toAbsolutePath().normalize().toString());
	}

	private String getTimelineAudioPathKey(Timeline timeline) {
		if (timeline == null) return null;
		Object audioPath = timeline.getMetadata("audioPath");
		if (audioPath == null) return null;
		return normalizeAudioPath(audioPath.toString());
	}

	private String normalizeAudioPath(String rawPath) {
		if (rawPath == null || rawPath.isBlank()) return null;
		return rawPath.trim().toLowerCase();
	}

	private record DenseApplyPayload(AudioAsset asset, AudioFeatureTimeline feature, long createdAtMs) {}

	private record AnimationMappingRule(
		String animationType,
		double baseDurationSeconds,
		float minEnergy,
		double minGapSeconds,
		String sourceStem
	) {}

	/**
	 * 将 trackListState 的茎轨道静音/独奏状态同步到 {@link BeatBlock#stemMixer}。
	 * 仅影响 key 为 "stem_wf_*" 的音频子轨对应的茎。
	 */
	private void syncStemMuteState(TimelineTrackListState trackListState) {
		if (BeatBlock.stemMixer == null || !BeatBlock.stemMixer.hasStems()) return;
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
			if (!isStemControlRowEffectivelyMuted(trackListState, rowIndex)) {
				anyAudibleMappedRow = true;
			}
		}

		// 语义：仅当存在映射行且这些行都被静音/独奏抑制时，才静音该茎
		boolean muted = hasMappedRow && !anyAudibleMappedRow;
		BeatBlock.stemMixer.setStemMuted(stemName, muted);
	}

	private boolean mapsToStemAudioControl(String trackKey, String stemName) {
		if (trackKey == null || stemName == null) return false;
		if (trackKey.startsWith("stem_wf_")) {
			return stemName.equals(trackKey.substring("stem_wf_".length()));
		}
		return false;
	}

	private boolean isStemControlRowEffectivelyMuted(TimelineTrackListState trackListState, int rowIndex) {
		if (trackListState == null) return false;
		if (trackListState.isMuted(rowIndex)) return true;

		boolean groupSoloed = trackListState.isSoloed(TimelineTrackMeta.ROW_AUDIO_GROUP);
		boolean anyStemControlSolo = groupSoloed;
		for (int slot = 0; slot < currentAudioSubTracks.size(); slot++) {
			TrackDefinition td = currentAudioSubTracks.get(slot);
			if (!td.getKey().startsWith("stem_wf_")) continue;
			int candidateRowIndex = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (trackListState.isSoloed(candidateRowIndex)) {
				anyStemControlSolo = true;
				break;
			}
		}

		if (!anyStemControlSolo) return false;
		if (groupSoloed) return false;
		return !trackListState.isSoloed(rowIndex);
	}

	private boolean isAudioRowEffectivelyMuted(TimelineTrackListState trackListState, int rowIndex) {
		if (trackListState == null) return false;
		if (trackListState.isMuted(rowIndex)) return true;

		boolean groupSoloed = trackListState.isSoloed(TimelineTrackMeta.ROW_AUDIO_GROUP);
		boolean anyAudioSolo = groupSoloed;
		int lastAudioRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START + currentAudioSubTracks.size() - 1;
		for (int r = TimelineTrackMeta.ROW_AUDIO_SUBS_START; r <= lastAudioRow; r++) {
			if (trackListState.isSoloed(r)) {
				anyAudioSolo = true;
				break;
			}
		}

		if (!anyAudioSolo) return false;
		if (groupSoloed) return false;
		return !trackListState.isSoloed(rowIndex);
	}

	private void syncPrimaryPlayerMuteState(TimelineTrackListState trackListState) {
		if (BeatBlock.musicPlayer == null) return;

		// StemMixer 激活时，主播放器保持静音，避免潜在双路叠加。
		if (BeatBlock.stemMixer != null && BeatBlock.stemMixer.hasStems()) {
			BeatBlock.musicPlayer.setMuted(true);
			return;
		}

		boolean anyAudioRowAudible = false;
		for (int slot = 0; slot < currentAudioSubTracks.size(); slot++) {
			TrackDefinition td = currentAudioSubTracks.get(slot);
			if (!"waveform".equals(td.getKey())) continue;
			int rowIndex = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (!isMainMixRowEffectivelyMuted(trackListState, rowIndex)) {
				anyAudioRowAudible = true;
				break;
			}
		}

		// 没有任何可听音频子轨时静音主播放器。
		BeatBlock.musicPlayer.setMuted(!anyAudioRowAudible);
	}

	private boolean isMainMixRowEffectivelyMuted(TimelineTrackListState trackListState, int rowIndex) {
		if (trackListState == null) return false;
		if (trackListState.isMuted(rowIndex)) return true;

		boolean groupSoloed = trackListState.isSoloed(TimelineTrackMeta.ROW_AUDIO_GROUP);
		boolean rowSoloed = trackListState.isSoloed(rowIndex);
		boolean anyMainMixSolo = groupSoloed || rowSoloed;
		if (!anyMainMixSolo) return false;
		if (groupSoloed) return false;
		return !rowSoloed;
	}

	/**
	 * 若 beatmap 为 Demucs 茎分离模式，将各茎 WAV 加载进 {@link BeatBlock#stemMixer}，
	 * 同时停止 musicPlayer（避免双轨输出）。若非 Demucs 模式则清空 stemMixer。
	 */
	private void bindStemAudioIfDemucs(com.beatblock.audio.beatmap.Beatmap beatmap) {
		if (BeatBlock.stemMixer == null) return;
		BeatBlock.stemMixer.clearStems();

		if (beatmap == null || beatmap.meta == null || !beatmap.meta.hasStemSeparation()) return;
		if (beatmap.beatmapFilePath == null || beatmap.meta.stems() == null) return;

		java.nio.file.Path beatmapDir = beatmap.beatmapFilePath.getParent();
		if (beatmapDir == null) return;

		boolean anyLoaded = false;
		for (java.util.Map.Entry<String, String> entry : beatmap.meta.stems().entrySet()) {
			String stemKey = entry.getKey();
			String relativePath = entry.getValue();
			if (relativePath == null || relativePath.isBlank()) continue;
			java.nio.file.Path stemPath = beatmapDir.resolve(relativePath).normalize();
			if (java.nio.file.Files.isRegularFile(stemPath)) {
				BeatBlock.stemMixer.loadStem(stemKey, stemPath);
				anyLoaded = true;
			} else {
				LOGGER.warn("BeatBlock TimelineRenderer: stem WAV not found key={} path={}", stemKey, stemPath);
			}
		}

		if (anyLoaded) {
			// 停止 MusicPlayer 以避免与 StemMixer 重叠输出
			if (BeatBlock.musicPlayer != null) {
				BeatBlock.musicPlayer.stop();
			}
			LOGGER.info("BeatBlock TimelineRenderer: StemMixer loaded {} stems", beatmap.meta.stems().size());
		}
	}

	private void bindDroppedAudioToPlayback(Timeline timeline, AudioAsset asset) {
		String audioPath = asset.getPath().toAbsolutePath().normalize().toString();
		timeline.setMetadata("audioPath", audioPath);
		if (asset.getDurationSeconds() > 0) {
			timeline.setDurationSeconds(asset.getDurationSeconds());
		}
		if (BeatBlock.musicPlayer != null) {
			boolean loaded = BeatBlock.musicPlayer.loadAudio(audioPath);
			BeatBlock.musicPlayer.setCurrentTimeSeconds(0);
			if (loaded) {
				LOGGER.info("BeatBlock Timeline: dropped audio asset bound to playback path={}", audioPath);
			} else {
				LOGGER.warn("BeatBlock Timeline: dropped audio asset failed to bind path={} reason={}", audioPath, BeatBlock.musicPlayer.getLastLoadError());
			}
		} else {
			LOGGER.warn("BeatBlock Timeline: dropped audio asset has no MusicPlayer instance path={}", audioPath);
		}
	}

	/**
	 * 绘制音频组拖放高亮边框（row 0~4 内容区外围），并在帧末重置标记。
	 * 应在所有行内容绘制完毕后调用。
	 */
	private void drawAudioGroupDropHighlight(TimelineLayout layout) {
		if (!audioGroupDropHighlight) return;
		// 寻找音频组内第一个和最后一个可见行（组头 + 所有活跃子轨）
		float y0 = -1f, y1 = -1f;
		int lastAudioRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START + layout.getActiveAudioSubRowCount() - 1;
		for (int r = TimelineTrackMeta.ROW_AUDIO_GROUP; r <= lastAudioRow; r++) {
			float ry = layout.getRowScreenY(r);
			if (ry < 0) continue;
			if (y0 < 0) y0 = ry;
			y1 = ry + layout.getRowHeight(r);
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

