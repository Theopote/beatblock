package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;

/**
 * 移动事件时间：execute 设为 newTime，undo 恢复 oldTime。
 */
public final class MoveEventCommand implements Command {

	private final Timeline timeline;
	private final String trackId;
	private final String clipId;
	private final String eventId;
	private final double oldTimeSeconds;
	private final double newTimeSeconds;

	public MoveEventCommand(Timeline timeline, String trackId, String clipId, String eventId, double oldTimeSeconds, double newTimeSeconds) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.clipId = clipId;
		this.eventId = eventId;
		this.oldTimeSeconds = oldTimeSeconds;
		this.newTimeSeconds = newTimeSeconds;
	}

	@Override
	public void execute() {
		apply(newTimeSeconds);
	}

	@Override
	public void undo() {
		apply(oldTimeSeconds);
	}

	private void apply(double timeSeconds) {
		if (timeline == null) return;
		var track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent e = clip.getEvent(eventId);
		if (e != null) {
			e.setTimeSeconds(timeSeconds);
			if (isAnimationTrack(trackId)) {
				timeline.markAnimationEventsDirty(trackId);
			}
		}
	}

	private static boolean isAnimationTrack(String trackId) {
		return Timeline.TRACK_ID_ANIMATION_BLOCK.equals(trackId)
			|| Timeline.TRACK_ID_ANIMATION_AUTO.equals(trackId)
			|| Timeline.TRACK_ID_BUILD_REVERSE.equals(trackId)
			|| Timeline.isBlockAnimationFeatureTrackId(trackId);
	}
}
