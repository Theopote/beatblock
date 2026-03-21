package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * 时间线顶部工具栏：播放控制、吸附选项、Beat 网格、Auto Map。
 * 参考专业 DCC（Blender / Unreal Sequencer）的 transport + 吸附条。
 */
public final class TimelineToolbar {

	private static final String TOOLTIP_PLAY = "播放 (空格)";
	private static final String TOOLTIP_PAUSE = "暂停";
	private static final String TOOLTIP_STOP = "停止并回到起点";
	private static final String TOOLTIP_TO_START = "回到开头";
	private static final String TOOLTIP_TO_END = "跳到结尾";
	private static final String TOOLTIP_BACK_BEAT = "后退 1 拍（无 BPM 时后退 1 秒）";
	private static final String TOOLTIP_FWD_BEAT = "前进 1 拍（无 BPM 时前进 1 秒）";
	private static final String TOOLTIP_SNAP = "拖拽事件时吸附到网格";
	private static final String TOOLTIP_BEAT_GRID = "显示节拍网格线";
	private static final String TOOLTIP_MAGNET = "吸附到其他事件/关键帧";
	private static final String TOOLTIP_AUTO_MAP = "根据频段事件自动生成动画事件（需先导入音乐）";
	private static final String TOOLTIP_LOOP = "循环播放";
	private static final String TOOLTIP_FIT = "缩放至整段时长可见";
	private static final String TOOLTIP_ZOOM = "时间线横向缩放";

	/** Zoom 预设：显示名与对应的缩放倍数（相对基准 1x） */
	private static final String[] ZOOM_PRESET_LABELS = { "0.25x", "0.5x", "1x", "2x", "3x", "4x" };
	private static final float ZOOM_BASE = 10f; // 1x 对应的像素/秒
	private static final float[] ZOOM_PRESET_VALUES = { 0.25f * ZOOM_BASE, 0.5f * ZOOM_BASE, ZOOM_BASE, 2f * ZOOM_BASE, 3f * ZOOM_BASE, 4f * ZOOM_BASE };

	/** 上次 Auto Map 生成数量，用于提示 */
	private int lastAutoMapCount = -1;
	/** Zoom 下拉当前选中索引（由 Combo 更新） */
	private final ImInt zoomComboIndex = new ImInt(2); // 默认 1x

