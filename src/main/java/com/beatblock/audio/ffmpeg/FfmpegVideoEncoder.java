package com.beatblock.audio.ffmpeg;

import com.beatblock.ui.i18n.BBTexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 通过 ffmpeg 将 raw RGBA 帧流编码为 MP4，可选混入音频轨。
 */
public final class FfmpegVideoEncoder implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegVideoEncoder.class);

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(String message, int percent);
	}

	private final Process process;
	private final OutputStream stdin;
	private final ProgressListener progressListener;
	private final Path outputPath;
	private final int totalFrames;
	private int framesWritten;
	private volatile boolean closed;

	public FfmpegVideoEncoder(
		String ffmpegExecutable,
		Path outputFile,
		int width,
		int height,
		int fps,
		int totalFrames,
		Path audioFile,
		ProgressListener progressListener
	) throws IOException {
		this.totalFrames = Math.max(1, totalFrames);
		this.outputPath = outputFile;
		this.progressListener = progressListener;
		Path parent = outputFile.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		List<String> command = buildVideoCommand(
			ffmpegExecutable,
			outputFile,
			width,
			height,
			fps,
			audioFile
		);
		LOGGER.info("Starting ffmpeg video encode: {}", command);
		this.process = new ProcessBuilder(command).redirectErrorStream(true).start();
		this.stdin = process.getOutputStream();
		startProgressReader();
		notifyProgress(BBTexts.get("beatblock.export.progress.starting_ffmpeg"), 1);
	}

	static List<String> buildVideoCommand(
		String ffmpegExecutable,
		Path outputFile,
		int width,
		int height,
		int fps,
		Path audioFile
	) {
		List<String> cmd = new ArrayList<>();
		cmd.add(ffmpegExecutable);
		cmd.add("-y");
		cmd.add("-f");
		cmd.add("rawvideo");
		cmd.add("-pix_fmt");
		cmd.add("rgba");
		cmd.add("-s");
		cmd.add(width + "x" + height);
		cmd.add("-r");
		cmd.add(String.valueOf(Math.max(1, fps)));
		cmd.add("-i");
		cmd.add("pipe:0");
		if (audioFile != null && Files.isRegularFile(audioFile)) {
			cmd.add("-i");
			cmd.add(audioFile.toAbsolutePath().toString());
		}
		cmd.add("-c:v");
		cmd.add("libx264");
		cmd.add("-preset");
		cmd.add("medium");
		cmd.add("-crf");
		cmd.add("18");
		cmd.add("-pix_fmt");
		cmd.add("yuv420p");
		if (audioFile != null && Files.isRegularFile(audioFile)) {
			cmd.add("-c:a");
			cmd.add("aac");
			cmd.add("-b:a");
			cmd.add("192k");
			cmd.add("-shortest");
		}
		cmd.add(outputFile.toAbsolutePath().toString());
		return List.copyOf(cmd);
	}

	private void startProgressReader() {
		Thread reader = new Thread(() -> {
			double[] totalDurationSec = {0.0};
			try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = br.readLine()) != null) {
					FfmpegProgressParser.parseLine(line, totalDurationSec, this::onFfmpegLine);
				}
			} catch (IOException e) {
				LOGGER.debug("ffmpeg stdout reader ended", e);
			}
		}, "beatblock-ffmpeg-video-progress");
		reader.setDaemon(true);
		reader.start();
	}

	private void onFfmpegLine(String message, int percent) {
		int framePercent = (int) Math.round((framesWritten * 100.0) / totalFrames);
		int merged = Math.max(framePercent, Math.min(percent, 99));
		notifyProgress(message, merged);
	}

	public synchronized void writeFrame(byte[] rgba) throws IOException {
		if (closed) {
			throw new IOException("FFmpeg encoder already closed.");
		}
		stdin.write(rgba);
		framesWritten++;
		int percent = Math.min(99, (int) Math.round((framesWritten * 100.0) / totalFrames));
		notifyProgress(BBTexts.get("beatblock.export.progress.writing_frame", framesWritten, totalFrames), percent);
	}

	public FfmpegTranscodeOutcome finishAndAwait() {
		try {
			closeStdin();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				return new FfmpegTranscodeOutcome.Failure(
					BBTexts.get("beatblock.export.error.encode_failed", exitCode)
				);
			}
			notifyProgress(BBTexts.get("beatblock.export.progress.encode_complete"), 100);
			return new FfmpegTranscodeOutcome.Success(outputPath);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return new FfmpegTranscodeOutcome.Failure(BBTexts.get("beatblock.export.cancelled"));
		} catch (IOException e) {
			process.destroyForcibly();
			return new FfmpegTranscodeOutcome.Failure(BBTexts.get("beatblock.export.error.finish_encode", e.getMessage()));
		} finally {
			closed = true;
		}
	}

	private void closeStdin() throws IOException {
		if (!closed) {
			stdin.flush();
			stdin.close();
			closed = true;
		}
	}

	private void notifyProgress(String message, int percent) {
		if (progressListener != null) {
			progressListener.onProgress(message, percent);
		}
	}

	@Override
	public void close() {
		if (!closed) {
			try {
				closeStdin();
			} catch (IOException ignored) {
			}
		}
		if (process.isAlive()) {
			process.destroyForcibly();
		}
	}

	public static String formatResolutionLabel(int width, int height) {
		return width + "x" + height;
	}

	public static int[] resolveTargetSize(int presetWidth, int presetHeight, int nativeWidth, int nativeHeight) {
		if (presetWidth > 0 && presetHeight > 0) {
			return new int[] { presetWidth, presetHeight };
		}
		return new int[] { Math.max(1, nativeWidth), Math.max(1, nativeHeight) };
	}

	public static String defaultOutputFileName(String projectHint) {
		String base = projectHint != null && !projectHint.isBlank()
			? sanitizeFileStem(projectHint)
			: "beatblock_export";
		return base + "_" + System.currentTimeMillis() + ".mp4";
	}

	private static String sanitizeFileStem(String raw) {
		String trimmed = raw.trim();
		int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
		if (slash >= 0 && slash + 1 < trimmed.length()) {
			trimmed = trimmed.substring(slash + 1);
		}
		int dot = trimmed.lastIndexOf('.');
		if (dot > 0) {
			trimmed = trimmed.substring(0, dot);
		}
		String sanitized = trimmed.replaceAll("[^A-Za-z0-9._\\- ]", "_").trim();
		return sanitized.isBlank() ? "beatblock_export" : sanitized;
	}
}
