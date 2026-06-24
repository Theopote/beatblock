package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.TimelineBindingEditorPresenter;
import com.beatblock.ui.presenter.TimelineToolbarActionsPresenter;
import com.beatblock.ui.presenter.TimelineToolbarConfigPresenter;
import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
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

	private final ImInt zoomComboIndex = new ImInt(2);
	private final ImInt speedComboIndex = new ImInt(2);
	private final ImInt actionRollbackComboIndex = new ImInt(0);

	private final TimelineTransportPresenter transport;
	private final TimelineToolbarActionRollbackControls actionRollbackControls;
	private final TimelineToolbarToolsControls toolsControls;
	private final TimelineToolbarSnapGridControls snapGridControls;
	private final TimelineToolbarViewControls viewControls;
	private final TimelineToolbarLoopSpeedControls loopSpeedControls;
	private final TimelineToolbarOverflowMenu overflowMenu;
	private final TimelineDemucsMappingControls demucsControls;

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
		var bindingEditorPopup = new TimelineBindingEditorPopup(binding, feedback);
		var trackHeightControls = new TimelineToolbarTrackHeightControls();
		this.actionRollbackControls = new TimelineToolbarActionRollbackControls(config, actionRollbackComboIndex);
		this.toolsControls = new TimelineToolbarToolsControls(actions, feedback, bindingEditorPopup);
		this.snapGridControls = new TimelineToolbarSnapGridControls();
		this.viewControls = new TimelineToolbarViewControls(zoomComboIndex, trackHeightControls);
		this.loopSpeedControls = new TimelineToolbarLoopSpeedControls(transport, speedComboIndex, actionRollbackControls);
		this.demucsControls = new TimelineDemucsMappingControls(config);
		this.overflowMenu = new TimelineToolbarOverflowMenu(
			loopSpeedControls, snapGridControls, viewControls, toolsControls, demucsControls);
	}

	public void render(TimelineEditor editor, TimelineToolbarState toolbarState) {
		if (editor == null) return;

		boolean shiftHeld = ImGui.getIO().getKeyShift();
		var transportState = transport.viewState(editor, shiftHeld);
		double seekStep = transportState.seekStep();
		double stepSeek = transportState.stepSeek();
		double now = transportState.currentTimeSeconds();

		renderTransportStrip(editor, transportState, stepSeek);
		TimelineToolbarImGui.nextGroupOrWrap(0);

		loopSpeedControls.renderInline(editor, toolbarState, seekStep, now);
		TimelineToolbarImGui.nextGroupOrWrap(0);

		snapGridControls.renderInline(toolbarState);
		viewControls.renderInline(editor);
		TimelineToolbarImGui.nextGroupOrWrap(0);

		toolsControls.renderInline();
		TimelineToolbarImGui.nextGroupOrWrap(0);
		demucsControls.render(false, TimelineToolbarImGui::nextItemInGroup);

		TimelineToolbarImGui.nextGroupOrWrap(0);
		overflowMenu.renderButtonAndPopup(editor, toolbarState, seekStep);
	}

	private void renderTransportStrip(
		TimelineEditor editor,
		TimelineTransportPresenter.TransportViewState transportState,
		double stepSeek
	) {
		final float tBtn = TimelineLayout.ROW_HEIGHT;
		String transportTooltip;
		IconButtonStyle.pushBeatBlockIconButton();

		if (ImGui.button(Icons.Play.REWIND_START + "##tlToStart", tBtn, tBtn)) {
			transport.seekTo(editor, 0);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(null, TOOLTIP_TO_START);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.REWIND + "##tlBackBeat", tBtn, tBtn)) {
			transport.seekBy(editor, -stepSeek);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_BACK_BEAT);
		TimelineToolbarImGui.nextItemInGroup();

		if (transportState.playing()) {
			if (ImGui.button(Icons.Play.PAUSE + "##tlPause", tBtn, tBtn)) transport.pause();
			transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_PAUSE);
		} else {
			if (ImGui.button(Icons.Play.PLAY + "##tlPlay", tBtn, tBtn)) transport.play(editor);
			transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_PLAY);
		}
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.STOP + "##tlStop", tBtn, tBtn)) transport.stop(editor);
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_STOP);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.FORWARD + "##tlFwdBeat", tBtn, tBtn)) transport.seekBy(editor, stepSeek);
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_FWD_BEAT);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.FORWARD_END + "##tlToEnd", tBtn, tBtn)) {
			transport.seekTo(editor, transportState.durationSeconds());
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_TO_END);
		TimelineToolbarImGui.nextGroup();

		if (ImGui.button(Icons.Action.ARROW_LEFT + "##tlPrevEvt", tBtn, tBtn)) {
			transport.jumpToNearbyEvent(editor, false);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_PREV_EVENT);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Action.ARROW_RIGHT + "##tlNextEvt", tBtn, tBtn)) {
			transport.jumpToNearbyEvent(editor, true);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_NEXT_EVENT);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Timeline.MARKER + "##tlAddMarker", tBtn, tBtn)) {
			transport.addMarkerAtCurrentTime(editor);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_ADD_MARKER);
		IconButtonStyle.popBeatBlockIconButton();
		if (transportTooltip != null) ImGui.setTooltip(transportTooltip);

		TimelineToolbarImGui.nextGroup();
		ImGui.textDisabled(transportState.positionDisplay());
	}
}
