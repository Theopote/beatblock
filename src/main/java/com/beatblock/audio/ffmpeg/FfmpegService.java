package com.beatblock.audio.ffmpeg;

import java.nio.file.Path;

/**
 * ffmpeg 能力统一入口：路径解析、PCM 解码、MP3 转码。
 */
public final class FfmpegService {

	private FfmpegService() {}

	public static String resolveExecutable() {
		return FfmpegLocator.resolveExecutable();
	}

	public static boolean isAvailable() {
		return FfmpegLocator.isAvailable();
	}

	public static byte[] decodeToPcm(Path inputFile, int sampleRate, int channels, int maxOutputBytes)
		throws java.io.IOException, InterruptedException {
		return FfmpegPcmDecoder.decodeToPcm(inputFile, sampleRate, channels, maxOutputBytes);
	}

	public static FfmpegTranscoder.Outcome transcodeToMp3(
		Path inputAudio,
		Path fallbackOutputDir,
		FfmpegTranscoder.ProgressListener onProgress
	) {
		return FfmpegTranscoder.transcodeToMp3(inputAudio, fallbackOutputDir, onProgress);
	}
}
