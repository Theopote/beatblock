package com.beatblock.timeline;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.interaction.TimelineInteraction;
import com.beatblock.timeline.interaction.TimelineInteractionDeleteSupport;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineRenderer;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TrackDefinition;
import com.beatblock.timeline.rendering.TrackRegistry;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.rendering.TimelineUiStateStore;
import imgui.ImGui;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ImGui 时间线编辑器入口：协调渲染与交互，UI 与数据分离。
 * 职责：UI 入口、协调各子系统。
 */
public final class TimelineEditor {

	private final Timeline timeline;
	private final TimelineEditorState state;
	private final TimelineRenderer renderer;
	private final TimelineInteraction interactionSystem;
	private final CommandManager commandManager;
	private final @Nullable MusicPlayer musicPlayer;
	private final TimelineToolbarState toolbarState = new TimelineToolbarState();
	private final TimelineTrackListState trackListState = new TimelineTrackListState();
	private final TimelineUiStateStore uiStateStore = new TimelineUiStateStore();
	private final TimelineLayout frameLayout = new TimelineLayout();
	private boolean frameLayoutPrepared;
	private boolean trackAreaContextAttached;
	private final @Nullable IAudioPlayer audioPlayer;

	/** 供 TimelinePanel 绘制贯通竖线：屏幕 X、标尺顶 Y、轨道内容区底 Y（每帧由 render 更新） */
	private float cachedDividerScreenX;
	private float cachedDividerTopScreenY;
	private float cachedDividerContentBottomScreenY;

	public float getCachedDividerScreenX() {
		return cachedDividerScreenX;
	}

	public float getCachedDividerTopScreenY() {
		return cachedDividerTopScreenY;
	}

	public float getCachedDividerContentBottomScreenY() {
		return cachedDividerContentBottomScreenY;
	}

	public TimelineEditor(@NonNull Timeline timeline, @Nullable IAudioPlayer audioPlayer) {
		this.timeline = timeline;
		this.audioPlayer = audioPlayer;
		this.musicPlayer = audioPlayer instanceof MusicPlayer mp ? mp : null;
		this.state = new TimelineEditorState(timeline);
		this.renderer = new TimelineRenderer();
		this.interactionSystem = new TimelineInteraction();
		this.interactionSystem.setAudioPlayer(audioPlayer);
		this.interactionSystem.bindTimelineEditor(this);
		if (audioPlayer instanceof MusicPlayer musicPlayer) {
			this.interactionSystem.setMusicPlayer(musicPlayer);
		}
		this.commandManager = new CommandManager();
		this.uiStateStore.loadTrackListState(timeline, trackListState);
	}

	/** 无音频源时使用（可独立运行和测试）。 */
	public TimelineEditor(@NonNull Timeline timeline) {
		this(timeline, null);
	}

	public @NonNull Timeline getTimeline() {
		return timeline;
	}

	public @NonNull TimelineEditorState getEditorState() {
		return state;
	}

	public @NonNull TimelineClock getClock() {
		return state.getClock();
	}

	public @NonNull TimelineViewState getViewState() {
		return state.getViewState();
	}

	public @NonNull SelectionState getSelectionState() {
		return state.getSelectionState();
	}

	public @NonNull InteractionState getInteractionState() {
		return state.getInteractionState();
	}

	public @NonNull SelectionBox getSelectionBox() {
		return state.getSelectionBox();
	}

	public @NonNull CommandManager getCommandManager() {
		return commandManager;
	}

	/** 打开/切换工程后丢弃 Undo/Redo 栈，避免旧命令引用已替换的时间线状态。 */
	public void clearUndoHistory() {
		commandManager.clear();
	}

	public @NonNull TimelineToolbarState getToolbarState() {
		return toolbarState;
	}

	public @Nullable IAudioPlayer getAudioPlayer() {
		return audioPlayer;
	}

	public @NonNull TimelineTrackListState getTrackListState() {
		return trackListState;
	}

	public void beginFrameLayout() {
		frameLayout.beginFrame(trackListState.getTrackHeaderWidth());
		frameLayoutPrepared = true;
		trackAreaContextAttached = false;
	}

	private TimelineLayout requireFrameLayout() {
		if (!frameLayoutPrepared) {
			beginFrameLayout();
		}
		return frameLayout;
	}

