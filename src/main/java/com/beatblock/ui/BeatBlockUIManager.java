package com.beatblock.ui;

import com.beatblock.client.export.VideoExportCoordinator;
import com.beatblock.client.render.BeatBlockLassoOverlay;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.panels.*;
import com.beatblock.ui.preferences.BeatBlockShortcutHandler;
import com.beatblock.ui.preferences.UiPreferences;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiViewport;
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
	private final MarkerPanel markerPanel;
	private final EventPropertiesPanel eventPropertiesPanel;
	private final CameraPropertiesPanel cameraPropertiesPanel;
	private final TimelinePanel timelinePanel;
	private final AnimationLibraryPanel animationLibraryPanel;
	private final SelectionPropertiesPanel selectionPropertiesPanel;
	private final LayerPanel layerPanel;
	private final RhythmDropPanel rhythmDropPanel;
	private final QuickStartWizardPanel quickStartWizardPanel;
	private final UndoHistoryPanel undoHistoryPanel;
	private final EventLibraryPanel eventLibraryPanel;
	private final PerformanceMonitorPanel performanceMonitorPanel;
	private final PreferencesPanel preferencesPanel;
	private final VideoExportDialog videoExportDialog;

	private final BeatBlockPanelVisibility panelVisibility = new BeatBlockPanelVisibility();
	private boolean firstLayout = true;
	private Runnable onCloseRequest;

	public BeatBlockUIManager(Runnable onCloseRequest) {
		this.onCloseRequest = onCloseRequest;
		this.toolPanel = new ToolPanel();
		this.markerPanel = new MarkerPanel();
		this.audioAnalysisPanel = new AudioAnalysisPanel();
		this.menuBarPanel = new MenuBarPanel(onCloseRequest, panelVisibility,
			() -> toolPanel.setShowAutoMapSettings(true),
			this::generateRhythmDropFromMenu,
			this::resetLayoutState, this::saveCurrentLayout, this::loadSavedLayout,
			this::openQuickStartWizard, this::openVideoExportDialog);
		this.eventPropertiesPanel = new EventPropertiesPanel();
		this.cameraPropertiesPanel = new CameraPropertiesPanel();
		this.timelinePanel = new TimelinePanel();
		this.animationLibraryPanel = new AnimationLibraryPanel();
		this.selectionPropertiesPanel = new SelectionPropertiesPanel();
		this.layerPanel = new LayerPanel();
		this.rhythmDropPanel = new RhythmDropPanel();
		this.quickStartWizardPanel = new QuickStartWizardPanel();
		this.undoHistoryPanel = new UndoHistoryPanel();
		this.eventLibraryPanel = new EventLibraryPanel();
		this.performanceMonitorPanel = new PerformanceMonitorPanel();
		this.preferencesPanel = new PreferencesPanel();
		this.videoExportDialog = new VideoExportDialog();
	}

	public void openQuickStartWizard() {
		quickStartWizardPanel.open();
	}

	public void openVideoExportDialog() {
		videoExportDialog.open();
	}

	public void setOnCloseRequest(Runnable onCloseRequest) {
		this.onCloseRequest = onCloseRequest;
	}

	private void generateRhythmDropFromMenu() {
		var result = PresenterFactories.rhythmDropPanelPresenter().generateFromSelectionWithDefaults();
		BeatBlockSelectionManager.get().setMessage(result.messageOrEmpty());
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

	private void loadSavedLayout() {
		try {
			ImGuiIO io = ImGui.getIO();
			if (io != null) {
				String path = io.getIniFilename();
				if (path != null && !path.isBlank()) {
					ImGui.loadIniSettingsFromDisk(path);
				}
			}
		} catch (Throwable ignored) {
		}
	}

	public void render() {
		BeatBlockShortcutHandler.processGlobalShortcuts();

		if (VideoExportCoordinator.getInstance().shouldHideEditorChrome()) {
			videoExportDialog.render();
			return;
		}

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

		// 3. 各停靠面板：主题色
		UiPreferences.pushPanelThemeColors();
		audioAnalysisPanel.render(panelVisibility.audioAnalysis);
		toolPanel.render(panelVisibility.tool);
		markerPanel.render(panelVisibility.marker);
		eventPropertiesPanel.render(panelVisibility.eventProperties);
		cameraPropertiesPanel.render(panelVisibility.cameraProperties);
		timelinePanel.render(panelVisibility.timeline);
		animationLibraryPanel.render(panelVisibility.animationLibrary);
		selectionPropertiesPanel.render(panelVisibility.selectionProperties);
		layerPanel.render(panelVisibility.layer);
		rhythmDropPanel.render(panelVisibility.rhythmDrop);
		undoHistoryPanel.render(panelVisibility.undoHistory);
		eventLibraryPanel.render(panelVisibility.eventLibrary);
		performanceMonitorPanel.render(panelVisibility.performanceMonitor);
		preferencesPanel.render(panelVisibility.preferences);
		UiPreferences.popPanelThemeColors();

		quickStartWizardPanel.render();
		videoExportDialog.render();
		BeatBlockLassoOverlay.render();
		ToastNotificationSystem.render();
	}

	public void resetLayoutState() {
		BeatBlockDockSpaceLayoutBuilder.resetLayoutState();
		firstLayout = true;
	}
}
