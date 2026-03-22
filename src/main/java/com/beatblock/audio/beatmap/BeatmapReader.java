package com.beatblock.audio.beatmap;

import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BeatmapReader
 * ─────────────────────────────────────────────────────────────────────────────
 * 将 .beatmap JSON 文件反序列化为 {@link Beatmap} 对象。
 *
 * <p>使用 Gson 手动解析（避免引入 Jackson 增加 mod 体积）。
 * 只读，不做任何写入操作。</p>
 *
 * <h3>版本兼容</h3>
 * <ul>
 *   <li>version == 1：完整支持</li>
 *   <li>version 未来增加字段：用 {@code getAsXxx} 的 null 检查容错</li>
 *   <li>version 不匹配：抛出 {@link BeatmapVersionException}</li>
 * </ul>
 */
public final class BeatmapReader {

	private static final int SUPPORTED_VERSION = 1;

	private BeatmapReader() {}

	// ── 公共 API ────────────────────────────────────────────────────────────

	/**
	 * 从文件路径读取并解析 beatmap。
	 *
	 * @param path .beatmap 文件路径
	 * @return 解析后的 Beatmap 对象
	 * @throws IOException              文件读取失败
	 * @throws BeatmapParseException    JSON 格式错误
	 * @throws BeatmapVersionException  版本不兼容
	 */
	public static Beatmap read(Path path)
		throws IOException, BeatmapParseException, BeatmapVersionException {
		String json = Files.readString(path, StandardCharsets.UTF_8);
		return parse(json);
	}

