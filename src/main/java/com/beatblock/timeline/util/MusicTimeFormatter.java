package com.beatblock.timeline.util;

/**
 * 音乐时间格式化工具：将秒数转换为 mm:ss 和小节:拍 格式，
 * 用于时间线 UI 标尺标签与工具栏时间显示。
 *
 * 假设拍号为 4/4。
 */
public final class MusicTimeFormatter {

	private static final int BEATS_PER_BAR = 4;

	private MusicTimeFormatter() {}

	/**
	 * 格式化为 m:ss。
	 * 示例：0 → "0:00"，63.7 → "1:03"，3723 → "62:03"
	 */
	public static String formatMmSs(double seconds) {
		int total = (int) Math.max(0, seconds);
		int m = total / 60;
		int s = total % 60;
		return m + ":" + String.format("%02d", s);
	}

	/**
	 * 格式化为 m:ss.f（含十分秒），适合高精度显示。
	 * 示例：63.7 → "1:03.7"
	 */
	public static String formatMmSsFraction(double seconds) {
		int total = (int) Math.max(0, seconds);
		int m = total / 60;
		int s = total % 60;
		int f = (int) ((seconds - Math.floor(seconds)) * 10);
		return m + ":" + String.format("%02d", s) + "." + f;
	}

	/**
	 * 返回 "Bar N Beat M" 字符串（1-based，4/4 拍号）。
	 * bpm ≤ 0 时返回空串。
	 */
	public static String formatBarBeat(double seconds, double bpm) {
		if (bpm <= 0) return "";
		double spb = 60.0 / bpm;
		double totalBeats = Math.max(0, seconds) / spb;
		int bar  = (int) (totalBeats / BEATS_PER_BAR) + 1;
		int beat = ((int) totalBeats % BEATS_PER_BAR) + 1;
		return "Bar " + bar + " Beat " + beat;
	}

	/**
	 * 工具栏右侧完整位置显示。
	 * 有 BPM → "1:03 / 3:42  |  Bar 4 Beat 2"
	 * 无 BPM → "1:03 / 3:42"
	 */
	public static String formatPositionDisplay(double currentSec, double durationSec, double bpm) {
		String cur = formatMmSs(currentSec);
		String dur = formatMmSs(durationSec);
		if (bpm > 0) {
			return cur + " / " + dur + "  |  " + formatBarBeat(currentSec, bpm);
		}
		return cur + " / " + dur;
	}

	/**
	 * 返回小节序号（1-based）。bpm ≤ 0 时返回 0。
	 */
	public static int barNumber(double seconds, double bpm) {
		if (bpm <= 0) return 0;
		double spb = 60.0 / bpm;
		double totalBeats = Math.max(0, seconds) / spb;
		return (int) (totalBeats / BEATS_PER_BAR) + 1;
	}

	/**
	 * 返回拍号（1-based，范围 1–4）。bpm ≤ 0 时返回 0。
	 */
	public static int beatNumber(double seconds, double bpm) {
		if (bpm <= 0) return 0;
		double spb = 60.0 / bpm;
		double totalBeats = Math.max(0, seconds) / spb;
		return ((int) totalBeats % BEATS_PER_BAR) + 1;
	}
}
