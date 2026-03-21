package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.Track;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
	private static final String TOOLTIP_BACK_5S = "后退 5 秒";
	private static final String TOOLTIP_FWD_5S = "前进 5 秒";
	private static final String TOOLTIP_PREV_EVENT = "跳到上一事件点";
	private static final String TOOLTIP_NEXT_EVENT = "跳到下一事件点";
	private static final String TOOLTIP_ADD_MARKER = "在当前时间创建 Marker";
	private static final String TOOLTIP_LOOP_IN = "将当前时间设为循环起点；也可 Alt+左键点击标尺";
	private static final String TOOLTIP_LOOP_OUT = "将当前时间设为循环终点；也可 Alt+右键点击标尺";
	private static final String TOOLTIP_LOOP_CLEAR = "清除循环区间（保留 Loop 开关）";
	private static final String TOOLTIP_SPEED = "播放速度";
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
	private static final String[] SPEED_LABELS = { "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x" };
	private static final double[] SPEED_VALUES = { 0.5, 0.75, 1.0, 1.25, 1.5, 2.0 };

	/** 上次 Auto Map 生成数量，用于提示 */
	private int lastAutoMapCount = -1;
	/** Zoom 下拉当前选中索引（由 Combo 更新） */
	private final ImInt zoomComboIndex = new ImInt(2); // 默认 1x
	private final ImInt speedComboIndex = new ImInt(2); // 默认 1x

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
		if (ImGui.button("-5##tlBack5", tBtn + 8f, tBtn)) {
			seekBy(editor, -5.0);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BACK_5S);
		ImGui.sameLine();
		if (ImGui.button(Icons.Action.ARROW_LEFT + "##tlPrevEvt", tBtn, tBtn)) {
			jumpToNearbyEvent(editor, false);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_PREV_EVENT);
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
		if (ImGui.button("+5##tlFwd5", tBtn + 8f, tBtn)) {
			seekBy(editor, 5.0);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FWD_5S);
		ImGui.sameLine();
		if (ImGui.button(Icons.Action.ARROW_RIGHT + "##tlNextEvt", tBtn, tBtn)) {
			jumpToNearbyEvent(editor, true);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_NEXT_EVENT);
		ImGui.sameLine();
		if (ImGui.button(Icons.Play.FORWARD_END + "##tlToEnd", tBtn, tBtn)) {
			seekTo(editor, getDuration(editor));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TO_END);
		ImGui.sameLine();
		if (ImGui.button(Icons.Timeline.MARKER + "##tlAddMarker", tBtn, tBtn)) {
			addMarkerAtCurrentTime(editor);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ADD_MARKER);
		IconButtonStyle.popBeatBlockIconButton();

		ImGui.sameLine();
		//ImGui.separator();
		ImGui.sameLine();

		// ----- 1.5 循环区（In/Out）与速度 -----
		double now = editor.getClock().getCurrentTimeSeconds();
		if (ImGui.button("In")) {
			toolbarState.setLoopInSeconds(now);
			if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= now) {
				toolbarState.setLoopOutSeconds(now + Math.max(0.1, seekStep));
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_IN);
		ImGui.sameLine();
		if (ImGui.button("Out")) {
			double loopIn = toolbarState.getLoopInSeconds();
			toolbarState.setLoopOutSeconds(Math.max(now, loopIn + Math.max(0.1, seekStep)));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_OUT);
		ImGui.sameLine();
		if (ImGui.button("Clr")) {
			toolbarState.clearLoopRange();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_CLEAR);
		ImGui.sameLine();

		double currentSpeed = BeatBlock.musicPlayer != null ? BeatBlock.musicPlayer.getPlaybackSpeed() : editor.getClock().getPlaybackSpeed();
		speedComboIndex.set(indexOfClosestSpeed(currentSpeed));
		if (ImGui.combo("Speed", speedComboIndex, SPEED_LABELS)) {
			int sIdx = speedComboIndex.get();
			if (sIdx >= 0 && sIdx < SPEED_VALUES.length) {
				double speed = SPEED_VALUES[sIdx];
				editor.getClock().setPlaybackSpeed(speed);
				if (BeatBlock.musicPlayer != null) {
					BeatBlock.musicPlayer.setPlaybackSpeed(speed);
				}
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SPEED);
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

	private static int indexOfClosestSpeed(double speed) {
		int best = 0;
		double bestDiff = Math.abs(speed - SPEED_VALUES[0]);
		for (int i = 1; i < SPEED_VALUES.length; i++) {
			double d = Math.abs(speed - SPEED_VALUES[i]);
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

	private static void jumpToNearbyEvent(TimelineEditor editor, boolean forward) {
		if (editor == null || BeatBlock.timeline == null) return;
		List<Double> marks = collectNavigationTimes(BeatBlock.timeline);
		if (marks.isEmpty()) return;

		double current = editor.getClock().getCurrentTimeSeconds();
		double eps = 1e-6;
		double target = current;
		if (forward) {
			for (double t : marks) {
				if (t > current + eps) {
					target = t;
					break;
				}
			}
		} else {
			for (int i = marks.size() - 1; i >= 0; i--) {
				double t = marks.get(i);
				if (t < current - eps) {
					target = t;
					break;
				}
			}
		}
		seekTo(editor, target);
	}

	private static void addMarkerAtCurrentTime(TimelineEditor editor) {
		if (editor == null || BeatBlock.timeline == null) return;
		double t = editor.getClock().getCurrentTimeSeconds();
		int markerIndex = BeatBlock.timeline.getMarkers().size() + 1;
		BeatBlock.timeline.addMarker(new TimelineMarker(t, "Marker " + markerIndex));
	}

	private static List<Double> collectNavigationTimes(Timeline timeline) {
		if (timeline == null) return List.of();
		if (!timeline.getMarkers().isEmpty()) {
			List<Double> out = new ArrayList<>();
			for (TimelineMarker marker : timeline.getMarkers()) {
				if (marker != null) out.add(marker.getTimeSeconds());
			}
			Collections.sort(out);
			return out;
		}
		return collectEventTimes(timeline);
	}

	private static List<Double> collectEventTimes(Timeline timeline) {
		if (timeline == null) return List.of();
		List<Double> out = new ArrayList<>();
		for (Track track : timeline.getTracks()) {
			if (track == null) continue;
			for (Clip clip : track.getClips()) {
				if (clip == null) continue;
				for (TimelineEvent e : clip.getEvents()) {
					if (e == null) continue;
					out.add(e.getTimeSeconds());
				}
			}
		}
		Collections.sort(out);
		return out;
	}
}
