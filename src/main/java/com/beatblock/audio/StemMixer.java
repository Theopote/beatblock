package com.beatblock.audio;

import com.beatblock.audio.ffmpeg.FfmpegService;
import com.beatblock.timeline.IAudioPlayer;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多茎播放器 —— 每条 Demucs 茎对应一个独立 OpenAL source，
 * 通过 AL_GAIN 控制静音/独奏，避免依赖 JavaSound（在 Minecraft 环境下不可用）。
 *
 * <p>WAV 加载优先直接解析 RIFF 头（支持 PCM-16 与 IEEE-float32），
 * 失败时回退到 ffmpeg 进程解码。</p>
 */
public final class StemMixer implements IAudioPlayer {

	private static final Logger LOGGER = LoggerFactory.getLogger(StemMixer.class);

	// ── 每条茎的状态 ──────────────────────────────────────────────────────────

	private static final class StemTrack {
		final String key;
		final Path path;
		final int alSource;
		final int alBuffer;
		volatile boolean muted;

		StemTrack(String key, Path path, int alSource, int alBuffer) {
			this.key     = key;
			this.path = path;
			this.alSource = alSource;
			this.alBuffer = alBuffer;
		}
	}

	// ── 状态字段 ─────────────────────────────────────────────────────────────

	private final Map<String, StemTrack> stems = new LinkedHashMap<>();
	private volatile boolean playing = false;
	private volatile double durationSeconds   = 0.0;
	private volatile double lastKnownTimeSeconds = 0.0;
	private volatile boolean recoveringOpenAl = false;

	// ── 公共 API ─────────────────────────────────────────────────────────────

