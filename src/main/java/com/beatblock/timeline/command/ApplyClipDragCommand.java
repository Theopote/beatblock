package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;

/**
 * 片段拖动 / 缩放：execute 应用 after 快照，undo 恢复 before 快照。
 */
public final class ApplyClipDragCommand implements Command {

	private final Timeline timeline;
	private final ClipDragStateSnapshot before;
	private final ClipDragStateSnapshot after;

	public ApplyClipDragCommand(Timeline timeline, ClipDragStateSnapshot before, ClipDragStateSnapshot after) {
		this.timeline = timeline;
		this.before = before;
		this.after = after;
	}

	@Override
	public void execute() {
		if (after != null) after.applyTo(timeline);
	}

	@Override
	public void undo() {
		if (before != null) before.applyTo(timeline);
	}
}
