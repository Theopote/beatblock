package com.beatblock.timeline.rendering;

import imgui.ImGui;

final class TimelineToolbarSnapGridControls {

	private static final String TOOLTIP_SNAP = "拖拽事件时吸附到网格";
	private static final String TOOLTIP_BEAT_SNAP = "拖拽事件时吸附到节拍";
	private static final String TOOLTIP_BEAT_GRID = "显示节拍网格线";
	private static final String TOOLTIP_MAGNET = "吸附到其他事件/关键帧";
	private static final String TOOLTIP_LOOP = "循环播放";

	void renderInline(TimelineToolbarState toolbarState) {
		if (toolbarState == null) return;
		renderSnap(toolbarState, "Snap", false);
		TimelineToolbarImGui.nextItemInGroup();
		renderBeatSnap(toolbarState, "Beat Snap", false);
		TimelineToolbarImGui.nextItemInGroup();
		renderBeatGrid(toolbarState, "Beat Grid", false);
		TimelineToolbarImGui.nextItemInGroup();
		renderMagnet(toolbarState, "Magnet", false);
		TimelineToolbarImGui.nextGroupOrWrap(0);
		renderLoop(toolbarState, "Loop", false);
		TimelineToolbarImGui.nextGroupOrWrap(0);
	}

	void renderCompact(TimelineToolbarState toolbarState) {
		if (toolbarState == null) return;
		ImGui.separator();
		ImGui.textDisabled("Snap & Grid");
		renderSnap(toolbarState, "Snap##tlMoreSnap", true);
		renderBeatSnap(toolbarState, "Beat Snap##tlMoreBeatSnap", true);
		renderBeatGrid(toolbarState, "Beat Grid##tlMoreBeatGrid", true);
		renderMagnet(toolbarState, "Magnet##tlMoreMagnet", true);
		renderLoop(toolbarState, "Loop##tlMoreLoop", true);
	}

	private static void renderSnap(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean snap = toolbarState.isSnapToGrid();
		if (ImGui.checkbox(label, snap)) toolbarState.setSnapToGrid(!snap);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SNAP);
		if (blockLayout) return;
	}

	private static void renderBeatSnap(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean beatSnap = toolbarState.isSnapToBeat();
		if (ImGui.checkbox(label, beatSnap)) toolbarState.setSnapToBeat(!beatSnap);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_SNAP);
		if (blockLayout) return;
	}

	private static void renderBeatGrid(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean beatGrid = toolbarState.isBeatGridVisible();
		if (ImGui.checkbox(label, beatGrid)) toolbarState.setBeatGridVisible(!beatGrid);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_GRID);
		if (blockLayout) return;
	}

	private static void renderMagnet(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean magnet = toolbarState.isMagnetSnap();
		if (ImGui.checkbox(label, magnet)) toolbarState.setMagnetSnap(!magnet);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_MAGNET);
		if (blockLayout) return;
	}

	private static void renderLoop(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean loop = toolbarState.isLoop();
		if (ImGui.checkbox(label, loop)) toolbarState.setLoop(!loop);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP);
		if (blockLayout) return;
	}
}
