package com.beatblock.timeline.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraSegmentKindTest {

	@Test
	void fromParamParsesKnownKinds() {
		assertEquals(CameraSegmentKind.DOLLY, CameraSegmentKind.fromParam("dolly"));
		assertEquals(CameraSegmentKind.ORBIT, CameraSegmentKind.fromParam("ORBIT"));
	}

	@Test
	void fromParamDefaultsToPath() {
		assertEquals(CameraSegmentKind.PATH, CameraSegmentKind.fromParam(null));
		assertEquals(CameraSegmentKind.PATH, CameraSegmentKind.fromParam("unknown"));
	}
}
