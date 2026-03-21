package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.project.OscProjectStore;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.nio.file.Path;

/**
 * 顶部通栏菜单栏：文件、编辑、视图、演出、帮助。
 */
public class MenuBarPanel {

	private static final int IMPORT_PATH_CAPACITY = 512;

	private final Runnable onCloseRequest;
	private final Runnable onToggleAnimationLibrary;
	private final Runnable onOpenSmartAutoMap;
	private boolean animationLibraryVisible;
	private boolean showImportDialog;
	private boolean showOpenProjectDialog;
	private boolean showSaveProjectDialog;
	private boolean showAboutDialog;
	private final ImString importPath = new ImString(IMPORT_PATH_CAPACITY);
	private final ImString openProjectPath = new ImString(IMPORT_PATH_CAPACITY);
	private final ImString saveProjectPath = new ImString(IMPORT_PATH_CAPACITY);
	private String projectDialogMessage = "";

	public MenuBarPanel(Runnable onCloseRequest, Runnable onToggleAnimationLibrary, Runnable onOpenSmartAutoMap) {
		this.onCloseRequest = onCloseRequest;
		this.onToggleAnimationLibrary = onToggleAnimationLibrary;
		this.onOpenSmartAutoMap = onOpenSmartAutoMap != null ? onOpenSmartAutoMap : () -> {};
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
			// 文件
			if (ImGui.beginMenu("文件")) {
				if (ImGui.menuItem("打开工程(.osc)", "Ctrl+Shift+O")) {
					showOpenProjectDialog = true;
					projectDialogMessage = "";
					openProjectPath.set("");
				}
				if (ImGui.menuItem("保存工程(.osc)", "Ctrl+S")) {
					showSaveProjectDialog = true;
					projectDialogMessage = "";
					Object p = BeatBlock.timeline != null ? BeatBlock.timeline.getMetadata("projectPath") : null;
					saveProjectPath.set(p == null ? "" : String.valueOf(p));
				}
				ImGui.separator();
				if (ImGui.menuItem("导入音乐", "Ctrl+O")) {
					showImportDialog = true;
					importPath.set("");
				}
				ImGui.separator();
				if (ImGui.menuItem("关闭 BeatBlock", "Esc")) {
					if (onCloseRequest != null) onCloseRequest.run();
				}
				ImGui.endMenu();
			}
			// 编辑
			if (ImGui.beginMenu("编辑")) {
				boolean canUndo = BeatBlock.timelineEditor != null && BeatBlock.timelineEditor.getCommandManager() != null && BeatBlock.timelineEditor.getCommandManager().canUndo();
				boolean canRedo = BeatBlock.timelineEditor != null && BeatBlock.timelineEditor.getCommandManager() != null && BeatBlock.timelineEditor.getCommandManager().canRedo();
				if (ImGui.menuItem("撤销", "Ctrl+Z", false, canUndo)) {
					if (BeatBlock.timelineEditor != null && BeatBlock.timelineEditor.getCommandManager() != null)
						BeatBlock.timelineEditor.getCommandManager().undo();
				}
				if (ImGui.menuItem("重做", "Ctrl+Y", false, canRedo)) {
					if (BeatBlock.timelineEditor != null && BeatBlock.timelineEditor.getCommandManager() != null)
						BeatBlock.timelineEditor.getCommandManager().redo();
				}
				ImGui.endMenu();
			}
			// 视图
			if (ImGui.beginMenu("视图")) {
				if (ImGui.menuItem("动画库", null, animationLibraryVisible)) {
					animationLibraryVisible = !animationLibraryVisible;
					if (onToggleAnimationLibrary != null) onToggleAnimationLibrary.run();
				}
				ImGui.endMenu();
			}
			// 演出
			if (ImGui.beginMenu("演出")) {
				if (ImGui.menuItem("Smart Auto Map...", "自动编排")) {
					if (onOpenSmartAutoMap != null) onOpenSmartAutoMap.run();
				}
				if (ImGui.isItemHovered()) ImGui.setTooltip("根据音乐自动生成方块动画、镜头与粒子");
				ImGui.endMenu();
			}
			// 帮助
			if (ImGui.beginMenu("帮助")) {
				if (ImGui.menuItem("关于 BeatBlock")) {
					showAboutDialog = true;
				}
				ImGui.endMenu();
			}
		} finally {
			ImGui.endMainMenuBar();
		}
		renderImportDialog();
		renderOpenProjectDialog();
		renderSaveProjectDialog();
		renderAboutDialog();
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

