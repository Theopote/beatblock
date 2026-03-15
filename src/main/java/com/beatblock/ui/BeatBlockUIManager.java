package com.beatblock.ui;

import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.panels.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BeatBlock 主 UI 管理器：菜单栏 + Dockspace + 各面板。
 * 布局：顶部菜单栏；底部时间线；左侧工具；右侧事件属性；中间场景；动画库可开关。
 */
public class BeatBlockUIManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockUIManager.class);
	private static final String DOCKSPACE_WINDOW_NAME = "BeatBlockDockSpace";
	private static final String DOCKSPACE_ID = "BeatBlockDockSpace";
	private static final int DOCKSPACE_FLAGS =
		ImGuiWindowFlags.NoTitleBar
			| ImGuiWindowFlags.NoCollapse
			| ImGuiWindowFlags.NoResize
			| ImGuiWindowFlags.NoMove
			| ImGuiWindowFlags.NoBringToFrontOnFocus
			| ImGuiWindowFlags.NoNavFocus
			| ImGuiWindowFlags.NoBackground;

	private final MenuBarPanel menuBarPanel;
	private final ToolPanel toolPanel;
	private final EventPropertiesPanel eventPropertiesPanel;
	private final TimelinePanel timelinePanel;
	private final CentralViewPanel centralViewPanel;
	private final AnimationLibraryPanel animationLibraryPanel;

	private boolean animationLibraryVisible = false;
	private boolean firstLayout = true;
	private Runnable onCloseRequest;

	public BeatBlockUIManager(Runnable onCloseRequest) {
		this.onCloseRequest = onCloseRequest;
		this.menuBarPanel = new MenuBarPanel(onCloseRequest, this::toggleAnimationLibrary);
		this.toolPanel = new ToolPanel();
		this.eventPropertiesPanel = new EventPropertiesPanel();
		this.timelinePanel = new TimelinePanel();
		this.centralViewPanel = new CentralViewPanel();
		this.animationLibraryPanel = new AnimationLibraryPanel();
	}

	public void setOnCloseRequest(Runnable onCloseRequest) {
		this.onCloseRequest = onCloseRequest;
	}

	private void toggleAnimationLibrary() {
		animationLibraryVisible = !animationLibraryVisible;
		menuBarPanel.setAnimationLibraryVisible(animationLibraryVisible);
	}

	public void render() {
		// 1. 菜单栏（独立于 Dockspace）
		menuBarPanel.render();

		// 2. Dockspace 窗口（全屏透明，仅提供停靠区域）
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
			// PassthruCentralNode：中央不捕获鼠标，可看到并操作场景
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

		// 3. 各停靠面板
		toolPanel.render();
		eventPropertiesPanel.render();
		timelinePanel.render();
		centralViewPanel.render();

		// 4. 动画库（可选，可从菜单打开/关闭）
		if (animationLibraryVisible) {
			animationLibraryPanel.render();
		}
	}

	public void resetLayoutState() {
		BeatBlockDockSpaceLayoutBuilder.resetLayoutState();
		firstLayout = true;
	}
}
