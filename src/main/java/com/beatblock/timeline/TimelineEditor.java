package com.beatblock.timeline;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.interaction.TimelineInteraction;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineRenderer;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import imgui.ImGui;

/**
 * ImGui 时间线编辑器入口：协调渲染与交互，UI 与数据分离。
 * 职责：UI 入口、协调各子系统。
 */
public final class TimelineEditor {

	private final Timeline timeline;
	private final com.beatblock.timeline.editor.TimelineEditor state;
	private final TimelineRenderer renderer;
	private final TimelineInteraction interactionSystem;
	private final CommandManager commandManager;
	private final TimelineToolbarState toolbarState = new TimelineToolbarState();
	private final TimelineTrackListState trackListState = new TimelineTrackListState();

	public TimelineEditor(Timeline timeline) {
		this.timeline = timeline;
		this.state = new com.beatblock.timeline.editor.TimelineEditor(timeline);
		this.renderer = new TimelineRenderer();
		this.interactionSystem = new TimelineInteraction();
		this.commandManager = new CommandManager();
	}

	public Timeline getTimeline() {
		return timeline;
	}

	public com.beatblock.timeline.editor.TimelineEditor getState() {
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

	public TimelineTrackListState getTrackListState() {
		return trackListState;
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
		if (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.isPlaying()) {
			state.getClock().setCurrentTimeSeconds(BeatBlock.musicPlayer.getCurrentTimeSeconds());
		}
		TimelineLayout layout = new TimelineLayout();
		layout.build();
		double duration = timeline.getDurationSeconds() > 0 ? timeline.getDurationSeconds() : 60.0;
		TimelineViewState viewState = state.getViewState();
		if (viewState.getViewEndTimeSeconds() >= 59 && viewState.getViewEndTimeSeconds() <= 61 && duration > 0 && layout.contentWidth > 0) {
			viewState.fitToDuration(duration, layout.contentWidth);
			layout.build();
		}
		renderer.renderRulerRow(layout, viewState);
	}

	/**
	 * 可滚动区域：在 BeginChild 内调用，绘制轨道区（网格 + 一行一行轨道 + 播放头 + 框选），并处理交互。
	 */
	public void renderTrackArea() {
		if (timeline == null) return;
		TimelineLayout layout = new TimelineLayout();
		layout.build(true); // 在可滚动子窗口内，无标尺行
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
			layout
		);
	}
}
