package com.beatblock.ui.panels;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 顶部通栏菜单栏。
 */
public class MenuBarPanel {

	private final Runnable onCloseRequest;
	private final Runnable onToggleAnimationLibrary;
	private boolean animationLibraryVisible;

	public MenuBarPanel(Runnable onCloseRequest, Runnable onToggleAnimationLibrary) {
		this.onCloseRequest = onCloseRequest;
		this.onToggleAnimationLibrary = onToggleAnimationLibrary;
	}

	public void setAnimationLibraryVisible(boolean visible) {
		animationLibraryVisible = visible;
	}

	public boolean isAnimationLibraryVisible() {
		return animationLibraryVisible;
	}

	public void render() {
		if (!ImGui.beginMainMenuBar()) return;
		try {
			if (ImGui.beginMenu("文件")) {
				if (ImGui.menuItem("关闭", "Esc")) {
					if (onCloseRequest != null) onCloseRequest.run();
				}
				ImGui.endMenu();
			}
			if (ImGui.beginMenu("视图")) {
				if (ImGui.menuItem("动画库", null, animationLibraryVisible)) {
					animationLibraryVisible = !animationLibraryVisible;
					if (onToggleAnimationLibrary != null) onToggleAnimationLibrary.run();
				}
				ImGui.endMenu();
			}
		} finally {
			ImGui.endMainMenuBar();
		}
	}
}
