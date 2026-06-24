package com.beatblock.audio.ffmpeg;

import java.nio.file.Path;

public sealed interface FfmpegTranscodeOutcome permits
	FfmpegTranscodeOutcome.AlreadyMp3,
	FfmpegTranscodeOutcome.Success,
	FfmpegTranscodeOutcome.Failure {

	record AlreadyMp3(Path path) implements FfmpegTranscodeOutcome {}

	record Success(Path outputPath) implements FfmpegTranscodeOutcome {}

	record Failure(String message) implements FfmpegTranscodeOutcome {}
}
