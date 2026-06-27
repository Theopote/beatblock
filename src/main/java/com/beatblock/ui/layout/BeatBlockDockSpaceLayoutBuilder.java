package com.beatblock.ui.layout;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.flag.ImGuiDir;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BeatBlock Dockspace 默认布局：
 * 底部通栏 = 时间线（音乐/摄像机/动画事件）；
 * 顶部 = 菜单栏（单独渲染）；
 * 右侧 = 事件属性 + 摄像机属性面板；
 * 左侧 = 工具面板；
 * 中间 = 不放置任何面板，该区域即为 Minecraft 场景可见区域；
 * 动画库面板 = 可通过菜单打开/关闭，可为浮动或停靠。
 */
public final class BeatBlockDockSpaceLayoutBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockDockSpaceLayoutBuilder.class);

	public static final String AUDIO_ANALYSIS_PANEL_ID = "AudioAnalysisPanel";
	public static final String TOOL_PANEL_ID = "ToolPanel";
	public static final String MARKER_PANEL_ID = "MarkerPanel";
	public static final String EVENT_PROPERTIES_PANEL_ID = "EventPropertiesPanel";
	public static final String CAMERA_PROPERTIES_PANEL_ID = "CameraPropertiesPanel";
	public static final String TIMELINE_PANEL_ID = "TimelinePanel";
	public static final String ANIMATION_LIBRARY_PANEL_ID = "AnimationLibraryPanel";
	public static final String SELECTION_PROPERTIES_PANEL_ID = "BeatBlockSelectionProperties";
	public static final String LAYER_PANEL_ID = "LayerPanel";
	public static final String RHYTHM_DROP_PANEL_ID = "RhythmDropPanel";
	public static final String UNDO_HISTORY_PANEL_ID = "UndoHistoryPanel";
	public static final String EVENT_LIBRARY_PANEL_ID = "EventLibraryPanel";
	public static final String PERFORMANCE_MONITOR_PANEL_ID = "PerformanceMonitorPanel";
	public static final String PREFERENCES_PANEL_ID = "PreferencesPanel";

	private static boolean layoutInitialized = false;

	public static String audioAnalysisWindow() {
		return BBTexts.windowTitle("beatblock.panel.audio_analysis", AUDIO_ANALYSIS_PANEL_ID);
	}

	public static String toolPanelWindow() {
		return BBTexts.windowTitle("beatblock.panel.tool", TOOL_PANEL_ID);
	}

	public static String markerPanelWindow() {
		return BBTexts.windowTitle("beatblock.panel.marker_debug", MARKER_PANEL_ID);
	}

	public static String eventPropertiesWindow() {
		return BBTexts.windowTitle("beatblock.panel.event_properties", EVENT_PROPERTIES_PANEL_ID);
	}

	public static String cameraPropertiesWindow() {
		return BBTexts.windowTitle("beatblock.panel.camera_properties", CAMERA_PROPERTIES_PANEL_ID);
	}

	public static String timelinePanelWindow() {
		return BBTexts.windowTitle("beatblock.panel.timeline", TIMELINE_PANEL_ID);
	}

	public static String animationLibraryWindow() {
		return BBTexts.windowTitle("beatblock.panel.animation_library", ANIMATION_LIBRARY_PANEL_ID);
	}

	public static String selectionPropertiesWindow() {
		return BBTexts.windowTitle("beatblock.panel.selection_properties", SELECTION_PROPERTIES_PANEL_ID);
	}

	public static String layerPanelWindow() {
		return BBTexts.windowTitle("beatblock.panel.layer", LAYER_PANEL_ID);
	}

	public static String rhythmDropPanelWindow() {
		return BBTexts.windowTitle("beatblock.panel.rhythm_drop", RHYTHM_DROP_PANEL_ID);
	}

	public static String undoHistoryWindow() {
		return BBTexts.windowTitle("beatblock.panel.undo_history", UNDO_HISTORY_PANEL_ID);
	}

	public static String eventLibraryWindow() {
		return BBTexts.windowTitle("beatblock.panel.event_library", EVENT_LIBRARY_PANEL_ID);
	}

	public static String performanceMonitorWindow() {
		return BBTexts.windowTitle("beatblock.panel.performance_monitor", PERFORMANCE_MONITOR_PANEL_ID);
	}

	public static String preferencesWindow() {
		return BBTexts.windowTitle("beatblock.panel.preferences", PREFERENCES_PANEL_ID);
	}

	public static void buildDefaultLayout(int dockspaceId) {
		if (layoutInitialized) return;
		try {
			imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
			imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
			imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId,
				ImGui.getMainViewport().getWorkSizeX(),
				ImGui.getMainViewport().getWorkSizeY());

			ImInt dockMain = new ImInt(dockspaceId);

			// 1. 底部分割：时间线（约 22%）
			ImInt dockBottom = new ImInt();
			imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Down, 0.22f, dockBottom, dockMain);

			// 2. 左侧分割：音频解析 + 工具面板（总宽约 22%）
			ImInt dockLeft = new ImInt();
			imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Left, 0.22f, dockLeft, dockMain);

			// 2.1 左侧再纵向分割：上半音频解析，中制40%工具，下半60%Marker与调试
			ImInt dockLeftTop = new ImInt();
			ImInt dockLeftMiddle = new ImInt();
			ImInt dockLeftBottom = new ImInt();
			imgui.internal.ImGui.dockBuilderSplitNode(dockLeft.get(), ImGuiDir.Up, 0.52f, dockLeftTop, dockLeftMiddle);
			imgui.internal.ImGui.dockBuilderSplitNode(dockLeftMiddle.get(), ImGuiDir.Up, 0.55f, dockLeftMiddle, dockLeftBottom);

			// 3. 右侧分割：属性面板（约 26%），再纵向分为事件属性 + 摄像机属性
			ImInt dockRight = new ImInt();
			imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Right, 0.26f, dockRight, dockMain);
			ImInt dockRightTop = new ImInt();
			ImInt dockRightBottom = new ImInt();
			imgui.internal.ImGui.dockBuilderSplitNode(dockRight.get(), ImGuiDir.Up, 0.5f, dockRightTop, dockRightBottom);

			// 4. 停靠窗口（中间不 dock 任何窗口，即为 Minecraft 场景区域）
			imgui.internal.ImGui.dockBuilderDockWindow(timelinePanelWindow(), dockBottom.get());
			imgui.internal.ImGui.dockBuilderDockWindow(audioAnalysisWindow(), dockLeftTop.get());
			imgui.internal.ImGui.dockBuilderDockWindow(toolPanelWindow(), dockLeftMiddle.get());
			imgui.internal.ImGui.dockBuilderDockWindow(markerPanelWindow(), dockLeftBottom.get());
			imgui.internal.ImGui.dockBuilderDockWindow(eventPropertiesWindow(), dockRightTop.get());
			imgui.internal.ImGui.dockBuilderDockWindow(cameraPropertiesWindow(), dockRightBottom.get());

			imgui.internal.ImGui.dockBuilderFinish(dockspaceId);
			layoutInitialized = true;
			LOGGER.info("BeatBlock 默认 Dockspace 布局已应用");
		} catch (Exception e) {
			LOGGER.error("构建 Dockspace 布局失败", e);
		}
	}

	public static void resetLayoutState() {
		layoutInitialized = false;
	}
}
