package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * 左侧工具面板。提供 Smart Auto Map：点击后弹出设置（风格/复杂度/镜头/粒子），生成完整编排。
 */
public class ToolPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private boolean showAutoMapSettings = false;
	private final AutoMapSettingsPanel autoMapSettingsPanel = new AutoMapSettingsPanel();
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

		ImGui.spacing();
		ImGui.textWrapped("选择、画笔、橡皮等工具将在此列出。");
		ImGui.end();

		// 设置弹窗（独立窗口）
		if (showAutoMapSettings) {
			boolean done = autoMapSettingsPanel.render(res -> lastAutoMapResult = res);
			if (done) showAutoMapSettings = false;
		}
	}
}
