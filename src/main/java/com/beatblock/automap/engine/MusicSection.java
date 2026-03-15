package com.beatblock.automap.engine;

/**
 * 音乐段落：起止时间与类型，用于驱动镜头与动画密度。
 */
public final class MusicSection {

	private final double startSeconds;
	private final double endSeconds;
	private final SectionType type;

	public MusicSection(double startSeconds, double endSeconds, SectionType type) {
		this.startSeconds = Math.max(0, startSeconds);
		this.endSeconds = Math.max(this.startSeconds, endSeconds);
		this.type = type != null ? type : SectionType.VERSE;
	}

	public double getStartSeconds() { return startSeconds; }
	public double getEndSeconds() { return endSeconds; }
	public SectionType getType() { return type; }
	public double getDurationSeconds() { return endSeconds - startSeconds; }
}
