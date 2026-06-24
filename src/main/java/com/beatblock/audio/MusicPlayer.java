package com.beatblock.audio;

import com.beatblock.audio.ffmpeg.FfmpegService;
import com.beatblock.audio.playback.JavaSoundMixerSupport;
import com.beatblock.audio.playback.OpenAlMusicBackend;
import com.beatblock.audio.playback.StreamMusicBackend;
import com.beatblock.timeline.IAudioPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 音乐播放与进度控制，与 BeatScheduler 同步驱动时间轴。
 * 播放后端：Clip / SourceDataLine / OpenAL（见 {@code com.beatblock.audio.playback}）。
 */
public class MusicPlayer implements IAudioPlayer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MusicPlayer.class);

	private boolean playing;
	private double currentTimeSeconds;
	private double durationSeconds;
	private double playbackSpeed = 1.0;
	private Clip audioClip;
	private final StreamMusicBackend streamBackend = new StreamMusicBackend();
	private final OpenAlMusicBackend openAlBackend;
	private String loadedAudioPath;
	private String lastLoadError;
	private volatile boolean muted;
	private boolean recoveringOpenAl;

	public MusicPlayer() {
		this.playing = false;
		this.currentTimeSeconds = 0;
		this.durationSeconds = 0;
		this.muted = false;
		this.openAlBackend = new OpenAlMusicBackend(LOGGER, this::recoverOpenAlBackend);
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
		applyOutputGain();
	}

	public boolean isMuted() {
		return muted;
	}

	public boolean isPlaying() {
		syncClipState();
		return playing;
	}

	public void play() {
		syncClipState();
		if (audioClip != null) {
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				currentTimeSeconds = 0;
				audioClip.setMicrosecondPosition(0);
			}
			audioClip.start();
			LOGGER.info("BeatBlock MusicPlayer: playback started path={} time={}s", loadedAudioPath, String.format("%.3f", currentTimeSeconds));
		} else if (streamBackend.isActive()) {
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				streamBackend.setBytePosition(0);
				currentTimeSeconds = 0;
			}
			streamBackend.setStreamRunning(true);
			if (!startStreamPlayback()) {
				LOGGER.warn("BeatBlock MusicPlayer: stream playback start failed. lastLoadError={}", lastLoadError);
				playing = false;
				return;
			}
		} else if (openAlBackend.isActive()) {
			if (!ensureOpenAlBackendReady()) {
				LOGGER.warn("BeatBlock MusicPlayer: OpenAL backend is unavailable after device reset. lastLoadError={}", lastLoadError);
				playing = false;
				return;
			}
			boolean restart = durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001;
			if (restart) {
				currentTimeSeconds = 0;
			}
			openAlBackend.play(restart, currentTimeSeconds);
			LOGGER.info("BeatBlock MusicPlayer: OpenAL playback started path={} time={}s", loadedAudioPath, String.format("%.3f", currentTimeSeconds));
		} else {
			LOGGER.warn("BeatBlock MusicPlayer: play requested but no audio clip is loaded. lastLoadError={}", lastLoadError);
		}
		applyOutputGain();
		playing = true;
	}

	public void pause() {
		if (audioClip != null) {
			audioClip.stop();
			currentTimeSeconds = clipPositionSeconds();
		} else if (streamBackend.isActive()) {
			streamBackend.setStreamRunning(false);
			streamBackend.stopPlayback(false);
			currentTimeSeconds = streamBackend.positionSeconds(currentTimeSeconds);
		} else if (openAlBackend.isActive()) {
			if (!ensureOpenAlBackendReady()) {
				playing = false;
				return;
			}
			currentTimeSeconds = openAlBackend.positionSeconds(currentTimeSeconds);
			openAlBackend.pause(currentTimeSeconds);
		}
		playing = false;
	}

	public void stop() {
		if (audioClip != null) {
			audioClip.stop();
			audioClip.setMicrosecondPosition(0);
		} else if (streamBackend.isActive()) {
			streamBackend.setStreamRunning(false);
			streamBackend.stopPlayback(true);
		} else if (openAlBackend.isActive()) {
			if (!ensureOpenAlBackendReady()) {
				playing = false;
				currentTimeSeconds = 0;
				return;
			}
			openAlBackend.stop();
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
		} else if (streamBackend.isActive()) {
			streamBackend.setBytePosition(streamBackend.bytePositionForSeconds(this.currentTimeSeconds));
			if (playing) {
				streamBackend.setStreamRunning(false);
				streamBackend.stopPlayback(false);
				streamBackend.setStreamRunning(true);
				startStreamPlayback();
			}
		} else if (openAlBackend.isActive()) {
			if (!ensureOpenAlBackendReady()) {
				playing = false;
				return;
			}
			openAlBackend.seek(this.currentTimeSeconds, playing);
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
			LOGGER.info("BeatBlock MusicPlayer: audio loaded path={} duration={}s", loadedAudioPath, String.format("%.3f", durationSeconds));
			applyOutputGain();
			return true;
		} catch (UnsupportedAudioFileException e) {
			LOGGER.warn("BeatBlock: unsupported audio format for {}: {}, trying ffmpeg fallback", file, e.getMessage());
			if (tryLoadViaFfmpegFallback(file)) {
				loadedAudioPath = file.toString();
				playing = false;
				currentTimeSeconds = 0;
				lastLoadError = null;
				LOGGER.info("BeatBlock MusicPlayer: audio loaded via ffmpeg PCM fallback path={} duration={}s", loadedAudioPath, String.format("%.3f", durationSeconds));
				return true;
			}
			if (lastLoadError == null || lastLoadError.isBlank()) {
				lastLoadError = "格式不受当前音频后端支持: " + file.getFileName();
			}
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
		} catch (Throwable t) {
			lastLoadError = "音频后端运行时错误: " + t.getClass().getSimpleName();
			LOGGER.error("BeatBlock: runtime audio backend error for {}", file, t);
			closeAudioClip();
			return false;
		}
	}

	public String getLoadedAudioPath() {
		return loadedAudioPath;
	}

	public String getLastLoadError() {
		return lastLoadError;
	}

	public void tick(double deltaSeconds) {
		if (audioClip != null || streamBackend.isActive() || openAlBackend.isActive()) {
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
				openPcmAsPlaybackBackend(readAllBytes(pcmStream), pcmFmt);
			}
		}
	}

	private void syncClipState() {
		if (audioClip != null) {
			currentTimeSeconds = clipPositionSeconds();
			if (playing && !audioClip.isRunning()) {
				if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
					currentTimeSeconds = durationSeconds;
				}
				playing = false;
			}
			return;
		}
		if (streamBackend.isActive()) {
			currentTimeSeconds = streamBackend.positionSeconds(currentTimeSeconds);
			if (playing && !streamBackend.isThreadAlive()) {
				if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
					currentTimeSeconds = durationSeconds;
				}
				playing = false;
			}
			return;
		}
		if (openAlBackend.isActive()) {
			if (!ensureOpenAlBackendReady()) {
				playing = false;
				return;
			}
			currentTimeSeconds = openAlBackend.positionSeconds(currentTimeSeconds);
			if (playing && openAlBackend.syncPlayingState(true, durationSeconds)) {
				currentTimeSeconds = durationSeconds;
				playing = false;
			}
		}
	}

	private void applyOutputGain() {
		if (audioClip != null && audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			float gain = muted ? 0.0f : 1.0f;
			try {
				FloatControl control = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
				if (gain <= 0.0001f) {
					control.setValue(control.getMinimum());
				} else {
					float db = (float) (20.0 * Math.log10(gain));
					db = Math.max(control.getMinimum(), Math.min(control.getMaximum(), db));
					control.setValue(db);
				}
			} catch (Exception ignored) {
			}
		}
		streamBackend.applyGain(muted);
		if (openAlBackend.isActive()) {
			if (!ensureOpenAlBackendReady()) {
				return;
			}
			openAlBackend.applyGain(muted);
		}
	}

	private double clipPositionSeconds() {
		if (audioClip == null) return currentTimeSeconds;
		return audioClip.getMicrosecondPosition() / 1_000_000.0;
	}

	private void closeAudioClip() {
		streamBackend.close(false);
		openAlBackend.close();
		if (audioClip == null) return;
		try {
			audioClip.stop();
			audioClip.close();
		} finally {
			audioClip = null;
		}
	}

	private boolean ensureOpenAlBackendReady() {
		StringBuilder error = new StringBuilder();
		boolean ready = openAlBackend.ensureReady(loadedAudioPath, error);
		if (!error.isEmpty()) {
			lastLoadError = error.toString();
		}
		return ready;
	}

	private boolean recoverOpenAlBackend() {
		String path = loadedAudioPath;
		if (path == null || path.isBlank()) {
			openAlBackend.close();
			lastLoadError = "OpenAL 设备重建后无法恢复：未绑定音频文件";
			return false;
		}
		recoveringOpenAl = true;
		boolean resumePlaying = playing;
		double resumeTime = currentTimeSeconds;
		try {
			LOGGER.warn("BeatBlock MusicPlayer: OpenAL handles became invalid, rebuilding backend path={} time={}s",
				path, String.format("%.3f", resumeTime));
			boolean loaded = loadAudio(path);
			if (!loaded) {
				LOGGER.warn("BeatBlock MusicPlayer: OpenAL backend rebuild failed path={} reason={}", path, lastLoadError);
				playing = false;
				return false;
			}
			setCurrentTimeSeconds(resumeTime);
			if (resumePlaying) {
				play();
			} else {
				playing = false;
			}
			LOGGER.info("BeatBlock MusicPlayer: OpenAL backend rebuilt successfully path={}", path);
			return true;
		} finally {
			recoveringOpenAl = false;
		}
	}

	private boolean tryLoadViaFfmpegFallback(Path originalFile) {
		if (!FfmpegService.isAvailable()) {
			lastLoadError = "格式不受支持，且找不到 ffmpeg 进行解码兜底";
			return false;
		}

		int[][] candidates = {
			{48_000, 2}, {44_100, 2}, {48_000, 1}, {44_100, 1}, {32_000, 1}, {22_050, 1}
		};
		Throwable lastFailure = null;
		for (int[] c : candidates) {
			int sampleRate = c[0];
			int channels = c[1];
			byte[] pcmBytes;
			try {
				pcmBytes = FfmpegService.decodeToPcm(originalFile, sampleRate, channels, 256 * 1024 * 1024);
			} catch (Exception e) {
				lastLoadError = "ffmpeg 解码失败: " + e.getMessage();
				return false;
			}
			try {
				AudioFormat fmt = new AudioFormat(
					AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false);
				openPcmAsPlaybackBackend(pcmBytes, fmt);
				LOGGER.info("BeatBlock MusicPlayer: ffmpeg fallback selected format {}Hz/{}ch for {}",
					sampleRate, channels, originalFile.getFileName());
				return true;
			} catch (Throwable e) {
				lastFailure = e;
				LOGGER.warn("BeatBlock MusicPlayer: format candidate {}Hz/{}ch rejected: {}",
					sampleRate, channels, e.getMessage());
			}
		}
		lastLoadError = "ffmpeg 已解码，但加载 PCM 失败 (all formats rejected): "
			+ lastFailure.getMessage();
		return false;
	}

	private void openPcmAsPlaybackBackend(byte[] pcmBytes, AudioFormat pcmFmt) throws LineUnavailableException, IOException {
		if (pcmBytes == null || pcmBytes.length == 0) {
			throw new IOException("PCM 数据为空");
		}
		long frameLength = pcmBytes.length / pcmFmt.getFrameSize();

		try (AudioInputStream pcmStream = new AudioInputStream(new ByteArrayInputStream(pcmBytes), pcmFmt, frameLength)) {
			try {
				Clip clip = JavaSoundMixerSupport.acquireClipFromAnyMixer(pcmFmt);
				clip.open(pcmStream);
				audioClip = clip;
				streamBackend.close(false);
				openAlBackend.close();
				durationSeconds = clip.getMicrosecondLength() / 1_000_000.0;
				LOGGER.info("BeatBlock MusicPlayer: using Clip backend for {}Hz/{}ch",
					(int) pcmFmt.getSampleRate(), pcmFmt.getChannels());
				return;
			} catch (Throwable e) {
				LOGGER.warn("BeatBlock MusicPlayer: Clip backend failed ({}), trying SourceDataLine", e.getMessage());
			}
		}

		SourceDataLine probe = JavaSoundMixerSupport.openSourceDataLineFromAnyMixer(pcmFmt);
		if (probe != null) {
			probe.close();
			audioClip = null;
			openAlBackend.close();
			streamBackend.load(pcmBytes, pcmFmt);
			durationSeconds = streamBackend.getDurationSeconds();
			LOGGER.info("BeatBlock MusicPlayer: using SourceDataLine stream backend for {}Hz/{}ch",
				(int) pcmFmt.getSampleRate(), pcmFmt.getChannels());
			return;
		}

		LOGGER.info("BeatBlock MusicPlayer: JavaSound unavailable (0 mixers), using OpenAL backend");
		audioClip = null;
		streamBackend.close(false);
		openAlBackend.open(pcmBytes, pcmFmt);
		durationSeconds = openAlBackend.getBytesPerSecond() > 0
			? pcmBytes.length / openAlBackend.getBytesPerSecond()
			: 0;
	}

	private boolean startStreamPlayback() {
		StringBuilder error = new StringBuilder();
		boolean started = streamBackend.startPlayback(LOGGER, loadedAudioPath, error);
		if (!started && !error.isEmpty()) {
			lastLoadError = error.toString();
		}
		return started;
	}

	private byte[] readAllBytes(AudioInputStream stream) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
		byte[] buf = new byte[8192];
		int n;
		while ((n = stream.read(buf)) != -1) {
			out.write(buf, 0, n);
			if (out.size() > 256 * 1024 * 1024) {
				throw new IOException("音频过长，解码后内存占用过大");
			}
		}
		return out.toByteArray();
	}
}
