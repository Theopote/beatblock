package com.beatblock.audio.playback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

/** SourceDataLine 流式 PCM 播放后端。 */
public final class StreamMusicBackend {

	private static final Logger LOGGER = LoggerFactory.getLogger(StreamMusicBackend.class);

	private byte[] pcmData;
	private AudioFormat format;
	private SourceDataLine line;
	private Thread thread;
	private volatile int bytePosition;
	private volatile int startBytePosition;
	private volatile double durationSeconds;
	private volatile boolean streamRunning;

	public boolean isActive() {
		return pcmData != null && format != null;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void load(byte[] pcmBytes, AudioFormat pcmFmt) {
		close(false);
		pcmData = pcmBytes;
		format = pcmFmt;
		bytePosition = 0;
		startBytePosition = 0;
		durationSeconds = pcmBytes.length / (pcmFmt.getFrameRate() * pcmFmt.getFrameSize());
	}

	public void close(boolean resetPosition) {
		stopPlayback(resetPosition);
		pcmData = null;
		format = null;
		durationSeconds = 0;
	}

	public double positionSeconds(double fallback) {
		if (!isActive()) return fallback;
		double bytesPerSecond = format.getFrameRate() * format.getFrameSize();
		if (bytesPerSecond <= 0.0) return 0.0;
		int pos = bytePosition;
		if (line != null) {
			long lineFrames = line.getLongFramePosition();
			int lineBytes = (int) Math.max(0, Math.min(Integer.MAX_VALUE, lineFrames * format.getFrameSize()));
			pos = Math.max(pos, Math.min(startBytePosition + lineBytes, pcmData.length));
		}
		return Math.max(0, pos / bytesPerSecond);
	}

	public int bytePositionForSeconds(double seconds) {
		if (!isActive()) return 0;
		double bytesPerSecond = format.getFrameRate() * format.getFrameSize();
		int raw = (int) Math.max(0, Math.round(seconds * bytesPerSecond));
		int frame = Math.max(1, format.getFrameSize());
		raw -= (raw % frame);
		return Math.max(0, Math.min(raw, pcmData.length));
	}

	public int getBytePosition() {
		return bytePosition;
	}

	public void setBytePosition(int bytePosition) {
		this.bytePosition = bytePosition;
	}

	public void setStreamRunning(boolean streamRunning) {
		this.streamRunning = streamRunning;
	}

	public boolean startPlayback(Logger logger, String loadedPath, StringBuilder lastLoadError) {
		if (!isActive()) return false;
		stopPlayback(false);
		byte[] data = pcmData;
		AudioFormat fmt = format;
		int startByte = Math.max(0, Math.min(bytePosition, data.length));
		int frameSize = Math.max(1, fmt.getFrameSize());
		startByte -= (startByte % frameSize);
		final int alignedStartByte = startByte;
		try {
			SourceDataLine opened = JavaSoundMixerSupport.openSourceDataLineFromAnyMixer(fmt);
			if (opened == null) {
				if (lastLoadError != null) {
					lastLoadError.setLength(0);
					lastLoadError.append("无法从任何混音器打开流式输出");
				}
				return false;
			}
			opened.start();
			line = opened;
			startBytePosition = alignedStartByte;
			bytePosition = alignedStartByte;
			streamRunning = true;
			Thread t = new Thread(
				() -> runLoop(opened, data, fmt, alignedStartByte),
				"BeatBlock-AudioStream"
			);
			t.setDaemon(true);
			thread = t;
			t.start();
			logger.info(
				"BeatBlock MusicPlayer: stream playback started path={} time={}s",
				loadedPath,
				String.format("%.3f", positionSeconds(0))
			);
			return true;
		} catch (Exception e) {
			if (lastLoadError != null) {
				lastLoadError.setLength(0);
				lastLoadError.append("无法打开流式音频输出设备: ").append(e.getMessage());
			}
			return false;
		}
	}

	public void stopPlayback(boolean resetPosition) {
		streamRunning = false;
		Thread t = thread;
		thread = null;
		SourceDataLine activeLine = line;
		line = null;
		if (activeLine != null) {
			try {
				activeLine.stop();
				activeLine.flush();
			} catch (RuntimeException e) {
				LOGGER.debug("Failed to stop audio line during playback shutdown", e);
			}
			try {
				activeLine.close();
			} catch (RuntimeException e) {
				LOGGER.debug("Failed to close audio line during playback shutdown", e);
			}
		}
		if (t != null && t != Thread.currentThread()) {
			try {
				t.join(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		if (resetPosition) {
			bytePosition = 0;
			startBytePosition = 0;
		}
	}

	public void applyGain(boolean muted) {
		if (line == null || !line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
		float gain = muted ? 0.0f : 1.0f;
		try {
			FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
			if (gain <= 0.0001f) {
				control.setValue(control.getMinimum());
			} else {
				float db = (float) (20.0 * Math.log10(gain));
				db = Math.max(control.getMinimum(), Math.min(control.getMaximum(), db));
				control.setValue(db);
			}
		} catch (RuntimeException e) {
			LOGGER.debug("Unable to set stream MASTER_GAIN", e);
		}
	}

	public boolean isThreadAlive() {
		return thread != null && thread.isAlive();
	}

	private void runLoop(SourceDataLine activeLine, byte[] data, AudioFormat fmt, int startByte) {
		int pos = startByte;
		int frameSize = Math.max(1, fmt.getFrameSize());
		byte[] buffer = new byte[8192];
		try {
			while (streamRunning && thread == Thread.currentThread()) {
				if (pos >= data.length) break;
				int len = Math.min(buffer.length, data.length - pos);
				len -= (len % frameSize);
				if (len <= 0) break;
				System.arraycopy(data, pos, buffer, 0, len);
				int written = activeLine.write(buffer, 0, len);
				if (written <= 0) break;
				pos += written;
				bytePosition = pos;
			}
		} finally {
			try {
				activeLine.stop();
				activeLine.flush();
			} catch (RuntimeException e) {
				LOGGER.debug("Failed to stop audio line in stream loop cleanup", e);
			}
			try {
				activeLine.close();
			} catch (RuntimeException e) {
				LOGGER.debug("Failed to close audio line in stream loop cleanup", e);
			}
			if (line == activeLine) {
				line = null;
			}
			if (thread == Thread.currentThread()) {
				thread = null;
			}
			bytePosition = Math.max(0, Math.min(pos, data.length));
		}
	}
}