	/**
	 * 从 JSON 字符串解析（便于单元测试）。
	 */
	public static Beatmap parse(String json)
		throws BeatmapParseException, BeatmapVersionException {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			return deserialize(root);
		} catch (JsonParseException e) {
			throw new BeatmapParseException("JSON 格式错误：" + e.getMessage(), e);
		} catch (BeatmapVersionException e) {
			throw e;
		} catch (Exception e) {
			throw new BeatmapParseException("解析失败：" + e.getMessage(), e);
		}
	}

	// ── 反序列化逻辑 ─────────────────────────────────────────────────────────

	private static Beatmap deserialize(JsonObject root)
		throws BeatmapVersionException, BeatmapParseException {

		// ── 版本检查 ────────────────────────────────────────────────────────
		int version = root.get("version").getAsInt();
		if (version != SUPPORTED_VERSION) {
			throw new BeatmapVersionException(version, SUPPORTED_VERSION);
		}

		// ── meta ────────────────────────────────────────────────────────────
		BeatmapMeta meta = parseMeta(root.getAsJsonObject("meta"));

		// ── beats ────────────────────────────────────────────────────────────
		List<BeatEvent> beats = parseBeats(root.getAsJsonArray("beats"));

		// ── sections ─────────────────────────────────────────────────────────
		List<MusicSection> sections = parseSections(root.getAsJsonArray("sections"));

		// ── waveform_preview（可选）────────────────────────────────────────
		WaveformPreview waveform = null;
		if (root.has("waveform_preview") && !root.get("waveform_preview").isJsonNull()) {
			waveform = parseWaveform(root.getAsJsonObject("waveform_preview"));
		}

		// ── stem_waveforms（可选，Demucs 模式）──────────────────────────────
		Map<String, WaveformPreview> stemWaveforms = null;
		if (root.has("stem_waveforms") && !root.get("stem_waveforms").isJsonNull()) {
			stemWaveforms = parseStemWaveforms(root.getAsJsonObject("stem_waveforms"));
		}

		return new Beatmap(version, meta, beats, sections, waveform, stemWaveforms);
	}

	private static BeatmapMeta parseMeta(JsonObject m) {
		String style = m.has("style") && !m.get("style").isJsonNull()
			? m.get("style").getAsString() : null;
		String separationMode = m.has("separation_mode") && !m.get("separation_mode").isJsonNull()
			? m.get("separation_mode").getAsString() : null;
		Map<String, String> stems = null;
		if (m.has("stems") && m.get("stems").isJsonObject()) {
			stems = new HashMap<>();
			for (var entry : m.getAsJsonObject("stems").entrySet()) {
				if (!entry.getValue().isJsonNull()) {
					stems.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		}
		return new BeatmapMeta(
			getString(m, "source_file", "unknown"),
			getLong(m, "duration_ms", 0),
			getDouble(m, "bpm", 120.0),
			getDouble(m, "bpm_confidence", 1.0),
			getString(m, "time_signature", "4/4"),
			getInt(m, "sample_rate", 44100),
			getString(m, "generated_at", ""),
			getString(m, "analyzer_version", ""),
			style,
			separationMode,
			stems
		);
	}

	private static List<BeatEvent> parseBeats(JsonArray arr) throws BeatmapParseException {
		List<BeatEvent> list = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			JsonObject o = el.getAsJsonObject();
			try {
				// band 字段现在是开放字符串键（"kick"/"snare"/"hihat"或旧的"low"/"mid"/"high"）
				String bandKey = getString(o, "band", "low");
				list.add(new BeatEvent(
					getLong(o, "time_ms", 0),
					bandKey,
					(float) getDouble(o, "energy", 0.5),
					AnchorType.fromJson(getString(o, "anchor", "arrive")),
					getInt(o, "beat_index", 0),
					getInt(o, "bar_index", 0),
					getInt(o, "beat_in_bar", 0)
				));
			} catch (IllegalArgumentException e) {
				throw new BeatmapParseException("beats 字段解析失败：" + e.getMessage(), e);
			}
		}
		return list;
	}

	private static List<MusicSection> parseSections(JsonArray arr) {
		List<MusicSection> list = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			JsonObject o = el.getAsJsonObject();
			list.add(new MusicSection(
				getLong(o, "start_ms", 0),
				getLong(o, "end_ms", 0),
				SectionLabel.fromJson(getString(o, "label", "unknown")),
				(float) getDouble(o, "energy_mean", 0.5)
			));
		}
		return list;
	}

	private static WaveformPreview parseWaveform(JsonObject o) {
		int sps = getInt(o, "samples_per_second", 100);
		JsonArray dataArr = o.getAsJsonArray("data");
		float[] data = new float[dataArr.size()];
		for (int i = 0; i < dataArr.size(); i++) {
			data[i] = dataArr.get(i).getAsFloat();
		}
		return new WaveformPreview(sps, data);
	}

	private static Map<String, WaveformPreview> parseStemWaveforms(JsonObject obj) {
		Map<String, WaveformPreview> map = new HashMap<>();
		for (var entry : obj.entrySet()) {
			if (!entry.getValue().isJsonNull() && entry.getValue().isJsonObject()) {
				map.put(entry.getKey(), parseWaveform(entry.getValue().getAsJsonObject()));
			}
		}
		return map;
	}

	// ── JSON 字段安全读取工具 ─────────────────────────────────────────────────

	private static String getString(JsonObject o, String key, String def) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
	}

	private static int getInt(JsonObject o, String key, int def) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : def;
	}

	private static long getLong(JsonObject o, String key, long def) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : def;
	}

	private static double getDouble(JsonObject o, String key, double def) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : def;
	}

	// ── 异常类型 ──────────────────────────────────────────────────────────────

	public static final class BeatmapParseException extends Exception {
		public BeatmapParseException(String msg, Throwable cause) { super(msg, cause); }
	}

	public static final class BeatmapVersionException extends Exception {
		public final int found;
		public final int expected;
		public BeatmapVersionException(int found, int expected) {
			super(String.format("Beatmap 版本不兼容：文件版本 %d，支持版本 %d", found, expected));
			this.found    = found;
			this.expected = expected;
		}
	}
}

