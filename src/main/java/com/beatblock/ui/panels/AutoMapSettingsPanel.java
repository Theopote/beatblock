package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.function.Consumer;

/**
 * Smart Auto-Map 设置弹窗：风格、复杂度、镜头/粒子开关，点击 Generate 执行编排并关闭。
 */
public final class AutoMapSettingsPanel {

	private static final String WINDOW_TITLE = "Auto Map Settings";
	private static final String[] STYLE_LABELS = { "EDM", "Cinematic", "Ambient", "Chaos", "Minimal" };
	private static final String[] COMPLEXITY_LABELS = { "Low", "Medium", "High", "Extreme" };

	private final AutoMapSettings settings = new AutoMapSettings();
	private final ImInt styleIndex = new ImInt(0);
	private final ImInt complexityIndex = new ImInt(1);

	/**
	 * 渲染弹窗。若返回 true 表示已执行生成并关闭，onResult 已被调用。
	 */
	public boolean render(Consumer<SmartAutoMapEngine.AutoMapResult> onResult) {
		if (!ImGui.begin(WINDOW_TITLE, ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.end();
			return false;
		}
		ImGui.text("根据音乐分析自动生成：方块动画、摄像机、粒子与节奏结构。");
		ImGui.spacing();

		// Animation Style
		ImGui.text("Animation Style");
		if (ImGui.combo("##style", styleIndex, STYLE_LABELS)) {
			int i = Math.max(0, Math.min(styleIndex.get(), AutoMapStyle.values().length - 1));
			settings.setStyle(AutoMapStyle.values()[i]);
		}
		ImGui.sameLine();
		if (ImGui.isItemHovered()) ImGui.setTooltip("EDM=强节拍/爆炸感, Cinematic=慢镜头/波浪, Ambient=柔和, Chaos=密集, Minimal=稀疏");

		// Complexity
		ImGui.text("Complexity");
		if (ImGui.combo("##complexity", complexityIndex, COMPLEXITY_LABELS)) {
			int i = Math.max(0, Math.min(complexityIndex.get(), Complexity.values().length - 1));
			settings.setComplexity(Complexity.values()[i]);
		}
		ImGui.sameLine();
		if (ImGui.isItemHovered()) ImGui.setTooltip("Low=少量事件, Extreme=最密");

		// Camera / Particles
		boolean cam = settings.isCameraEnabled();
		if (ImGui.checkbox("Camera", cam)) settings.setCameraEnabled(!cam);
		if (ImGui.isItemHovered()) ImGui.setTooltip("根据段落自动插入镜头关键帧");
		ImGui.sameLine();
		boolean part = settings.isParticlesEnabled();
		if (ImGui.checkbox("Particles", part)) settings.setParticlesEnabled(!part);
		if (ImGui.isItemHovered()) ImGui.setTooltip("根据高频能量生成粒子事件");

		ImGui.spacing();
		ImGui.separator();
		ImGui.spacing();

		// Sync settings from combo (in case user changed without firing callback)
		settings.setStyle(AutoMapStyle.values()[Math.max(0, Math.min(styleIndex.get(), AutoMapStyle.values().length - 1))]);
		settings.setComplexity(Complexity.values()[Math.max(0, Math.min(complexityIndex.get(), Complexity.values().length - 1))]);

		boolean generated = false;
		if (ImGui.button("Generate", 120, 0)) {
			AudioFeatureTimeline feature = BeatBlock.audioAnalysisEngine != null ? BeatBlock.audioAnalysisEngine.getLastFeatureTimeline() : null;
			Timeline timeline = BeatBlock.timeline;
			if (feature == null) {
				ImGui.setTooltip("请先导入音乐以进行分析");
			} else if (timeline != null) {
				SmartAutoMapEngine.AutoMapResult res = SmartAutoMapEngine.generate(feature, settings, timeline);
				if (BeatBlock.timelineEditor != null) BeatBlock.timelineEditor.syncClockDuration();
				if (onResult != null) onResult.accept(res);
				generated = true;
			}
		}
		if (ImGui.isItemHovered() && BeatBlock.audioAnalysisEngine != null && BeatBlock.audioAnalysisEngine.getLastFeatureTimeline() == null) {
			ImGui.setTooltip("请先通过菜单导入音乐");
		}

		ImGui.end();
		return generated;
	}

	public AutoMapSettings getSettings() {
		return settings;
	}

	public void setStyleIndex(int index) {
		styleIndex.set(Math.max(0, Math.min(index, AutoMapStyle.values().length - 1)));
		settings.setStyle(AutoMapStyle.values()[styleIndex.get()]);
	}

	public void setComplexityIndex(int index) {
		complexityIndex.set(Math.max(0, Math.min(index, Complexity.values().length - 1)));
		settings.setComplexity(Complexity.values()[complexityIndex.get()]);
	}
}
