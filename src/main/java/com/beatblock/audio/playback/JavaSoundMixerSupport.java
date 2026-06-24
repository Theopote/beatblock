package com.beatblock.audio.playback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/** JavaSound Clip / SourceDataLine 混音器探测与线路获取。 */
public final class JavaSoundMixerSupport {

	private static final Logger LOGGER = LoggerFactory.getLogger(JavaSoundMixerSupport.class);

	private JavaSoundMixerSupport() {}

	public static Clip acquireClip(AudioFormat preferredFormat) throws LineUnavailableException {
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

	public static Clip acquireClipFromAnyMixer(AudioFormat fmt) throws LineUnavailableException {
		try {
			return acquireClip(fmt);
		} catch (Throwable ignored) {
		}
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

	public static SourceDataLine openSourceDataLineFromAnyMixer(AudioFormat fmt) {
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
		try {
			SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
			line.open(fmt);
			return line;
		} catch (Throwable ignored) {
		}
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
}
