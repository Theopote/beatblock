package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.Locale;

/**
 * 左侧工具面板。提供 Smart Auto Map：点击后弹出设置（风格/复杂度/镜头/粒子），生成完整编排。
 */
public class ToolPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private boolean showAutoMapSettings = false;
	private final AutoMapSettingsPanel autoMapSettingsPanel = new AutoMapSettingsPanel();
	private String selectedMarkerId;
	private final ImString markerNameBuffer = new ImString(128);
	private final ImString markerTimeBuffer = new ImString(32);

	/** 由菜单栏「演出 → Smart Auto Map」调用，打开设置弹窗 */
	public void setShowAutoMapSettings(boolean show) {
		this.showAutoMapSettings = show;
	}
	/** 上次生成统计 */
	private SmartAutoMapEngine.AutoMapResult lastAutoMapResult = null;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}
		ImGui.text("工具");
		ImGui.separator();

		if (ImGui.button("Smart Auto Map")) {
			showAutoMapSettings = true;
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("自动编排：根据音乐生成方块动画、摄像机、粒子与节奏结构（先导入音乐）");
		}
		if (lastAutoMapResult != null) {
			ImGui.sameLine();
			ImGui.textDisabled(String.format("动画 %d, 镜头 %d, 粒子 %d",
				lastAutoMapResult.getAnimationEvents(),
				lastAutoMapResult.getCameraEvents(),
				lastAutoMapResult.getParticleEvents()));
		}

		renderMarkerManager();

		ImGui.spacing();
		ImGui.textWrapped("选择、画笔、橡皮等工具将在此列出。");
		ImGui.end();

		// 设置弹窗（独立窗口）
		if (showAutoMapSettings) {
			boolean done = autoMapSettingsPanel.render(res -> lastAutoMapResult = res);
			if (done) showAutoMapSettings = false;
		}
	}

	private void renderMarkerManager() {
		Timeline timeline = BeatBlock.timeline;
		if (timeline == null) return;

		ImGui.spacing();
		ImGui.separator();
		ImGui.text("Marker");

		if (timeline.getMarkers().isEmpty()) {
			selectedMarkerId = null;
			ImGui.textDisabled("暂无 Marker。可在工具条创建，或双击标尺空白处创建。");
			return;
		}

		if (selectedMarkerId != null && timeline.findMarkerIndexById(selectedMarkerId) < 0) {
			selectedMarkerId = null;
		}

		if (ImGui.beginChild("##MarkerList", 0, 110, true)) {
			for (TimelineMarker marker : timeline.getMarkers()) {
				if (marker == null) continue;
				String markerId = marker.getId();
				boolean selected = markerId.equals(selectedMarkerId);
				String label = String.format(Locale.ROOT, "%.2fs  %s##%s",
					marker.getTimeSeconds(),
					marker.getName() == null || marker.getName().isBlank() ? "(unnamed)" : marker.getName(),
					markerId);
				if (ImGui.selectable(label, selected)) {
					selectedMarkerId = markerId;
					markerNameBuffer.set(marker.getName());
					markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", marker.getTimeSeconds()));
				}
			}
		}
		ImGui.endChild();

		int markerIndex = selectedMarkerId != null ? timeline.findMarkerIndexById(selectedMarkerId) : -1;
		if (markerIndex < 0) return;

		TimelineMarker marker = timeline.getMarkers().get(markerIndex);
		ImGui.textDisabled("选中 Marker");
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("名称##markerName", markerNameBuffer);
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("时间(秒)##markerTime", markerTimeBuffer);

		if (ImGui.button("Jump##toolMarkerJump")) {
			jumpToMarker(marker);
		}
		ImGui.sameLine();
		if (ImGui.button("Apply##toolMarkerApply")) {
			applyMarkerEdits(timeline, selectedMarkerId, marker);
		}
		ImGui.sameLine();
		if (ImGui.button("Delete##toolMarkerDelete")) {
			timeline.removeMarker(selectedMarkerId);
			selectedMarkerId = null;
		}

		ImGui.spacing();
		ImGui.textDisabled("循环区");
		if (ImGui.button("Set In##toolMarkerSetIn")) {
			setLoopIn(marker.getTimeSeconds());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("将当前 Marker 设为循环起点");
		}
		ImGui.sameLine();
		if (ImGui.button("Set Out##toolMarkerSetOut")) {
			setLoopOut(marker.getTimeSeconds());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("将当前 Marker 设为循环终点");
		}

		TimelineMarker prevMarker = markerIndex > 0 ? timeline.getMarkers().get(markerIndex - 1) : null;
		TimelineMarker nextMarker = markerIndex + 1 < timeline.getMarkers().size() ? timeline.getMarkers().get(markerIndex + 1) : null;

		if (ImGui.button("Prev->This##toolMarkerLoopPrev", 0, 0)) {
			applyLoopRange(prevMarker, marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(prevMarker != null ? "将上一个 Marker 到当前 Marker 设为循环区" : "没有上一个 Marker");
		}
		ImGui.sameLine();
		if (ImGui.button("This->Next##toolMarkerLoopNext", 0, 0)) {
			applyLoopRange(marker, nextMarker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(nextMarker != null ? "将当前 Marker 到下一个 Marker 设为循环区" : "没有下一个 Marker");
		}
	}

	private void jumpToMarker(TimelineMarker marker) {
		if (marker == null || BeatBlock.timelineEditor == null) return;
		double t = marker.getTimeSeconds();
		BeatBlock.timelineEditor.getClock().seek(t);
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setCurrentTimeSeconds(t);
		}
	}

	private void applyMarkerEdits(Timeline timeline, String markerId, TimelineMarker marker) {
		if (timeline == null || markerId == null || marker == null) return;
		String name = markerNameBuffer.get() == null ? "" : markerNameBuffer.get().trim();
		double timeSeconds = marker.getTimeSeconds();
		try {
			String raw = markerTimeBuffer.get();
			if (raw != null && !raw.isBlank()) {
				timeSeconds = Math.max(0, Double.parseDouble(raw.trim()));
			}
		} catch (NumberFormatException ignored) {
			markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", marker.getTimeSeconds()));
			return;
		}
		timeline.updateMarker(markerId, timeSeconds, name);
		int newIndex = timeline.findMarkerIndexById(markerId);
		if (newIndex >= 0) {
			TimelineMarker updated = timeline.getMarkers().get(newIndex);
			markerNameBuffer.set(updated.getName());
			markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", updated.getTimeSeconds()));
		}
	}

	private void setLoopIn(double timeSeconds) {
		if (BeatBlock.timelineEditor == null) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		toolbarState.setLoopInSeconds(timeSeconds);
		if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= timeSeconds) {
			toolbarState.setLoopOutSeconds(timeSeconds + 0.1);
		}
		toolbarState.setLoop(true);
	}

	private void setLoopOut(double timeSeconds) {
		if (BeatBlock.timelineEditor == null) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		double loopIn = toolbarState.getLoopInSeconds();
		toolbarState.setLoopOutSeconds(Math.max(timeSeconds, loopIn + 0.1));
		toolbarState.setLoop(true);
	}

	private void applyLoopRange(TimelineMarker startMarker, TimelineMarker endMarker) {
		if (startMarker == null || endMarker == null || BeatBlock.timelineEditor == null) return;
		double start = Math.min(startMarker.getTimeSeconds(), endMarker.getTimeSeconds());
		double end = Math.max(startMarker.getTimeSeconds(), endMarker.getTimeSeconds());
		if (end <= start) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		toolbarState.setLoopInSeconds(start);
		toolbarState.setLoopOutSeconds(end);
		toolbarState.setLoop(true);
		BeatBlock.timelineEditor.getClock().seek(start);
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setCurrentTimeSeconds(start);
		}
	}
}
