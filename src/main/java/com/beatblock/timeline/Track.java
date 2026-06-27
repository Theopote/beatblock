package com.beatblock.timeline;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 轨道：一条时间线轨道，包含多个 Clip；AUDIO 类型另有 AudioTrackData。
 */
public class Track {

	private String id;
	private String name;
	private TrackType type;
	private final List<Clip> clips = new ArrayList<>();
	private boolean enabled = true;
	private @Nullable AudioTrackData audioData;

	public Track() { this("", "", TrackType.ANIMATION); }

	public Track(@Nullable String id, @Nullable String name, @Nullable TrackType type) {
		this.id = id != null ? id : "";
		this.name = name != null ? name : "";
		this.type = type != null ? type : TrackType.ANIMATION;
		if (this.type == TrackType.AUDIO) this.audioData = new AudioTrackData();
	}

	public @NonNull String getId() { return id; }
	public void setId(@Nullable String id) { this.id = id != null ? id : ""; }
	public @NonNull String getName() { return name; }
	public void setName(@Nullable String name) { this.name = name != null ? name : ""; }
	public @NonNull TrackType getType() { return type; }
	public void setType(@Nullable TrackType type) { this.type = type != null ? type : TrackType.ANIMATION; }
	public @NonNull List<Clip> getClips() { return Collections.unmodifiableList(clips); }
	public void addClip(@Nullable Clip clip) { if (clip != null) clips.add(clip); }
	public boolean removeClip(@Nullable String clipId) { return clips.removeIf(c -> clipId != null && clipId.equals(c.getId())); }
	public @Nullable Clip getClip(@Nullable String clipId) {
		for (Clip c : clips) if (clipId != null && clipId.equals(c.getId())) return c;
		return null;
	}
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public @Nullable AudioTrackData getAudioData() { return audioData; }
	public void setAudioData(@Nullable AudioTrackData audioData) { this.audioData = audioData; }
}
