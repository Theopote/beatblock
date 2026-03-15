package com.beatblock.timeline;

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
	private AudioTrackData audioData;

	public Track() { this("", "", TrackType.ANIMATION); }

	public Track(String id, String name, TrackType type) {
		this.id = id != null ? id : "";
		this.name = name != null ? name : "";
		this.type = type != null ? type : TrackType.ANIMATION;
		if (type == TrackType.AUDIO) this.audioData = new AudioTrackData();
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id != null ? id : ""; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name != null ? name : ""; }
	public TrackType getType() { return type; }
	public void setType(TrackType type) { this.type = type != null ? type : TrackType.ANIMATION; }
	public List<Clip> getClips() { return Collections.unmodifiableList(clips); }
	public void addClip(Clip clip) { if (clip != null) clips.add(clip); }
	public boolean removeClip(String clipId) { return clips.removeIf(c -> clipId != null && clipId.equals(c.getId())); }
	public Clip getClip(String clipId) {
		for (Clip c : clips) if (clipId != null && clipId.equals(c.getId())) return c;
		return null;
	}
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public AudioTrackData getAudioData() { return audioData; }
	public void setAudioData(AudioTrackData audioData) { this.audioData = audioData; }
}
