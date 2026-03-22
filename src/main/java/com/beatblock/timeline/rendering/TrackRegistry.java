package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TrackRegistry — 根据 {@link Timeline} 中当前存在的音频特征数据动态生成音频子轨定义列表。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>波形行：始终存在。</li>
 *   <li>节奏组（kick / snare / hihat）：若 featureTracks 中含对应 key，则生成。</li>
 *   <li>扩展组：其余 featureTracks key，由 Python 脚本自定义写入的任意轨道。</li>
 *   <li>遗留回退：若 featureTracks 为空但存在遗留三频段数据，则降级生成 low/mid/high 三条轨道。</li>
 * </ul>
 */
public final class TrackRegistry {

	// ── 已知节奏键（priority 顺序）───────────────────────────────────────────
	private static final List<String> RHYTHM_KEYS = List.of("kick", "snare", "hihat");

	// ── 颜色表（ABGR，与 TimelineRenderer 遗留色对齐）───────────────────────
	// kick：偏暖橙红，snare：青绿，hihat：天蓝；扩展轨道用循环色板
	private static final int COLOR_KICK    = 0xFF_55_88_EE; // 暖橙红
	private static final int COLOR_SNARE   = 0xFF_57_C4_A0; // 青绿（沿用 MID 色）
	private static final int COLOR_HIHAT   = 0xFF_27_A0_EF; // 天蓝（沿用 HIGH 色）
	private static final int COLOR_LOW     = 0xFF_77_77_DD; // 遗留低频色
	private static final int COLOR_MID     = 0xFF_57_C4_A0; // 遗留中频色
	private static final int COLOR_HIGH    = 0xFF_27_A0_EF; // 遗留高频色

	/** 扩展轨道循环色板，提供最多 8 种可区分色。 */
	private static final int[] EXTENDED_COLORS = {
		0xFF_E0_88_40,
		0xFF_AA_55_CC,
		0xFF_40_C0_80,
		0xFF_CC_88_20,
		0xFF_60_A0_D0,
		0xFF_D0_60_80,
		0xFF_80_C0_40,
		0xFF_A0_80_D0,
	};

	private TrackRegistry() {}

	/**
	 * 从 Timeline 的当前特征数据构建音频子轨定义列表（含波形行）。
	 * 调用方应在每帧 Timeline 数据变化时重新调用（开销很低，仅遍历 key set）。
	 *
	 * @param timeline 当前时间线，可为 null（返回只含波形的最小列表）
	 * @return 不可变的轨道定义列表，按「波形 → 节奏组 → 扩展组」顺序排列
	 */
	public static List<TrackDefinition> buildAudioSubTracks(Timeline timeline) {
		List<TrackDefinition> result = new ArrayList<>();

		// 1. 波形行（始终有）
		result.add(new TrackDefinition(
			"waveform", "波形",
			TrackDefinition.VisualType.WAVEFORM,
			TrackDefinition.GROUP_NONE
		));

		if (timeline == null) return List.copyOf(result);

		Set<String> featureKeys = timeline.getFeatureTracks().keySet();

		if (!featureKeys.isEmpty()) {
			// ── 新路径：命名特征轨道 ──────────────────────────────────
			// 优先按 RHYTHM_KEYS 顺序输出已知节奏轨道
			for (String key : RHYTHM_KEYS) {
				if (featureKeys.contains(key)) {
					result.add(new TrackDefinition(
						key,
						localizedName(key),
						TrackDefinition.VisualType.IMPULSE,
						TrackDefinition.GROUP_RHYTHM,
						colorForKey(key, 0)
					));
				}
			}

			// 再输出不在 RHYTHM_KEYS 中的扩展轨道
			int extIdx = 0;
			for (String key : featureKeys) {
				if (!RHYTHM_KEYS.contains(key)) {
					result.add(new TrackDefinition(
						key,
						localizedName(key),
						TrackDefinition.VisualType.IMPULSE,
						TrackDefinition.GROUP_EXTENDED,
						EXTENDED_COLORS[extIdx % EXTENDED_COLORS.length]
					));
					extIdx++;
				}
			}
		} else {
			// ── 遗留回退：生成 low/mid/high 三条轨道 ────────────────
			result.add(new TrackDefinition("low",  "低频", TrackDefinition.VisualType.IMPULSE,
				TrackDefinition.GROUP_RHYTHM, COLOR_LOW));
			result.add(new TrackDefinition("mid",  "中频", TrackDefinition.VisualType.IMPULSE,
				TrackDefinition.GROUP_RHYTHM, COLOR_MID));
			result.add(new TrackDefinition("high", "高频", TrackDefinition.VisualType.IMPULSE,
				TrackDefinition.GROUP_RHYTHM, COLOR_HIGH));
		}

		return List.copyOf(result);
	}

	/** 本地化显示名称（未知 key 直接大写返回）。 */
	private static String localizedName(String key) {
		return switch (key.toLowerCase()) {
			case "kick"        -> "底鼓";
			case "snare"       -> "军鼓";
			case "hihat", "hat"-> "踩镲";
			case "bass"        -> "贝斯";
			case "vocal"       -> "人声";
			case "low"         -> "低频";
			case "mid"         -> "中频";
			case "high"        -> "高频";
			default            -> key;
		};
	}

	/** 根据 key 选取预设颜色；extFallbackIndex 用于扩展 key 时循环色板。 */
	private static int colorForKey(String key, int extFallbackIndex) {
		return switch (key.toLowerCase()) {
			case "kick"         -> COLOR_KICK;
			case "snare"        -> COLOR_SNARE;
			case "hihat", "hat" -> COLOR_HIHAT;
			case "low"          -> COLOR_LOW;
			case "mid"          -> COLOR_MID;
			case "high"         -> COLOR_HIGH;
			default             -> EXTENDED_COLORS[extFallbackIndex % EXTENDED_COLORS.length];
		};
	}
}
