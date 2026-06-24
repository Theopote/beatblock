package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 将 FREE_HIDDEN 图层绑定到 BUILD 反向轨道：创建 Clip + BUILD 事件，图层进入 BOUND_TO_TRACK。
 */
public final class BindLayerToTrackCommand implements com.beatblock.timeline.command.Command {

	public static final double DEFAULT_CLIP_DURATION_SECONDS = 2.0;

	private final Timeline timeline;
	private final BuildLayerManager layerManager;
	private final String layerId;
	private final double clipStartSeconds;
	private final double clipDurationSeconds;

	private String createdClipId;
	private String createdEventId;
	private String previousBoundClipId;
	private LayerVisibilityState previousState;

	public BindLayerToTrackCommand(
		Timeline timeline,
		BuildLayerManager layerManager,
		com.beatblock.timeline.command.CommandManager commandManager,
		String layerId,
		double clipStartSeconds,
		double clipDurationSeconds
	) {
		this.timeline = timeline;
		this.layerManager = layerManager;
		this.layerId = layerId;
		this.clipStartSeconds = Math.max(0, clipStartSeconds);
		this.clipDurationSeconds = clipDurationSeconds > 0 ? clipDurationSeconds : DEFAULT_CLIP_DURATION_SECONDS;
	}

	@Override
	public void execute() {
		BuildLayer layer = layerManager != null ? layerManager.get(layerId) : null;
		if (layer == null || !layer.canBindToTrack() || timeline == null) return;

		previousState = layer.getState();
		previousBoundClipId = layer.getBoundClipId();

		createdClipId = "clip_layer_" + UUID.randomUUID().toString().substring(0, 8);
		createdEventId = "evt_layer_" + UUID.randomUUID().toString().substring(0, 8);
		double clipEnd = clipStartSeconds + clipDurationSeconds;

		var track = timeline.getTrack(Timeline.TRACK_ID_BUILD_REVERSE);
		if (track == null) {
			track = new com.beatblock.timeline.Track(
				Timeline.TRACK_ID_BUILD_REVERSE, "建造还原", com.beatblock.timeline.TrackType.ANIMATION);
			timeline.addTrack(track);
		}

		Clip clip = new Clip(createdClipId, clipStartSeconds, clipEnd);
		track.addClip(clip);

		Map<String, Object> params = new HashMap<>();
		params.put("actionMode", TimelineAnimationActionMode.BUILD.name());
		params.put("layerId", layer.getId());
		params.put("buildMode", "WALL");
		params.put("buildDissolve", "false");
		params.put("layerBound", "true");
		params.put("targetObject", layer.getStageObjectId());
		params.put("durationSeconds", clipDurationSeconds);
		params.put("energy", 1.0f);

		TimelineEvent event = new TimelineEvent(
			createdEventId,
			clipStartSeconds,
			EventType.ANIMATION,
			params
		);
		clip.addEvent(event);

		layerManager.bindToClip(layer, createdClipId);
		timeline.markAnimationEventsDirty(Timeline.TRACK_ID_BUILD_REVERSE);
	}

	@Override
	public void undo() {
		BuildLayer layer = layerManager != null ? layerManager.get(layerId) : null;
		if (timeline == null || createdClipId == null) return;

		var track = timeline.getTrack(Timeline.TRACK_ID_BUILD_REVERSE);
		if (track != null) {
			Clip clip = track.getClip(createdClipId);
			if (clip != null && createdEventId != null) {
				clip.removeEvent(createdEventId);
			}
			track.removeClip(createdClipId);
		}

		if (layer != null) {
			layerManager.restoreBinding(layer, previousBoundClipId, previousState);
		}

		timeline.markAnimationEventsDirty(Timeline.TRACK_ID_BUILD_REVERSE);
		createdClipId = null;
		createdEventId = null;
	}

	public String getCreatedClipId() {
		return createdClipId;
	}
}
