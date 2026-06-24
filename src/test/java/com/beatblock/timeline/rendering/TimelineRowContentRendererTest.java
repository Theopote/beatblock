package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class TimelineRowContentRendererTest {

	@Test
	void resolveClipWaveformDataReturnsNullWithoutClipPathMetadata() {
		Timeline timeline = Timeline.createDefault();
		var clip = TimelineOperations.addClip(timeline, Timeline.TRACK_ID_AUDIO, 0, 2.0);

		assertNull(TimelineRowContentRenderer.resolveClipWaveformData(timeline, clip, null));
	}
}
