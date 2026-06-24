package com.beatblock.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPlayerTest {

	@Test
	void loadAudioRejectsBlankPath() {
		MusicPlayer player = new MusicPlayer();

		assertFalse(player.loadAudio(null));
		assertFalse(player.loadAudio("  "));
		assertEquals("未提供音频路径", player.getLastLoadError());
		assertNull(player.getLoadedAudioPath());
	}

	@Test
	void tickAdvancesTimeWhenNoBackendLoaded() {
		MusicPlayer player = new MusicPlayer();
		player.setDurationSeconds(10);
		player.play();

		player.tick(1.5);

		assertEquals(1.5, player.getCurrentTimeSeconds(), 1e-6);
	}

	@Test
	void muteFlagIsTracked() {
		MusicPlayer player = new MusicPlayer();
		assertFalse(player.isMuted());
		player.setMuted(true);
		assertTrue(player.isMuted());
	}
}
