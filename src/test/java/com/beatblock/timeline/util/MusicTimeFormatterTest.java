package com.beatblock.timeline.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicTimeFormatterTest {

	@Test
	void formatMmSsPadsSeconds() {
		assertEquals("0:00", MusicTimeFormatter.formatMmSs(0));
		assertEquals("1:03", MusicTimeFormatter.formatMmSs(63.7));
		assertEquals("62:03", MusicTimeFormatter.formatMmSs(3723));
	}

	@Test
	void formatMmSsFractionIncludesTenths() {
		assertEquals("1:03.7", MusicTimeFormatter.formatMmSsFraction(63.7));
	}

	@Test
	void formatBarBeatAt120Bpm() {
		assertEquals("Bar 1 Beat 1", MusicTimeFormatter.formatBarBeat(0, 120));
		assertEquals("Bar 2 Beat 1", MusicTimeFormatter.formatBarBeat(2.0, 120));
		assertEquals("Bar 2 Beat 2", MusicTimeFormatter.formatBarBeat(2.5, 120));
		assertEquals("", MusicTimeFormatter.formatBarBeat(1.0, 0));
	}

	@Test
	void formatPositionDisplayWithAndWithoutBpm() {
		assertEquals("1:00 / 3:00  |  Bar 31 Beat 1",
			MusicTimeFormatter.formatPositionDisplay(60, 180, 120));
		assertEquals("1:00 / 3:00",
			MusicTimeFormatter.formatPositionDisplay(60, 180, 0));
	}

	@Test
	void barAndBeatNumbers() {
		assertEquals(2, MusicTimeFormatter.barNumber(2.0, 120));
		assertEquals(2, MusicTimeFormatter.beatNumber(2.5, 120));
		assertEquals(0, MusicTimeFormatter.barNumber(1.0, -1));
	}
}
