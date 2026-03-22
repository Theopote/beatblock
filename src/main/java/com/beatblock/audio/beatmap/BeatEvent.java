package com.beatblock.audio.beatmap;

/**
 * 单个踩点事件，对应 JSON beats 数组中的一个元素。
 *
 * <p>{@code bandKey} 是开放字符串键（如 "kick"、"snare"、"hihat"，也可是脚本自定义键）。
 * 遗留 {@link #band()} 方法将 key 映射回 {@link FrequencyBand} 枚举，用于向后兼容。</p>
 */
public final class BeatEvent implements Comparable<BeatEvent> {

	public final long       timeMs;
	/** 开放键，Python 脚本写什么这里就是什么（e.g. "kick", "snare", "hihat"）。 */
	public final String     bandKey;
	public final float      energy;
	public final AnchorType anchor;
	public final int        beatIndex;
	public final int        barIndex;
	public final int        beatInBar;

	public BeatEvent(long timeMs, String bandKey, float energy,
	                 AnchorType anchor, int beatIndex, int barIndex, int beatInBar) {
		this.timeMs    = timeMs;
		this.bandKey   = bandKey != null ? bandKey : "low";
		this.energy    = Math.max(0f, Math.min(1f, energy));
		this.anchor    = anchor != null ? anchor : AnchorType.ARRIVE;
		this.beatIndex = beatIndex;
		this.barIndex  = barIndex;
		this.beatInBar = beatInBar;
	}

	/** Shim constructor: 从旧 FrequencyBand 枚举创建（仅供 Java 内部分析路径使用）。 */
	public BeatEvent(long timeMs, FrequencyBand band, float energy,
	                 AnchorType anchor, int beatIndex, int barIndex, int beatInBar) {
		this(timeMs, band != null ? band.name().toLowerCase() : "low",
		     energy, anchor, beatIndex, barIndex, beatInBar);
	}

	// ── 访问器（保持 record 风格，方便保持调用侧兼容） ───────────────────

	public long       timeMs()    { return timeMs;    }
	public String     bandKey()   { return bandKey;   }
	public float      energy()    { return energy;    }
	public AnchorType anchor()    { return anchor;    }
	public int        beatIndex() { return beatIndex; }
	public int        barIndex()  { return barIndex;  }
	public int        beatInBar() { return beatInBar; }

	/**
	 * 向后兼容：将 bandKey 映射回 {@link FrequencyBand} 枚举。
	 * 未知 key 按照感知优先级回退：kick → LOW，其余含义接近中频的 → MID，高频 → HIGH。
	 */
	public FrequencyBand band() {
		return switch (bandKey.toLowerCase()) {
			case "low", "kick", "bass", "sub"       -> FrequencyBand.LOW;
			case "high", "hihat", "hat", "cymbal"   -> FrequencyBand.HIGH;
			default                                  -> FrequencyBand.MID;
		};
	}

	@Override
	public int compareTo(BeatEvent other) {
		return Long.compare(this.timeMs, other.timeMs);
	}
}

