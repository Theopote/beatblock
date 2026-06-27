package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
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

	private final ImInt zoomComboIndex = new ImInt(2);
	private final ImInt speedComboIndex = new ImInt(2);
	private final ImInt actionRollbackComboIndex = new ImInt(0);

	private final TimelineTransportPresenter transport;
	private final TimelineToolbarTransportStrip transportStrip;
	private final TimelineToolbarActionRollbackControls actionRollbackControls;
	private final TimelineToolbarToolsControls toolsControls;
	private final TimelineToolbarEditControls editControls;
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
		this.transportStrip = new TimelineToolbarTransportStrip(transport);
		var bindingEditorPopup = new TimelineBindingEditorPopup(binding, feedback);
		var trackHeightControls = new TimelineToolbarTrackHeightControls();
		this.actionRollbackControls = new TimelineToolbarActionRollbackControls(config, actionRollbackComboIndex);
		this.toolsControls = new TimelineToolbarToolsControls(actions, feedback, bindingEditorPopup);
		this.editControls = new TimelineToolbarEditControls();
		this.snapGridControls = new TimelineToolbarSnapGridControls();
		this.viewControls = new TimelineToolbarViewControls(zoomComboIndex, trackHeightControls);
		this.loopSpeedControls = new TimelineToolbarLoopSpeedControls(transport, speedComboIndex, actionRollbackControls);
		this.demucsControls = new TimelineDemucsMappingControls(config);
		this.overflowMenu = new TimelineToolbarOverflowMenu(
			loopSpeedControls, snapGridControls, viewControls, toolsControls, editControls, demucsControls);
	}

	public void render(TimelineEditor editor, TimelineToolbarState toolbarState) {
		if (editor == null) return;

		boolean shiftHeld = ImGui.getIO().getKeyShift();
		var transportState = transport.viewState(editor, shiftHeld);
		double seekStep = transportState.seekStep();
		double stepSeek = transportState.stepSeek();
		double now = transportState.currentTimeSeconds();

		transportStrip.render(editor, transportState, stepSeek);
		TimelineToolbarImGui.nextGroupOrWrap(0);

		loopSpeedControls.renderInline(editor, toolbarState, seekStep, now);
		TimelineToolbarImGui.nextGroupOrWrap(0);

		snapGridControls.renderInline(toolbarState);
		viewControls.renderInline(editor);
		TimelineToolbarImGui.nextGroupOrWrap(0);

		editControls.renderInline(editor);
		TimelineToolbarImGui.nextGroupOrWrap(0);

		toolsControls.renderInline();
		TimelineToolbarImGui.nextGroupOrWrap(0);
		demucsControls.render(false, TimelineToolbarImGui::nextItemInGroup);

		TimelineToolbarImGui.nextGroupOrWrap(0);
		overflowMenu.renderButtonAndPopup(editor, toolbarState, seekStep);
	}
}