	/**
	 * 加载一条茎 WAV 文件并上传到 OpenAL。
	 * 必须从 OpenAL 上下文线程（渲染线程）调用。
	 *
	 * @param key     茎名称，如 "drums"、"bass"、"vocals"、"other"
	 * @param wavPath WAV 文件绝对路径
	 * @return true 表示加载成功
	 */
	public synchronized boolean loadStem(String key, Path wavPath) {
		if (key == null || wavPath == null) return false;
		Path normalizedPath = wavPath.toAbsolutePath().normalize();
		try {
			// 1. 解码为原始 16-bit signed LE PCM
			StemPcmData pcmData = decodeStemAudio(normalizedPath);

			// 2. 上传到 OpenAL buffer
			ByteBuffer directBuf = ByteBuffer.allocateDirect(pcmData.pcm.length);
			directBuf.put(pcmData.pcm).flip();
			int alFormat = (pcmData.channels == 2) ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;

			int buf = AL10.alGenBuffers();
			AL10.alBufferData(buf, alFormat, directBuf, pcmData.sampleRate);
			int err = AL10.alGetError();
			if (err != AL10.AL_NO_ERROR) {
				AL10.alDeleteBuffers(buf);
				LOGGER.warn("BeatBlock StemMixer: alBufferData failed key={} error=0x{}", key, Integer.toHexString(err));
				return false;
			}

			// 3. 创建并配置 source（SOURCE_RELATIVE 防止 Minecraft 3D 距离衰减）
			int src = AL10.alGenSources();
			AL10.alSourcei(src, AL10.AL_BUFFER, buf);
			AL10.alSourcef(src, AL10.AL_GAIN, 1.0f);
			AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
			AL10.alSource3f(src, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
			AL10.alSource3f(src, AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f);

			// 4. 替换同名旧茎
			StemTrack old = stems.get(key);
			if (old != null) releaseTrack(old);
			stems.put(key, new StemTrack(key, normalizedPath, src, buf));

			// 首条茎决定时长
			if (durationSeconds <= 0.0) {
				durationSeconds = pcmData.pcm.length / (double)(pcmData.sampleRate * pcmData.channels * 2);
			}

			LOGGER.info("BeatBlock StemMixer: loaded stem key={} bytes={} {}Hz/{}ch",
					key, pcmData.pcm.length, pcmData.sampleRate, pcmData.channels);
			return true;

		} catch (Exception e) {
			LOGGER.warn("BeatBlock StemMixer: failed to load stem key={} path={} reason={}",
					key, normalizedPath, e.getMessage());
			return false;
		}
	}

	/** @return 是否至少有一条茎加载成功（决定 StemMixer 是否为活跃状态）。 */
	public boolean hasStems() {
		return !stems.isEmpty();
	}

	/** 当前所有茎的总时长（秒，由首条茎计算）。 */
	public double getDurationSeconds() {
		return durationSeconds;
	}

	/**
	 * 停止播放并释放所有 OpenAL 资源。
	 * 必须从渲染线程调用。
	 */
	public synchronized void clearStems() {
		for (StemTrack t : stems.values()) releaseTrack(t);
		stems.clear();
		playing = false;
		durationSeconds = 0.0;
	}

	/**
	 * 设置指定茎的静音状态（立即生效：通过 AL_GAIN 为 0/1 实现）。
	 *
	 * @param key   茎名称
	 * @param muted true = 静音，false = 恢复
	 */
	public void setStemMuted(String key, boolean muted) {
		ensureOpenAlBackendReady();
		StemTrack t = stems.get(key);
		if (t == null) return;
		t.muted = muted;
		try {
			AL10.alSourcef(t.alSource, AL10.AL_GAIN, muted ? 0.0f : 1.0f);
		} catch (Throwable ignored) {}
	}

	// ── IAudioPlayer ─────────────────────────────────────────────────────────

	@Override
	public boolean isPlaying() {
		ensureOpenAlBackendReady();
		return playing;
	}

	@Override
	public synchronized double getCurrentTimeSeconds() {
		if (!ensureOpenAlBackendReady()) return lastKnownTimeSeconds;
		for (StemTrack t : stems.values()) {
			try {
				lastKnownTimeSeconds = AL11.alGetSourcef(t.alSource, AL11.AL_SEC_OFFSET);
				return lastKnownTimeSeconds;
			} catch (Throwable ignored) {}
		}
		return lastKnownTimeSeconds;
	}

	@Override
	public synchronized void setCurrentTimeSeconds(double seconds) {
		if (!ensureOpenAlBackendReady()) {
			lastKnownTimeSeconds = Math.max(0.0, seconds);
			playing = false;
			return;
		}
		float secs = (float) Math.max(0.0, seconds);
		lastKnownTimeSeconds = secs;
		boolean wasPlaying = playing;
		if (wasPlaying) pauseAll();
		for (StemTrack t : stems.values()) {
			try {
				AL11.alSourcef(t.alSource, AL11.AL_SEC_OFFSET, secs);
			} catch (Throwable ignored) {}
		}
		if (wasPlaying) playAll();
	}

	@Override
	public synchronized void play() {
		if (stems.isEmpty()) return;
		if (!ensureOpenAlBackendReady()) {
			playing = false;
			return;
		}
		playAll();
		playing = true;
	}

	@Override
	public synchronized void pause() {
		if (!ensureOpenAlBackendReady()) {
			playing = false;
			return;
		}
		pauseAll();
		playing = false;
	}

	@Override
	public synchronized void stop() {
		if (!ensureOpenAlBackendReady()) {
			playing = false;
			lastKnownTimeSeconds = 0.0;
			return;
		}
		for (StemTrack t : stems.values()) {
			try {
				AL10.alSourceStop(t.alSource);
				AL11.alSourcef(t.alSource, AL11.AL_SEC_OFFSET, 0.0f);
			} catch (Throwable ignored) {}
		}
		playing = false;
		lastKnownTimeSeconds = 0.0;
	}

	// ── 内部：OpenAL 操作 ─────────────────────────────────────────────────────

	private void playAll() {
		if (!ensureOpenAlBackendReady()) return;
		for (StemTrack t : stems.values()) {
			try {
				AL10.alSourcePlay(t.alSource);
			} catch (Throwable e) {
				LOGGER.warn("BeatBlock StemMixer: alSourcePlay failed stem={}: {}", t.key, e.getMessage());
			}
		}
	}

	private void pauseAll() {
		if (!ensureOpenAlBackendReady()) return;
		for (StemTrack t : stems.values()) {
			try {
				AL10.alSourcePause(t.alSource);
			} catch (Throwable ignored) {}
		}
	}

	private void releaseTrack(StemTrack t) {
		try {
			AL10.alSourceStop(t.alSource);
			AL10.alSourcei(t.alSource, AL10.AL_BUFFER, 0);
			AL10.alDeleteSources(t.alSource);
		} catch (Throwable ignored) {}
		try {
			AL10.alDeleteBuffers(t.alBuffer);
		} catch (Throwable ignored) {}
	}

	private boolean ensureOpenAlBackendReady() {
		if (stems.isEmpty()) return true;
		if (recoveringOpenAl) return false;
		if (areStemHandlesValid()) return true;
		return recoverOpenAlBackend();
	}

	private boolean areStemHandlesValid() {
		if (stems.isEmpty()) return true;
		try {
			for (StemTrack t : stems.values()) {
				AL10.alGetError();
				boolean sourceValid = AL10.alIsSource(t.alSource);
				int sourceErr = AL10.alGetError();
				boolean bufferValid = AL10.alIsBuffer(t.alBuffer);
				int bufferErr = AL10.alGetError();
				if (sourceErr != AL10.AL_NO_ERROR || bufferErr != AL10.AL_NO_ERROR || !sourceValid || !bufferValid) {
					return false;
				}
			}
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private boolean recoverOpenAlBackend() {
		if (stems.isEmpty()) return true;
		recoveringOpenAl = true;
		boolean resumePlaying = playing;
		double resumeTime = lastKnownTimeSeconds;
		Map<String, StemTrack> snapshot = new LinkedHashMap<>(stems);
		try {
			LOGGER.warn("BeatBlock StemMixer: OpenAL handles became invalid, rebuilding {} stems at {}s",
				snapshot.size(), String.format("%.3f", resumeTime));
			clearStems();
			for (StemTrack track : snapshot.values()) {
				if (!loadStem(track.key, track.path)) {
					LOGGER.warn("BeatBlock StemMixer: failed to rebuild stem key={} path={}", track.key, track.path);
					playing = false;
					return false;
				}
				setStemMuted(track.key, track.muted);
			}
			setCurrentTimeSeconds(resumeTime);
			if (resumePlaying) {
				playAll();
				playing = true;
			} else {
				playing = false;
			}
			LOGGER.info("BeatBlock StemMixer: OpenAL backend rebuilt successfully with {} stems", stems.size());
			return true;
		} finally {
			recoveringOpenAl = false;
		}
	}

	// ── 内部：PCM 解码 ────────────────────────────────────────────────────────

	private record StemPcmData(byte[] pcm, int sampleRate, int channels) {}

	/**
	 * 解码茎音频文件为原始 16-bit signed LE PCM 字节。
	 * 优先直接解析 RIFF WAV（支持 PCM-16 和 IEEE-float32）；失败时回退 ffmpeg。
	 */
	private StemPcmData decodeStemAudio(Path wavPath) throws IOException {
		try {
			return readWavPcmDirect(wavPath);
		} catch (Exception e) {
			LOGGER.warn("BeatBlock StemMixer: direct WAV parse failed for {}, trying ffmpeg: {}",
					wavPath.getFileName(), e.getMessage());
		}
		return decodePcmWithFfmpeg(wavPath);
	}

	/**
	 * 直接解析 RIFF WAV 文件头，提取 PCM 字节数据。
	 * 支持 audioFormat=1（PCM-16）和 audioFormat=3（IEEE-float32，转换为 PCM-16）。
	 */
	private StemPcmData readWavPcmDirect(Path wavPath) throws IOException {
		byte[] file = Files.readAllBytes(wavPath);
		if (file.length < 44) throw new IOException("file too small");

		// 验证 RIFF/WAVE 头
		if (!matchesAscii(file, 0, "RIFF") || !matchesAscii(file, 8, "WAVE")) {
			throw new IOException("not a RIFF/WAVE file");
		}

		ByteBuffer bb = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);

		int audioFormat = 0, channels = 0, sampleRate = 0, bitsPerSample = 0;
		int dataOffset = -1, dataSize = -1;

		// 遍历 RIFF chunk
		int pos = 12;
		while (pos + 8 <= file.length) {
			String chunkId   = new String(file, pos, 4);
			int    chunkSize = bb.getInt(pos + 4);
			int    dataStart = pos + 8;

			if ("fmt ".equals(chunkId)) {
				audioFormat   = bb.getShort(dataStart)      & 0xFFFF;
				channels      = bb.getShort(dataStart + 2)  & 0xFFFF;
				sampleRate    = bb.getInt(dataStart + 4);
				bitsPerSample = bb.getShort(dataStart + 14) & 0xFFFF;
			} else if ("data".equals(chunkId)) {
				dataOffset = dataStart;
				dataSize   = chunkSize;
				break;
			}
			pos = dataStart + chunkSize;
			if ((chunkSize & 1) != 0) pos++; // RIFF chunk word-align
		}

		if (dataOffset < 0 || channels == 0 || sampleRate == 0) {
			throw new IOException("could not locate fmt/data chunks");
		}
		if (audioFormat != 1 && audioFormat != 3) {
			throw new IOException("unsupported audioFormat=" + audioFormat);
		}
		if (bitsPerSample != 16 && bitsPerSample != 32) {
			throw new IOException("unsupported bitsPerSample=" + bitsPerSample);
		}

		dataSize = Math.min(dataSize, file.length - dataOffset);
		byte[] output;

		if (audioFormat == 1 && bitsPerSample == 16) {
			// PCM-16：直接拷贝 data chunk
			output = new byte[dataSize];
			System.arraycopy(file, dataOffset, output, 0, dataSize);

		} else {
			// IEEE float32 → 16-bit signed
			int numSamples = dataSize / 4;
			output = new byte[numSamples * 2];
			for (int i = 0; i < numSamples; i++) {
				float sample = bb.getFloat(dataOffset + i * 4);
				int   s      = Math.round(sample * 32767.0f);
				s = Math.max(-32768, Math.min(32767, s));
				output[i * 2]     = (byte) (s & 0xFF);
				output[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
			}
		}

		return new StemPcmData(output, sampleRate, channels);
	}

	private static boolean matchesAscii(byte[] data, int offset, String s) {
		if (offset + s.length() > data.length) return false;
		for (int i = 0; i < s.length(); i++) {
			if (data[offset + i] != (byte) s.charAt(i)) return false;
		}
		return true;
	}

	/**
	 * 通过 ffmpeg 进程将任意音频文件解码为 44100Hz / 双声道 / 16-bit LE PCM。
	 */
	private StemPcmData decodePcmWithFfmpeg(Path inputFile) throws IOException {
		final int sampleRate = 44100;
		final int channels = 2;
		try {
			byte[] pcm = FfmpegService.decodeToPcm(inputFile, sampleRate, channels, 512 * 1024 * 1024);
			return new StemPcmData(pcm, sampleRate, channels);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted waiting for ffmpeg");
		}
	}
}
