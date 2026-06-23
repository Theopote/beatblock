package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.FrequencyBands;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleDirectorTest {

	@Test
	void disabledOrShortInputReturnsEmpty() {
		List<FrequencyBands> bands = List.of(
			new FrequencyBands(0.0, 0, 0, 0.1f),
			new FrequencyBands(0.1, 0, 0, 0.5f)
		);
		assertTrue(ParticleDirector.generate(bands, false).isEmpty());
		assertTrue(ParticleDirector.generate(bands, true).isEmpty());
		assertTrue(ParticleDirector.generate(null, true).isEmpty());
	}

	@Test
	void detectsLocalHighEnergyPeaks() {
		List<FrequencyBands> bands = List.of(
			new FrequencyBands(0.0, 0, 0, 0.1f),
			new FrequencyBands(0.1, 0, 0, 0.5f),
			new FrequencyBands(0.2, 0, 0, 0.1f)
		);
		List<ParticleEvent> events = ParticleDirector.generate(bands, true);
		assertEquals(1, events.size());
		assertEquals(0.1, events.getFirst().getTimeSeconds(), 1e-9);
		assertEquals(ParticleType.SPARK, events.getFirst().getType());
	}

	@Test
	void classifiesFlashSparkAndDustByEnergy() {
		List<FrequencyBands> dust = List.of(
			new FrequencyBands(0.0, 0, 0, 0.1f),
			new FrequencyBands(0.1, 0, 0, 0.25f),
			new FrequencyBands(0.2, 0, 0, 0.1f)
		);
		assertEquals(ParticleType.DUST, ParticleDirector.generate(dust, true).getFirst().getType());

		List<FrequencyBands> flash = List.of(
			new FrequencyBands(0.0, 0, 0, 0.1f),
			new FrequencyBands(0.1, 0, 0, 0.7f),
			new FrequencyBands(0.2, 0, 0, 0.1f)
		);
		assertEquals(ParticleType.FLASH, ParticleDirector.generate(flash, true).getFirst().getType());
	}

	@Test
	void enforcesMinimumGapBetweenParticles() {
		List<FrequencyBands> bands = List.of(
			new FrequencyBands(0.0, 0, 0, 0.1f),
			new FrequencyBands(0.1, 0, 0, 0.5f),
			new FrequencyBands(0.15, 0, 0, 0.6f),
			new FrequencyBands(0.2, 0, 0, 0.1f)
		);
		assertEquals(1, ParticleDirector.generate(bands, true).size());
	}
}
