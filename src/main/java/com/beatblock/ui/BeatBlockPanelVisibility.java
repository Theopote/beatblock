package com.beatblock.ui;

import imgui.type.ImBoolean;

/**
 * 各 Dock 面板的显示状态。与 {@link com.beatblock.ui.layout.BeatBlockDockPanelBegin} 配合：
 * 停靠时不显示「×」，浮动时显示「×」并与本处 {@link imgui.type.ImBoolean} 同步；视图菜单勾选亦同步。
 */
public final class BeatBlockPanelVisibility {

	public final ImBoolean audioAnalysis = new ImBoolean(true);
	public final ImBoolean tool = new ImBoolean(true);
	public final ImBoolean marker = new ImBoolean(true);
	public final ImBoolean eventProperties = new ImBoolean(true);
	public final ImBoolean cameraProperties = new ImBoolean(true);
	public final ImBoolean timeline = new ImBoolean(true);
	public final ImBoolean animationLibrary = new ImBoolean(false);
	public final ImBoolean selectionProperties = new ImBoolean(false);
	public final ImBoolean layer = new ImBoolean(false);
	public final ImBoolean rhythmDrop = new ImBoolean(false);
	public final ImBoolean undoHistory = new ImBoolean(false);
	public final ImBoolean eventLibrary = new ImBoolean(false);
	public final ImBoolean performanceMonitor = new ImBoolean(false);
	public final ImBoolean preferences = new ImBoolean(false);

	public void closeAll() {
		audioAnalysis.set(false);
		tool.set(false);
		marker.set(false);
		eventProperties.set(false);
		cameraProperties.set(false);
		timeline.set(false);
		animationLibrary.set(false);
		selectionProperties.set(false);
		layer.set(false);
		rhythmDrop.set(false);
		undoHistory.set(false);
		eventLibrary.set(false);
		performanceMonitor.set(false);
		preferences.set(false);
	}

	public void openAll() {
		audioAnalysis.set(true);
		tool.set(true);
		marker.set(true);
		eventProperties.set(true);
		cameraProperties.set(true);
		timeline.set(true);
		animationLibrary.set(true);
		selectionProperties.set(true);
		layer.set(true);
		rhythmDrop.set(true);
		undoHistory.set(true);
		eventLibrary.set(true);
		performanceMonitor.set(true);
		preferences.set(true);
	}
}
