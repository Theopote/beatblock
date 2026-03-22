package com.beatblock.audio.beatmap;

/**
 * Beatmap 元信息，对应 JSON 中的 meta 字段。
 */
public record BeatmapMeta(
	String  sourceFile,
	long    durationMs,
	double  bpm,
	double  bpmConfidence,
	String  timeSignature,
	int     sampleRate,
	String  generatedAt,
	String  analyzerVersion,
	String  style,              // "acoustic" | "electronic" | null for old beatmaps
	String  separationMode,     // "demucs" | null (no stem separation)
	java.util.Map<String, String> stems  // stem_name -> relative wav path, null if no stems
) {
	/** Whether this beatmap was generated with stem separation. */
	public boolean hasStemSeparation() {
		return separationMode != null && stems != null && !stems.isEmpty();
	}
}

