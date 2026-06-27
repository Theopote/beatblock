package com.beatblock.audio.process;

import com.beatblock.audio.AnalysisProgressCallback;
import com.beatblock.audio.AnalysisSummary;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Python analyze.py 的 stdout 协议解析（PROGRESS / RESULT / ERROR）。
 */
public final class AnalyzerProcessIo {

	private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerProcessIo.class);

	public record StdoutParseResult(String resultJson, String errorText) {}

	private AnalyzerProcessIo() {}

	public static @NonNull String consumeStdout(
		@NonNull InputStream stdout,
		@NonNull AnalysisProgressCallback onProgress
	) throws IOException {
		String resultJson = null;
		StringBuilder errorBuf = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("PROGRESS ")) {
					String[] parts = line.split(" ", 3);
					if (parts.length == 3) {
						try {
							String step = parts[1];
							int pct = Integer.parseInt(parts[2].trim());
							onProgress.onProgress(step, pct);
						} catch (NumberFormatException e) {
							LOGGER.debug("Malformed PROGRESS line: {}", line, e);
						}
					}
				} else if (line.startsWith("RESULT ")) {
					resultJson = line.substring("RESULT ".length());
				} else if (line.startsWith("ERROR ")) {
					errorBuf.append(line.substring("ERROR ".length())).append('\n');
				}
			}
		}
		return toStdoutResult(resultJson, errorBuf.toString());
	}

	public static @NonNull StdoutParseResult parseStdoutResult(@Nullable String raw) {
		if (raw == null) return new StdoutParseResult("", "");
		int sep = raw.indexOf('\u001f');
		if (sep < 0) return new StdoutParseResult(raw, "");
		String result = raw.substring(0, sep);
		String error = sep + 1 < raw.length() ? raw.substring(sep + 1) : "";
		return new StdoutParseResult(result, error);
	}

	public static @Nullable AnalysisSummary parseResultSummary(@Nullable String resultJson) {
		if (resultJson == null || resultJson.isBlank()) return null;
		try {
			JsonObject o = JsonParser.parseString(resultJson).getAsJsonObject();
			float bpm = o.has("bpm") ? o.get("bpm").getAsFloat() : 0f;
			int beatCount = o.has("beat_count") ? o.get("beat_count").getAsInt() : 0;
			int sectionCount = o.has("section_count") ? o.get("section_count").getAsInt() : 0;
			long durationMs = o.has("duration_ms") ? o.get("duration_ms").getAsLong() : 0L;
			String separationMode = o.has("separation_mode") ? o.get("separation_mode").getAsString() : "basic";
			String cacheSource = o.has("cache_source") ? o.get("cache_source").getAsString() : "fresh";
			return new AnalysisSummary(
				bpm, beatCount, sectionCount, durationMs, separationMode, cacheSource);
		} catch (RuntimeException e) {
			LOGGER.debug("Failed to parse analysis result summary JSON", e);
			return null;
		}
	}

	private static String toStdoutResult(String resultJson, String errorText) {
		String result = resultJson == null ? "" : resultJson.replace("\u001f", " ");
		String error = errorText == null ? "" : errorText.replace("\u001f", " ");
		return result + "\u001f" + error;
	}
}