	private TimelineLayout requireTrackAreaLayout() {
		TimelineLayout layout = requireFrameLayout();
		if (!trackAreaContextAttached) {
			List<TrackDefinition> audioDefs = TrackRegistry.buildAudioSubTracks(timeline);
			List<TrackDefinition> controlDefs = TrackRegistry.buildBlockAnimationControlTracks(timeline);
			layout.setActiveAudioSubRowCount(audioDefs.size());
			layout.setActiveAnimationSubRowCount(controlDefs.size());
			layout.setCustomRowOrder(buildFeaturePairedRowOrder(audioDefs, controlDefs));
			layout.setCustomRowParents(buildCustomRowParents(audioDefs, controlDefs));
			layout.attachTrackAreaContext(trackListState);
			trackAreaContextAttached = true;
		}
		return frameLayout;
	}

	private List<Integer> buildFeaturePairedRowOrder(List<TrackDefinition> audioDefs, List<TrackDefinition> controlDefs) {
		List<Integer> ordered = new ArrayList<>(TimelineLayout.CONTENT_ROW_COUNT);
		Set<Integer> addedRows = new HashSet<>();

		addRow(ordered, addedRows, TimelineTrackMeta.ROW_AUDIO_GROUP);

		Map<String, Integer> audioFeatureRows = new HashMap<>();
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			TrackDefinition td = audioDefs.get(slot);
			int row = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (td.getVisualType() == TrackDefinition.VisualType.IMPULSE) {
				audioFeatureRows.put(td.getKey(), row);
			} else {
				addRow(ordered, addedRows, row);
			}
		}

		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIMATION_GROUP);

		Map<String, Integer> controlFeatureRows = new HashMap<>();
		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			TrackDefinition td = controlDefs.get(slot);
			String featureKey = Timeline.blockAnimationFeatureKeyFromTrackId(td.getKey());
			if (featureKey == null || featureKey.isBlank()) continue;
			controlFeatureRows.put(featureKey, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
		}

		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			TrackDefinition td = audioDefs.get(slot);
			if (td.getVisualType() != TrackDefinition.VisualType.IMPULSE) continue;
			String featureKey = td.getKey();
			Integer audioRow = audioFeatureRows.get(featureKey);
			Integer controlRow = controlFeatureRows.get(featureKey);
			if (audioRow != null) addRow(ordered, addedRows, audioRow);
			if (controlRow != null) addRow(ordered, addedRows, controlRow);
		}

		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
		}

		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ACTION_GROUP);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIM_BLOCK);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_CAMERA);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIM_AUTO);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_BUILD_REVERSE);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_GLOBAL_EVENT);

		for (int i = 0; i < TimelineLayout.CONTENT_ROW_COUNT; i++) {
			addRow(ordered, addedRows, i);
		}

		return ordered;
	}

	private Map<Integer, Integer> buildCustomRowParents(List<TrackDefinition> audioDefs, List<TrackDefinition> controlDefs) {
		Map<Integer, Integer> parents = new HashMap<>();
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			TrackDefinition td = audioDefs.get(slot);
			int row = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (td.getVisualType() == TrackDefinition.VisualType.WAVEFORM) {
				parents.put(row, TimelineTrackMeta.ROW_AUDIO_GROUP);
			} else {
				parents.put(row, TimelineTrackMeta.ROW_ANIMATION_GROUP);
			}
		}
		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			parents.put(TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot, TimelineTrackMeta.ROW_ANIMATION_GROUP);
		}
		parents.put(TimelineTrackMeta.ROW_ACTION_GROUP, TimelineTrackMeta.NO_PARENT);
		parents.put(TimelineTrackMeta.ROW_ANIM_BLOCK, TimelineTrackMeta.ROW_ACTION_GROUP);
		parents.put(TimelineTrackMeta.ROW_CAMERA, TimelineTrackMeta.ROW_ACTION_GROUP);
		parents.put(TimelineTrackMeta.ROW_ANIM_AUTO, TimelineTrackMeta.ROW_ACTION_GROUP);
		parents.put(TimelineTrackMeta.ROW_BUILD_REVERSE, TimelineTrackMeta.ROW_ACTION_GROUP);
		return parents;
	}

	private static void addRow(List<Integer> ordered, Set<Integer> addedRows, int row) {
		if (row < 0 || row >= TimelineLayout.CONTENT_ROW_COUNT) return;
		if (addedRows.add(row)) ordered.add(row);
	}

	/**
	 * 在父窗口标尺带点击分割线开始拖动（子窗口未覆盖标尺区域，需在此处命中）。
	 */
	public void tryBeginTimelineDividerDragOnRuler() {
		if (timeline == null) return;
		TimelineLayout l = requireFrameLayout();
		interactionSystem.tryBeginDividerDragOnRuler(trackListState, getInteractionState(), l);
	}

	/** 在主窗口标尺上下文处理交互（Scrub / Loop Handle / Marker / 右键等）。 */
	public void handleRulerInteraction() {
		if (timeline == null) return;
		TimelineLayout layout = requireFrameLayout();
		interactionSystem.updateRulerOnly(
			timeline,
			state.getViewState(),
			state.getInteractionState(),
			state.getSelectionState(),
			state.getClock(),
			layout,
			toolbarState
		);
	}

	/** 同步时钟时长与 Timeline 一致 */
	public void syncClockDuration() {
		state.syncClockDuration();
	}

	/**
	 * 固定区域：只绘制时间刻度（标尺）行，并占位，不随滚动条滚动。在 TimelinePanel 中先调用此方法，再 BeginChild。
	 */
	public void renderRulerOnly() {
		if (timeline == null) return;
		state.syncClockDuration();
		if (audioPlayer != null && audioPlayer.isPlaying()) {
			boolean segmentedHandled = syncClockFromSegmentedAudioPlayback();
			if (!segmentedHandled) {
				double t = audioPlayer.getCurrentTimeSeconds();
				double dur = timeline.getDurationSeconds();
				if (toolbarState.isLoop()) {
					double loopIn = Math.max(0, toolbarState.getLoopInSeconds());
					double loopOut = toolbarState.hasLoopRange() ? toolbarState.getLoopOutSeconds() : dur;
					if (loopOut <= loopIn) loopOut = dur;
					if (loopOut > 0) {
						if (t >= loopOut) {
							audioPlayer.setCurrentTimeSeconds(loopIn);
							state.getClock().seek(loopIn);
						} else if (t < loopIn) {
							audioPlayer.setCurrentTimeSeconds(loopIn);
							state.getClock().seek(loopIn);
						} else {
							state.getClock().setCurrentTimeSeconds(t);
						}
					} else {
						state.getClock().setCurrentTimeSeconds(t);
					}
				} else {
					state.getClock().setCurrentTimeSeconds(t);
				}
			}
		}
		TimelineLayout layout = requireFrameLayout();
		cachedDividerScreenX = layout.contentLeft;
		cachedDividerTopScreenY = layout.rulerTop;
		double duration = timeline.getDurationSeconds() > 0 ? timeline.getDurationSeconds() : 60.0;
		TimelineViewState viewState = state.getViewState();
		if (viewState.getViewEndTimeSeconds() >= 59 && viewState.getViewEndTimeSeconds() <= 61 && duration > 0 && layout.contentWidth > 0) {
			viewState.fitToDuration(duration, layout.contentWidth);
		}
		renderer.renderRulerRow(layout, viewState, timeline.getBpm(), toolbarState, timeline);
	}

	private boolean syncClockFromSegmentedAudioPlayback() {
		if (timeline == null || musicPlayer == null || audioPlayer != musicPlayer) return false;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null || audioTrack.getClips().isEmpty()) return false;
		boolean segmentedTimeline = false;
		for (Clip c : audioTrack.getClips()) {
			if (c == null) continue;
			Object pathObj = timeline.getMetadata("clipAudioPath_" + c.getId());
			if (pathObj != null && !pathObj.toString().isBlank()) {
				segmentedTimeline = true;
				break;
			}
		}

		double clockTime = state.getClock().getCurrentTimeSeconds();
		Clip active = null;
		for (Clip c : audioTrack.getClips()) {
			if (c == null) continue;
			if (clockTime >= c.getStartTimeSeconds() && clockTime <= c.getEndTimeSeconds()) {
				active = c;
				break;
			}
		}
		if (active == null) {
			if (!segmentedTimeline) return false;
			if (musicPlayer.isPlaying()) {
				musicPlayer.pause();
			}
			return true;
		}

		Object pathObj = timeline.getMetadata("clipAudioPath_" + active.getId());
		if (pathObj == null) return false;
		String targetPath = pathObj.toString();
		String loadedPath = musicPlayer.getLoadedAudioPath();
		if (loadedPath == null || !loadedPath.equals(targetPath)) {
			musicPlayer.loadAudio(targetPath);
			musicPlayer.play();
			double local = Math.max(0.0, Math.min(clockTime - active.getStartTimeSeconds(), active.getDurationSeconds()));
			musicPlayer.setCurrentTimeSeconds(local);
		}

		double globalTime = active.getStartTimeSeconds() + musicPlayer.getCurrentTimeSeconds();
		if (globalTime >= active.getEndTimeSeconds()) {
			Clip next = null;
			for (Clip c : audioTrack.getClips()) {
				if (c != null && c.getStartTimeSeconds() >= active.getEndTimeSeconds()) {
					if (next == null || c.getStartTimeSeconds() < next.getStartTimeSeconds()) next = c;
				}
			}
			if (next != null) {
				Object nextPathObj = timeline.getMetadata("clipAudioPath_" + next.getId());
				if (nextPathObj != null) {
					String nextPath = nextPathObj.toString();
					if (!nextPath.equals(musicPlayer.getLoadedAudioPath())) {
						musicPlayer.loadAudio(nextPath);
					}
					musicPlayer.setCurrentTimeSeconds(0);
					musicPlayer.play();
					globalTime = next.getStartTimeSeconds();
				}
			}
		}
		state.getClock().setCurrentTimeSeconds(globalTime);
		return true;
	}

	/**
	 * 可滚动区域：在 BeginChild 内调用，绘制轨道区（网格 + 一行一行轨道 + 播放头 + 框选），并处理交互。
	 */
	public void renderTrackArea() {
		if (timeline == null) return;
		TimelineLayout layout = requireTrackAreaLayout();
		cachedDividerScreenX = layout.contentLeft;
		cachedDividerContentBottomScreenY = layout.contentTop + layout.contentHeight;
		TimelineViewState viewState = state.getViewState();
		interactionSystem.update(
			timeline,
			viewState,
			state.getInteractionState(),
			state.getSelectionState(),
			state.getClock(),
			state.getSelectionBox(),
			trackListState,
			layout,
			toolbarState
		);
		renderer.renderTrackArea(
			timeline,
			viewState,
			state.getSelectionState(),
			state.getClock(),
			state.getSelectionBox(),
			state.getInteractionState(),
			trackListState,
			layout
		);
		uiStateStore.syncAndFlush(timeline, trackListState);
	}

	/**
	 * 在 TimelinePanel 中于标尺与轨道区绘制完成后调用，绘制贯通播放头竖线。
	 */
	public void renderPlayheadOverlay() {
		if (timeline == null) return;
		TimelineLayout layout = requireFrameLayout();
		TimelineViewState viewState = state.getViewState();
		TimelineClock clock = state.getClock();
		if (viewState == null || clock == null) return;

		double currentTime = clock.getCurrentTimeSeconds();
		float playheadX = layout.contentLeft + viewState.timeToScreen(currentTime);
		if (playheadX < layout.contentLeft - 2 || playheadX > layout.contentLeft + layout.contentWidth + 2) {
			return;
		}
		float y0 = layout.rulerTop;
		float y1 = cachedDividerContentBottomScreenY > y0 ? cachedDividerContentBottomScreenY : layout.contentTop + layout.contentHeight;
		ImGui.getWindowDrawList().addLine(playheadX, y0, playheadX, y1, TimelineRenderer.PLAYHEAD_COLOR, 2f);
	}

	/**
	 * 编辑器生命周期结束时释放后台资源。
	 */
	public void shutdown() {
		renderer.shutdown();
	}

	public void copySelectedEvents() {
		interactionSystem.copySelectedEvents(timeline, state.getSelectionState());
	}

	public void pasteClipboardAtPlayhead() {
		interactionSystem.pasteClipboardEvents(
			timeline,
			state.getSelectionState(),
			state.getClock().getCurrentTimeSeconds(),
			trackListState
		);
	}

	public void deleteSelectedEntries() {
		interactionSystem.deleteSelectedEntries(timeline, state.getSelectionState(), trackListState);
	}

	public boolean hasDeletableSelection() {
		return TimelineInteractionDeleteSupport.hasDeletableSelection(
			timeline, state.getSelectionState(), trackListState);
	}

	public boolean hasClipboardContent() {
		return !interactionSystem.clipboardEvents().isEmpty();
	}

	public boolean hasTimelineSelection() {
		SelectionState selection = state.getSelectionState();
		return !selection.getSelectedEvents().isEmpty() || !selection.getSelectedClips().isEmpty();
	}
}
