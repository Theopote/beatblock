package com.beatblock.audio.ffmpeg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegProgressParserTest {

	@Test
	void parseHmsToSecondsHandlesStandardFormat() {
		assertEquals(3661.5, FfmpegProgressParser.parseHmsToSeconds("01:01:01.50"), 1e-9);
	}

	@Test
	void parseLineReportsDurationAndProgress() {
		double[] total = {0.0};
		List<String> messages = new ArrayList<>();
		List<Integer> percents = new ArrayList<>();

		FfmpegProgressParser.parseLine(
			"Duration: 00:02:00.00",
			total,
			(msg, pct) -> {
				messages.add(msg);
				percents.add(pct);
			}
		);
		assertEquals(120.0, total[0], 1e-9);
		assertEquals("读取音频时长...", messages.get(0));
		assertEquals(4, percents.get(0));

		FfmpegProgressParser.parseLine(
			"size=      10kB time=00:01:00.00 bitrate=   1.0kbits/s speed=1x",
			total,
			(msg, pct) -> {
				messages.add(msg);
				percents.add(pct);
			}
		);
		assertEquals("FFmpeg 转换中", messages.get(1));
		assertEquals(50, percents.get(1));
	}
}
