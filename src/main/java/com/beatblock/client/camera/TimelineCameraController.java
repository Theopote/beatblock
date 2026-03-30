package com.beatblock.client.camera;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.InteractionMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线摄像机控制器
 * <p>
 * 职责：
 * - 根据播放 / 拖动 / 预览状态采样 CameraSample，写入 CameraRuntime
 * - 在需要时接管/释放相机控制权（CameraRuntime.Owner）
 */
public final class TimelineCameraController {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCameraController.class);
	private static final TimelineCameraController INSTANCE = new TimelineCameraController();

	private boolean previewingKeyframe = false;
	private TimelineCameraEvaluator.CameraSample keyframeSample = null;
	private int keyframePreviewFrames = 0;

	private long lastUpdateNanoTime = 0L;

	private TimelineCameraController() {}

	public static TimelineCameraController getInstance() {
		return INSTANCE;
	}

	public void previewKeyframeDirect(TimelineCameraEvaluator.CameraSample sample) {
		if (sample == null) return;
		this.keyframeSample = sample;
		this.keyframePreviewFrames = 5;
	}

	public void stopKeyframePreview() {
		this.keyframePreviewFrames = 0;
		this.keyframeSample = null;
	}

	public void onTimelineUiClosed() {
		stopKeyframePreview();
		CameraRuntime.getInstance().reset();
		LOGGER.debug("[TimelineCameraController] UI 关闭，释放摄像机控制权");
	}

	public synchronized void tick() {
		long now = System.nanoTime();
		float delta = lastUpdateNanoTime == 0L ? 1f / 60f : (now - lastUpdateNanoTime) / 1_000_000_000f;
		lastUpdateNanoTime = now;
		delta = Math.min(delta, 0.5f);

		updateOwnerAndSample(delta);
	}

	private void updateOwnerAndSample(float deltaSeconds) {
		boolean playing = false;
		if (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.isPlaying()) playing = true;
		if (BeatBlock.timelineEditor != null && BeatBlock.timelineEditor.getClock().isPlaying()) playing = true;

		boolean scrubbing = false;
		if (BeatBlock.timelineEditor != null && BeatBlock.timelineEditor.getInteractionState() != null) {
			scrubbing = BeatBlock.timelineEditor.getInteractionState().getMode() == InteractionMode.SCRUB_TIME;
		}

		if (keyframePreviewFrames > 0 && !playing && !scrubbing) {
			previewingKeyframe = true;
			keyframePreviewFrames--;
		} else {
			previewingKeyframe = false;
			keyframeSample = null;
			keyframePreviewFrames = 0;
		}

		CameraRuntime runtime = CameraRuntime.getInstance();
		
		boolean hasCameraTrackClips = false;
		if (BeatBlock.timeline != null) {
			var track = BeatBlock.timeline.getTrack(Timeline.TRACK_ID_CAMERA);
			if (track != null && !track.getClips().isEmpty()) {
				hasCameraTrackClips = true;
			}
		}

		boolean wantsTimeline = previewingKeyframe || ((playing || scrubbing) && hasCameraTrackClips);

		if (wantsTimeline && !runtime.isTimelineOwner()) {
			runtime.setOwner(CameraRuntime.Owner.TIMELINE);
			LOGGER.debug("[CameraController] 接管相机控制");
		} else if (!wantsTimeline && runtime.isTimelineOwner()) {
			TimelineCameraEvaluator.CameraSample lastSample = runtime.getCurrentSample();
			runtime.applyLerpToPlayer(lastSample, 0.1f);
			runtime.setOwner(CameraRuntime.Owner.PLAYER);
			LOGGER.debug("[CameraController] 恢复玩家控制");
		}

		if (runtime.isTimelineOwner() || wantsTimeline) {
			if (playing || scrubbing) {
				double timeSeconds = BeatBlockClientDriver.previewTimelineTimeSeconds();
				MinecraftClient client = MinecraftClient.getInstance();
				Vec3d anchor = client.player != null ? client.player.getEyePos() : Vec3d.ZERO;
				float fallbackYaw = client.player != null ? client.player.getYaw() : 0f;
				float fallbackPitch = client.player != null ? client.player.getPitch() : 0f;

				TimelineCameraEvaluator.CameraSample sample = TimelineCameraEvaluator.evaluate(
					BeatBlock.timeline, timeSeconds, anchor, fallbackYaw, fallbackPitch);
				
				if (sample != null) {
					runtime.applyTimelineSample(sample);
				}
			} else if (previewingKeyframe && keyframeSample != null) {
				runtime.applyTimelineSample(keyframeSample);
			}
		} else {
			runtime.tickPlayerLerp(deltaSeconds);
		}
	}
}
