package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WaveformDataTest {

	@Test
	void timeToIndexMapsAcrossDuration() {
		float[] samples = {0f, 0.5f, 1f};
		WaveformData data = new WaveformData(samples, 3.0, 44100);

		assertEquals(0, data.timeToIndex(0));
		assertEquals(1, data.timeToIndex(1.5));
		assertEquals(2, data.timeToIndex(3.0));
	}

	@Test
	void getSampleClampsIndex() {
		WaveformData data = new WaveformData(new float[]{0.2f, 0.8f}, 2.0, 44100);
		assertEquals(0.2f, data.getSample(-5));
		assertEquals(0.8f, data.getSample(99));
	}

	@Test
	void getSamplesReturnsDefensiveCopy() {
		float[] original = {0.1f, 0.9f};
		WaveformData data = new WaveformData(original, 1.0, 44100);
		float[] copy = data.getSamples();
		copy[0] = 0f;
		assertArrayEquals(new float[]{0.1f, 0.9f}, data.getSamples());
	}

	@Test
	void emptyWaveformReturnsZeroIndexAndSample() {
		WaveformData data = new WaveformData(null, 0, 0);
		assertEquals(0, data.getSampleCount());
		assertEquals(0, data.timeToIndex(1.0));
		assertEquals(0f, data.getSample(0));
	}
}