	private void renderOpenProjectDialog() {
		if (!showOpenProjectDialog) return;
		ImGui.setNextWindowSize(460, 0);
		if (ImGui.begin("打开工程 (.osc)", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text("工程文件路径（.osc）：");
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##openOscPath", openProjectPath);
			if (ImGui.button("打开")) {
				String p = openProjectPath.get().trim();
				if (p.isEmpty()) {
					projectDialogMessage = "路径不能为空";
				} else if (BeatBlock.timeline == null) {
					projectDialogMessage = "Timeline 不可用";
				} else {
					try {
						OscProjectStore.LoadedProject loaded = OscProjectStore.load(Path.of(p));
						if (!loaded.getTimelineName().isBlank()) {
							BeatBlock.timeline.setName(loaded.getTimelineName());
						}
						BeatBlock.timeline.setMetadata("projectId", loaded.getProjectId());
						BeatBlock.timeline.setMetadata("projectPath", loaded.getProjectPath());
						if (!loaded.getAudioPath().isBlank()) {
							BeatBlock.timeline.setMetadata("audioPath", loaded.getAudioPath());
						}
						BeatBlock.timeline.setMarkers(loaded.getMarkers());
						if (!loaded.getAudioPath().isBlank() && BeatBlock.audioLoader != null) {
							BeatBlock.audioLoader.load(loaded.getAudioPath());
						}
						if (BeatBlock.timelineEditor != null) {
							BeatBlock.timelineEditor.syncClockDuration();
						}
						projectDialogMessage = "工程已打开";
						showOpenProjectDialog = false;
					} catch (Exception e) {
						projectDialogMessage = "打开失败: " + e.getMessage();
					}
				}
			}
			ImGui.sameLine();
			if (ImGui.button("取消##openOsc")) {
				showOpenProjectDialog = false;
			}
			if (!projectDialogMessage.isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(projectDialogMessage);
			}
		}
		ImGui.end();
	}

	private void renderSaveProjectDialog() {
		if (!showSaveProjectDialog) return;
		ImGui.setNextWindowSize(460, 0);
		if (ImGui.begin("保存工程 (.osc)", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text("保存路径（.osc）：");
			ImGui.setNextItemWidth(-1);
			ImGui.inputText("##saveOscPath", saveProjectPath);
			if (ImGui.button("保存")) {
				String p = saveProjectPath.get().trim();
				if (p.isEmpty()) {
					projectDialogMessage = "路径不能为空";
				} else if (BeatBlock.timeline == null) {
					projectDialogMessage = "Timeline 不可用";
				} else {
					try {
						OscProjectStore.save(Path.of(p), BeatBlock.timeline);
						projectDialogMessage = "工程已保存";
						showSaveProjectDialog = false;
					} catch (Exception e) {
						projectDialogMessage = "保存失败: " + e.getMessage();
					}
				}
			}
			ImGui.sameLine();
			if (ImGui.button("取消##saveOsc")) {
				showSaveProjectDialog = false;
			}
			if (!projectDialogMessage.isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(projectDialogMessage);
			}
		}
		ImGui.end();
	}

	private void renderAboutDialog() {
		if (!showAboutDialog) return;
		ImGui.setNextWindowSize(360, 0);
		if (ImGui.begin("关于 BeatBlock", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text("BeatBlock");
			ImGui.text("音乐驱动的 Minecraft 方块动画引擎");
			ImGui.spacing();
			ImGui.textWrapped("导入音乐、分析节拍与频段，在时间线上编排方块动画、镜头与粒子，打造随音乐起舞的视觉演出。");
			ImGui.spacing();
			ImGui.text("基于 Fabric 与 ImGui。");
			ImGui.spacing();
			if (ImGui.button("确定")) {
				showAboutDialog = false;
			}
		}
		ImGui.end();
	}
}
