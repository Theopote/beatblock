package com.beatblock.timeline.editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineClockTest {

	private TimelineClock clock;

	@BeforeEach
	void setUp() {
		clock = new TimelineClock();
		clock.setDurationSeconds(10.0);
	}

	@Test
	void seekClampsToDuration() {
		clock.seek(15.0);
		assertEquals(10.0, clock.getCurrentTimeSeconds(), 1e-9);
		clock.seek(-3.0);
		assertEquals(0.0, clock.getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void updateStopsAtEndWhenNotLooping() {
		clock.setCurrentTimeSeconds(9.5);
		clock.play();
		clock.update(1.0);
		assertEquals(10.0, clock.getCurrentTimeSeconds(), 1e-9);
		assertFalse(clock.isPlaying());
	}

	@Test
	void updateLoopsWhenEnabled() {
		clock.setCurrentTimeSeconds(9.5);
		clock.play();
		clock.update(1.0, true);
		assertEquals(0.0, clock.getCurrentTimeSeconds(), 1e-9);
		assertTrue(clock.isPlaying());
	}

	@Test
	void playbackSpeedIsClamped() {
		clock.setPlaybackSpeed(99.0);
		assertEquals(4.0, clock.getPlaybackSpeed(), 1e-9);
		clock.setPlaybackSpeed(0.01);
		assertEquals(0.1, clock.getPlaybackSpeed(), 1e-9);
	}
}
