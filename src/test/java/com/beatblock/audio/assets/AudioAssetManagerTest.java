package com.beatblock.audio.assets;

import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.runtime.BeatBlockContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioAssetManagerTest {

	private AudioAssetManager manager;

	@BeforeEach
	void setUp() {
		manager = AudioAssetManager.getInstance();
	}

	@AfterEach
	void tearDown() {
		for (AudioAsset asset : manager.getAssets()) {
			manager.remove(asset.getId());
		}
		AudioAssetManager.resetContextBindingForTests();
	}

	@Test
	void startAnalysisFailsWhenContextHasNoAnalyzer() {
		manager.bindContext(() -> BeatBlockContext.builder().build());
		AudioAsset asset = new AudioAsset(Path.of("sample.mp3"));

		manager.startAnalysis(asset);

		assertEquals(AudioAssetStatus.FAILED, asset.getStatus());
		assertTrue(asset.getErrorMessage().contains("未初始化"));
	}

	@Test
	void clearCacheAndReanalyzeRequiresAnalyzer() {
		manager.bindContext(() -> BeatBlockContext.builder().build());
		AudioAsset asset = new AudioAsset(Path.of("sample.mp3"));

		String result = manager.clearCacheAndReanalyze(asset);

		assertEquals("外部音频分析器未初始化", result);
	}

	@Test
	void startAnalysisUsesDemucsFlagFromInjectedContext() {
		AudioAnalysisService service = new AudioAnalysisService();
		service.setUseDemucs(false);
		manager.bindContext(() -> BeatBlockContext.builder().externalAudioAnalyzer(service).build());
		AudioAsset asset = new AudioAsset(Path.of("sample.mp3"));

		manager.startAnalysis(asset);

		assertEquals(AudioAnalysisMode.BASIC, asset.getRequestedAnalysisMode());
		assertTrue(
			asset.getStatus() == AudioAssetStatus.QUEUED || asset.getStatus() == AudioAssetStatus.ANALYZING,
			"unexpected status: " + asset.getStatus()
		);
	}
}
