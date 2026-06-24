package com.beatblock.audio.ffmpeg;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 ffmpeg stderr/stdout 中的 Duration 与 time= 进度行。
 */
final class FfmpegProgressParser {

	private static final Pattern DURATION_PATTERN = Pattern.compile("Duration:\\s*([0-9:.]+)");
	private static final Pattern TIME_PATTERN = Pattern.compile("time=([0-9:.]+)");

	@FunctionalInterface
	interface ProgressSink {
		void accept(String message, int percent);
	}

	private FfmpegProgressParser() {}

	static void parseLine(String line, double[] totalDurationSec, ProgressSink onProgress) {
		if (line == null || line.isBlank() || onProgress == null) {
			return;
		}

		Matcher durationMatcher = DURATION_PATTERN.matcher(line);
		if (durationMatcher.find()) {
			double duration = parseHmsToSeconds(durationMatcher.group(1));
			if (duration > 0) {
				totalDurationSec[0] = duration;
				onProgress.accept("读取音频时长...", 4);
			}
		}

		Matcher timeMatcher = TIME_PATTERN.matcher(line);
		if (timeMatcher.find()) {
			double current = parseHmsToSeconds(timeMatcher.group(1));
			if (current <= 0) {
				return;
			}
			if (totalDurationSec[0] > 0) {
				int pct = (int) Math.round((current / totalDurationSec[0]) * 100.0);
				pct = Math.max(5, Math.min(98, pct));
				onProgress.accept("FFmpeg 转换中", pct);
			} else {
				onProgress.accept("FFmpeg 转换中（无法估时）", 10);
			}
		}
	}

	static double parseHmsToSeconds(String hms) {
		if (hms == null || hms.isBlank()) {
			return 0.0;
		}
		String[] parts = hms.trim().split(":");
		if (parts.length != 3) {
			return 0.0;
		}
		try {
			double h = Double.parseDouble(parts[0]);
			double m = Double.parseDouble(parts[1]);
			double s = Double.parseDouble(parts[2]);
			return (h * 3600.0) + (m * 60.0) + s;
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}
}
