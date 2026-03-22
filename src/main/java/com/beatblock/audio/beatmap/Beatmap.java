package com.beatblock.audio.beatmap;

import java.util.List;

/**
 * Beatmap 根对象 —— 对应 JSON 契约的顶层结构。
 * 由 BeatmapReader 从 .beatmap 文件反序列化而来，之后只读。
 */
public final class Beatmap {

	/** 契约版本号，与 Python 脚本保持一致 */
	public final int version;
	public final BeatmapMeta meta;
	public final List<BeatEvent> beats;
	public final List<MusicSection> sections;
	/** 可选：UI 波形预览数据，可能为 null */
	public final WaveformPreview waveformPreview;
	/** 可选：每条茎的独立波形预览，key = stem name (drums/bass/vocals/other) */
	public final java.util.Map<String, WaveformPreview> stemWaveforms;

	/**
	 * 运行时元数据：该 beatmap 文件的绝对路径（读取后由 BeatmapReader 设置）。
	 * 用于解析茎音频 WAV 文件的相对路径。不参与序列化。
	 */
	public java.nio.file.Path beatmapFilePath;

	public Beatmap(int version, BeatmapMeta meta,
	               List<BeatEvent> beats, List<MusicSection> sections,
	               WaveformPreview waveformPreview,
	               java.util.Map<String, WaveformPreview> stemWaveforms) {
		this.version         = version;
		this.meta            = meta;
		this.beats           = List.copyOf(beats);
		this.sections        = List.copyOf(sections);
		this.waveformPreview = waveformPreview;
		this.stemWaveforms   = stemWaveforms != null ? java.util.Map.copyOf(stemWaveforms) : java.util.Map.of();
	}

	// ── 便捷查询 ─────────────────────────────────────────────────────────

	/** 返回指定频段的踩点列表 */
	public List<BeatEvent> beatsForBand(FrequencyBand band) {
		return beats.stream()
			.filter(b -> b.band() == band)
			.toList();
	}

	/** 返回指定时间范围内的踩点（start 含，end 不含，单位 ms）*/
	public List<BeatEvent> beatsInRange(long startMs, long endMs) {
		return beats.stream()
			.filter(b -> b.timeMs() >= startMs && b.timeMs() < endMs)
			.toList();
	}

	/** 找出 timeMs 所属的段落，找不到返回 null */
	public MusicSection sectionAt(long timeMs) {
		for (MusicSection s : sections) {
			if (timeMs >= s.startMs() && timeMs < s.endMs()) return s;
		}
		return null;
	}
}

