package com.beatblock.audio.assets;

public enum AudioAnalysisMode {
	BASIC,
	DEMUCS;

	public String label() {
		return this == DEMUCS ? "Demucs" : "Basic";
	}
}
