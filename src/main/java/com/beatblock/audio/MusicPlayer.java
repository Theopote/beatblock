package com.beatblock.audio;

import com.beatblock.timeline.IAudioPlayer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
	private SourceDataLine streamLine;
	private Thread streamThread;
    private byte[] streamPcmData;
	private AudioFormat streamPcmFormat;
	private int streamBytePosition;
	private int streamStartBytePosition;
	private String loadedAudioPath;
	private String lastLoadError;
	private volatile boolean muted;

	// OpenAL backend (used when JavaSound has no mixers, e.g. inside Minecraft/LWJGL)
	private int alBuffer = 0;
	private int alSource = 0;
	private boolean useOpenAl = false;
	private double alBytesPerSecond = 0.0;
	private boolean recoveringOpenAl = false;

	public MusicPlayer() {
		this.playing = false;
		this.currentTimeSeconds = 0;
		this.durationSeconds = 0;
		this.muted = false;
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
		} else if (hasStreamBackend()) {
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				streamBytePosition = 0;
				currentTimeSeconds = 0;
			}
			if (!startStreamPlayback()) {
				LOGGER.warn("BeatBlock MusicPlayer: stream playback start failed. lastLoadError={}", lastLoadError);
				playing = false;
				return;
			}
			LOGGER.info("BeatBlock MusicPlayer: stream playback started path={} time={}s", loadedAudioPath, String.format("%.3f", currentTimeSeconds));
		} else if (useOpenAl) {
			if (!ensureOpenAlBackendReady()) {
				LOGGER.warn("BeatBlock MusicPlayer: OpenAL backend is unavailable after device reset. lastLoadError={}", lastLoadError);
				playing = false;
				return;
			}
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				try { AL11.alSourcef(alSource, AL11.AL_SEC_OFFSET, 0.0f); } catch (Throwable ignored) {}
				currentTimeSeconds = 0;
			}
			try {
				AL10.alSourcePlay(alSource);
				LOGGER.info("BeatBlock MusicPlayer: OpenAL playback started path={} time={}s", loadedAudioPath, String.format("%.3f", currentTimeSeconds));
			} catch (Throwable e) {
				LOGGER.warn("BeatBlock MusicPlayer: OpenAL play failed: {}", e.getMessage());
				playing = false;
				return;
			}
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
		} else if (hasStreamBackend()) {
			stopStreamPlayback(false);
			currentTimeSeconds = streamPositionSeconds();
		} else if (useOpenAl) {
			if (!ensureOpenAlBackendReady()) {
				playing = false;
				return;
			}
			try {
				currentTimeSeconds = openAlPositionSeconds();
				AL10.alSourcePause(alSource);
			} catch (Throwable ignored) {}
		}
		playing = false;
	}

	public void stop() {
		if (audioClip != null) {
			audioClip.stop();
			audioClip.setMicrosecondPosition(0);
		} else if (hasStreamBackend()) {
			stopStreamPlayback(true);
		} else if (useOpenAl) {
			if (!ensureOpenAlBackendReady()) {
				playing = false;
				currentTimeSeconds = 0;
				return;
			}
			try {
				AL10.alSourceStop(alSource);
				AL11.alSourcef(alSource, AL11.AL_SEC_OFFSET, 0.0f);
			} catch (Throwable ignored) {}
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
		} else if (hasStreamBackend()) {
			streamBytePosition = secondsToStreamBytePosition(this.currentTimeSeconds);
			if (playing) {
				stopStreamPlayback(false);
				startStreamPlayback();
			}
		} else if (useOpenAl) {
			if (!ensureOpenAlBackendReady()) {
				playing = false;
				return;
			}
			try {
				boolean wasPlaying = playing && AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
				if (wasPlaying) AL10.alSourcePause(alSource);
				AL11.alSourcef(alSource, AL11.AL_SEC_OFFSET, (float) this.currentTimeSeconds);
				if (wasPlaying) AL10.alSourcePlay(alSource);
			} catch (Throwable ignored) {}
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
				byte[] pcmBytes = readAllBytes(pcmStream);
				openPcmAsPlaybackBackend(pcmBytes, pcmFmt);
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
		if (hasStreamBackend()) {
			currentTimeSeconds = streamPositionSeconds();
			if (playing && (streamThread == null || !streamThread.isAlive())) {
				if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
					currentTimeSeconds = durationSeconds;
				}
				playing = false;
			}
			return;
		}
		if (useOpenAl) {
			if (!ensureOpenAlBackendReady()) {
				playing = false;
				return;
			}
			try {
				currentTimeSeconds = openAlPositionSeconds();
				if (playing) {
					int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
					if (state == AL10.AL_STOPPED || state == AL10.AL_INITIAL) {
						if (durationSeconds > 0) currentTimeSeconds = durationSeconds;
						playing = false;
					}
				}
			} catch (Throwable ignored) {}
		}
	}

	private void applyOutputGain() {
		float gain = muted ? 0.0f : 1.0f;

		if (audioClip != null && audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			try {
				FloatControl control = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
				if (gain <= 0.0001f) {
					control.setValue(control.getMinimum());
				} else {
					float db = (float) (20.0 * Math.log10(gain));
					db = Math.max(control.getMinimum(), Math.min(control.getMaximum(), db));
					control.setValue(db);
				}
			} catch (Exception ignored) {}
		}

		if (streamLine != null && streamLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			try {
				FloatControl control = (FloatControl) streamLine.getControl(FloatControl.Type.MASTER_GAIN);
				if (gain <= 0.0001f) {
					control.setValue(control.getMinimum());
				} else {
					float db = (float) (20.0 * Math.log10(gain));
					db = Math.max(control.getMinimum(), Math.min(control.getMaximum(), db));
					control.setValue(db);
				}
			} catch (Exception ignored) {}
		}

		if (useOpenAl && alSource != 0) {
			if (!ensureOpenAlBackendReady()) {
				return;
			}
			try { AL10.alSourcef(alSource, AL10.AL_GAIN, gain); } catch (Throwable ignored) {}
		}
	}

	private double clipPositionSeconds() {
		if (audioClip == null) return currentTimeSeconds;
		return audioClip.getMicrosecondPosition() / 1_000_000.0;
	}

	private void closeAudioClip() {
		stopStreamPlayback(false);
		streamPcmData = null;
		streamPcmFormat = null;
		streamBytePosition = 0;
		streamStartBytePosition = 0;
		closeOpenAlBackend();
		if (audioClip == null) return;
		try {
			audioClip.stop();
			audioClip.close();
		} finally {
			audioClip = null;
		}
	}

	private void closeOpenAlBackend() {
		useOpenAl = false;
		alBytesPerSecond = 0.0;
		if (alSource != 0) {
			try {
				AL10.alSourceStop(alSource);
				AL10.alSourcei(alSource, AL10.AL_BUFFER, 0);
				AL10.alDeleteSources(alSource);
			} catch (Throwable ignored) {}
			alSource = 0;
		}
		if (alBuffer != 0) {
			try {
				AL10.alDeleteBuffers(alBuffer);
			} catch (Throwable ignored) {}
			alBuffer = 0;
		}
	}

	private boolean ensureOpenAlBackendReady() {
		if (!useOpenAl) return false;
		if (recoveringOpenAl) return false;
		if (isOpenAlBackendValid()) return true;
		return recoverOpenAlBackend();
	}

	private boolean isOpenAlBackendValid() {
		if (!useOpenAl || alSource == 0 || alBuffer == 0) return false;
		try {
			AL10.alGetError();
			boolean sourceValid = AL10.alIsSource(alSource);
			int sourceErr = AL10.alGetError();
			boolean bufferValid = AL10.alIsBuffer(alBuffer);
			int bufferErr = AL10.alGetError();
			return sourceErr == AL10.AL_NO_ERROR && bufferErr == AL10.AL_NO_ERROR && sourceValid && bufferValid;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private boolean recoverOpenAlBackend() {
		String path = loadedAudioPath;
		if (path == null || path.isBlank()) {
			closeOpenAlBackend();
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
		String ffmpeg = resolveFfmpegExecutable();
		if (ffmpeg == null) {
			lastLoadError = "格式不受支持，且找不到 ffmpeg 进行解码兜底";
			return false;
		}

		// 依次尝试多种常见 PCM 输出格式，直到找到系统可接受的一组。
		int[][] candidates = {
			{48_000, 2}, {44_100, 2}, {48_000, 1}, {44_100, 1}, {32_000, 1}, {22_050, 1}
		};
		Throwable lastFailure = null;
		for (int[] c : candidates) {
			int sampleRate = c[0];
			int channels = c[1];
			byte[] pcmBytes;
			try {
				pcmBytes = decodePcmWithFfmpeg(ffmpeg, originalFile, sampleRate, channels);
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

	private byte[] decodePcmWithFfmpeg(String ffmpeg, Path originalFile, int sampleRate, int channels)
		throws IOException, InterruptedException {
		List<String> command = List.of(
			ffmpeg, "-y", "-i", originalFile.toAbsolutePath().toString(),
			"-vn", "-ar", String.valueOf(sampleRate), "-ac", String.valueOf(channels),
			"-acodec", "pcm_s16le", "-f", "s16le", "pipe:1"
		);
		Process process = new ProcessBuilder(command)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
		ByteArrayOutputStream pcmOut = new ByteArrayOutputStream(1 << 20);
		try (var in = process.getInputStream()) {
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) != -1) {
				pcmOut.write(buf, 0, n);
				if (pcmOut.size() > 256 * 1024 * 1024) {
					process.destroyForcibly();
					throw new IOException("音频过长，解码后内存占用过大");
				}
			}
		}
		int exitCode = process.waitFor();
		if (exitCode != 0 || pcmOut.size() == 0) {
			throw new IOException("ffmpeg 进程退出码=" + exitCode);
		}
		return pcmOut.toByteArray();
	}

	private void openPcmAsPlaybackBackend(byte[] pcmBytes, AudioFormat pcmFmt) throws LineUnavailableException, IOException {
		if (pcmBytes == null || pcmBytes.length == 0) {
			throw new IOException("PCM 数据为空");
		}
		long frameLength = pcmBytes.length / pcmFmt.getFrameSize();

		// 尝试 Clip 后端
		try (AudioInputStream pcmStream = new AudioInputStream(new ByteArrayInputStream(pcmBytes), pcmFmt, frameLength)) {
			try {
				Clip clip = acquireClipFromAnyMixer(pcmFmt);
				clip.open(pcmStream);
				audioClip = clip;
				streamPcmData = null;
				streamPcmFormat = null;
				streamBytePosition = 0;
				streamStartBytePosition = 0;
				durationSeconds = clip.getMicrosecondLength() / 1_000_000.0;
				LOGGER.info("BeatBlock MusicPlayer: using Clip backend for {}Hz/{}ch",
					(int) pcmFmt.getSampleRate(), pcmFmt.getChannels());
				return;
			} catch (Throwable e) {
				LOGGER.warn("BeatBlock MusicPlayer: Clip backend failed ({}), trying SourceDataLine", e.getMessage());
			}
		}

		// 尝试 SourceDataLine 后端——遍历所有 mixer，不依赖 isLineSupported()
		SourceDataLine foundLine = openSourceDataLineFromAnyMixer(pcmFmt);
		if (foundLine != null) {
			foundLine.close();
			streamPcmData = pcmBytes;
			streamPcmFormat = pcmFmt;
			streamBytePosition = 0;
			streamStartBytePosition = 0;
			audioClip = null;
			durationSeconds = pcmBytes.length / (pcmFmt.getFrameRate() * pcmFmt.getFrameSize());
			LOGGER.info("BeatBlock MusicPlayer: using SourceDataLine stream backend for {}Hz/{}ch",
				(int) pcmFmt.getSampleRate(), pcmFmt.getChannels());
			return;
		}

		// 最终兜底：直接使用 Minecraft 已初始化的 OpenAL（LWJGL）
		LOGGER.info("BeatBlock MusicPlayer: JavaSound unavailable (0 mixers), using OpenAL backend");
		tryOpenAlBackend(pcmBytes, pcmFmt);
	}

	private void tryOpenAlBackend(byte[] pcmBytes, AudioFormat pcmFmt) throws LineUnavailableException {
		closeOpenAlBackend();
		try {
			int channels = pcmFmt.getChannels();
			int bits = pcmFmt.getSampleSizeInBits();
			int alFormat;
			if (channels == 2 && bits == 16) alFormat = AL10.AL_FORMAT_STEREO16;
			else if (channels == 2) alFormat = AL10.AL_FORMAT_STEREO8;
			else if (bits == 16) alFormat = AL10.AL_FORMAT_MONO16;
			else alFormat = AL10.AL_FORMAT_MONO8;
			int sampleRate = (int) pcmFmt.getSampleRate();

			ByteBuffer directBuf = ByteBuffer.allocateDirect(pcmBytes.length);
			directBuf.put(pcmBytes).flip();

			int buf = AL10.alGenBuffers();
			AL10.alBufferData(buf, alFormat, directBuf, sampleRate);
			int err = AL10.alGetError();
			if (err != AL10.AL_NO_ERROR) {
				AL10.alDeleteBuffers(buf);
				throw new LineUnavailableException("alBufferData failed, error=0x" + Integer.toHexString(err));
			}

			int src = AL10.alGenSources();
			AL10.alSourcei(src, AL10.AL_BUFFER, buf);
			AL10.alSourcef(src, AL10.AL_GAIN, 1.0f);
			// 使 source 相对于 listener（非 3D 定位），避免 Minecraft 世界距离衰减导致静音
			AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
			AL10.alSource3f(src, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
			AL10.alSource3f(src, AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f);
			AL10.alSourcef(src, AL11.AL_SEC_OFFSET, 0.0f);
			err = AL10.alGetError();
			if (err != AL10.AL_NO_ERROR) {
				AL10.alDeleteSources(src);
				AL10.alDeleteBuffers(buf);
				throw new LineUnavailableException("alGenSources/alSourcei failed, error=0x" + Integer.toHexString(err));
			}

			alBuffer = buf;
			alSource = src;
			useOpenAl = true;
			alBytesPerSecond = pcmFmt.getFrameRate() * pcmFmt.getFrameSize();
			audioClip = null;
			streamPcmData = null;
			streamPcmFormat = null;
			durationSeconds = alBytesPerSecond > 0 ? pcmBytes.length / alBytesPerSecond : 0;
			LOGGER.info("BeatBlock MusicPlayer: OpenAL backend ready {}Hz/{}ch duration={}s",
				sampleRate, channels, String.format("%.3f", durationSeconds));
		} catch (LineUnavailableException e) {
			throw e;
		} catch (Throwable e) {
			closeOpenAlBackend();
			throw new LineUnavailableException("OpenAL backend init failed: " + e.getMessage());
		}
	}

	private double openAlPositionSeconds() {
		if (!useOpenAl || alSource == 0) return currentTimeSeconds;
		try {
			return AL10.alGetSourcef(alSource, AL11.AL_SEC_OFFSET);
		} catch (Throwable ignored) {
			return currentTimeSeconds;
		}
	}

	private Clip acquireClipFromAnyMixer(AudioFormat fmt) throws LineUnavailableException {
		// 首先尝试默认路径
		try {
			return acquireClip(fmt);
		} catch (Throwable ignored) {
		}
		// 遍历所有 mixer
		DataLine.Info clipInfo = new DataLine.Info(Clip.class, fmt);
		for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
			try (Mixer mixer = AudioSystem.getMixer(mi)) {
				if (mixer.isLineSupported(clipInfo)) {
					Line line = mixer.getLine(clipInfo);
					if (line instanceof Clip clip) return clip;
				}
			} catch (Throwable ignored) {
			}
		}
		throw new LineUnavailableException("no mixer supports Clip");
	}

	private SourceDataLine openSourceDataLineFromAnyMixer(AudioFormat fmt) {
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
		// 1. 默认路径
		try {
			SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
			line.open(fmt);
			return line;
		} catch (Throwable ignored) {
		}
		// 2. 遍历所有 mixer
		for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
			try {
				Mixer mixer = AudioSystem.getMixer(mi);
				if (mixer.isLineSupported(info)) {
					SourceDataLine line = (SourceDataLine) mixer.getLine(info);
					line.open(fmt);
					LOGGER.info("BeatBlock MusicPlayer: acquired SourceDataLine from mixer '{}'", mi.getName());
					return line;
				}
			} catch (Throwable ignored) {
			}
		}
		// 3. 强行尝试不检查 isLineSupported
		for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
			try {
				Mixer mixer = AudioSystem.getMixer(mi);
				SourceDataLine line = (SourceDataLine) mixer.getLine(info);
				line.open(fmt);
				LOGGER.info("BeatBlock MusicPlayer: force-acquired SourceDataLine from mixer '{}'", mi.getName());
				return line;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private boolean hasStreamBackend() {
		return streamPcmData != null && streamPcmFormat != null;
	}

	private boolean startStreamPlayback() {
		if (!hasStreamBackend()) return false;
		stopStreamPlayback(false);
		AudioFormat fmt = streamPcmFormat;
		byte[] data = streamPcmData;
		int startByte = Math.max(0, Math.min(streamBytePosition, data.length));
		int frameSize = Math.max(1, fmt.getFrameSize());
		startByte -= (startByte % frameSize);
		final int alignedStartByte = startByte;
		try {
			SourceDataLine line = openSourceDataLineFromAnyMixer(fmt);
			if (line == null) {
				lastLoadError = "无法从任何混音器打开流式输出";
				return false;
			}
			line.start();
			streamLine = line;
			streamStartBytePosition = alignedStartByte;
			streamBytePosition = alignedStartByte;
			Thread t = new Thread(() -> runStreamLoop(line, data, fmt, alignedStartByte), "BeatBlock-AudioStream");
			t.setDaemon(true);
			streamThread = t;
			t.start();
			return true;
		} catch (Exception e) {
			lastLoadError = "无法打开流式音频输出设备: " + e.getMessage();
			return false;
		}
	}

	private void runStreamLoop(SourceDataLine line, byte[] data, AudioFormat fmt, int startByte) {
		int pos = startByte;
		int frameSize = Math.max(1, fmt.getFrameSize());
		byte[] buffer = new byte[8192];
		try {
			while (playing && streamThread == Thread.currentThread()) {
				if (pos >= data.length) break;
				int len = Math.min(buffer.length, data.length - pos);
				len -= (len % frameSize);
				if (len <= 0) break;
				System.arraycopy(data, pos, buffer, 0, len);
				int written = line.write(buffer, 0, len);
				if (written <= 0) break;
				pos += written;
				streamBytePosition = pos;
			}
		} finally {
			try {
				line.stop();
				line.flush();
			} catch (Exception ignored) {
			}
			try {
				line.close();
			} catch (Exception ignored) {
			}
			if (streamLine == line) {
				streamLine = null;
			}
			if (streamThread == Thread.currentThread()) {
				streamThread = null;
			}
			streamBytePosition = Math.max(0, Math.min(pos, data.length));
			currentTimeSeconds = streamPositionSeconds();
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				playing = false;
			}
		}
	}

	private void stopStreamPlayback(boolean resetPosition) {
		Thread t = streamThread;
		streamThread = null;
		SourceDataLine line = streamLine;
		streamLine = null;
		if (line != null) {
			try {
				line.stop();
				line.flush();
			} catch (Exception ignored) {
			}
			try {
				line.close();
			} catch (Exception ignored) {
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
			streamBytePosition = 0;
			streamStartBytePosition = 0;
		}
	}

	private int secondsToStreamBytePosition(double seconds) {
		if (!hasStreamBackend()) return 0;
		double bytesPerSecond = streamPcmFormat.getFrameRate() * streamPcmFormat.getFrameSize();
		int raw = (int) Math.max(0, Math.round(seconds * bytesPerSecond));
		int frame = Math.max(1, streamPcmFormat.getFrameSize());
		raw -= (raw % frame);
		return Math.max(0, Math.min(raw, streamPcmData.length));
	}

	private double streamPositionSeconds() {
		if (!hasStreamBackend()) return currentTimeSeconds;
		double bytesPerSecond = streamPcmFormat.getFrameRate() * streamPcmFormat.getFrameSize();
		if (bytesPerSecond <= 0.0) return 0.0;
		int pos = streamBytePosition;
		if (streamLine != null) {
			long lineFrames = streamLine.getLongFramePosition();
			int lineBytes = (int) Math.max(0, Math.min(Integer.MAX_VALUE, lineFrames * streamPcmFormat.getFrameSize()));
			pos = Math.max(pos, Math.min(streamStartBytePosition + lineBytes, streamPcmData.length));
		}
		return Math.max(0, pos / bytesPerSecond);
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

	private Clip acquireClip(AudioFormat preferredFormat) throws LineUnavailableException {
		try {
			return AudioSystem.getClip();
		} catch (NoSuchMethodError | SecurityException | IllegalArgumentException ignored) {
			AudioFormat safeFormat = preferredFormat != null
				? preferredFormat
				: new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44_100, 16, 2, 4, 44_100, false);
			DataLine.Info info = new DataLine.Info(Clip.class, safeFormat);
			Line line = AudioSystem.getLine(info);
			if (line instanceof Clip clip) {
				return clip;
			}
			throw new LineUnavailableException("系统未提供 Clip 音频线路");
		}
	}

	private String resolveFfmpegExecutable() {
		Path configPath = FabricLoader.getInstance().getConfigDir().resolve("beatblock/ffmpeg_path.txt");
		if (Files.exists(configPath)) {
			try {
				String txt = Files.readString(configPath).trim();
				if (!txt.isEmpty() && isExecutable(txt)) return txt;
			} catch (IOException ignored) {
			}
		}

		Path gameDir = FabricLoader.getInstance().getGameDir();
		List<Path> candidates = List.of(
			gameDir.resolve("ffmpeg.exe"),
			gameDir.resolve("ffmpeg"),
			gameDir.resolve("ffmpeg/bin/ffmpeg.exe"),
			gameDir.resolve("ffmpeg/bin/ffmpeg")
		);
		for (Path p : candidates) {
			if (Files.isRegularFile(p) && isExecutable(p.toAbsolutePath().toString())) {
				return p.toAbsolutePath().toString();
			}
		}

		if (isExecutable("ffmpeg")) return "ffmpeg";
		return null;
	}

	private boolean isExecutable(String executable) {
		try {
			Process p = new ProcessBuilder(executable, "-version")
				.redirectErrorStream(true)
				.start();
			boolean finished = p.waitFor(3, TimeUnit.SECONDS);
			return finished && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 每帧调用，推进播放进度（仅当 playing 时）。
	 */
	public void tick(double deltaSeconds) {
		if (audioClip != null || hasStreamBackend() || useOpenAl) {
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
