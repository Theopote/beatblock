package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.TimelineBindingEditorPresenter;
import com.beatblock.ui.presenter.TimelineToolbarActionsPresenter;
import com.beatblock.ui.presenter.TimelineToolbarConfigPresenter;
import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import com.beatblock.ui.presenter.TimelineTransportPresenter;
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
	private static final String TOOLTIP_ACTION_ROLLBACK = "PLACE/CLEAR 预览回滚策略：Preview 会在停止/回退时恢复方块；Persistent 会保留写入结果";
	private static final String TOOLTIP_ACTION_ROLLBACK_STATUS = "当前 PLACE/CLEAR 执行策略状态";
	private static final String TOOLTIP_BINDING_MAP = "按绑定规则将音频特征批量转换为动画事件；无规则时自动创建默认规则";
	private static final String TOOLTIP_BINDING_EDITOR = "编辑特征绑定规则：来源特征、动作、目标对象、阈值和冷却";
	private static final float TOOLBAR_ITEM_SPACING = 4f;

	private static final float TRACK_HEIGHT_SLIDER_WIDTH = 120f;
	private static final float TRACK_HEIGHT_SLIDER_WIDTH_COMPACT = 180f;
	private static final float TOOLBAR_GROUP_SPACING = 8f;

	private final ImInt zoomComboIndex = new ImInt(2); // 默认 1x
	private final ImInt speedComboIndex = new ImInt(2); // 默认 1x
	private final TimelineTransportPresenter transport;
	private final TimelineToolbarActionsPresenter actions;
	private final TimelineToolbarConfigPresenter config;
	private final TimelineBindingEditorPresenter binding;
	private final TimelineToolbarFeedbackPresenter feedback;
	private final TimelineBindingEditorPopup bindingEditorPopup;
	private final TimelineDemucsMappingControls demucsControls;
	private final ImInt actionRollbackComboIndex = new ImInt(0); // 默认 preview

	public TimelineToolbar() {
		this(
			PresenterFactories.timelineTransportPresenter(),
			PresenterFactories.timelineToolbarActionsPresenter(),
			PresenterFactories.timelineToolbarConfigPresenter(),
			PresenterFactories.timelineBindingEditorPresenter(),
			PresenterFactories.timelineToolbarFeedbackPresenter()
		);
	}

	TimelineToolbar(
		TimelineTransportPresenter transport,
		TimelineToolbarActionsPresenter actions,
		TimelineToolbarConfigPresenter config,
		TimelineBindingEditorPresenter binding,
		TimelineToolbarFeedbackPresenter feedback
	) {
		this.transport = transport;
		this.actions = actions;
		this.config = config;
		this.binding = binding;
		this.feedback = feedback;
		this.bindingEditorPopup = new TimelineBindingEditorPopup(binding, feedback);
		this.demucsControls = new TimelineDemucsMappingControls(config);
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
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(TimelineToolbarViewPresenter.SPEED_LABELS));
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
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS));
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
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_MAP);
		nextItemInGroup();
		if (ImGui.button("Bindings...##tlBindingEditorOpen")) {
			ImGui.openPopup(TimelineBindingEditorPopup.POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_EDITOR);
		bindingEditorPopup.renderIfOpen();
		nextItemInGroup();
		if (ImGui.button("Auto Map")) {
			var outcome = actions.runAutoMap();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_AUTO_MAP);
		nextItemInGroup();
		if (ImGui.button("烘焙 STEP##tlBakeStep")) {
			var outcome = actions.runBakeStepSequences();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BAKE_STEP);
		nextItemInGroup();
		renderToolActionFeedback();

		nextGroupOrWrap(0);
		demucsControls.render(false, TimelineToolbar::nextItemInGroup);

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
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(TimelineToolbarViewPresenter.SPEED_LABELS));
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
		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS));
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
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_MAP);
		ImGui.sameLine();
		if (ImGui.button("Bindings...##tlMoreBindingEditorOpen")) {
			ImGui.openPopup(TimelineBindingEditorPopup.POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_EDITOR);
		bindingEditorPopup.renderIfOpen();
		if (ImGui.button("Auto Map##tlMoreAutoMap")) {
			var outcome = actions.runAutoMap();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_AUTO_MAP);
		if (ImGui.button("烘焙 STEP##tlMoreBakeStep")) {
			var outcome = actions.runBakeStepSequences();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BAKE_STEP);
		renderToolActionFeedback();

		demucsControls.render(true, null);

		ImGui.endPopup();
	}

	private void renderActionRollbackControl(boolean compactMode) {
		config.ensureActionExecutionConfigLoaded();
		actionRollbackComboIndex.set(TimelineToolbarConfigPresenter.indexOfActionRollbackValue(config.readActionRollbackMode()));
		if (compactMode) {
			ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS));
			if (ImGui.combo("Rollback##tlMoreActionRollback", actionRollbackComboIndex, TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS)) {
				config.writeActionRollbackMode(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_VALUES[actionRollbackComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK);
			return;
		}

		ImGui.setNextItemWidth(TimelineToolbarImGui.comboWidthForLabels(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS));
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
		return TimelineToolbarImGui.comboWidthForLabels(values) + ImGui.calcTextSize(label).x + 10f;
	}

	private static float sliderTotalWidth(float sliderWidth) {
		return sliderWidth + ImGui.calcTextSize("Track H").x + 10f;
	}

	private void renderTrackHeightControl(TimelineEditor editor, boolean compactMode) {
		if (editor == null) return;
		var trackHeight = TimelineToolbarViewPresenter.trackHeightViewState(editor);
		float[] v = new float[] { trackHeight.current() };

		if (compactMode) {
			ImGui.separator();
			ImGui.textDisabled("Track Height");
			ImGui.setNextItemWidth(TRACK_HEIGHT_SLIDER_WIDTH_COMPACT);
			if (ImGui.sliderFloat("Track H##tlMoreTrackH", v, trackHeight.min(), trackHeight.max(), "%.0f px")) {
				TimelineToolbarViewPresenter.setTrackHeight(editor, v[0]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT);
			if (ImGui.button("Reset##tlMoreTrackHReset")) {
				TimelineToolbarViewPresenter.resetTrackHeight(editor);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT_RESET);
			return;
		}

		ImGui.setNextItemWidth(TRACK_HEIGHT_SLIDER_WIDTH);
		if (ImGui.sliderFloat("Track H", v, trackHeight.min(), trackHeight.max(), "%.0f px")) {
			TimelineToolbarViewPresenter.setTrackHeight(editor, v[0]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT);
		nextItemInGroup();
		if (ImGui.button("Reset##tlTrackHReset")) {
			TimelineToolbarViewPresenter.resetTrackHeight(editor);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT_RESET);
	}

	private void renderToolActionFeedback() {
		TimelineToolbarImGui.renderFeedback(feedback.viewToolActionFeedback());
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

	private static String hoveredTooltip(String current, String text) {
		if (current == null && ImGui.isItemHovered()) {
			return text;
		}
		return current;
	}
}
