package com.beatblock.audio;

import com.beatblock.timeline.IAudioPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多茎软件混音播放器。
 *
 * <p>将 Demucs 分离的 4 条茎 WAV（drums / bass / vocals / other）加载为 PCM 字节数组，
 * 通过单条 {@link SourceDataLine} 实时软件混音输出。每条茎可独立静音 / 独奏。</p>
 *
 * <p>使用方：调用 {@link #loadStem(String, Path)} 加载所有茎后，即可通过
 * {@link #play()}/{@link #pause()}/{@link #stop()} 控制播放；通过
 * {@link #setStemMuted(String, boolean)} 控制单茎静音。
 * 调用 {@link #clearStems()} 停止并释放所有资源。</p>
 */
public final class StemMixer implements IAudioPlayer {

	private static final Logger LOGGER = LoggerFactory.getLogger(StemMixer.class);

	// ── 每条茎的状态 ──────────────────────────────────────────────────────────

	private static final class StemTrack {
		final String key;
		final byte[] pcm;          // 16-bit signed little-endian interleaved PCM
		volatile boolean muted;

		StemTrack(String key, byte[] pcm) {
			this.key = key;
			this.pcm = pcm;
		}
	}

	// ── 状态字段 ─────────────────────────────────────────────────────────────

	private final Map<String, StemTrack> stems = new LinkedHashMap<>();
	private AudioFormat sharedFormat;                   // 公共格式（首个茎决定）
	private SourceDataLine outputLine;
	private Thread mixThread;

	private volatile boolean playing  = false;
	private volatile boolean stopFlag = false;
	/** 当前播放位置（字节偏移，对应 sharedFormat）。*/
	private volatile int bytePos = 0;

	// ── 公共 API ─────────────────────────────────────────────────────────────

	/**
	 * 加载一条茎 WAV 文件。必须在 {@link #play()} 之前完成全部 loadStem 调用。
	 *
	 * @param key     茎名称，如 "drums"、"bass"、"vocals"、"other"
	 * @param wavPath WAV 文件绝对路径
	 */
	public synchronized void loadStem(String key, Path wavPath) {
		if (key == null || wavPath == null) return;
		try {
			AudioInputStream raw = AudioSystem.getAudioInputStream(wavPath.toFile());
			AudioFormat rawFmt = raw.getFormat();

			// 统一转为 16-bit signed little-endian，维持原采样率和声道数
			AudioFormat pcmFmt = new AudioFormat(
					AudioFormat.Encoding.PCM_SIGNED,
					rawFmt.getSampleRate(),
					16,
					rawFmt.getChannels(),
					rawFmt.getChannels() * 2,
					rawFmt.getSampleRate(),
					false);

			AudioInputStream pcmAis;
			if (rawFmt.matches(pcmFmt)) {
				pcmAis = raw;
			} else {
				pcmAis = AudioSystem.getAudioInputStream(pcmFmt, raw);
			}

			byte[] pcm = pcmAis.readAllBytes();
			pcmAis.close();

			// 第一条茎的格式成为公共格式（Demucs 各茎格式相同）
			if (sharedFormat == null) {
				sharedFormat = pcmFmt;
			}

			stems.put(key, new StemTrack(key, pcm));
			LOGGER.info("BeatBlock StemMixer: loaded stem key={} bytes={} format={}",
					key, pcm.length, pcmFmt);
		} catch (UnsupportedAudioFileException | IOException e) {
			LOGGER.warn("BeatBlock StemMixer: failed to load stem key={} path={} reason={}",
					key, wavPath, e.getMessage());
		}
	}

	/** @return 是否已加载至少一条茎（决定 StemMixer 是否为活跃状态）。 */
	public boolean hasStems() {
		return !stems.isEmpty();
	}

	/**
	 * 停止播放并释放所有茎数据与音频行资源。
	 * 之后可再次调用 {@link #loadStem} 重新加载。
	 */
	public synchronized void clearStems() {
		stopMixThread();
		closeOutputLine();
		stems.clear();
		sharedFormat = null;
		bytePos = 0;
		playing = false;
	}

	/**
	 * 设置指定茎的静音状态。线程安全（混音线程实时读取 volatile muted）。
	 *
	 * @param key   茎名称
	 * @param muted true = 静音，false = 恢复
	 */
	public void setStemMuted(String key, boolean muted) {
		StemTrack t = stems.get(key);
		if (t != null) t.muted = muted;
	}

	// ── IAudioPlayer ─────────────────────────────────────────────────────────

	@Override
	public boolean isPlaying() {
		return playing;
	}

	@Override
	public double getCurrentTimeSeconds() {
		if (sharedFormat == null) return 0.0;
		int frameSize = sharedFormat.getFrameSize();
		float frameRate = sharedFormat.getFrameRate();
		if (frameSize <= 0 || frameRate <= 0) return 0.0;
		return (bytePos / (double) frameSize) / frameRate;
	}

	@Override
	public synchronized void setCurrentTimeSeconds(double seconds) {
		if (sharedFormat == null) return;
		int frameSize = sharedFormat.getFrameSize();
		float frameRate = sharedFormat.getFrameRate();
		if (frameSize <= 0 || frameRate <= 0) return;

		int newPos = (int)(seconds * frameRate) * frameSize;
		newPos = Math.max(0, newPos);
		// 对齐到帧边界
		newPos = (newPos / frameSize) * frameSize;

		boolean wasPlaying = playing;
		if (wasPlaying) {
			stopMixThread();
		}
		bytePos = newPos;
		if (wasPlaying) {
			startMixThread();
		}
	}

	@Override
	public synchronized void play() {
		if (stems.isEmpty()) return;
		if (playing) return;
		if (!openOutputLine()) return;
		startMixThread();
		playing = true;
	}

	@Override
	public synchronized void pause() {
		if (!playing) return;
		stopMixThread();
		playing = false;
		// 记录暂停时刻的字节位置（mixThread 退出时 bytePos 已更新）
	}

	@Override
	public synchronized void stop() {
		stopMixThread();
		playing = false;
		bytePos = 0;
		if (outputLine != null) {
			outputLine.flush();
		}
	}

	// ── 内部：打开输出行 ──────────────────────────────────────────────────────

	private boolean openOutputLine() {
		if (outputLine != null && outputLine.isOpen()) return true;
		if (sharedFormat == null) return false;
		try {
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, sharedFormat);
			outputLine = (SourceDataLine) AudioSystem.getLine(info);
			outputLine.open(sharedFormat, 4096 * sharedFormat.getFrameSize());
			outputLine.start();
			return true;
		} catch (LineUnavailableException e) {
			LOGGER.error("BeatBlock StemMixer: cannot open SourceDataLine: {}", e.getMessage());
			return false;
		}
	}

	private void closeOutputLine() {
		if (outputLine != null) {
			try {
				outputLine.stop();
				outputLine.close();
			} catch (Exception ignored) {}
			outputLine = null;
		}
	}

	// ── 内部：混音线程 ────────────────────────────────────────────────────────

	private void startMixThread() {
		stopFlag = false;
		mixThread = new Thread(this::mixLoop, "beatblock-stem-mixer");
		mixThread.setDaemon(true);
		mixThread.start();
	}

	private void stopMixThread() {
		stopFlag = true;
		Thread t = mixThread;
		if (t != null) {
			t.interrupt();
			try { t.join(2000); } catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			mixThread = null;
		}
		if (outputLine != null) {
			outputLine.drain();
			outputLine.stop();
		}
	}

	/**
	 * 混音循环：每次读取 CHUNK 帧，对所有非静音茎做 16-bit 整数加法混音，
	 * 并截幅（-32768 ~ 32767），写入 SourceDataLine。
	 */
	private void mixLoop() {
		final int FRAMES_PER_CHUNK = 1024;
		final AudioFormat fmt = sharedFormat;
		if (fmt == null) return;

		final int frameSize = fmt.getFrameSize();
		final int chunkBytes = FRAMES_PER_CHUNK * frameSize;
		final int channels = fmt.getChannels();

		byte[] outBuf = new byte[chunkBytes];

		// 求最长茎字节数（决定总时长）
		int maxLen = 0;
		for (StemTrack st : stems.values()) maxLen = Math.max(maxLen, st.pcm.length);

		if (outputLine != null && !outputLine.isRunning()) {
			outputLine.start();
		}

		while (!stopFlag && !Thread.currentThread().isInterrupted()) {
			int pos = bytePos;
			if (pos >= maxLen) break; // 到达末尾

			// 当帧实际可读长度（不超出任何茎末尾和块大小）
			int actualBytes = Math.min(chunkBytes, maxLen - pos);
			actualBytes = (actualBytes / frameSize) * frameSize; // 对齐帧
			if (actualBytes <= 0) break;

			// 清零输出缓冲
			java.util.Arrays.fill(outBuf, 0, actualBytes, (byte) 0);

			// 逐茎混音
			for (StemTrack st : stems.values()) {
				if (st.muted) continue;
				byte[] src = st.pcm;
				for (int i = 0; i < actualBytes; i += 2) {
					int srcOff = pos + i;
					if (srcOff + 1 >= src.length) break;
					// 读 16-bit signed little-endian sample
					short sample = (short)((src[srcOff + 1] << 8) | (src[srcOff] & 0xFF));
					// 读已有混音值
					short mixed = (short)((outBuf[i + 1] << 8) | (outBuf[i] & 0xFF));
					// 加法混音，截幅
					int sum = (int) mixed + (int) sample;
					if (sum > 32767) sum = 32767;
					else if (sum < -32768) sum = -32768;
					outBuf[i]     = (byte)(sum & 0xFF);
					outBuf[i + 1] = (byte)((sum >> 8) & 0xFF);
				}
			}

			// 写入 SourceDataLine（阻塞直到缓冲接受）
			if (outputLine != null) {
				outputLine.write(outBuf, 0, actualBytes);
			}

			bytePos = pos + actualBytes;
		}

		playing = false;
	}
}
