package com.beatblock.timeline.editing;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.command.ApplyClipDragCommand;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.MoveEventCommand;
import com.beatblock.timeline.command.UpdateAnimationEventCommand;

/**
 * 通过 CommandManager 提交事件属性编辑，供 Panel 与 Interaction 共用。
 */
public final class TimelineEventEditActions {

	private TimelineEventEditActions() {}

	public static boolean execute(
		Timeline timeline,
		CommandManager commandManager,
		String trackId,
		String clipId,
		String eventId,
		AnimationEventSnapshot before,
		AnimationEventSnapshot after
	) {
		if (timeline == null || commandManager == null || trackId == null || clipId == null) {
			return false;
		}
		String resolvedEventId = eventId;
		if (resolvedEventId == null || resolvedEventId.isBlank()) {
			Clip clip = timeline.getTrack(trackId) != null ? timeline.getTrack(trackId).getClip(clipId) : null;
			if (clip == null || clip.getEvents().isEmpty()) return false;
			resolvedEventId = clip.getEvents().get(0).getId();
		}
		commandManager.execute(new UpdateAnimationEventCommand(
			timeline, trackId, clipId, resolvedEventId, before, after
		));
		return true;
	}

	public static boolean execute(
		Timeline timeline,
		CommandManager commandManager,
		String trackId,
		Clip clip,
		TimelineEvent event,
		AnimationEventSnapshot before,
		AnimationEventSnapshot after
	) {
		if (clip == null) return false;
		String eventId = event != null ? event.getId() : null;
		return execute(timeline, commandManager, trackId, clip.getId(), eventId, before, after);
	}

	public static boolean commitEventMove(
		Timeline timeline,
		CommandManager commandManager,
		String trackId,
		String clipId,
		String eventId,
		double oldTimeSeconds,
		double newTimeSeconds
	) {
		if (timeline == null || commandManager == null || trackId == null || clipId == null || eventId == null) {
			return false;
		}
		if (Math.abs(oldTimeSeconds - newTimeSeconds) < 1e-9) {
			return false;
		}
		commandManager.execute(new MoveEventCommand(
			timeline, trackId, clipId, eventId, oldTimeSeconds, newTimeSeconds
		));
		return true;
	}

	public static boolean commitClipDrag(
		Timeline timeline,
		CommandManager commandManager,
		ClipDragStateSnapshot before,
		ClipDragStateSnapshot after
	) {
		if (timeline == null || commandManager == null || before == null || after == null) {
			return false;
		}
		if (before.equals(after)) {
			return false;
		}
		commandManager.execute(new ApplyClipDragCommand(timeline, before, after));
		return true;
	}
}
