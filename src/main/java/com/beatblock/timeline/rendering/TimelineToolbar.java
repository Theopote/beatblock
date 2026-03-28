package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.binding.AnimationBindingRule;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.Track;
import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.StageObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 时间线顶部工具栏：播放控制、吸附选项、Beat 网格、Auto Map。
 * 参考专业 DCC（Blender / Unreal Sequencer）的 transport + 吸附条。
 */
public final class TimelineToolbar {
	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineToolbar.class);

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
	private static final String TOOLTIP_TRACK_HEIGHT = "调整音频轨（波形/低中高频）高度，便于看清节奏细节";
	private static final String TOOLTIP_TRACK_HEIGHT_RESET = "恢复音频轨默认高度";
	private static final String TOOLTIP_DEMUCS_PRESET = "Demucs 映射预设：Drive=更强律动，Detail=更细节，Balanced=平衡";
	private static final String TOOLTIP_CLIP_GENERATION_MODE = "控制轨片段生成策略：Trigger=逐点短片段，Sustain=持续分段，Mixed=按特征自动混合";
	private static final String TOOLTIP_DEMUCS_ADVANCED = "高级参数：时长/能量阈值/最小间隔";
	private static final String TOOLTIP_ACTION_ROLLBACK = "PLACE/CLEAR 预览回滚策略：Preview 会在停止/回退时恢复方块；Persistent 会保留写入结果";
	private static final String TOOLTIP_ACTION_ROLLBACK_STATUS = "当前 PLACE/CLEAR 执行策略状态";
	private static final String TOOLTIP_BINDING_MAP = "按绑定规则将音频特征批量转换为动画事件；无规则时自动创建默认规则";
	private static final String TOOLTIP_BINDING_EDITOR = "编辑特征绑定规则：来源特征、动作、目标对象、阈值和冷却";

	/** Zoom 预设：显示名与对应的缩放倍数（相对基准 1x） */
	private static final String[] ZOOM_PRESET_LABELS = { "0.25x", "0.5x", "1x", "2x", "3x", "4x" };
	private static final float ZOOM_BASE = 10f; // 1x 对应的像素/秒
	private static final float[] ZOOM_PRESET_VALUES = { 0.25f * ZOOM_BASE, 0.5f * ZOOM_BASE, ZOOM_BASE, 2f * ZOOM_BASE, 3f * ZOOM_BASE, 4f * ZOOM_BASE };
	private static final String[] SPEED_LABELS = { "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x" };
	private static final double[] SPEED_VALUES = { 0.5, 0.75, 1.0, 1.25, 1.5, 2.0 };
	private static final String[] DEMUCS_PRESET_LABELS = { "Drive", "Balanced", "Detail" };
	private static final String[] DEMUCS_PRESET_VALUES = { "drive", "balanced", "detail" };
	private static final String[] CLIP_GENERATION_MODE_LABELS = { "Mixed", "Trigger", "Sustain" };
	private static final String[] CLIP_GENERATION_MODE_VALUES = { "mixed", "trigger", "sustain" };
	private static final String[] ACTION_ROLLBACK_LABELS = { "Preview", "Persistent" };
	private static final String[] ACTION_ROLLBACK_VALUES = { "preview", "persistent" };
	private static final String[] DEMUCS_FEATURE_KEYS = {
		"kick", "snare", "hihat", "hihat_open", "snare_hi", "bass", "vocals", "other"
	};
	private static final String[] DEMUCS_FEATURE_LABELS = {
		"Kick", "Snare", "HiHat", "HiHat Open", "Snare Hi", "Bass", "Vocals", "Other"
	};
	private static final String DEMUCS_ADVANCED_POPUP_ID = "tlDemucsMappingAdvanced";
	private static final String BINDING_EDITOR_POPUP_ID = "tlBindingEditor";
	private static final String[] BINDING_ACTION_LABELS = { "动画", "放置", "清除" };
	private static final String[] BINDING_ACTION_VALUES = { "ANIMATE", "PLACE", "CLEAR" };
	private static final String[] BINDING_SPATIAL_LABELS = { "ALL", "SEQUENTIAL", "RADIAL", "RANDOM", "SPIRAL" };
	private static final String[] BINDING_SPATIAL_VALUES = { "ALL", "SEQUENTIAL", "RADIAL", "RANDOM", "SPIRAL" };
	private static final Gson UI_CONFIG_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final float TOOLBAR_ITEM_SPACING = 4f;
	private static final float TOOLBAR_GROUP_SPACING = 8f;
	private static final double DEMUCS_SCALE_MIN = 0.5;
	private static final double DEMUCS_SCALE_MAX = 2.0;
	private static final double DEMUCS_ENERGY_SCALE_MIN = 0.6;
	private static final double DEMUCS_ENERGY_SCALE_MAX = 1.6;

	/** 上次 Auto Map 生成数量，用于提示 */
	private int lastAutoMapCount = -1;
	/** 上次 Binding Map 生成数量，用于提示 */
	private int lastBindingMapCount = -1;
	/** Zoom 下拉当前选中索引（由 Combo 更新） */
	private final ImInt zoomComboIndex = new ImInt(2); // 默认 1x
	private final ImInt speedComboIndex = new ImInt(2); // 默认 1x
	private final ImInt demucsPresetComboIndex = new ImInt(1); // 默认 balanced
	private final ImInt clipGenerationModeComboIndex = new ImInt(0); // 默认 mixed
	private final ImInt actionRollbackComboIndex = new ImInt(0); // 默认 preview
	private boolean demucsMappingConfigLoaded;
	private boolean actionExecutionConfigLoaded;

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
				// 暂停主音乐播放器
				if (BeatBlock.musicPlayer != null) {
					BeatBlock.musicPlayer.pause();
				}
				// 同时暂停任何其他活跃播放器
				com.beatblock.timeline.IAudioPlayer ap = BeatBlock.getActiveAudioPlayer();
				if (ap != null && ap != BeatBlock.musicPlayer) {
					ap.pause();
				}
				// 同时暂停时间线时钟
				if (BeatBlock.timelineEditor != null) {
					BeatBlock.timelineEditor.getClock().pause();
				}
			}
			transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PAUSE);
		} else {
			if (ImGui.button(Icons.Play.PLAY + "##tlPlay", tBtn, tBtn)) {
				ensureMusicDurationForPlayback(editor);
				// 始终启动主音楽播放器（作为同步时钟源）
				if (BeatBlock.musicPlayer != null) {
					BeatBlock.musicPlayer.play();
				}
				// 同时启动任何活跃的音频播放器（如果有茎混音，也会由 BeatBlockClientDriver 同步）
				com.beatblock.timeline.IAudioPlayer ap = BeatBlock.getActiveAudioPlayer();
				if (ap != null && ap != BeatBlock.musicPlayer) {
					ap.play();
				}
				// 启动驱动以便每帧推进时间，播放头随音乐移动
				if (!BeatBlockClientDriver.isDriving()) BeatBlockClientDriver.startDriving();
				// 同时启动时间线时钟
				if (BeatBlock.timelineEditor != null) {
					BeatBlock.timelineEditor.getClock().play();
				}
			}
			transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PLAY);
		}
		nextItemInGroup();
		if (ImGui.button(Icons.Play.STOP + "##tlStop", tBtn, tBtn)) {
			// 停止主音乐播放器
			if (BeatBlock.musicPlayer != null) {
				BeatBlock.musicPlayer.stop();
			}
			// 同时停止任何其他活跃播放器
			com.beatblock.timeline.IAudioPlayer ap = BeatBlock.getActiveAudioPlayer();
			if (ap != null && ap != BeatBlock.musicPlayer) {
				ap.stop();
			}
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
		nextItemInGroup();
		renderActionRollbackControl(false);
		nextItemInGroup();
		renderActionRollbackStatus();
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
		nextItemInGroup();
		renderTrackHeightControl(editor, false);
		nextGroup();

		// ----- 4. Auto Map -----
		if (ImGui.button("Binding Map")) {
			if (BeatBlock.timeline != null) {
				lastBindingMapCount = AnimationBindingEngine.applyRules(BeatBlock.timeline, TimelineTrackMeta.ROW_ANIM_BLOCK, true);
				editor.syncClockDuration();
			} else {
				lastBindingMapCount = -1;
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_MAP);
		if (lastBindingMapCount >= 0) {
			nextItemInGroup();
			ImGui.textDisabled("(" + lastBindingMapCount + " bound)");
		}
		nextItemInGroup();
		if (ImGui.button("Bindings...##tlBindingEditorOpen")) {
			ImGui.openPopup(BINDING_EDITOR_POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_EDITOR);
		renderBindingEditorPopup();
		nextItemInGroup();
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

		nextGroup();
		renderDemucsMappingPresetControl(false);

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
		renderActionRollbackControl(true);
		renderActionRollbackStatus();

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
		renderTrackHeightControl(editor, true);

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

		renderDemucsMappingPresetControl(true);

		ImGui.endPopup();
	}

	private void renderActionRollbackControl(boolean compactMode) {
		ensureActionExecutionConfigLoaded();
		actionRollbackComboIndex.set(indexOfActionRollbackValue(readActionRollbackModeFromTimeline()));
		if (compactMode) {
			ImGui.setNextItemWidth(comboWidthForLabels(ACTION_ROLLBACK_LABELS));
			if (ImGui.combo("Rollback##tlMoreActionRollback", actionRollbackComboIndex, ACTION_ROLLBACK_LABELS)) {
				writeActionRollbackModeToTimeline(ACTION_ROLLBACK_VALUES[actionRollbackComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK);
			return;
		}

		ImGui.setNextItemWidth(comboWidthForLabels(ACTION_ROLLBACK_LABELS));
		if (ImGui.combo("Rollback", actionRollbackComboIndex, ACTION_ROLLBACK_LABELS)) {
			writeActionRollbackModeToTimeline(ACTION_ROLLBACK_VALUES[actionRollbackComboIndex.get()]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK);
	}

	private void renderActionRollbackStatus() {
		String mode = readActionRollbackModeFromTimeline();
		String label = "persistent".equalsIgnoreCase(mode)
			? "Action: Persistent"
			: "Action: Preview";
		ImGui.textDisabled(label);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK_STATUS);
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
		float trackHeightGroupWidth = sliderTotalWidth("Track H", 120f) + TOOLBAR_ITEM_SPACING + buttonWidth("Reset");
		float autoMapWidth = buttonWidth("Auto Map") + 70f;

		return transportWidth
			+ TOOLBAR_GROUP_SPACING
			+ loopAndSpeedWidth
			+ TOOLBAR_GROUP_SPACING
			+ snapGroupWidth
			+ TOOLBAR_GROUP_SPACING
			+ viewGroupWidth
			+ TOOLBAR_GROUP_SPACING
			+ trackHeightGroupWidth
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

	private static float sliderTotalWidth(String label, float sliderWidth) {
		return sliderWidth + ImGui.calcTextSize(label).x + 10f;
	}

	private static void renderTrackHeightControl(TimelineEditor editor, boolean compactMode) {
		if (editor == null || editor.getTrackListState() == null) return;
		TimelineTrackListState trackState = editor.getTrackListState();
		float min = trackState.getAudioRowHeightMin();
		float max = trackState.getAudioRowHeightMax();
		float[] v = new float[] { trackState.getAudioRowHeight() };

		if (compactMode) {
			ImGui.separator();
			ImGui.textDisabled("Track Height");
			ImGui.setNextItemWidth(180f);
			if (ImGui.sliderFloat("Track H##tlMoreTrackH", v, min, max, "%.0f px")) {
				trackState.setAudioRowHeight(v[0]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT);
			if (ImGui.button("Reset##tlMoreTrackHReset")) {
				trackState.resetAudioRowHeight();
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT_RESET);
			return;
		}

		ImGui.setNextItemWidth(120f);
		if (ImGui.sliderFloat("Track H", v, min, max, "%.0f px")) {
			trackState.setAudioRowHeight(v[0]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT);
		nextItemInGroup();
		if (ImGui.button("Reset##tlTrackHReset")) {
			trackState.resetAudioRowHeight();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT_RESET);
	}

	private void renderDemucsMappingPresetControl(boolean compactMode) {
		if (BeatBlock.timeline == null) return;
		Object separationMode = BeatBlock.timeline.getMetadata("separationMode");
		if (separationMode == null || !"demucs".equalsIgnoreCase(separationMode.toString().trim())) return;
		ensureDemucsMappingConfigLoaded();

		int currentIndex = indexOfDemucsPresetValue(readDemucsPresetFromTimeline());
		demucsPresetComboIndex.set(currentIndex);
		clipGenerationModeComboIndex.set(indexOfClipGenerationMode(readClipGenerationModeFromTimeline()));

		if (compactMode) {
			ImGui.separator();
			ImGui.textDisabled("Demucs Mapping");
			ImGui.setNextItemWidth(comboWidthForLabels(DEMUCS_PRESET_LABELS));
			if (ImGui.combo("Preset##tlMoreDemucsPreset", demucsPresetComboIndex, DEMUCS_PRESET_LABELS)) {
				writeDemucsPresetToTimeline(DEMUCS_PRESET_VALUES[demucsPresetComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_PRESET);
			ImGui.setNextItemWidth(comboWidthForLabels(CLIP_GENERATION_MODE_LABELS));
			if (ImGui.combo("Clip Mode##tlMoreClipMode", clipGenerationModeComboIndex, CLIP_GENERATION_MODE_LABELS)) {
				writeClipGenerationModeToTimeline(CLIP_GENERATION_MODE_VALUES[clipGenerationModeComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_CLIP_GENERATION_MODE);
			if (ImGui.button("Advanced##tlMoreDemucsAdvanced")) {
				ImGui.openPopup(DEMUCS_ADVANCED_POPUP_ID);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_ADVANCED);
			renderDemucsAdvancedPopup();
			return;
		}

		ImGui.setNextItemWidth(comboWidthForLabels(DEMUCS_PRESET_LABELS));
		if (ImGui.combo("Demucs", demucsPresetComboIndex, DEMUCS_PRESET_LABELS)) {
			writeDemucsPresetToTimeline(DEMUCS_PRESET_VALUES[demucsPresetComboIndex.get()]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_PRESET);
		nextItemInGroup();
		ImGui.setNextItemWidth(comboWidthForLabels(CLIP_GENERATION_MODE_LABELS));
		if (ImGui.combo("Clip Mode", clipGenerationModeComboIndex, CLIP_GENERATION_MODE_LABELS)) {
			writeClipGenerationModeToTimeline(CLIP_GENERATION_MODE_VALUES[clipGenerationModeComboIndex.get()]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_CLIP_GENERATION_MODE);
		nextItemInGroup();
		if (ImGui.button("Map...##tlDemucsAdvanced")) {
			ImGui.openPopup(DEMUCS_ADVANCED_POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_ADVANCED);
		renderDemucsAdvancedPopup();
	}

	private void renderBindingEditorPopup() {
		if (!ImGui.beginPopup(BINDING_EDITOR_POPUP_ID)) return;
		Timeline timeline = BeatBlock.timeline;
		if (timeline == null) {
			ImGui.textDisabled("Timeline 未初始化");
			ImGui.endPopup();
			return;
		}

		List<AnimationBindingRule> rules = new ArrayList<>(AnimationBindingEngine.loadRules(timeline));
		List<String> featureKeys = new ArrayList<>(timeline.getFeatureTracks().keySet());
		Collections.sort(featureKeys);

		Map<String, String> targetDisplayToId = new HashMap<>();
		List<String> targetDisplays = collectTargetDisplays(targetDisplayToId);
		List<String> animationIds = collectAnimationIds();

		ImGui.textDisabled("Binding Rules");
		ImGui.sameLine();
		ImGui.text("(" + rules.size() + ")");

		if (ImGui.button("Create Defaults##bindingCreateDefaults")) {
			rules = new ArrayList<>(AnimationBindingEngine.createDefaultRules(timeline));
			AnimationBindingEngine.saveRules(timeline, rules);
		}
		ImGui.sameLine();
		if (ImGui.button("Add Rule##bindingAddRule")) {
			AnimationBindingRule added = buildAddedRule(featureKeys, targetDisplays, targetDisplayToId);
			if (added != null) {
				rules.add(added);
				AnimationBindingEngine.saveRules(timeline, rules);
			}
		}

		if (featureKeys.isEmpty()) {
			ImGui.textDisabled("当前没有可用特征轨，请先导入并分析音频。\n");
		}

		if (rules.isEmpty()) {
			ImGui.textDisabled("没有规则，可点击 Create Defaults 或 Add Rule。\n");
		}

		int removeIndex = -1;
		boolean changedAny = false;
		for (int i = 0; i < rules.size(); i++) {
			AnimationBindingRule rule = rules.get(i);
			String nodeLabel = rule.name() + "##bindingRuleNode_" + rule.id();
			if (!ImGui.treeNode(nodeLabel)) continue;

			ImGui.pushID("binding_rule_" + rule.id());
			boolean changed = false;

			boolean[] enabled = new boolean[] { rule.enabled() };
			if (ImGui.checkbox("Enabled", enabled[0])) changed = true;

			ImString nameBuf = new ImString(rule.name(), 128);
			if (ImGui.inputText("Name", nameBuf)) changed = true;

			if (!featureKeys.isEmpty()) {
				int featureIndex = indexOfValue(featureKeys, rule.sourceFeatureKey());
				ImInt featureCombo = new ImInt(Math.max(0, featureIndex));
				if (ImGui.combo("Feature", featureCombo, toArray(featureKeys))) changed = true;
				featureIndex = featureCombo.get();
				if (featureIndex < 0 || featureIndex >= featureKeys.size()) featureIndex = 0;

				int animationIndex = indexOfValue(animationIds, rule.animationTypeId());
				ImInt animationCombo = new ImInt(Math.max(0, animationIndex));
				if (ImGui.combo("Animation", animationCombo, toArray(animationIds))) changed = true;
				animationIndex = animationCombo.get();
				if (animationIndex < 0 || animationIndex >= animationIds.size()) animationIndex = 0;

				int actionIndex = indexOfValue(BINDING_ACTION_VALUES, rule.actionMode().name());
				ImInt actionCombo = new ImInt(Math.max(0, actionIndex));
				if (ImGui.combo("Action", actionCombo, BINDING_ACTION_LABELS)) changed = true;
				actionIndex = actionCombo.get();
				if (actionIndex < 0 || actionIndex >= BINDING_ACTION_VALUES.length) actionIndex = 0;

				int spatialIndex = indexOfValue(BINDING_SPATIAL_VALUES, rule.spatialMode().name());
				ImInt spatialCombo = new ImInt(Math.max(0, spatialIndex));
				if (ImGui.combo("Spatial", spatialCombo, BINDING_SPATIAL_LABELS)) changed = true;
				spatialIndex = spatialCombo.get();
				if (spatialIndex < 0 || spatialIndex >= BINDING_SPATIAL_VALUES.length) spatialIndex = 0;

				int targetIndex = indexOfTargetDisplay(targetDisplays, targetDisplayToId, rule.targetObjectId());
				ImInt targetCombo = new ImInt(Math.max(0, targetIndex));
				if (!targetDisplays.isEmpty() && ImGui.combo("Target", targetCombo, toArray(targetDisplays))) changed = true;
				targetIndex = targetCombo.get();
				if (targetIndex < 0 || targetIndex >= targetDisplays.size()) targetIndex = 0;

				float[] threshold = new float[] { rule.energyThreshold() };
				if (ImGui.sliderFloat("Threshold", threshold, 0f, 1f, "%.2f")) changed = true;

				float[] scale = new float[] { rule.energyScale() };
				if (ImGui.sliderFloat("Energy Scale", scale, 0f, 2f, "%.2f")) changed = true;

				float[] duration = new float[] { (float) rule.durationSeconds() };
				if (ImGui.sliderFloat("Duration", duration, 0.05f, 4f, "%.2f s")) changed = true;

				float[] cooldown = new float[] { (float) rule.cooldownSeconds() };
				if (ImGui.sliderFloat("Cooldown", cooldown, 0f, 1.5f, "%.2f s")) changed = true;

				float[] probability = new float[] { rule.probability() };
				if (ImGui.sliderFloat("Probability", probability, 0f, 1f, "%.2f")) changed = true;

				float[] seqDelay = new float[] { (float) rule.sequentialDelaySeconds() };
				if (ImGui.sliderFloat("Step Delay", seqDelay, 0f, 0.5f, "%.2f s")) changed = true;

				if (changed) {
					String selectedFeature = featureKeys.get(featureIndex);
					String selectedAnimation = animationIds.isEmpty() ? rule.animationTypeId() : animationIds.get(animationIndex);
					String selectedTargetDisplay = targetDisplays.isEmpty() ? "" : targetDisplays.get(targetIndex);
					String selectedTargetId = targetDisplayToId.getOrDefault(selectedTargetDisplay, rule.targetObjectId());
					Map<String, Object> extraCopy = new HashMap<>(rule.extraParams());
					AnimationBindingRule updated = AnimationBindingRule.builder()
						.id(rule.id())
						.name(nameBuf.get() == null || nameBuf.get().isBlank() ? rule.name() : nameBuf.get().trim())
						.enabled(enabled[0])
						.sourceFeatureKey(selectedFeature)
						.animationTypeId(selectedAnimation)
						.actionMode(TimelineAnimationActionMode.fromValue(BINDING_ACTION_VALUES[actionIndex]))
						.targetObjectId(selectedTargetId)
						.energyThreshold(threshold[0])
						.energyScale(scale[0])
						.durationSeconds(duration[0])
						.cooldownSeconds(cooldown[0])
						.probability(probability[0])
						.spatialMode(SpatialDispatchMode.fromValue(BINDING_SPATIAL_VALUES[spatialIndex]))
						.sequentialDelaySeconds(seqDelay[0])
						.sectionFilter(rule.sectionFilter())
						.extraParams(extraCopy)
						.build();
					rules.set(i, updated);
					changedAny = true;
				}
			}

			if (ImGui.button("Delete##bindingDelete_" + i)) {
				removeIndex = i;
			}

			ImGui.popID();
			ImGui.treePop();
		}

		if (removeIndex >= 0 && removeIndex < rules.size()) {
			rules.remove(removeIndex);
			changedAny = true;
		}

		if (changedAny) {
			AnimationBindingEngine.saveRules(timeline, rules);
		}

		ImGui.separator();
		if (ImGui.button("Apply To Block Track##bindingApplyBlock")) {
			lastBindingMapCount = AnimationBindingEngine.applyRules(timeline, TimelineTrackMeta.ROW_ANIM_BLOCK, false);
			if (BeatBlock.timelineEditor != null) BeatBlock.timelineEditor.syncClockDuration();
		}
		ImGui.sameLine();
		if (ImGui.button("Apply To Auto Track##bindingApplyAuto")) {
			lastBindingMapCount = AnimationBindingEngine.applyRules(timeline, TimelineTrackMeta.ROW_ANIM_AUTO, false);
			if (BeatBlock.timelineEditor != null) BeatBlock.timelineEditor.syncClockDuration();
		}

		ImGui.endPopup();
	}

	private AnimationBindingRule buildAddedRule(List<String> featureKeys, List<String> targetDisplays, Map<String, String> targetDisplayToId) {
		if (featureKeys == null || featureKeys.isEmpty()) return null;
		if (targetDisplays == null || targetDisplays.isEmpty()) return null;
		String feature = featureKeys.get(0);
		String targetDisplay = targetDisplays.get(0);
		String targetId = targetDisplayToId.getOrDefault(targetDisplay, "");
		if (targetId.isBlank()) return null;
		return AnimationBindingRule.builder()
			.name("Bind " + feature)
			.sourceFeatureKey(feature)
			.animationTypeId("Pulse")
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.targetObjectId(targetId)
			.energyThreshold(0.2f)
			.energyScale(1.0f)
			.durationSeconds(0.4)
			.cooldownSeconds(0.08)
			.probability(1.0f)
			.spatialMode(SpatialDispatchMode.ALL)
			.sequentialDelaySeconds(0.0)
			.build();
	}

	private List<String> collectAnimationIds() {
		List<String> ids = new ArrayList<>();
		if (BeatBlock.blockAnimationEngine != null && BeatBlock.blockAnimationEngine.getAnimationLibrary() != null) {
			List<AnimationDefinition> defs = new ArrayList<>(BeatBlock.blockAnimationEngine.getAnimationLibrary().getAll().values());
			defs.sort(Comparator.comparing(AnimationDefinition::getId, String.CASE_INSENSITIVE_ORDER));
			for (AnimationDefinition def : defs) {
				ids.add(def.getId());
			}
		}
		if (ids.isEmpty()) ids.add("Pulse");
		return ids;
	}

	private List<String> collectTargetDisplays(Map<String, String> outDisplayToId) {
		List<String> displays = new ArrayList<>();
		if (outDisplayToId == null) return displays;
		if (BeatBlock.blockAnimationEngine == null || BeatBlock.blockAnimationEngine.getStageObjectSystem() == null) {
			return displays;
		}
		List<StageObject> objects = new ArrayList<>(BeatBlock.blockAnimationEngine.getStageObjectSystem().getAll());
		objects.sort(Comparator.comparing(StageObject::getName, String.CASE_INSENSITIVE_ORDER));
		for (StageObject object : objects) {
			String display = object.getName() + " [" + object.getId() + "]";
			if (outDisplayToId.containsKey(display)) continue;
			outDisplayToId.put(display, object.getId());
			displays.add(display);
		}
		return displays;
	}

	private static String[] toArray(List<String> values) {
		if (values == null || values.isEmpty()) return new String[] { "" };
		LinkedHashSet<String> dedup = new LinkedHashSet<>(values);
		return dedup.toArray(new String[0]);
	}

	private static int indexOfValue(List<String> values, String target) {
		if (values == null || values.isEmpty()) return 0;
		if (target == null) return 0;
		for (int i = 0; i < values.size(); i++) {
			if (target.equalsIgnoreCase(values.get(i))) return i;
		}
		return 0;
	}

	private static int indexOfValue(String[] values, String target) {
		if (values == null || values.length == 0) return 0;
		if (target == null) return 0;
		for (int i = 0; i < values.length; i++) {
			if (target.equalsIgnoreCase(values[i])) return i;
		}
		return 0;
	}

	private static int indexOfTargetDisplay(List<String> targetDisplays, Map<String, String> displayToId, String targetId) {
		if (targetDisplays == null || targetDisplays.isEmpty() || displayToId == null) return 0;
		if (targetId == null || targetId.isBlank()) return 0;
		for (int i = 0; i < targetDisplays.size(); i++) {
			String display = targetDisplays.get(i);
			String id = displayToId.get(display);
			if (targetId.equals(id)) return i;
		}
		return 0;
	}

	private void renderDemucsAdvancedPopup() {
		if (!ImGui.beginPopup(DEMUCS_ADVANCED_POPUP_ID)) return;
		ImGui.textDisabled("Demucs Mapping Advanced");

		float[] durationScale = new float[] { (float) readTimelineScale("demucsMapDurationScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX) };
		float[] energyScale = new float[] { (float) readTimelineScale("demucsMapEnergyScale", 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX) };
		float[] gapScale = new float[] { (float) readTimelineScale("demucsMapGapScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX) };

		boolean changed = false;
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Duration Scale##demucsDur", durationScale, (float) DEMUCS_SCALE_MIN, (float) DEMUCS_SCALE_MAX, "%.2f");
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Energy Threshold##demucsEnergy", energyScale, (float) DEMUCS_ENERGY_SCALE_MIN, (float) DEMUCS_ENERGY_SCALE_MAX, "%.2f");
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Min Gap Scale##demucsGap", gapScale, (float) DEMUCS_SCALE_MIN, (float) DEMUCS_SCALE_MAX, "%.2f");

		if (changed) {
			writeTimelineScale("demucsMapDurationScale", durationScale[0]);
			writeTimelineScale("demucsMapEnergyScale", energyScale[0]);
			writeTimelineScale("demucsMapGapScale", gapScale[0]);
			persistDemucsMappingConfig();
		}

		if (ImGui.button("Reset to 1.0##demucsScaleReset")) {
			writeTimelineScale("demucsMapDurationScale", 1.0f);
			writeTimelineScale("demucsMapEnergyScale", 1.0f);
			writeTimelineScale("demucsMapGapScale", 1.0f);
			persistDemucsMappingConfig();
		}

		ImGui.separator();
		if (ImGui.treeNode("Per-Feature Overrides##demucsFeatureOverrides")) {
			boolean featureChanged = false;
			for (int i = 0; i < DEMUCS_FEATURE_KEYS.length; i++) {
				String featureKey = DEMUCS_FEATURE_KEYS[i];
				String label = DEMUCS_FEATURE_LABELS[i];
				if (ImGui.treeNode(label + "##demucsFeatureNode_" + featureKey)) {
					boolean nodeChanged = false;
					float[] fDur = new float[] {
						(float) readTimelineScale(featureMetadataKey(featureKey, "duration"), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX)
					};
					float[] fEnergy = new float[] {
						(float) readTimelineScale(featureMetadataKey(featureKey, "energy"), 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX)
					};
					float[] fGap = new float[] {
						(float) readTimelineScale(featureMetadataKey(featureKey, "gap"), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX)
					};

					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Duration##demucsFeatDur_" + featureKey, fDur, (float) DEMUCS_SCALE_MIN, (float) DEMUCS_SCALE_MAX, "%.2f");
					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Energy##demucsFeatEnergy_" + featureKey, fEnergy, (float) DEMUCS_ENERGY_SCALE_MIN, (float) DEMUCS_ENERGY_SCALE_MAX, "%.2f");
					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Gap##demucsFeatGap_" + featureKey, fGap, (float) DEMUCS_SCALE_MIN, (float) DEMUCS_SCALE_MAX, "%.2f");

					if (nodeChanged) {
						writeTimelineScale(featureMetadataKey(featureKey, "duration"), fDur[0]);
						writeTimelineScale(featureMetadataKey(featureKey, "energy"), fEnergy[0]);
						writeTimelineScale(featureMetadataKey(featureKey, "gap"), fGap[0]);
						featureChanged = true;
					}

					ImGui.treePop();
				}
			}

			if (featureChanged) {
				persistDemucsMappingConfig();
			}

			if (ImGui.button("Reset Feature Overrides##demucsFeatReset")) {
				for (String featureKey : DEMUCS_FEATURE_KEYS) {
					writeTimelineScale(featureMetadataKey(featureKey, "duration"), 1.0f);
					writeTimelineScale(featureMetadataKey(featureKey, "energy"), 1.0f);
					writeTimelineScale(featureMetadataKey(featureKey, "gap"), 1.0f);
				}
				persistDemucsMappingConfig();
			}

			ImGui.treePop();
		}

		ImGui.endPopup();
	}

	private String readDemucsPresetFromTimeline() {
		if (BeatBlock.timeline == null) return "balanced";
		Object preset = BeatBlock.timeline.getMetadata("demucsMappingPreset");
		if (preset == null) return "balanced";
		String value = preset.toString().trim().toLowerCase();
		if ("drive".equals(value) || "detail".equals(value) || "balanced".equals(value)) {
			return value;
		}
		return "balanced";
	}

	private void writeDemucsPresetToTimeline(String preset) {
		if (BeatBlock.timeline == null) return;
		BeatBlock.timeline.setMetadata("demucsMappingPreset", preset);
		persistDemucsMappingConfig();
	}

	private String readClipGenerationModeFromTimeline() {
		if (BeatBlock.timeline == null) return "mixed";
		Object mode = BeatBlock.timeline.getMetadata("featureClipGenerationMode");
		if (mode == null) return "mixed";
		String value = mode.toString().trim().toLowerCase(Locale.ROOT);
		if ("trigger".equals(value) || "sustain".equals(value) || "mixed".equals(value)) {
			return value;
		}
		return "mixed";
	}

	private void writeClipGenerationModeToTimeline(String mode) {
		if (BeatBlock.timeline == null) return;
		String normalized = (mode == null ? "mixed" : mode.trim().toLowerCase(Locale.ROOT));
		if (!"trigger".equals(normalized) && !"sustain".equals(normalized) && !"mixed".equals(normalized)) {
			normalized = "mixed";
		}
		BeatBlock.timeline.setMetadata("featureClipGenerationMode", normalized);
		persistDemucsMappingConfig();
	}

	private void ensureDemucsMappingConfigLoaded() {
		if (demucsMappingConfigLoaded || BeatBlock.timeline == null) return;
		demucsMappingConfigLoaded = true;
		Path configPath = getUiConfigPath();
		if (!Files.isRegularFile(configPath)) return;
		try {
			String txt = Files.readString(configPath, StandardCharsets.UTF_8);
			if (txt == null || txt.isBlank()) return;
			JsonObject root = JsonParser.parseString(txt).getAsJsonObject();
			if (!root.has("demucsMapping") || !root.get("demucsMapping").isJsonObject()) return;
			JsonObject dm = root.getAsJsonObject("demucsMapping");

			if (BeatBlock.timeline.getMetadata("demucsMappingPreset") == null && dm.has("preset")) {
				String preset = dm.get("preset").getAsString();
				if ("drive".equalsIgnoreCase(preset) || "detail".equalsIgnoreCase(preset) || "balanced".equalsIgnoreCase(preset)) {
					BeatBlock.timeline.setMetadata("demucsMappingPreset", preset.toLowerCase());
				}
			}
			if (BeatBlock.timeline.getMetadata("featureClipGenerationMode") == null && dm.has("clipGenerationMode")) {
				String mode = dm.get("clipGenerationMode").getAsString();
				if ("mixed".equalsIgnoreCase(mode) || "trigger".equalsIgnoreCase(mode) || "sustain".equalsIgnoreCase(mode)) {
					BeatBlock.timeline.setMetadata("featureClipGenerationMode", mode.toLowerCase(Locale.ROOT));
				}
			}

			applyDefaultScaleFromJson(dm, "durationScale", "demucsMapDurationScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
			applyDefaultScaleFromJson(dm, "energyScale", "demucsMapEnergyScale", 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX);
			applyDefaultScaleFromJson(dm, "gapScale", "demucsMapGapScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);

			if (dm.has("featureScale") && dm.get("featureScale").isJsonObject()) {
				JsonObject featureScale = dm.getAsJsonObject("featureScale");
				for (String featureKey : DEMUCS_FEATURE_KEYS) {
					if (!featureScale.has(featureKey) || !featureScale.get(featureKey).isJsonObject()) continue;
					JsonObject featureObj = featureScale.getAsJsonObject(featureKey);
					applyDefaultScaleFromJson(featureObj, "durationScale", featureMetadataKey(featureKey, "duration"), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
					applyDefaultScaleFromJson(featureObj, "energyScale", featureMetadataKey(featureKey, "energy"), 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX);
					applyDefaultScaleFromJson(featureObj, "gapScale", featureMetadataKey(featureKey, "gap"), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
				}
			}
		} catch (Exception e) {
			LOGGER.debug("BeatBlock TimelineToolbar: failed to read ui.json demucs mapping config reason={}", e.toString());
		}
	}

	private void applyDefaultScaleFromJson(JsonObject dm, String jsonKey, String metadataKey,
	                                     double defaultValue, double min, double max) {
		if (BeatBlock.timeline == null) return;
		if (BeatBlock.timeline.getMetadata(metadataKey) != null) return;
		double v = defaultValue;
		if (dm.has(jsonKey)) {
			try {
				v = dm.get(jsonKey).getAsDouble();
			} catch (Exception ignored) {}
		}
		v = Math.max(min, Math.min(max, v));
		BeatBlock.timeline.setMetadata(metadataKey, v);
	}

	private void persistDemucsMappingConfig() {
		if (BeatBlock.timeline == null) return;
		Path configPath = getUiConfigPath();
		try {
			JsonObject root = new JsonObject();
			if (Files.isRegularFile(configPath)) {
				String existing = Files.readString(configPath, StandardCharsets.UTF_8);
				if (existing != null && !existing.isBlank()) {
					root = JsonParser.parseString(existing).getAsJsonObject();
				}
			}

			JsonObject dm = root.has("demucsMapping") && root.get("demucsMapping").isJsonObject()
				? root.getAsJsonObject("demucsMapping")
				: new JsonObject();

			dm.addProperty("preset", readDemucsPresetFromTimeline());
			dm.addProperty("clipGenerationMode", readClipGenerationModeFromTimeline());
			dm.addProperty("durationScale", readTimelineScale("demucsMapDurationScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX));
			dm.addProperty("energyScale", readTimelineScale("demucsMapEnergyScale", 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX));
			dm.addProperty("gapScale", readTimelineScale("demucsMapGapScale", 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX));

			JsonObject featureScale = new JsonObject();
			for (String featureKey : DEMUCS_FEATURE_KEYS) {
				JsonObject featureObj = new JsonObject();
				featureObj.addProperty("durationScale", readTimelineScale(featureMetadataKey(featureKey, "duration"), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX));
				featureObj.addProperty("energyScale", readTimelineScale(featureMetadataKey(featureKey, "energy"), 1.0, DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX));
				featureObj.addProperty("gapScale", readTimelineScale(featureMetadataKey(featureKey, "gap"), 1.0, DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX));
				featureScale.add(featureKey, featureObj);
			}
			dm.add("featureScale", featureScale);

			root.add("demucsMapping", dm);
			Files.createDirectories(configPath.getParent());
			Files.writeString(configPath, UI_CONFIG_GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.debug("BeatBlock TimelineToolbar: failed to persist ui.json demucs mapping config reason={}", e.toString());
		}
	}

	private String readActionRollbackModeFromTimeline() {
		if (BeatBlock.timeline == null) return "preview";
		Object mode = BeatBlock.timeline.getMetadata("timelineActionRollbackMode");
		if (mode == null) return "preview";
		String value = mode.toString().trim().toLowerCase(Locale.ROOT);
		if ("preview".equals(value) || "persistent".equals(value) || "performance".equals(value)) {
			return "performance".equals(value) ? "persistent" : value;
		}
		return "preview";
	}

	private void writeActionRollbackModeToTimeline(String mode) {
		if (BeatBlock.timeline == null) return;
		String normalized = ("persistent".equalsIgnoreCase(mode) || "performance".equalsIgnoreCase(mode))
			? "persistent"
			: "preview";
		BeatBlock.timeline.setMetadata("timelineActionRollbackMode", normalized);
		persistActionExecutionConfig();
	}

	private void ensureActionExecutionConfigLoaded() {
		if (actionExecutionConfigLoaded || BeatBlock.timeline == null) return;
		actionExecutionConfigLoaded = true;
		if (BeatBlock.timeline.getMetadata("timelineActionRollbackMode") != null) return;
		Path configPath = getUiConfigPath();
		if (!Files.isRegularFile(configPath)) return;
		try {
			String txt = Files.readString(configPath, StandardCharsets.UTF_8);
			if (txt == null || txt.isBlank()) return;
			JsonObject root = JsonParser.parseString(txt).getAsJsonObject();
			if (!root.has("timelineActionExecution") || !root.get("timelineActionExecution").isJsonObject()) return;
			JsonObject action = root.getAsJsonObject("timelineActionExecution");
			if (!action.has("rollbackMode")) return;
			String mode = action.get("rollbackMode").getAsString();
			writeActionRollbackModeToTimeline(mode);
		} catch (Exception e) {
			LOGGER.debug("BeatBlock TimelineToolbar: failed to read ui.json timeline action config reason={}", e.toString());
		}
	}

	private void persistActionExecutionConfig() {
		if (BeatBlock.timeline == null) return;
		Path configPath = getUiConfigPath();
		try {
			JsonObject root = new JsonObject();
			if (Files.isRegularFile(configPath)) {
				String existing = Files.readString(configPath, StandardCharsets.UTF_8);
				if (existing != null && !existing.isBlank()) {
					root = JsonParser.parseString(existing).getAsJsonObject();
				}
			}

			JsonObject action = root.has("timelineActionExecution") && root.get("timelineActionExecution").isJsonObject()
				? root.getAsJsonObject("timelineActionExecution")
				: new JsonObject();
			action.addProperty("rollbackMode", readActionRollbackModeFromTimeline());
			root.add("timelineActionExecution", action);

			Files.createDirectories(configPath.getParent());
			Files.writeString(configPath, UI_CONFIG_GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.debug("BeatBlock TimelineToolbar: failed to persist ui.json timeline action config reason={}", e.toString());
		}
	}

	private double readTimelineScale(String key, double defaultValue, double min, double max) {
		if (BeatBlock.timeline == null || key == null || key.isBlank()) return defaultValue;
		Object raw = BeatBlock.timeline.getMetadata(key);
		if (raw == null) return defaultValue;
		double value;
		if (raw instanceof Number n) {
			value = n.doubleValue();
		} else {
			try {
				value = Double.parseDouble(raw.toString().trim());
			} catch (Exception e) {
				return defaultValue;
			}
		}
		if (Double.isNaN(value) || Double.isInfinite(value)) return defaultValue;
		return Math.max(min, Math.min(max, value));
	}

	private void writeTimelineScale(String key, float value) {
		if (BeatBlock.timeline == null || key == null || key.isBlank()) return;
		BeatBlock.timeline.setMetadata(key, value);
	}

	private static String featureMetadataKey(String featureKey, String metric) {
		if (featureKey == null || featureKey.isBlank() || metric == null || metric.isBlank()) return "";
		String normalizedFeature = featureKey.trim().toLowerCase(Locale.ROOT);
		String normalizedMetric = metric.trim().toLowerCase(Locale.ROOT);
		return switch (normalizedMetric) {
			case "duration" -> "demucsFeatDuration_" + normalizedFeature;
			case "energy" -> "demucsFeatEnergy_" + normalizedFeature;
			case "gap" -> "demucsFeatGap_" + normalizedFeature;
			default -> "";
		};
	}

	private static Path getUiConfigPath() {
		return FabricLoader.getInstance().getGameDir().resolve("config").resolve("beatblock").resolve("ui.json");
	}

	private static int indexOfDemucsPresetValue(String value) {
		if (value == null || value.isBlank()) return 1;
		for (int i = 0; i < DEMUCS_PRESET_VALUES.length; i++) {
			if (DEMUCS_PRESET_VALUES[i].equalsIgnoreCase(value)) return i;
		}
		return 1;
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

	private static int indexOfActionRollbackValue(String value) {
		if (value == null || value.isBlank()) return 0;
		for (int i = 0; i < ACTION_ROLLBACK_VALUES.length; i++) {
			if (ACTION_ROLLBACK_VALUES[i].equalsIgnoreCase(value)) return i;
		}
		return 0;
	}

	private static int indexOfClipGenerationMode(String value) {
		if (value == null || value.isBlank()) return 0;
		for (int i = 0; i < CLIP_GENERATION_MODE_VALUES.length; i++) {
			if (CLIP_GENERATION_MODE_VALUES[i].equalsIgnoreCase(value)) return i;
		}
		return 0;
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
			ensureMusicDurationForPlayback(editor);
			BeatBlock.musicPlayer.setCurrentTimeSeconds(t);
		}
	}

	private static void ensureMusicDurationForPlayback(TimelineEditor editor) {
		if (BeatBlock.musicPlayer == null || editor == null) return;
		if (BeatBlock.timeline != null) {
			Object audioPath = BeatBlock.timeline.getMetadata("audioPath");
			if (audioPath instanceof String path && !path.isBlank()) {
				String loadedPath = BeatBlock.musicPlayer.getLoadedAudioPath();
				if (!path.equals(loadedPath)) {
					boolean loaded = BeatBlock.musicPlayer.loadAudio(path);
					if (loaded) {
						LOGGER.info("BeatBlock TimelineToolbar: auto-bound timeline audioPath={} before transport action", path);
					} else {
						LOGGER.warn("BeatBlock TimelineToolbar: failed to auto-bind timeline audioPath={} reason={}", path, BeatBlock.musicPlayer.getLastLoadError());
					}
				}
			}
		}
		if (BeatBlock.musicPlayer.getDurationSeconds() > 0) return;
		double duration = getDuration(editor);
		if (duration > 0) {
			BeatBlock.musicPlayer.setDurationSeconds(duration);
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
