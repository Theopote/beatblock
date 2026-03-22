package com.beatblock.audio;

import com.beatblock.timeline.IAudioPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 音乐播放与进度控制，与 BeatScheduler 同步驱动时间轴。
 */
public class MusicPlayer implements IAudioPlayer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MusicPlayer.class);

	private boolean playing;
	private double currentTimeSeconds;
	private double durationSeconds;
	private double playbackSpeed = 1.0;
	private Clip audioClip;
	private String loadedAudioPath;
	private String lastLoadError;

	public MusicPlayer() {
		this.playing = false;
		this.currentTimeSeconds = 0;
		this.durationSeconds = 0;
	}

	public boolean isPlaying() {
		syncClipState();
		return playing;
	}

	public void setPlaying(boolean playing) {
		this.playing = playing;
	}

	public void play() {
		syncClipState();
		if (audioClip != null) {
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				currentTimeSeconds = 0;
				audioClip.setMicrosecondPosition(0);
			}
			audioClip.start();
		}
		playing = true;
	}

	public void pause() {
		if (audioClip != null) {
			audioClip.stop();
			currentTimeSeconds = clipPositionSeconds();
		}
		playing = false;
	}

	public void stop() {
		if (audioClip != null) {
			audioClip.stop();
			audioClip.setMicrosecondPosition(0);
		}
		playing = false;
		currentTimeSeconds = 0;
	}

	public double getCurrentTimeSeconds() {
		syncClipState();
		return currentTimeSeconds;
	}

	public void setCurrentTimeSeconds(double currentTimeSeconds) {
		if (durationSeconds > 0) {
			this.currentTimeSeconds = Math.max(0, Math.min(currentTimeSeconds, durationSeconds));
		} else {
			this.currentTimeSeconds = Math.max(0, currentTimeSeconds);
		}

		if (audioClip != null) {
			boolean restart = playing && audioClip.isRunning();
			audioClip.stop();
			audioClip.setMicrosecondPosition((long) Math.max(0, this.currentTimeSeconds * 1_000_000.0));
			if (restart) {
				audioClip.start();
			}
		}
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = Math.max(0, durationSeconds);
	}

	public double getPlaybackSpeed() {
		return playbackSpeed;
	}

	public void setPlaybackSpeed(double playbackSpeed) {
		this.playbackSpeed = Math.max(0.25, Math.min(4.0, playbackSpeed));
	}

	public boolean loadAudio(String path) {
		closeAudioClip();
		loadedAudioPath = null;
		lastLoadError = null;
		if (path == null || path.isBlank()) {
			lastLoadError = "未提供音频路径";
			return false;
		}

		Path file = Path.of(path).toAbsolutePath().normalize();
		if (!Files.isRegularFile(file)) {
			lastLoadError = "音频文件不存在: " + file;
			return false;
		}

		try {
			loadClip(file);
			loadedAudioPath = file.toString();
			playing = false;
			currentTimeSeconds = 0;
			lastLoadError = null;
			return true;
		} catch (UnsupportedAudioFileException e) {
			lastLoadError = "格式不受当前音频后端支持: " + file.getFileName();
			LOGGER.warn("BeatBlock: unsupported audio format for {}: {}", file, e.getMessage());
			closeAudioClip();
			return false;
		} catch (LineUnavailableException e) {
			lastLoadError = "无法打开系统音频输出设备";
			LOGGER.warn("BeatBlock: audio output unavailable for {}: {}", file, e.getMessage());
			closeAudioClip();
			return false;
		} catch (Exception e) {
			lastLoadError = "音频绑定失败: " + e.getMessage();
			LOGGER.warn("BeatBlock: audio playback unavailable for {}: {}", file, e.getMessage());
			closeAudioClip();
			return false;
		}
	}

	public String getLoadedAudioPath() {
		return loadedAudioPath;
	}

	public String getPlaybackStatusText() {
		if (loadedAudioPath == null || loadedAudioPath.isBlank()) {
			return "音频输出: 未绑定";
		}
		Path p = Path.of(loadedAudioPath);
		String name = p.getFileName() != null ? p.getFileName().toString() : loadedAudioPath;
		return "音频输出: " + name;
	}

	public String getLastLoadError() {
		return lastLoadError;
	}

	private void loadClip(Path file) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
			AudioFormat srcFmt = source.getFormat();
			AudioFormat pcmFmt = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				srcFmt.getSampleRate(),
				16,
				srcFmt.getChannels(),
				srcFmt.getChannels() * 2,
				srcFmt.getSampleRate(),
				false
			);
			try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFmt, source)) {
				Clip clip = AudioSystem.getClip();
				clip.open(pcmStream);
				audioClip = clip;
				durationSeconds = clip.getMicrosecondLength() / 1_000_000.0;
			}
		}
	}

	private void syncClipState() {
		if (audioClip == null) return;
		currentTimeSeconds = clipPositionSeconds();
		if (playing && !audioClip.isRunning()) {
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				currentTimeSeconds = durationSeconds;
			}
			playing = false;
		}
	}

	private double clipPositionSeconds() {
		if (audioClip == null) return currentTimeSeconds;
		return audioClip.getMicrosecondPosition() / 1_000_000.0;
	}

	private void closeAudioClip() {
		if (audioClip == null) return;
		try {
			audioClip.stop();
			audioClip.close();
		} finally {
			audioClip = null;
		}
	}

	/**
	 * 每帧调用，推进播放进度（仅当 playing 时）。
	 */
	public void tick(double deltaSeconds) {
		if (audioClip != null) {
			syncClipState();
			return;
		}
		if (!playing) return;
		currentTimeSeconds = Math.max(0, currentTimeSeconds + deltaSeconds * playbackSpeed);
		if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds) {
			currentTimeSeconds = durationSeconds;
			playing = false;
		}
	}
}
