package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.presenter.TimelineTransportPresenter;
import imgui.ImGui;

/**
 * 时间线工具栏 Transport 图标条：播放控制、事件跳转、Marker、时间显示。
 */
final class TimelineToolbarTransportStrip {

	private final TimelineTransportPresenter transport;

	TimelineToolbarTransportStrip(TimelineTransportPresenter transport) {
		this.transport = transport;
	}

	void render(
		TimelineEditor editor,
		TimelineTransportPresenter.TransportViewState transportState,
		double stepSeek
	) {
		final float buttonSize = TimelineLayout.ROW_HEIGHT;
		String transportTooltip;
		IconButtonStyle.pushBeatBlockIconButton();

		if (ImGui.button(Icons.Play.REWIND_START + "##tlToStart", buttonSize, buttonSize)) {
			transport.seekTo(editor, 0);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(null, BBTexts.get("beatblock.timeline.transport.to_start"));
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.REWIND + "##tlBackBeat", buttonSize, buttonSize)) {
			transport.seekBy(editor, -stepSeek);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.back_beat"));
		TimelineToolbarImGui.nextItemInGroup();

		if (transportState.playing()) {
			if (ImGui.button(Icons.Play.PAUSE + "##tlPause", buttonSize, buttonSize)) transport.pause();
			transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.pause"));
		} else {
			if (ImGui.button(Icons.Play.PLAY + "##tlPlay", buttonSize, buttonSize)) transport.play(editor);
			transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.play"));
		}
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.STOP + "##tlStop", buttonSize, buttonSize)) transport.stop(editor);
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.stop"));
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.FORWARD + "##tlFwdBeat", buttonSize, buttonSize)) {
			transport.seekBy(editor, stepSeek);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.fwd_beat"));
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.FORWARD_END + "##tlToEnd", buttonSize, buttonSize)) {
			transport.seekTo(editor, transportState.durationSeconds());
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.to_end"));
		TimelineToolbarImGui.nextGroup();

		if (ImGui.button(Icons.Action.ARROW_LEFT + "##tlPrevEvt", buttonSize, buttonSize)) {
			transport.jumpToNearbyEvent(editor, false);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.prev_event"));
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Action.ARROW_RIGHT + "##tlNextEvt", buttonSize, buttonSize)) {
			transport.jumpToNearbyEvent(editor, true);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.next_event"));
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Timeline.MARKER + "##tlAddMarker", buttonSize, buttonSize)) {
			transport.addMarkerAtCurrentTime(editor);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, BBTexts.get("beatblock.timeline.transport.add_marker"));
		IconButtonStyle.popBeatBlockIconButton();
		if (transportTooltip != null) ImGui.setTooltip(transportTooltip);

		TimelineToolbarImGui.nextGroup();
		ImGui.textDisabled(transportState.positionDisplay());
	}
}
