package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.performance.PerformanceMonitor;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/** 性能监控面板：FPS、内存、时间线与动画运行时指标。 */
public final class PerformanceMonitorPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.performanceMonitorWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.performanceMonitorWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			var snap = PerformanceMonitor.snapshot(BeatBlock.getContext());

			ImGui.text(BBTexts.get("beatblock.performance.title"));
			ImGui.separator();
			ImGui.text(BBTexts.get("beatblock.performance.fps", snap.fps()));
			ImGui.text(BBTexts.get("beatblock.performance.memory", snap.heapUsedMb(), snap.heapMaxMb()));
			ImGui.text(BBTexts.get("beatblock.performance.uptime", snap.uptimeSeconds()));
			ImGui.spacing();
			ImGui.textDisabled(BBTexts.get("beatblock.performance.timeline_section"));
			ImGui.text(BBTexts.get("beatblock.performance.duration", snap.timelineDurationSeconds()));
			ImGui.text(BBTexts.get("beatblock.performance.manual_events", snap.manualAnimationEvents()));
			ImGui.text(BBTexts.get("beatblock.performance.auto_events", snap.autoAnimationEvents()));
			ImGui.spacing();
			ImGui.textDisabled(BBTexts.get("beatblock.performance.engine_section"));
			ImGui.text(BBTexts.get("beatblock.performance.stage_objects", snap.stageObjectCount()));
			ImGui.text(BBTexts.get("beatblock.performance.active_instances", snap.activeAnimationInstances()));
			ImGui.text(BBTexts.get("beatblock.performance.animated_blocks", snap.animatedBlocksThisFrame()));
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.performanceMonitorWindow());
		}
	}
}
