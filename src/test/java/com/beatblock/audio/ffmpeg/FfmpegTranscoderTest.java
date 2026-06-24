package com.beatblock.audio.ffmpeg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegTranscoderTest {

	@TempDir
	Path tempDir;

	@Test
	void buildMp3CommandUsesLibmp3lame() {
		Path input = tempDir.resolve("song.flac");
		Path output = tempDir.resolve("song.mp3");

		List<String> cmd = FfmpegTranscoder.buildMp3Command("ffmpeg.exe", input, output);
		assertEquals("ffmpeg.exe", cmd.get(0));
		assertTrue(cmd.contains("-codec:a"));
		assertTrue(cmd.contains("libmp3lame"));
		assertTrue(cmd.contains("-q:a"));
		assertTrue(cmd.contains("2"));
		assertEquals(output.toAbsolutePath().toString(), cmd.get(cmd.size() - 1));
	}

	@Test
	void resolveMp3OutputPathAvoidsExistingFile() throws Exception {
		Path input = tempDir.resolve("track.wav");
		Files.createFile(input);
		Path existing = tempDir.resolve("track.mp3");
		Files.createFile(existing);

		Path resolved = FfmpegTranscoder.resolveMp3OutputPath(input, tempDir);
		assertEquals(tempDir.resolve("track_converted_1.mp3"), resolved);
	}

	@Test
	void transcodeToMp3ReturnsAlreadyMp3WithoutExecutable() throws Exception {
		Path input = tempDir.resolve("ready.mp3");
		Files.writeString(input, "fake");

		var outcome = FfmpegTranscoder.transcodeToMp3(input, tempDir, () -> null, null);
		assertInstanceOf(FfmpegTranscodeOutcome.AlreadyMp3.class, outcome);
		assertEquals(input, ((FfmpegTranscodeOutcome.AlreadyMp3) outcome).path());
	}

	@Test
	void transcodeToMp3FailsWhenExecutableMissing() throws Exception {
		Path input = tempDir.resolve("track.flac");
		Files.writeString(input, "fake");

		var outcome = FfmpegTranscoder.transcodeToMp3(input, tempDir, () -> null, null);
		assertInstanceOf(FfmpegTranscodeOutcome.Failure.class, outcome);
		assertTrue(((FfmpegTranscodeOutcome.Failure) outcome).message().contains("找不到 ffmpeg"));
	}

	@Test
	void transcodeToMp3FailsWhenInputMissing() {
		var outcome = FfmpegTranscoder.transcodeToMp3(tempDir.resolve("missing.wav"), tempDir, () -> "ffmpeg", null);
		assertInstanceOf(FfmpegTranscodeOutcome.Failure.class, outcome);
		assertEquals("待转换文件不存在。", ((FfmpegTranscodeOutcome.Failure) outcome).message());
	}
}
