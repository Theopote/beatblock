package com.beatblock.timeline;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.interaction.TimelineInteraction;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineRenderer;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.rendering.TimelineUiStateStore;

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
	private final TimelineToolbarState toolbarState = new TimelineToolbarState();
	private final TimelineTrackListState trackListState = new TimelineTrackListState();
	private final TimelineUiStateStore uiStateStore = new TimelineUiStateStore();
	private final TimelineLayout frameLayout = new TimelineLayout();
	private boolean frameLayoutPrepared;
	private boolean trackAreaContextAttached;
	private final IAudioPlayer audioPlayer;

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

	public TimelineEditor(Timeline timeline, IAudioPlayer audioPlayer) {
		this.timeline = timeline;
		this.audioPlayer = audioPlayer;
		this.state = new TimelineEditorState(timeline);
		this.renderer = new TimelineRenderer();
		this.interactionSystem = new TimelineInteraction();
		this.interactionSystem.setAudioPlayer(audioPlayer);
		this.commandManager = new CommandManager();
		this.uiStateStore.loadTrackListState(timeline, trackListState);
	}

	/** 无音频源时使用（可独立运行和测试）。 */
	public TimelineEditor(Timeline timeline) {
		this(timeline, null);
	}

	public Timeline getTimeline() {
		return timeline;
	}

	public TimelineEditorState getEditorState() {
		return state;
	}

	public TimelineClock getClock() {
		return state.getClock();
	}

	public TimelineViewState getViewState() {
		return state.getViewState();
	}

	public SelectionState getSelectionState() {
		return state.getSelectionState();
	}

	public InteractionState getInteractionState() {
		return state.getInteractionState();
	}

	public SelectionBox getSelectionBox() {
		return state.getSelectionBox();
	}

	public CommandManager getCommandManager() {
		return commandManager;
	}

	public TimelineToolbarState getToolbarState() {
		return toolbarState;
	}

	public IAudioPlayer getAudioPlayer() {
		return audioPlayer;
	}

	public TimelineTrackListState getTrackListState() {
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
			layout.attachTrackAreaContext(trackListState);
			trackAreaContextAttached = true;
		}
		return frameLayout;
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
		if (timeline == null || BeatBlock.musicPlayer == null || audioPlayer != BeatBlock.musicPlayer) return false;
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
			if (BeatBlock.musicPlayer.isPlaying()) {
				BeatBlock.musicPlayer.pause();
			}
			return true;
		}

		Object pathObj = timeline.getMetadata("clipAudioPath_" + active.getId());
		if (pathObj == null) return false;
		String targetPath = pathObj.toString();
		String loadedPath = BeatBlock.musicPlayer.getLoadedAudioPath();
		if (loadedPath == null || !loadedPath.equals(targetPath)) {
			BeatBlock.musicPlayer.loadAudio(targetPath);
			BeatBlock.musicPlayer.play();
			double local = Math.max(0.0, Math.min(clockTime - active.getStartTimeSeconds(), active.getDurationSeconds()));
			BeatBlock.musicPlayer.setCurrentTimeSeconds(local);
		}

		double globalTime = active.getStartTimeSeconds() + BeatBlock.musicPlayer.getCurrentTimeSeconds();
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
					if (!nextPath.equals(BeatBlock.musicPlayer.getLoadedAudioPath())) {
						BeatBlock.musicPlayer.loadAudio(nextPath);
					}
					BeatBlock.musicPlayer.setCurrentTimeSeconds(0);
					BeatBlock.musicPlayer.play();
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
		renderer.renderTrackArea(
			timeline,
			viewState,
			state.getSelectionState(),
			state.getClock(),
			state.getSelectionBox(),
			trackListState,
			layout
		);
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
		uiStateStore.syncAndFlush(timeline, trackListState);
	}

	/**
	 * 编辑器生命周期结束时释放后台资源。
	 */
	public void shutdown() {
		renderer.shutdown();
	}
}
