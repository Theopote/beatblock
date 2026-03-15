package com.beatblock.timeline.editor;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

/**
 * 添加事件：execute 在指定轨道的 Clip 中插入事件，undo 移除。
 */
public class AddEventCommand implements Command {

	private final Timeline timeline;
	private final String trackId;
	private final String clipId;
	private final TimelineEvent event;
	private boolean done;

	public AddEventCommand(Timeline timeline, String trackId, String clipId, TimelineEvent event) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.clipId = clipId;
		this.event = event;
	}

	@Override
	public void execute() {
		if (timeline == null || event == null || done) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		clip.addEvent(event);
		done = true;
	}

	@Override
	public void undo() {
		if (!done) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip != null) clip.removeEvent(event.getId());
		done = false;
	}
}
