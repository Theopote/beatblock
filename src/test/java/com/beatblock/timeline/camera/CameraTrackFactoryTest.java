package com.beatblock.timeline.camera;

import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraTrackFactoryTest {

	@Test
	void addPathSegmentCreatesClipWithSegmentAndKeyframe() {
		Timeline timeline = Timeline.createDefault();
		CameraTrackFactory.addPathSegment(timeline, 2.0, 10, 64, 20, 45, -10);

		var track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		assertEquals(1, track.getClips().size());
		var clip = track.getClips().getFirst();
		assertNotNull(CameraTrackFactory.findSegmentHeadEvent(clip));
		assertEquals(CameraSegmentKind.PATH.name(),
			CameraTrackFactory.findSegmentHeadEvent(clip).getParameter("kind"));

		long keyframeCount = clip.getEvents().stream()
			.filter(e -> e.getType() == EventType.CAMERA_KEYFRAME)
			.count();
		assertEquals(1, keyframeCount);
		assertTrue(timeline.getDurationSeconds() >= 6.0);
	}

	@Test
	void addDollySegmentComputesEndPointFromYaw() {
		Timeline timeline = Timeline.createDefault();
		CameraTrackFactory.addDollySegment(timeline, 0, 0, 64, 0, 0, 4.0);

		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		var segment = CameraTrackFactory.findSegmentHeadEvent(clip);
		assertNotNull(segment);
		assertEquals(CameraSegmentKind.DOLLY.name(), segment.getParameter("kind"));
		double endZ = ((Number) segment.getParameter("endZ")).doubleValue();
		assertEquals(4.0, endZ, 1e-3);
	}

	@Test
	void findSegmentHeadEventReturnsEarliestSegment() {
		Timeline timeline = Timeline.createDefault();
		CameraTrackFactory.addShakeSegment(timeline, 1.0, 0, 64, 0, 0, 0);
		var clip = timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().getFirst();
		assertNotNull(CameraTrackFactory.findSegmentHeadEvent(clip));
	}
}