	public void render(TimelineEditor editor, TimelineToolbarState toolbarState) {
		if (editor == null) return;

		// ----- 1. 播放控制 -----
		boolean hasMusic = BeatBlock.musicPlayer != null && BeatBlock.timeline != null && BeatBlock.timeline.getDurationSeconds() > 0;
		boolean playing = hasMusic && BeatBlock.musicPlayer.isPlaying();
		double bpm = BeatBlock.timeline != null ? BeatBlock.timeline.getBpm() : 0;
		double seekStep = bpm > 0 ? 60.0 / bpm : 1.0;

		// 图标按钮：与轨道行同高、零内边距，字形尽量铺满并居中
		final float tBtn = TimelineLayout.ROW_HEIGHT;
		IconButtonStyle.pushBeatBlockIconButton();
		if (ImGui.button(Icons.Play.REWIND_START + "##tlToStart", tBtn, tBtn)) {
			seekTo(editor, 0);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TO_START);
		ImGui.sameLine();
		if (ImGui.button(Icons.Play.REWIND + "##tlBackBeat", tBtn, tBtn)) {
			seekBy(editor, -seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BACK_BEAT);
		ImGui.sameLine();
		// 使用 BeatBlock.ttf（Icons），避免 ▶⏸■ 等未进 ImGui 图集显示为 ?
		if (playing) {
			if (ImGui.button(Icons.Play.PAUSE + "##tlPause", tBtn, tBtn)) {
				if (BeatBlock.musicPlayer != null) BeatBlock.musicPlayer.pause();
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_PAUSE);
		} else {
			if (ImGui.button(Icons.Play.PLAY + "##tlPlay", tBtn, tBtn)) {
				if (BeatBlock.musicPlayer != null) {
					BeatBlock.musicPlayer.play();
					// 启动驱动以便每帧推进时间，播放头随音乐移动
					if (!BeatBlockClientDriver.isDriving()) BeatBlockClientDriver.startDriving();
				}
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_PLAY);
		}
		ImGui.sameLine();
		if (ImGui.button(Icons.Play.STOP + "##tlStop", tBtn, tBtn)) {
			if (BeatBlock.musicPlayer != null) BeatBlock.musicPlayer.stop();
			seekTo(editor, 0);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_STOP);
		ImGui.sameLine();
		if (ImGui.button(Icons.Play.FORWARD + "##tlFwdBeat", tBtn, tBtn)) {
			seekBy(editor, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FWD_BEAT);
		ImGui.sameLine();
		if (ImGui.button(Icons.Play.FORWARD_END + "##tlToEnd", tBtn, tBtn)) {
			seekTo(editor, getDuration(editor));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TO_END);
		IconButtonStyle.popBeatBlockIconButton();

		ImGui.sameLine();
		//ImGui.separator();
		ImGui.sameLine();

		// ----- 2. 吸附与网格 -----
		if (toolbarState != null) {
			boolean snap = toolbarState.isSnapToGrid();
			if (ImGui.checkbox("Snap", snap)) {
				toolbarState.setSnapToGrid(!snap);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SNAP);
			ImGui.sameLine();

			boolean beatGrid = toolbarState.isBeatGridVisible();
			if (ImGui.checkbox("Beat Grid", beatGrid)) {
				toolbarState.setBeatGridVisible(!beatGrid);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_GRID);
			ImGui.sameLine();

			boolean magnet = toolbarState.isMagnetSnap();
			if (ImGui.checkbox("Magnet", magnet)) {
				toolbarState.setMagnetSnap(!magnet);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_MAGNET);
			ImGui.sameLine();

			//ImGui.separator();
			ImGui.sameLine();

			boolean loop = toolbarState.isLoop();
			if (ImGui.checkbox("Loop", loop)) {
				toolbarState.setLoop(!loop);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP);
			ImGui.sameLine();
		}

		// ----- 3. 视图：Zoom 下拉 + Fit -----
		zoomComboIndex.set(indexOfClosestZoom(editor.getViewState().getZoom()));
		if (ImGui.combo("Zoom", zoomComboIndex, ZOOM_PRESET_LABELS)) {
			int idx = zoomComboIndex.get();
			if (idx >= 0 && idx < ZOOM_PRESET_VALUES.length) {
				editor.getViewState().setZoom(ZOOM_PRESET_VALUES[idx]);
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ZOOM);
		ImGui.sameLine();
		if (ImGui.button("Fit")) {
			double dur = BeatBlock.timeline != null ? BeatBlock.timeline.getDurationSeconds() : 60;
			float w = ImGui.getContentRegionAvailX() - 130f;
			if (dur > 0 && w > 0) editor.getViewState().fitToDuration(dur, w);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FIT);
		ImGui.sameLine();
		//ImGui.separator();
		ImGui.sameLine();

		// ----- 4. Auto Map -----
		if (ImGui.button("Auto Map")) {
			if (BeatBlock.timeline != null) {
				AutoMapConfig config = AutoMapConfig.createDefault();
				lastAutoMapCount = AutoMapGenerator.generate(BeatBlock.timeline, config, true);
				editor.syncClockDuration();
			} else {
				lastAutoMapCount = -1;
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_AUTO_MAP);
		if (lastAutoMapCount >= 0) {
			ImGui.sameLine();
			ImGui.textDisabled("(" + lastAutoMapCount + " events)");
		}
	}

	private static int indexOfClosestZoom(float zoom) {
		int best = 0;
		float bestDiff = Math.abs(zoom - ZOOM_PRESET_VALUES[0]);
		for (int i = 1; i < ZOOM_PRESET_VALUES.length; i++) {
			float d = Math.abs(zoom - ZOOM_PRESET_VALUES[i]);
			if (d < bestDiff) {
				bestDiff = d;
				best = i;
			}
		}
		return best;
	}

	private static void seekBy(TimelineEditor editor, double deltaSeconds) {
		if (editor == null) return;
		double current = editor.getClock().getCurrentTimeSeconds();
		seekTo(editor, current + deltaSeconds);
	}

	private static void seekTo(TimelineEditor editor, double targetSeconds) {
		if (editor == null) return;
		double duration = getDuration(editor);
		double t = Math.max(0, Math.min(targetSeconds, duration));
		editor.getClock().seek(t);
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setCurrentTimeSeconds(t);
		}
	}

	private static double getDuration(TimelineEditor editor) {
		double timelineDur = BeatBlock.timeline != null ? BeatBlock.timeline.getDurationSeconds() : 0;
		if (timelineDur > 0) return timelineDur;
		double playerDur = BeatBlock.musicPlayer != null ? BeatBlock.musicPlayer.getDurationSeconds() : 0;
		if (playerDur > 0) return playerDur;
		double clockDur = editor.getClock().getDurationSeconds();
		if (clockDur > 0) return clockDur;
		return 60.0;
	}
}
