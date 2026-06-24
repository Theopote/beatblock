package com.beatblock.audio.playback;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import java.nio.ByteBuffer;

/** LWJGL OpenAL PCM 播放后端（Minecraft 内 JavaSound 不可用时的兜底）。 */
public final class OpenAlMusicBackend {

	@FunctionalInterface
	public interface RecoveryHandler {
		boolean recover();
	}

	private final Logger logger;
	private final RecoveryHandler recoveryHandler;

	private int alBuffer;
	private int alSource;
	private double bytesPerSecond;
	private boolean recovering;

	public OpenAlMusicBackend(Logger logger, RecoveryHandler recoveryHandler) {
		this.logger = logger;
		this.recoveryHandler = recoveryHandler;
	}

	public boolean isActive() {
		return alSource != 0;
	}

	public double getBytesPerSecond() {
		return bytesPerSecond;
	}

	public void open(byte[] pcmBytes, AudioFormat pcmFmt) throws LineUnavailableException {
		close();
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
			bytesPerSecond = pcmFmt.getFrameRate() * pcmFmt.getFrameSize();
			logger.info(
				"BeatBlock MusicPlayer: OpenAL backend ready {}Hz/{}ch duration={}s",
				sampleRate,
				channels,
				String.format("%.3f", pcmBytes.length / bytesPerSecond)
			);
		} catch (LineUnavailableException e) {
			throw e;
		} catch (Throwable e) {
			close();
			throw new LineUnavailableException("OpenAL backend init failed: " + e.getMessage());
		}
	}

	public void close() {
		bytesPerSecond = 0.0;
		if (alSource != 0) {
			try {
				AL10.alSourceStop(alSource);
				AL10.alSourcei(alSource, AL10.AL_BUFFER, 0);
				AL10.alDeleteSources(alSource);
			} catch (Throwable ignored) {
			}
			alSource = 0;
		}
		if (alBuffer != 0) {
			try {
				AL10.alDeleteBuffers(alBuffer);
			} catch (Throwable ignored) {
			}
			alBuffer = 0;
		}
	}

	public boolean ensureReady(String loadedPath, StringBuilder lastLoadError) {
		if (!isActive()) return false;
		if (recovering) return false;
		if (isValid()) return true;
		return recover(loadedPath, lastLoadError);
	}

	public void play(boolean restartFromStart, double currentTimeSeconds) {
		if (!isActive()) return;
		if (restartFromStart) {
			try {
				AL11.alSourcef(alSource, AL11.AL_SEC_OFFSET, 0.0f);
			} catch (Throwable ignored) {
			}
		}
		try {
			AL10.alSourcePlay(alSource);
		} catch (Throwable ignored) {
		}
	}

	public void pause(double currentTimeSeconds) {
		if (!isActive()) return;
		try {
			AL10.alSourcePause(alSource);
		} catch (Throwable ignored) {
		}
	}

	public void stop() {
		if (!isActive()) return;
		try {
			AL10.alSourceStop(alSource);
			AL11.alSourcef(alSource, AL11.AL_SEC_OFFSET, 0.0f);
		} catch (Throwable ignored) {
		}
	}

	public void seek(double seconds, boolean wasPlaying) {
		if (!isActive()) return;
		try {
			if (wasPlaying && AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
				AL10.alSourcePause(alSource);
			}
			AL11.alSourcef(alSource, AL11.AL_SEC_OFFSET, (float) seconds);
			if (wasPlaying) {
				AL10.alSourcePlay(alSource);
			}
		} catch (Throwable ignored) {
		}
	}

	public double positionSeconds(double fallback) {
		if (!isActive()) return fallback;
		try {
			return AL10.alGetSourcef(alSource, AL11.AL_SEC_OFFSET);
		} catch (Throwable ignored) {
			return fallback;
		}
	}

	public void applyGain(boolean muted) {
		if (!isActive()) return;
		try {
			AL10.alSourcef(alSource, AL10.AL_GAIN, muted ? 0.0f : 1.0f);
		} catch (Throwable ignored) {
		}
	}

	public boolean syncPlayingState(boolean playing, double durationSeconds) {
		if (!isActive()) return false;
		try {
			if (playing) {
				int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
				if (state == AL10.AL_STOPPED || state == AL10.AL_INITIAL) {
					return durationSeconds > 0;
				}
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	public boolean isPlayingOpenAl() {
		if (!isActive()) return false;
		try {
			return AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private boolean isValid() {
		if (!isActive()) return false;
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

	private boolean recover(String loadedPath, StringBuilder lastLoadError) {
		if (loadedPath == null || loadedPath.isBlank()) {
			close();
			if (lastLoadError != null) {
				lastLoadError.setLength(0);
				lastLoadError.append("OpenAL 设备重建后无法恢复：未绑定音频文件");
			}
			return false;
		}
		recovering = true;
		try {
			logger.warn("BeatBlock MusicPlayer: OpenAL handles became invalid, rebuilding backend path={}", loadedPath);
			return recoveryHandler.recover();
		} finally {
			recovering = false;
		}
	}
}
