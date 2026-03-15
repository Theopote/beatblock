package com.beatblock.ui.layout;

import imgui.ImGui;
import imgui.flag.ImGuiDir;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BeatBlock Dockspace 默认布局：
 * 底部通栏 = 时间线（音乐/摄像机/动画事件）；
 * 顶部 = 菜单栏（单独渲染）；
 * 右侧 = 事件属性面板；
 * 左侧 = 工具面板；
 * 中间 = 不放置任何面板，该区域即为 Minecraft 场景可见区域；
 * 动画库面板 = 可通过菜单打开/关闭，可为浮动或停靠。
 */
public final class BeatBlockDockSpaceLayoutBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockDockSpaceLayoutBuilder.class);

	/** 与各面板 window name 一致，用于 dockBuilderDockWindow */
	public static final String TOOL_PANEL_WINDOW       = "工具###ToolPanel";
	public static final String EVENT_PROPERTIES_WINDOW = "事件属性###EventPropertiesPanel";
	public static final String TIMELINE_PANEL_WINDOW   = "时间线###TimelinePanel";
	public static final String ANIMATION_LIBRARY_WINDOW = "动画库###AnimationLibraryPanel";

	private static boolean layoutInitialized = false;

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

			// 2. 左侧分割：工具面板（约 18%）
			ImInt dockLeft = new ImInt();
			imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Left, 0.18f, dockLeft, dockMain);

			// 3. 右侧分割：事件属性面板（约 22%）
			ImInt dockRight = new ImInt();
			imgui.internal.ImGui.dockBuilderSplitNode(dockMain.get(), ImGuiDir.Right, 0.22f, dockRight, dockMain);

			// 4. 停靠窗口（中间不 dock 任何窗口，即为 Minecraft 场景区域）
			imgui.internal.ImGui.dockBuilderDockWindow(TIMELINE_PANEL_WINDOW, dockBottom.get());
			imgui.internal.ImGui.dockBuilderDockWindow(TOOL_PANEL_WINDOW, dockLeft.get());
			imgui.internal.ImGui.dockBuilderDockWindow(EVENT_PROPERTIES_WINDOW, dockRight.get());

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
