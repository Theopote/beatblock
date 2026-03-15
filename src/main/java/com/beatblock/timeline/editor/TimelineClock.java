package com.beatblock.timeline.editor;

/**
 * 时间系统：播放、跳转、当前时间、播放速度。
 * 与 MusicPlayer 可同步或独立。
 */
public class TimelineClock {

	private double currentTimeSeconds;
	private double durationSeconds;
	private boolean playing;
	private double playbackSpeed = 1.0;

	public double getCurrentTimeSeconds() {
		return currentTimeSeconds;
	}

	public void setCurrentTimeSeconds(double currentTimeSeconds) {
		this.currentTimeSeconds = Math.max(0, Math.min(currentTimeSeconds, durationSeconds));
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = Math.max(0, durationSeconds);
		this.currentTimeSeconds = Math.min(this.currentTimeSeconds, durationSeconds);
	}

	public boolean isPlaying() {
		return playing;
	}

	public double getPlaybackSpeed() {
		return playbackSpeed;
	}

	public void setPlaybackSpeed(double playbackSpeed) {
		this.playbackSpeed = Math.max(0.1, Math.min(4.0, playbackSpeed));
	}

	public void play() {
		playing = true;
	}

	public void pause() {
		playing = false;
	}

	public void seek(double timeSeconds) {
		setCurrentTimeSeconds(timeSeconds);
	}

	/** 每帧调用：currentTime += delta * playbackSpeed */
	public void update(double deltaSeconds) {
		if (!playing || durationSeconds <= 0) return;
		currentTimeSeconds = Math.min(currentTimeSeconds + deltaSeconds * playbackSpeed, durationSeconds);
		if (currentTimeSeconds >= durationSeconds) {
			playing = false;
		}
	}
}
