package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

/**
 * 顶部通栏菜单栏。文件 → 导入音乐：弹出路径输入，加载 WAV 并写波形 + 频段到时间线。
 */
public class MenuBarPanel {

	private static final int IMPORT_PATH_CAPACITY = 512;

	private final Runnable onCloseRequest;
	private final Runnable onToggleAnimationLibrary;
	private boolean animationLibraryVisible;
	private boolean showImportDialog;
	private final ImString importPath = new ImString(IMPORT_PATH_CAPACITY);

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
				if (ImGui.menuItem("导入音乐", "WAV 路径")) {
					showImportDialog = true;
					importPath.set("");
				}
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
		renderImportDialog();
	}

	private void renderImportDialog() {
		if (!showImportDialog) return;
		ImGui.setNextWindowSize(400, 0);
		if (ImGui.begin("导入音乐", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text("WAV 文件路径（本地绝对路径）：");
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##path", importPath);
			if (ImGui.button("导入")) {
				String path = importPath.get().trim();
				if (!path.isEmpty() && BeatBlock.audioLoader != null) {
					boolean ok = BeatBlock.audioLoader.load(path);
					if (ok) showImportDialog = false;
				}
			}
			ImGui.sameLine();
			if (ImGui.button("取消")) {
				showImportDialog = false;
			}
		}
		ImGui.end();
	}
}
