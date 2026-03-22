package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.Clip;
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
	private static final String TOOLTIP_BACK_BEAT = "后退 1 拍（无 BPM 时后退 1 秒）；按住 Shift 后退 5 秒";
	private static final String TOOLTIP_FWD_BEAT = "前进 1 拍（无 BPM 时前进 1 秒）；按住 Shift 前进 5 秒";
	private static final String TOOLTIP_PREV_EVENT = "跳到上一事件点";
	private static final String TOOLTIP_NEXT_EVENT = "跳到下一事件点";
	private static final String TOOLTIP_ADD_MARKER = "在当前时间创建 Marker；也可双击标尺空白处";
	private static final String TOOLTIP_LOOP_IN = "将当前时间设为循环起点；也可 Alt+左键点击标尺";
	private static final String TOOLTIP_LOOP_OUT = "将当前时间设为循环终点；也可 Alt+右键点击标尺";
	private static final String TOOLTIP_LOOP_CLEAR = "清除循环区间（保留 Loop 开关）";
	private static final String TOOLTIP_SPEED = "播放速度";
	private static final String TOOLTIP_SNAP = "拖拽事件时吸附到网格";
	private static final String TOOLTIP_BEAT_SNAP = "拖拽事件时吸附到节拍";
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
	private static final float TOOLBAR_ITEM_SPACING = 4f;
	private static final float TOOLBAR_GROUP_SPACING = 8f;

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
		double stepSeek = ImGui.getIO().getKeyShift() ? 5.0 : seekStep;

		// 图标按钮：与轨道行同高、零内边距，字形尽量铺满并居中
		final float tBtn = TimelineLayout.ROW_HEIGHT;
		boolean compactToolbar = shouldUseCompactToolbar(tBtn);
		String transportTooltip = null;
		IconButtonStyle.pushBeatBlockIconButton();
		if (ImGui.button(Icons.Play.REWIND_START + "##tlToStart", tBtn, tBtn)) {
			seekTo(editor, 0);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_TO_START);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.REWIND + "##tlBackBeat", tBtn, tBtn)) {
			seekBy(editor, -stepSeek);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_BACK_BEAT);
		nextItemInGroup();
		// 使用 BeatBlock.ttf（Icons），避免 ▶⏸■ 等未进 ImGui 图集显示为 ?
		if (playing) {
			if (ImGui.button(Icons.Play.PAUSE + "##tlPause", tBtn, tBtn)) {
				if (BeatBlock.musicPlayer != null) BeatBlock.musicPlayer.pause();
				// 同时暂停时间线时钟
				if (BeatBlock.timelineEditor != null) {
					BeatBlock.timelineEditor.getClock().pause();
				}
			}
			transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PAUSE);
		} else {
			if (ImGui.button(Icons.Play.PLAY + "##tlPlay", tBtn, tBtn)) {
				if (BeatBlock.musicPlayer != null) {
					BeatBlock.musicPlayer.play();
					// 启动驱动以便每帧推进时间，播放头随音乐移动
					if (!BeatBlockClientDriver.isDriving()) BeatBlockClientDriver.startDriving();
				}
				// 同时启动时间线时钟
				if (BeatBlock.timelineEditor != null) {
					BeatBlock.timelineEditor.getClock().play();
				}
			}
			transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PLAY);
		}
		nextItemInGroup();
		if (ImGui.button(Icons.Play.STOP + "##tlStop", tBtn, tBtn)) {
			if (BeatBlock.musicPlayer != null) BeatBlock.musicPlayer.stop();
			// 同时暂停和重置时间线时钟
			if (BeatBlock.timelineEditor != null) {
				BeatBlock.timelineEditor.getClock().pause();
			}
			seekTo(editor, 0);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_STOP);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.FORWARD + "##tlFwdBeat", tBtn, tBtn)) {
			seekBy(editor, stepSeek);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_FWD_BEAT);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.FORWARD_END + "##tlToEnd", tBtn, tBtn)) {
			seekTo(editor, getDuration(editor));
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_TO_END);
		nextGroup();
		if (ImGui.button(Icons.Action.ARROW_LEFT + "##tlPrevEvt", tBtn, tBtn)) {
			jumpToNearbyEvent(editor, false);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PREV_EVENT);
		nextItemInGroup();
		if (ImGui.button(Icons.Action.ARROW_RIGHT + "##tlNextEvt", tBtn, tBtn)) {
			jumpToNearbyEvent(editor, true);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_NEXT_EVENT);
		nextItemInGroup();
		if (ImGui.button(Icons.Timeline.MARKER + "##tlAddMarker", tBtn, tBtn)) {
			addMarkerAtCurrentTime(editor);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_ADD_MARKER);
		IconButtonStyle.popBeatBlockIconButton();
		if (transportTooltip != null) {
			ImGui.setTooltip(transportTooltip);
		}

		if (compactToolbar) {
			nextGroup();
			renderOverflowMenu(editor, toolbarState, seekStep);
			return;
		}

		nextGroup();

		// ----- 1.5 循环区（In/Out）与速度 -----
		double now = editor.getClock().getCurrentTimeSeconds();
		if (ImGui.button("In")) {
			toolbarState.setLoopInSeconds(now);
			if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= now) {
				toolbarState.setLoopOutSeconds(now + Math.max(0.1, seekStep));
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_IN);
		nextItemInGroup();
		if (ImGui.button("Out")) {
			double loopIn = toolbarState.getLoopInSeconds();
			toolbarState.setLoopOutSeconds(Math.max(now, loopIn + Math.max(0.1, seekStep)));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_OUT);
		nextItemInGroup();
		if (ImGui.button("Clr")) {
			toolbarState.clearLoopRange();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_CLEAR);
		nextItemInGroup();

		double currentSpeed = BeatBlock.musicPlayer != null ? BeatBlock.musicPlayer.getPlaybackSpeed() : editor.getClock().getPlaybackSpeed();
		speedComboIndex.set(indexOfClosestSpeed(currentSpeed));
		ImGui.setNextItemWidth(comboWidthForLabels(SPEED_LABELS));
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
		nextGroup();

		// ----- 2. 吸附与网格 -----
		if (toolbarState != null) {
			boolean snap = toolbarState.isSnapToGrid();
			if (ImGui.checkbox("Snap", snap)) {
				toolbarState.setSnapToGrid(!snap);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SNAP);
			nextItemInGroup();

			boolean beatSnap = toolbarState.isSnapToBeat();
			if (ImGui.checkbox("Beat Snap", beatSnap)) {
				toolbarState.setSnapToBeat(!beatSnap);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_SNAP);
			nextItemInGroup();

			boolean beatGrid = toolbarState.isBeatGridVisible();
			if (ImGui.checkbox("Beat Grid", beatGrid)) {
				toolbarState.setBeatGridVisible(!beatGrid);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_GRID);
			nextItemInGroup();

			boolean magnet = toolbarState.isMagnetSnap();
			if (ImGui.checkbox("Magnet", magnet)) {
				toolbarState.setMagnetSnap(!magnet);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_MAGNET);
			nextGroup();

			boolean loop = toolbarState.isLoop();
			if (ImGui.checkbox("Loop", loop)) {
				toolbarState.setLoop(!loop);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP);
			nextGroup();
		}

		// ----- 3. 视图：Zoom 下拉 + Fit -----
		zoomComboIndex.set(indexOfClosestZoom(editor.getViewState().getZoom()));
		ImGui.setNextItemWidth(comboWidthForLabels(ZOOM_PRESET_LABELS));
		if (ImGui.combo("Zoom", zoomComboIndex, ZOOM_PRESET_LABELS)) {
			int idx = zoomComboIndex.get();
			if (idx >= 0 && idx < ZOOM_PRESET_VALUES.length) {
				editor.getViewState().setZoom(ZOOM_PRESET_VALUES[idx]);
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ZOOM);
		nextItemInGroup();
		if (ImGui.button("Fit")) {
			double dur = BeatBlock.timeline != null ? BeatBlock.timeline.getDurationSeconds() : 60;
			float w = ImGui.getContentRegionAvailX() - 130f;
			if (dur > 0 && w > 0) editor.getViewState().fitToDuration(dur, w);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FIT);
		nextGroup();

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
			nextItemInGroup();
			ImGui.textDisabled("(" + lastAutoMapCount + " events)");
		}
	}

	private void renderOverflowMenu(TimelineEditor editor, TimelineToolbarState toolbarState, double seekStep) {
		if (ImGui.button("More##tlMore")) {
			ImGui.openPopup("tlMorePopup");
		}
		if (!ImGui.beginPopup("tlMorePopup")) return;

		double now = editor.getClock().getCurrentTimeSeconds();
		ImGui.textDisabled("Loop & Speed");
		if (ImGui.button("In##tlMoreIn")) {
			toolbarState.setLoopInSeconds(now);
			if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= now) {
				toolbarState.setLoopOutSeconds(now + Math.max(0.1, seekStep));
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_IN);
		ImGui.sameLine();
		if (ImGui.button("Out##tlMoreOut")) {
			double loopIn = toolbarState.getLoopInSeconds();
			toolbarState.setLoopOutSeconds(Math.max(now, loopIn + Math.max(0.1, seekStep)));
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_OUT);
		ImGui.sameLine();
		if (ImGui.button("Clr##tlMoreClr")) {
			toolbarState.clearLoopRange();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_CLEAR);

		double currentSpeed = BeatBlock.musicPlayer != null ? BeatBlock.musicPlayer.getPlaybackSpeed() : editor.getClock().getPlaybackSpeed();
		speedComboIndex.set(indexOfClosestSpeed(currentSpeed));
		ImGui.setNextItemWidth(comboWidthForLabels(SPEED_LABELS));
		if (ImGui.combo("Speed##tlMoreSpeed", speedComboIndex, SPEED_LABELS)) {
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

		ImGui.separator();
		ImGui.textDisabled("Snap & Grid");
		boolean snap = toolbarState.isSnapToGrid();
		if (ImGui.checkbox("Snap##tlMoreSnap", snap)) {
			toolbarState.setSnapToGrid(!snap);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SNAP);

		boolean beatSnap = toolbarState.isSnapToBeat();
		if (ImGui.checkbox("Beat Snap##tlMoreBeatSnap", beatSnap)) {
			toolbarState.setSnapToBeat(!beatSnap);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_SNAP);

		boolean beatGrid = toolbarState.isBeatGridVisible();
		if (ImGui.checkbox("Beat Grid##tlMoreBeatGrid", beatGrid)) {
			toolbarState.setBeatGridVisible(!beatGrid);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_GRID);

		boolean magnet = toolbarState.isMagnetSnap();
		if (ImGui.checkbox("Magnet##tlMoreMagnet", magnet)) {
			toolbarState.setMagnetSnap(!magnet);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_MAGNET);

		boolean loop = toolbarState.isLoop();
		if (ImGui.checkbox("Loop##tlMoreLoop", loop)) {
			toolbarState.setLoop(!loop);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP);

		ImGui.separator();
		ImGui.textDisabled("View");
		zoomComboIndex.set(indexOfClosestZoom(editor.getViewState().getZoom()));
		ImGui.setNextItemWidth(comboWidthForLabels(ZOOM_PRESET_LABELS));
		if (ImGui.combo("Zoom##tlMoreZoom", zoomComboIndex, ZOOM_PRESET_LABELS)) {
			int idx = zoomComboIndex.get();
			if (idx >= 0 && idx < ZOOM_PRESET_VALUES.length) {
				editor.getViewState().setZoom(ZOOM_PRESET_VALUES[idx]);
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ZOOM);
		if (ImGui.button("Fit##tlMoreFit")) {
			double dur = BeatBlock.timeline != null ? BeatBlock.timeline.getDurationSeconds() : 60;
			float w = ImGui.getContentRegionAvailX() - 16f;
			if (dur > 0 && w > 0) editor.getViewState().fitToDuration(dur, w);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FIT);

		ImGui.separator();
		ImGui.textDisabled("Tools");
		if (ImGui.button("Auto Map##tlMoreAutoMap")) {
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

		ImGui.endPopup();
	}

	private static boolean shouldUseCompactToolbar(float tBtn) {
		float availableWidth = ImGui.getContentRegionAvailX();
		float requiredWidth = estimateExpandedToolbarWidth(tBtn);
		return availableWidth < requiredWidth;
	}

	private static float estimateExpandedToolbarWidth(float tBtn) {
		float transportWidth = estimateTransportWidth(tBtn);
		float loopAndSpeedWidth = buttonWidth("In") + TOOLBAR_ITEM_SPACING
			+ buttonWidth("Out") + TOOLBAR_ITEM_SPACING
			+ buttonWidth("Clr") + TOOLBAR_ITEM_SPACING
			+ comboTotalWidth("Speed", SPEED_LABELS);

		float snapGroupWidth = checkboxWidth("Snap") + TOOLBAR_ITEM_SPACING
			+ checkboxWidth("Beat Snap") + TOOLBAR_ITEM_SPACING
			+ checkboxWidth("Beat Grid") + TOOLBAR_ITEM_SPACING
			+ checkboxWidth("Magnet") + TOOLBAR_GROUP_SPACING
			+ checkboxWidth("Loop");

		float viewGroupWidth = comboTotalWidth("Zoom", ZOOM_PRESET_LABELS) + TOOLBAR_ITEM_SPACING + buttonWidth("Fit");
		float autoMapWidth = buttonWidth("Auto Map") + 70f;

		return transportWidth
			+ TOOLBAR_GROUP_SPACING
			+ loopAndSpeedWidth
			+ TOOLBAR_GROUP_SPACING
			+ snapGroupWidth
			+ TOOLBAR_GROUP_SPACING
			+ viewGroupWidth
			+ TOOLBAR_GROUP_SPACING
			+ autoMapWidth;
	}

	private static float estimateTransportWidth(float tBtn) {
		// to-start, back-step, prev-event, play/pause, stop, fwd-step, next-event, to-end, add-marker
		float buttonSum = tBtn * 9f;
		return buttonSum + TOOLBAR_ITEM_SPACING * 8f;
	}

	private static float buttonWidth(String label) {
		return ImGui.calcTextSize(label).x + 18f;
	}

	private static float checkboxWidth(String label) {
		return ImGui.calcTextSize(label).x + 28f;
	}

	private static float comboTotalWidth(String label, String[] values) {
		return comboWidthForLabels(values) + ImGui.calcTextSize(label).x + 10f;
	}

	private static void nextItemInGroup() {
		ImGui.sameLine(0f, TOOLBAR_ITEM_SPACING);
	}

	private static void nextGroup() {
		ImGui.sameLine(0f, TOOLBAR_GROUP_SPACING);
	}

	private static float comboWidthForLabels(String[] labels) {
		float maxText = 0f;
		for (String label : labels) {
			if (label == null) continue;
			maxText = Math.max(maxText, ImGui.calcTextSize(label).x);
		}
		return maxText + 40f;
	}

	private static String hoveredTooltip(String current, String text) {
		if (current == null && ImGui.isItemHovered()) {
			return text;
		}
		return current;
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
