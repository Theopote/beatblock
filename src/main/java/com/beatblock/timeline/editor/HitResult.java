package com.beatblock.timeline.editor;

/**
 * 统一 HitTest 结果：类型 + 关联 ID + 可选时间（TIME_HEADER / SCRUB）。
 */
public class HitResult {

	private final HitType hitType;
	private final String trackId;
	private final String clipId;
	private final String eventId;
	private final double timeSeconds;

	public HitResult(HitType hitType, String trackId, String clipId, String eventId, double timeSeconds) {
		this.hitType = hitType != null ? hitType : HitType.EMPTY;
		this.trackId = trackId;
		this.clipId = clipId;
		this.eventId = eventId;
		this.timeSeconds = Math.max(0, timeSeconds);
	}

	public static HitResult empty() {
		return new HitResult(HitType.EMPTY, null, null, null, 0);
	}

	public static HitResult timeHeader(double timeSeconds) {
		return new HitResult(HitType.TIME_HEADER, null, null, null, timeSeconds);
	}

	public static HitResult event(String trackId, String clipId, String eventId, double timeSeconds) {
		return new HitResult(HitType.EVENT, trackId, clipId, eventId, timeSeconds);
	}

	public static HitResult clip(String trackId, String clipId) {
		return new HitResult(HitType.CLIP, trackId, clipId, null, 0);
	}

	public static HitResult track(String trackId) {
		return new HitResult(HitType.TRACK, trackId, null, null, 0);
	}

	public HitType getHitType() { return hitType; }
	public String getTrackId() { return trackId; }
	public String getClipId() { return clipId; }
	public String getEventId() { return eventId; }
	public double getTimeSeconds() { return timeSeconds; }
	public boolean isEmpty() { return hitType == HitType.EMPTY; }
}
