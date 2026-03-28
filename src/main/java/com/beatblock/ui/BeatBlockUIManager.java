package com.beatblock.ui;

import com.beatblock.client.render.BeatBlockLassoOverlay;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.panels.*;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

/**
 * BeatBlock 主 UI 管理器：菜单栏 + Dockspace + 各面板。
 * 布局：顶部菜单栏；底部时间线；左侧工具；右侧事件属性；中间不放置面板（即 Minecraft 场景）；动画库可开关。
 */
public class BeatBlockUIManager {

	private static final String DOCKSPACE_WINDOW_NAME = "BeatBlockDockSpace";
	private static final String DOCKSPACE_ID = "BeatBlockDockSpace";
	// 与 ChronoBlocks 一致：NoBackground + 完全透明，否则整屏一块黑半透明遮挡
	private static final int DOCKSPACE_FLAGS =
		ImGuiWindowFlags.NoTitleBar
			| ImGuiWindowFlags.NoCollapse
			| ImGuiWindowFlags.NoResize
			| ImGuiWindowFlags.NoMove
			| ImGuiWindowFlags.NoBringToFrontOnFocus
			| ImGuiWindowFlags.NoNavFocus
			| ImGuiWindowFlags.NoBackground;

	private final MenuBarPanel menuBarPanel;
	private final AudioAnalysisPanel audioAnalysisPanel;
	private final ToolPanel toolPanel;
	private final EventPropertiesPanel eventPropertiesPanel;
	private final TimelinePanel timelinePanel;
	private final AnimationLibraryPanel animationLibraryPanel;
	private final SelectionPropertiesPanel selectionPropertiesPanel;

	private final BeatBlockPanelVisibility panelVisibility = new BeatBlockPanelVisibility();
	private boolean firstLayout = true;
	private Runnable onCloseRequest;

	public BeatBlockUIManager(Runnable onCloseRequest) {
		this.onCloseRequest = onCloseRequest;
		this.toolPanel = new ToolPanel(this::openSelectionPropertiesForTool);
		this.audioAnalysisPanel = new AudioAnalysisPanel();
		this.menuBarPanel = new MenuBarPanel(onCloseRequest, panelVisibility,
			() -> toolPanel.setShowAutoMapSettings(true), this::resetLayoutState, this::saveCurrentLayout);
		this.eventPropertiesPanel = new EventPropertiesPanel();
		this.timelinePanel = new TimelinePanel();
		this.animationLibraryPanel = new AnimationLibraryPanel();
		this.selectionPropertiesPanel = new SelectionPropertiesPanel();
	}

	public void setOnCloseRequest(Runnable onCloseRequest) {
		this.onCloseRequest = onCloseRequest;
	}

	/** 切换方块选择工具时自动打开「选择属性」面板。 */
	private void openSelectionPropertiesForTool() {
		panelVisibility.selectionProperties.set(true);
	}

	private void saveCurrentLayout() {
		try {
			ImGuiIO io = ImGui.getIO();
			if (io != null) {
				String path = io.getIniFilename();
				if (path != null && !path.isBlank()) {
					ImGui.saveIniSettingsToDisk(path);
				}
			}
		} catch (Throwable ignored) {
		}
	}

	public void render() {
		// 1. 菜单栏（独立于 Dockspace）
		menuBarPanel.render();

		// 2. Dockspace 窗口（与 ChronoBlocks 一致：完全透明 + NoBackground，不遮挡场景）
		imgui.ImGuiViewport viewport = ImGui.getMainViewport();
		ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY());
		ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
		ImGui.setNextWindowViewport(viewport.getID());

		ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
		ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
		ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
		ImGui.pushStyleColor(ImGuiCol.WindowBg, 0f, 0f, 0f, 0f);
		ImGui.pushStyleColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f);
		ImGui.pushStyleColor(ImGuiCol.DockingEmptyBg, 0f, 0f, 0f, 0f);

		int dockspaceId = -1;
		if (ImGui.begin(DOCKSPACE_WINDOW_NAME, DOCKSPACE_FLAGS)) {
			dockspaceId = ImGui.getID(DOCKSPACE_ID);
			ImGui.dockSpace(dockspaceId, 0, 0, imgui.internal.flag.ImGuiDockNodeFlags.PassthruCentralNode);

			if (firstLayout && dockspaceId != -1) {
				BeatBlockDockSpaceLayoutBuilder.buildDefaultLayout(dockspaceId);
				firstLayout = false;
			}
		}
		ImGui.end();

		ImGui.popStyleColor(3);
		ImGui.popStyleVar(3);

		if (dockspaceId == -1) return;

		// 3. 各停靠面板：背景 + 文字色 + 标题栏（参考 ChronoBlocks UITheme，否则面板黑底黑字看不见）
		ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.09f, 0.09f, 0.1f, 1f);           // 深灰背景
		ImGui.pushStyleColor(ImGuiCol.Text, 0.86f, 0.86f, 0.86f, 1f);              // 浅灰文字
		ImGui.pushStyleColor(ImGuiCol.TitleBg, 0.125f, 0.125f, 0.14f, 1f);         // 标题栏
		ImGui.pushStyleColor(ImGuiCol.TitleBgActive, 0.16f, 0.16f, 0.18f, 1f);
		ImGui.pushStyleColor(ImGuiCol.TitleBgCollapsed, 0.11f, 0.11f, 0.12f, 1f);
		audioAnalysisPanel.render(panelVisibility.audioAnalysis);
		toolPanel.render(panelVisibility.tool);
		eventPropertiesPanel.render(panelVisibility.eventProperties);
		timelinePanel.render(panelVisibility.timeline);
		animationLibraryPanel.render(panelVisibility.animationLibrary);
		selectionPropertiesPanel.render(panelVisibility.selectionProperties);
		ImGui.popStyleColor(5);

		BeatBlockLassoOverlay.render();
	}

	public void resetLayoutState() {
		BeatBlockDockSpaceLayoutBuilder.resetLayoutState();
		firstLayout = true;
	}
}
