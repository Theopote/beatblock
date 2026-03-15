package com.beatblock.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 从 WAV 文件或流解码为单声道 float[]（-1..1），供波形与频段分析使用。
 */
public final class WavDecoder {

	private static final Logger LOGGER = LoggerFactory.getLogger(WavDecoder.class);

	/**
	 * 从文件路径加载 WAV，返回解码后的音频；失败返回 null。
	 */
	public static DecodedAudio loadFromPath(String path) {
		if (path == null || path.isEmpty()) return null;
		Path p = Paths.get(path);
		if (!Files.isRegularFile(p)) {
			LOGGER.warn("BeatBlock: 非文件或不存在: {}", path);
			return null;
		}
		try (AudioInputStream ais = AudioSystem.getAudioInputStream(p.toFile())) {
			return decodeToMonoFloat(ais);
		} catch (Exception e) {
			LOGGER.warn("BeatBlock: 无法加载 WAV: {}", path, e);
			return null;
		}
	}

	/**
	 * 从 InputStream 加载 WAV（例如资源流），返回解码后的音频；失败返回 null。
	 */
	public static DecodedAudio loadFromStream(InputStream in) {
		if (in == null) return null;
		try (AudioInputStream ais = AudioSystem.getAudioInputStream(in)) {
			return decodeToMonoFloat(ais);
		} catch (Exception e) {
			LOGGER.warn("BeatBlock: 无法从流解码 WAV", e);
			return null;
		}
	}

	private static DecodedAudio decodeToMonoFloat(AudioInputStream ais) throws IOException {
		AudioFormat fmt = ais.getFormat();
		int sampleRate = (int) fmt.getSampleRate();
		int channels = fmt.getChannels();
		int bits = fmt.getSampleSizeInBits();
		boolean bigEndian = fmt.isBigEndian();

		long frameCount = ais.getFrameLength();
		if (frameCount <= 0 || frameCount > 60 * 60 * sampleRate) {
			LOGGER.warn("BeatBlock: 无效或过长的帧数: {}", frameCount);
			return null;
		}
		int bytesPerFrame = fmt.getFrameSize();
		byte[] raw = new byte[(int) Math.min(frameCount * bytesPerFrame, 512 * 1024 * 1024)];
		int totalRead = 0;
		int r;
		while (totalRead < raw.length && (r = ais.read(raw, totalRead, raw.length - totalRead)) != -1) {
			totalRead += r;
		}
		int frames = totalRead / bytesPerFrame;
		float[] mono = new float[frames];
		ByteBuffer bb = ByteBuffer.wrap(raw, 0, totalRead).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

		for (int i = 0; i < frames; i++) {
			float sample = 0;
			for (int c = 0; c < channels; c++) {
				if (bits == 16) {
					sample += bb.getShort() / 32768f;
				} else if (bits == 24) {
					int b0 = bb.get() & 0xFF;
					int b1 = bb.get() & 0xFF;
					int b2 = bb.get() & 0xFF;
					int s = bigEndian ? (b0 << 16) | (b1 << 8) | b2 : (b2 << 16) | (b1 << 8) | b0;
					if ((s & 0x800000) != 0) s |= 0xFF000000;
					sample += (s / 8388608f);
				} else if (bits == 32 && fmt.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
					sample += bb.getInt() / 2147483648f;
				} else {
					bb.position(bb.position() + (bits / 8));
				}
			}
			mono[i] = (channels > 0) ? (sample / channels) : 0;
		}
		double durationSeconds = frames / (double) sampleRate;
		return new DecodedAudio(mono, sampleRate, durationSeconds);
	}
}
