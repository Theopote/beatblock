package com.beatblock.ui.presenter;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

/**
 * 事件属性面板当前编辑目标。
 * <p>
 * {@code event} 可为 {@code null}，表示仅选中摄像机片段（无具体事件焦点）。
 */
public record EventPropertiesRef(Track track, Clip clip, TimelineEvent event) {

	public static String refKey(EventPropertiesRef ref) {
		if (ref == null) {
			return "";
		}
		if (ref.event() == null) {
			return "clip:" + ref.clip().getId();
		}
		return "event:" + ref.event().getId();
	}
}
