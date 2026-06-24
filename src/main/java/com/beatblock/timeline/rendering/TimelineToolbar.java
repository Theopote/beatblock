package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.binding.AnimationBindingRule;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.presenter.TimelineBindingEditorPresenter;
import com.beatblock.ui.presenter.TimelineToolbarActionsPresenter;
import com.beatblock.ui.presenter.TimelineToolbarConfigPresenter;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import com.beatblock.ui.presenter.TimelineTransportPresenter;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
	private static final String TOOLTIP_BAKE_STEP = "将 dispatchModel=STEP 的事件烘焙为 N 个带绝对时间的普通 BURST 事件（可 Undo）；需 StageObject 与参考节拍";
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
	private static final String TOOLTIP_BINDING_TEMPLATE = "规则模板：可覆盖（Replace）或合并（Append）到当前规则集";

	private static final String DEMUCS_ADVANCED_POPUP_ID = "tlDemucsMappingAdvanced";
	private static final String BINDING_EDITOR_POPUP_ID = "tlBindingEditor";
	private static final float TOOLBAR_ITEM_SPACING = 4f;
	private static final float TOOLBAR_GROUP_SPACING = 8f;

	/** 上次 Auto Map 生成数量，用于提示 */
	private int lastAutoMapCount = -1;
	/** 上次 Binding Map 生成数量，用于提示 */
	private int lastBindingMapCount = -1;
	/** Zoom 下拉当前选中索引（由 Combo 更新） */
	private final ImInt zoomComboIndex = new ImInt(2); // 默认 1x
	private final ImInt speedComboIndex = new ImInt(2); // 默认 1x
	private final TimelineTransportPresenter transport;
	private final TimelineToolbarActionsPresenter actions;
	private final TimelineToolbarConfigPresenter config;
	private final TimelineBindingEditorPresenter binding;
	private final ImInt demucsPresetComboIndex = new ImInt(1); // 默认 balanced
	private final ImInt clipGenerationModeComboIndex = new ImInt(0); // 默认 mixed
	private final ImInt actionRollbackComboIndex = new ImInt(0); // 默认 preview
	private final ImInt bindingTemplateComboIndex = new ImInt(0);
	private String lastToolActionFeedback = "";
	private long lastToolActionFeedbackAtMs = 0L;
	private boolean lastToolActionFeedbackSuccess = false;
	private String lastTemplateApplyFeedback = "";
	private long lastTemplateApplyFeedbackAtMs = 0L;
	private boolean lastTemplateApplyFeedbackSuccess = false;

	public TimelineToolbar() {
		this(
			PresenterFactories.timelineTransportPresenter(),
			PresenterFactories.timelineToolbarActionsPresenter(),
			PresenterFactories.timelineToolbarConfigPresenter(),
			PresenterFactories.timelineBindingEditorPresenter()
		);
	}

	TimelineToolbar(
		TimelineTransportPresenter transport,
		TimelineToolbarActionsPresenter actions,
		TimelineToolbarConfigPresenter config,
		TimelineBindingEditorPresenter binding
	) {
		this.transport = transport;
		this.actions = actions;
		this.config = config;
		this.binding = binding;
	}

	public void render(TimelineEditor editor, TimelineToolbarState toolbarState) {
		if (editor == null) return;

		// ----- 1. 播放控制 -----
		boolean shiftHeld = ImGui.getIO().getKeyShift();
		var transportState = transport.viewState(editor, shiftHeld);
		double seekStep = transportState.seekStep();
		double stepSeek = transportState.stepSeek();
		boolean playing = transportState.playing();

		// 图标按钮：与轨道行同高、零内边距，字形尽量铺满并居中
		final float tBtn = TimelineLayout.ROW_HEIGHT;
		String transportTooltip;
		IconButtonStyle.pushBeatBlockIconButton();
		if (ImGui.button(Icons.Play.REWIND_START + "##tlToStart", tBtn, tBtn)) {
			transport.seekTo(editor, 0);
		}
		transportTooltip = hoveredTooltip(null, TOOLTIP_TO_START);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.REWIND + "##tlBackBeat", tBtn, tBtn)) {
			transport.seekBy(editor, -stepSeek);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_BACK_BEAT);
		nextItemInGroup();
		if (playing) {
			if (ImGui.button(Icons.Play.PAUSE + "##tlPause", tBtn, tBtn)) {
				transport.pause();
			}
			transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PAUSE);
		} else {
			if (ImGui.button(Icons.Play.PLAY + "##tlPlay", tBtn, tBtn)) {
				transport.play(editor);
			}
			transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PLAY);
		}
		nextItemInGroup();
		if (ImGui.button(Icons.Play.STOP + "##tlStop", tBtn, tBtn)) {
			transport.stop(editor);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_STOP);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.FORWARD + "##tlFwdBeat", tBtn, tBtn)) {
			transport.seekBy(editor, stepSeek);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_FWD_BEAT);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.FORWARD_END + "##tlToEnd", tBtn, tBtn)) {
			transport.seekTo(editor, transportState.durationSeconds());
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_TO_END);
		nextGroup();
		if (ImGui.button(Icons.Action.ARROW_LEFT + "##tlPrevEvt", tBtn, tBtn)) {
			transport.jumpToNearbyEvent(editor, false);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PREV_EVENT);
		nextItemInGroup();
		if (ImGui.button(Icons.Action.ARROW_RIGHT + "##tlNextEvt", tBtn, tBtn)) {
			transport.jumpToNearbyEvent(editor, true);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_NEXT_EVENT);
		nextItemInGroup();
		if (ImGui.button(Icons.Timeline.MARKER + "##tlAddMarker", tBtn, tBtn)) {
			transport.addMarkerAtCurrentTime(editor);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_ADD_MARKER);
		IconButtonStyle.popBeatBlockIconButton();
		if (transportTooltip != null) {
			ImGui.setTooltip(transportTooltip);
		}

		// ----- 时间显示（始终可见）-----
		{
			nextGroup();
			ImGui.textDisabled(transportState.positionDisplay());
		}

		nextGroupOrWrap(0);

		// ----- 1.5 循环区（In/Out）与速度 -----
		double now = transportState.currentTimeSeconds();
		if (ImGui.button("In")) {
			transport.setLoopInAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_IN);
		nextItemInGroup();
		if (ImGui.button("Out")) {
			transport.setLoopOutAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_OUT);
		nextItemInGroup();
		if (ImGui.button("Clr")) {
			transport.clearLoopRange(toolbarState);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_CLEAR);
		nextItemInGroup();

		speedComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestSpeed(transport.currentPlaybackSpeed(editor)));
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarViewPresenter.SPEED_LABELS));
		if (ImGui.combo("Speed", speedComboIndex, TimelineToolbarViewPresenter.SPEED_LABELS)) {
			TimelineToolbarViewPresenter.applySpeedPreset(editor, transport, speedComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SPEED);
		nextItemInGroup();
		renderActionRollbackControl(false);
		nextGroupOrWrap(0);

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
			nextGroupOrWrap(0);

			boolean loop = toolbarState.isLoop();
			if (ImGui.checkbox("Loop", loop)) {
				toolbarState.setLoop(!loop);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP);
			nextGroupOrWrap(0);
		}

		// ----- 3. 视图：Zoom 下拉 + Fit -----
		zoomComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestZoom(editor.getViewState().getZoom()));
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS));
		if (ImGui.combo("Zoom", zoomComboIndex, TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS)) {
			TimelineToolbarViewPresenter.applyZoomPreset(editor, zoomComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ZOOM);
		nextItemInGroup();
		if (ImGui.button("Fit")) {
			TimelineToolbarViewPresenter.fitToDuration(
				editor, BeatBlock.timeline, ImGui.getContentRegionAvailX() - 130f);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FIT);
		nextItemInGroup();
		renderTrackHeightControl(editor, false);
		nextGroupOrWrap(0);

		// ----- 4. Auto Map -----
		{
			int objCount = BeatBlock.blockAnimationEngine != null
				? BeatBlock.blockAnimationEngine.getStageObjectSystem().size() : 0;
			if (objCount == 0) {
				ImGui.textColored(0.95f, 0.65f, 0.30f, 1f, "无对象");
				if (ImGui.isItemHovered()) ImGui.setTooltip("请先在工具面板中创建 StageObject（选区→创建），否则 Binding Map 无法生成事件");
				nextItemInGroup();
			}
		}
		if (ImGui.button("Binding Map")) {
			var outcome = actions.runBindingMap();
			lastBindingMapCount = outcome.count();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_MAP);
		nextItemInGroup();
		if (ImGui.button("Bindings...##tlBindingEditorOpen")) {
			ImGui.openPopup(BINDING_EDITOR_POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_EDITOR);
		renderBindingEditorPopup();
		nextItemInGroup();
		if (ImGui.button("Auto Map")) {
			var outcome = actions.runAutoMap();
			lastAutoMapCount = outcome.count();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_AUTO_MAP);
		nextItemInGroup();
		if (ImGui.button("烘焙 STEP##tlBakeStep")) {
			var outcome = actions.runBakeStepSequences();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BAKE_STEP);
		nextItemInGroup();
		renderToolActionFeedback();

		nextGroupOrWrap(0);
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
			transport.setLoopInAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_IN);
		ImGui.sameLine();
		if (ImGui.button("Out##tlMoreOut")) {
			transport.setLoopOutAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_OUT);
		ImGui.sameLine();
		if (ImGui.button("Clr##tlMoreClr")) {
			transport.clearLoopRange(toolbarState);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_CLEAR);

		speedComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestSpeed(transport.currentPlaybackSpeed(editor)));
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarViewPresenter.SPEED_LABELS));
		if (ImGui.combo("Speed##tlMoreSpeed", speedComboIndex, TimelineToolbarViewPresenter.SPEED_LABELS)) {
			TimelineToolbarViewPresenter.applySpeedPreset(editor, transport, speedComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SPEED);
		renderActionRollbackControl(true);

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
		zoomComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestZoom(editor.getViewState().getZoom()));
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS));
		if (ImGui.combo("Zoom##tlMoreZoom", zoomComboIndex, TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS)) {
			TimelineToolbarViewPresenter.applyZoomPreset(editor, zoomComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ZOOM);
		if (ImGui.button("Fit##tlMoreFit")) {
			TimelineToolbarViewPresenter.fitToDuration(
				editor, BeatBlock.timeline, ImGui.getContentRegionAvailX() - 16f);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FIT);
		renderTrackHeightControl(editor, true);

		ImGui.separator();
		ImGui.textDisabled("Tools");
		if (ImGui.button("Binding Map##tlMoreBindingMap")) {
			var outcome = actions.runBindingMap();
			lastBindingMapCount = outcome.count();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_MAP);
		ImGui.sameLine();
		if (ImGui.button("Bindings...##tlMoreBindingEditorOpen")) {
			ImGui.openPopup(BINDING_EDITOR_POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_EDITOR);
		renderBindingEditorPopup();
		if (ImGui.button("Auto Map##tlMoreAutoMap")) {
			var outcome = actions.runAutoMap();
			lastAutoMapCount = outcome.count();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_AUTO_MAP);
		if (ImGui.button("烘焙 STEP##tlMoreBakeStep")) {
			var outcome = actions.runBakeStepSequences();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BAKE_STEP);
		renderToolActionFeedback();

		renderDemucsMappingPresetControl(true);

		ImGui.endPopup();
	}

	private void renderActionRollbackControl(boolean compactMode) {
		config.ensureActionExecutionConfigLoaded();
		actionRollbackComboIndex.set(TimelineToolbarConfigPresenter.indexOfActionRollbackValue(config.readActionRollbackMode()));
		if (compactMode) {
			ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS));
			if (ImGui.combo("Rollback##tlMoreActionRollback", actionRollbackComboIndex, TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS)) {
				config.writeActionRollbackMode(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_VALUES[actionRollbackComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK);
			return;
		}

		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS));
		if (ImGui.combo("Rollback", actionRollbackComboIndex, TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS)) {
			config.writeActionRollbackMode(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_VALUES[actionRollbackComboIndex.get()]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK);
	}

	private void renderActionRollbackStatus() {
		ImGui.textDisabled(config.actionRollbackViewState().statusLabel());
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK_STATUS);
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

	private static float sliderTotalWidth(float sliderWidth) {
		return sliderWidth + ImGui.calcTextSize("Track H").x + 10f;
	}

	private static void renderTrackHeightControl(TimelineEditor editor, boolean compactMode) {
		if (editor == null) return;
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
		if (!config.isDemucsSeparationActive()) return;
		config.ensureDemucsMappingConfigLoaded();

		demucsPresetComboIndex.set(TimelineToolbarConfigPresenter.indexOfDemucsPresetValue(config.readDemucsPreset()));
		clipGenerationModeComboIndex.set(TimelineToolbarConfigPresenter.indexOfClipGenerationMode(config.readClipGenerationMode()));

		if (compactMode) {
			ImGui.separator();
			ImGui.textDisabled("Demucs Mapping");
			ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.DEMUCS_PRESET_LABELS));
			if (ImGui.combo("Preset##tlMoreDemucsPreset", demucsPresetComboIndex, TimelineToolbarConfigPresenter.DEMUCS_PRESET_LABELS)) {
				config.writeDemucsPreset(TimelineToolbarConfigPresenter.DEMUCS_PRESET_VALUES[demucsPresetComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_PRESET);
			ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_LABELS));
			if (ImGui.combo("Clip Mode##tlMoreClipMode", clipGenerationModeComboIndex, TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_LABELS)) {
				config.writeClipGenerationMode(TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_VALUES[clipGenerationModeComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_CLIP_GENERATION_MODE);
			if (ImGui.button("Advanced##tlMoreDemucsAdvanced")) {
				ImGui.openPopup(DEMUCS_ADVANCED_POPUP_ID);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_ADVANCED);
			renderDemucsAdvancedPopup();
			return;
		}

		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.DEMUCS_PRESET_LABELS));
		if (ImGui.combo("Demucs", demucsPresetComboIndex, TimelineToolbarConfigPresenter.DEMUCS_PRESET_LABELS)) {
			config.writeDemucsPreset(TimelineToolbarConfigPresenter.DEMUCS_PRESET_VALUES[demucsPresetComboIndex.get()]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_PRESET);
		nextItemInGroup();
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_LABELS));
		if (ImGui.combo("Clip Mode", clipGenerationModeComboIndex, TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_LABELS)) {
			config.writeClipGenerationMode(TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_VALUES[clipGenerationModeComboIndex.get()]);
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
		List<String> sectionFilters = collectSectionFilters(timeline);

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

		ImGui.setNextItemWidth(comboWidthForLabels(BINDING_TEMPLATE_LABELS));
		ImGui.combo("Template##bindingTemplate", bindingTemplateComboIndex, BINDING_TEMPLATE_LABELS);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_TEMPLATE);
		ImGui.sameLine();
		if (ImGui.button("Replace##bindingApplyTemplate")) {
			int idx = Math.max(0, Math.min(bindingTemplateComboIndex.get(), BINDING_TEMPLATE_VALUES.length - 1));
			List<AnimationBindingRule> templated = new ArrayList<>(
				AnimationBindingEngine.createTemplateRules(timeline, BINDING_TEMPLATE_VALUES[idx]));
			if (!templated.isEmpty()) {
				rules = templated;
				AnimationBindingEngine.saveRules(timeline, rules);
				setTemplateApplyFeedback("Template " + BINDING_TEMPLATE_LABELS[idx] + " replaced all rules: " + rules.size(), true);
			} else {
				setTemplateApplyFeedback("Template " + BINDING_TEMPLATE_LABELS[idx] + " produced no rules", false);
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip("覆盖当前规则集");
		ImGui.sameLine();
		if (ImGui.button("Append##bindingAppendTemplate")) {
			int idx = Math.max(0, Math.min(bindingTemplateComboIndex.get(), BINDING_TEMPLATE_VALUES.length - 1));
			List<AnimationBindingRule> templated = new ArrayList<>(
				AnimationBindingEngine.createTemplateRules(timeline, BINDING_TEMPLATE_VALUES[idx]));
			if (!templated.isEmpty()) {
				TemplateMergeResult merge = mergeTemplateRules(rules, templated);
				rules = merge.merged();
				AnimationBindingEngine.saveRules(timeline, rules);
				setTemplateApplyFeedback(
					"Template " + BINDING_TEMPLATE_LABELS[idx] + " appended: +" + merge.added() + ", skipped " + merge.skipped(),
					merge.added() > 0);
			} else {
				setTemplateApplyFeedback("Template " + BINDING_TEMPLATE_LABELS[idx] + " produced no rules", false);
			}
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip("保留现有规则并追加模板（自动去重）");
		renderTemplateApplyFeedback();

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

				int sectionIndex = indexOfSectionFilter(sectionFilters, rule.sectionFilter());
				ImInt sectionCombo = new ImInt(Math.max(0, sectionIndex));
				if (!sectionFilters.isEmpty() && ImGui.combo("Section", sectionCombo, toArray(sectionFilters))) changed = true;
				sectionIndex = sectionCombo.get();
				if (sectionIndex < 0 || sectionIndex >= sectionFilters.size()) sectionIndex = 0;

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

				Map<String, Object> extraCopy = new HashMap<>(rule.extraParams());
				String uiAnimation = animationIds.isEmpty() ? rule.animationTypeId() : animationIds.get(animationIndex);
				if ("WaveMotion".equalsIgnoreCase(uiAnimation)) {
					float[] waveAmp = new float[] { (float) extraParamAsDouble(extraCopy, "waveAmplitude", 0.5) };
					float[] wavePhase = new float[] { (float) extraParamAsDouble(extraCopy, "wavePhaseOffset", 0.5) };
					if (ImGui.sliderFloat("Wave Amp", waveAmp, 0f, 3f, "%.2f")) changed = true;
					if (ImGui.sliderFloat("Wave Phase", wavePhase, 0f, 3f, "%.2f")) changed = true;
					extraCopy.put("waveAmplitude", waveAmp[0]);
					extraCopy.put("wavePhaseOffset", wavePhase[0]);
				} else if ("BlockExplosion".equalsIgnoreCase(uiAnimation)) {
					float[] impactRadius = new float[] { (float) extraParamAsDouble(extraCopy, "impactRadius", 4.0) };
					float[] impactBurst = new float[] { (float) extraParamAsDouble(extraCopy, "impactBurst", 1.0) };
					if (ImGui.sliderFloat("Impact Radius", impactRadius, 1f, 16f, "%.1f")) changed = true;
					if (ImGui.sliderFloat("Impact Burst", impactBurst, 0f, 3f, "%.2f")) changed = true;
					extraCopy.put("impactRadius", impactRadius[0]);
					extraCopy.put("impactBurst", impactBurst[0]);
				} else if ("BlockDrop".equalsIgnoreCase(uiAnimation)) {
					float[] meteorHeight = new float[] { (float) extraParamAsDouble(extraCopy, "meteorHeight", 8.0) };
					float[] meteorScatter = new float[] { (float) extraParamAsDouble(extraCopy, "meteorScatter", 2.0) };
					if (ImGui.sliderFloat("Meteor Height", meteorHeight, 2f, 32f, "%.1f")) changed = true;
					if (ImGui.sliderFloat("Meteor Scatter", meteorScatter, 0f, 8f, "%.1f")) changed = true;
					extraCopy.put("meteorHeight", meteorHeight[0]);
					extraCopy.put("meteorScatter", meteorScatter[0]);
				}

				String uiAction = BINDING_ACTION_VALUES[actionIndex];
				if ("BUILD".equalsIgnoreCase(uiAction)) {
					String[] buildModeLabels = { "WALL", "BRIDGE", "TOWER", "DISSOLVE" };
					int bmIdx = indexOfValue(buildModeLabels, String.valueOf(extraCopy.getOrDefault("buildMode", "WALL")));
					ImInt bmCombo = new ImInt(Math.max(0, bmIdx));
					if (ImGui.combo("Build Mode", bmCombo, buildModeLabels)) changed = true;
					extraCopy.put("buildMode", buildModeLabels[Math.max(0, Math.min(bmCombo.get(), buildModeLabels.length - 1))]);

					ImString blockBuf = new ImString(128);
					blockBuf.set(String.valueOf(extraCopy.getOrDefault("placeBlock", "minecraft:diamond_block")));
					if (ImGui.inputText("Block ID##buildBlockId", blockBuf)) changed = true;
					extraCopy.put("placeBlock", blockBuf.get().trim());

					imgui.type.ImBoolean dissolveFlag = new imgui.type.ImBoolean(
						"true".equalsIgnoreCase(String.valueOf(extraCopy.getOrDefault("buildDissolve", "false"))));
					if (ImGui.checkbox("Dissolve (reverse)", dissolveFlag)) changed = true;
					extraCopy.put("buildDissolve", String.valueOf(dissolveFlag.get()));
				}

				if (changed) {
					String selectedFeature = featureKeys.get(featureIndex);
					String selectedAnimation = animationIds.isEmpty() ? rule.animationTypeId() : animationIds.get(animationIndex);
					String selectedTargetDisplay = targetDisplays.isEmpty() ? "" : targetDisplays.get(targetIndex);
					String selectedTargetId = targetDisplayToId.getOrDefault(selectedTargetDisplay, rule.targetObjectId());
					String selectedSection = sectionFilters.isEmpty() ? BINDING_SECTION_ALL : sectionFilters.get(sectionIndex);
					String sectionFilter = BINDING_SECTION_ALL.equalsIgnoreCase(selectedSection) ? "" : selectedSection.toLowerCase(Locale.ROOT);
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
						.sectionFilter(sectionFilter)
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
			setTemplateApplyFeedback("Apply To Block Track generated " + lastBindingMapCount + " events", lastBindingMapCount > 0);
		}
		ImGui.sameLine();
		if (ImGui.button("Apply To Auto Track##bindingApplyAuto")) {
			lastBindingMapCount = AnimationBindingEngine.applyRules(timeline, TimelineTrackMeta.ROW_ANIM_AUTO, false);
			if (BeatBlock.timelineEditor != null) BeatBlock.timelineEditor.syncClockDuration();
			setTemplateApplyFeedback("Apply To Auto Track generated " + lastBindingMapCount + " events", lastBindingMapCount > 0);
		}

		ImGui.endPopup();
	}

	private AnimationBindingRule buildAddedRule(List<String> featureKeys, List<String> targetDisplays, Map<String, String> targetDisplayToId) {
		if (featureKeys == null || featureKeys.isEmpty()) return null;
		if (targetDisplays == null || targetDisplays.isEmpty()) return null;
		String feature = featureKeys.getFirst();
		String targetDisplay = targetDisplays.getFirst();
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
		if (BeatBlock.blockAnimationEngine != null) {
			List<AnimationDefinition> defs = new ArrayList<>(BeatBlock.blockAnimationEngine.getAnimationLibrary().getAll().values());
			defs.sort(Comparator.comparing(AnimationDefinition::getId, String.CASE_INSENSITIVE_ORDER));
			for (AnimationDefinition def : defs) {
				ids.add(def.getId());
			}
		}
		if (ids.isEmpty()) ids.add("Pulse");
		return ids;
	}

	private List<String> collectSectionFilters(Timeline timeline) {
		LinkedHashSet<String> filters = new LinkedHashSet<>();
		filters.add(BINDING_SECTION_ALL);
		if (timeline == null) return new ArrayList<>(filters);
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null || marker.getType() != MarkerType.SECTION) continue;
			String label = extractSectionFilterLabel(marker.getName());
			if (!label.isBlank()) filters.add(label.toUpperCase(Locale.ROOT));
		}
		return new ArrayList<>(filters);
	}

	private static String extractSectionFilterLabel(String markerName) {
		if (markerName == null) return "";
		String text = markerName.trim();
		if (text.isBlank()) return "";
		String upper = text.toUpperCase(Locale.ROOT);
		if (upper.startsWith("SECTION ")) {
			text = text.substring("SECTION ".length()).trim();
		}
		return text;
	}

	private List<String> collectTargetDisplays(Map<String, String> outDisplayToId) {
		List<String> displays = new ArrayList<>();
		if (outDisplayToId == null) return displays;
		if (BeatBlock.blockAnimationEngine == null) {
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

	private static int indexOfSectionFilter(List<String> filters, String ruleSectionFilter) {
		if (filters == null || filters.isEmpty()) return 0;
		if (ruleSectionFilter == null || ruleSectionFilter.isBlank()) return 0;
		String wanted = ruleSectionFilter.trim().toUpperCase(Locale.ROOT);
		for (int i = 0; i < filters.size(); i++) {
			if (wanted.equalsIgnoreCase(filters.get(i))) return i;
		}
		return 0;
	}

	private static double extraParamAsDouble(Map<String, Object> params, String key, double fallback) {
		if (params == null || key == null || key.isBlank()) return fallback;
		Object raw = params.get(key);
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}

	private record TemplateMergeResult(List<AnimationBindingRule> merged, int added, int skipped) {}

	private static TemplateMergeResult mergeTemplateRules(List<AnimationBindingRule> existing, List<AnimationBindingRule> incoming) {
		List<AnimationBindingRule> out = new ArrayList<>();
		if (existing != null) out.addAll(existing);
		if (incoming == null || incoming.isEmpty()) return new TemplateMergeResult(out, 0, 0);

		LinkedHashSet<String> fingerprints = new LinkedHashSet<>();
		int added = 0;
		int skipped = 0;
		for (AnimationBindingRule rule : out) {
			if (rule == null) continue;
			fingerprints.add(ruleFingerprint(rule));
		}
		for (AnimationBindingRule rule : incoming) {
			if (rule == null) continue;
			String fp = ruleFingerprint(rule);
			if (fingerprints.contains(fp)) {
				skipped++;
				continue;
			}
			fingerprints.add(fp);
			out.add(rule);
			added++;
		}
		return new TemplateMergeResult(out, added, skipped);
	}

	private static String ruleFingerprint(AnimationBindingRule rule) {
		if (rule == null) return "";
		StringBuilder sb = new StringBuilder(256);
		sb.append(rule.sourceFeatureKey().toLowerCase(Locale.ROOT)).append('|');
		sb.append(rule.animationTypeId().toLowerCase(Locale.ROOT)).append('|');
		sb.append(rule.actionMode().name()).append('|');
		sb.append(rule.targetObjectId().toLowerCase(Locale.ROOT)).append('|');
		sb.append(rule.sectionFilter().toLowerCase(Locale.ROOT)).append('|');
		sb.append(rule.spatialMode().name()).append('|');
		sb.append(String.format(Locale.ROOT, "%.3f|%.3f|%.3f|%.3f|%.3f|%.3f",
			rule.energyThreshold(),
			rule.energyScale(),
			rule.durationSeconds(),
			rule.cooldownSeconds(),
			rule.probability(),
			rule.sequentialDelaySeconds()));
		if (!rule.extraParams().isEmpty()) {
			Map<String, String> sorted = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			for (Map.Entry<String, Object> e : rule.extraParams().entrySet()) {
				if (e.getKey() == null) continue;
				sorted.put(e.getKey().toLowerCase(Locale.ROOT), String.valueOf(e.getValue()));
			}
			sb.append('|').append(sorted);
		}
		return sb.toString();
	}

	private void setTemplateApplyFeedback(String message, boolean success) {
		lastTemplateApplyFeedback = message != null ? message : "";
		lastTemplateApplyFeedbackSuccess = success;
		lastTemplateApplyFeedbackAtMs = System.currentTimeMillis();
	}

	private void setToolActionFeedback(String message, boolean success) {
		lastToolActionFeedback = message != null ? message : "";
		lastToolActionFeedbackSuccess = success;
		lastToolActionFeedbackAtMs = System.currentTimeMillis();
	}

	private void renderToolActionFeedback() {
		renderFadingFeedback(
			lastToolActionFeedback,
			lastToolActionFeedbackSuccess,
			lastToolActionFeedbackAtMs,
			msg -> lastToolActionFeedback = msg
		);
	}

	private void renderTemplateApplyFeedback() {
		renderFadingFeedback(
			lastTemplateApplyFeedback,
			lastTemplateApplyFeedbackSuccess,
			lastTemplateApplyFeedbackAtMs,
			msg -> lastTemplateApplyFeedback = msg
		);
	}

	private void renderFadingFeedback(String message, boolean success, long messageAtMs, java.util.function.Consumer<String> clearSink) {
		if (message == null || message.isBlank()) return;
		final long now = System.currentTimeMillis();
		final long ageMs = Math.max(0L, now - messageAtMs);
		final long holdMs = 1700L;
		final long fadeMs = 1300L;
		final long ttlMs = holdMs + fadeMs;
		if (ageMs >= ttlMs) {
			if (clearSink != null) clearSink.accept("");
			return;
		}

		float alpha = 1.0f;
		if (ageMs > holdMs) {
			float t = (ageMs - holdMs) / (float) fadeMs;
			alpha = Math.max(0f, 1.0f - t);
		}

		if (success) {
			ImGui.textColored(0.55f, 0.92f, 0.62f, alpha, message);
		} else {
			ImGui.textColored(0.95f, 0.80f, 0.42f, alpha, message);
		}
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
			if (txt.isBlank()) return;
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

			applyDefaultScaleFromJson(dm, "durationScale", "demucsMapDurationScale", DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
			applyDefaultScaleFromJson(dm, "energyScale", "demucsMapEnergyScale", DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX);
			applyDefaultScaleFromJson(dm, "gapScale", "demucsMapGapScale", DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);

			if (dm.has("featureScale") && dm.get("featureScale").isJsonObject()) {
				JsonObject featureScale = dm.getAsJsonObject("featureScale");
				for (String featureKey : DEMUCS_FEATURE_KEYS) {
					if (!featureScale.has(featureKey) || !featureScale.get(featureKey).isJsonObject()) continue;
					JsonObject featureObj = featureScale.getAsJsonObject(featureKey);
					applyDefaultScaleFromJson(featureObj, "durationScale", featureMetadataKey(featureKey, "duration"), DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
					applyDefaultScaleFromJson(featureObj, "energyScale", featureMetadataKey(featureKey, "energy"), DEMUCS_ENERGY_SCALE_MIN, DEMUCS_ENERGY_SCALE_MAX);
					applyDefaultScaleFromJson(featureObj, "gapScale", featureMetadataKey(featureKey, "gap"), DEMUCS_SCALE_MIN, DEMUCS_SCALE_MAX);
				}
			}
		} catch (Exception e) {
			LOGGER.debug("BeatBlock TimelineToolbar: failed to read ui.json demucs mapping config reason={}", e.toString());
		}
	}

	private void applyDefaultScaleFromJson(JsonObject dm, String jsonKey, String metadataKey,
										   double min, double max) {
		if (BeatBlock.timeline == null) return;
		if (BeatBlock.timeline.getMetadata(metadataKey) != null) return;
		double v = 1.0;
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
				if (!existing.isBlank()) {
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
			if (txt.isBlank()) return;
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
				if (!existing.isBlank()) {
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

	/**
	 * 组间换行检测：如果当前行剩余空间不够放下一个元素组，就不调 sameLine，让 ImGui 自动换行。
	 * @param estimatedNextGroupWidth 下一组控件的近似宽度，0 表示使用默认阈值。
	 */
	private static void nextGroupOrWrap(float estimatedNextGroupWidth) {
		float threshold = estimatedNextGroupWidth > 0 ? estimatedNextGroupWidth : 80f;
		// 先 sameLine 把光标移到上一个控件右侧，再检查剩余空间
		ImGui.sameLine(0f, TOOLBAR_GROUP_SPACING);
		if (ImGui.getContentRegionAvailX() < threshold) {
			ImGui.newLine();
		}
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
}
