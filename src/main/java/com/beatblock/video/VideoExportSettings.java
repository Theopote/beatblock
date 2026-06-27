package com.beatblock.video;

import java.nio.file.Path;

/**
 * 视频导出参数。
 */
public record VideoExportSettings(
	Path outputPath,
	int width,
	int height,
	int fps,
	double startTimeSeconds,
	double endTimeSeconds,
	boolean includeAudio
) {
	public VideoExportSettings {
		fps = Math.max(1, fps);
		startTimeSeconds = Math.max(0.0, startTimeSeconds);
		endTimeSeconds = Math.max(startTimeSeconds + 0.01, endTimeSeconds);
	}

	public double durationSeconds() {
		return Math.max(0.01, endTimeSeconds - startTimeSeconds);
	}

	public int totalFrames() {
		return Math.max(1, (int) Math.ceil(durationSeconds() * fps));
	}
}
