package com.beatblock.ui;

import imgui.type.ImBoolean;

/**
 * 各 Dock 面板的显示状态。与 {@link com.beatblock.ui.layout.BeatBlockDockPanelBegin} 配合：
 * 停靠时不显示「×」，浮动时显示「×」并与本处 {@link imgui.type.ImBoolean} 同步；视图菜单勾选亦同步。
 */
public final class BeatBlockPanelVisibility {

	public final ImBoolean audioAnalysis = new ImBoolean(true);
	public final ImBoolean tool = new ImBoolean(true);
	public final ImBoolean eventProperties = new ImBoolean(true);
	public final ImBoolean timeline = new ImBoolean(true);
	public final ImBoolean animationLibrary = new ImBoolean(false);
	public final ImBoolean selectionProperties = new ImBoolean(false);

	public void closeAll() {
		audioAnalysis.set(false);
		tool.set(false);
		eventProperties.set(false);
		timeline.set(false);
		animationLibrary.set(false);
		selectionProperties.set(false);
	}

	public void openAll() {
		audioAnalysis.set(true);
		tool.set(true);
		eventProperties.set(true);
		timeline.set(true);
		animationLibrary.set(true);
		selectionProperties.set(true);
	}
}
