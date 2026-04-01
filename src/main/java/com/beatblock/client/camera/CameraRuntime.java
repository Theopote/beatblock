package com.beatblock.client.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一相机运行时（单一相机状态 + 控制权）
 * <p>
 * 核心设计：
 * - 只有一份 currentSample，表示“当前摄像机状态”
 * - 只有一个 owner 表示当前谁在控制摄像机：PLAYER 或 TIMELINE
 * - Mixin 每帧只与这一份状态交互：
 *    - 当 owner=PLAYER 时，从原版 Camera 写入 currentSample
 *    - 当 owner=TIMELINE 时，从 currentSample 写回原版 Camera
 */
public final class CameraRuntime {

	private static final Logger LOGGER = LoggerFactory.getLogger(CameraRuntime.class);
	private static final CameraRuntime INSTANCE = new CameraRuntime();
	private static final float EPSILON = 1e-4f;

	public enum Owner {
		PLAYER,
		TIMELINE
	}

	private volatile Owner owner = Owner.PLAYER;
	private volatile TimelineCameraEvaluator.CameraSample currentSample = new TimelineCameraEvaluator.CameraSample(Vec3d.ZERO, 0, 0);

	private record LerpState(TimelineCameraEvaluator.CameraSample start, TimelineCameraEvaluator.CameraSample target, float elapsed, float duration, boolean active) {
		static final LerpState INACTIVE = new LerpState(null, null, 0f, 0f, false);

		LerpState advance(float deltaSeconds) {
			float newElapsed = elapsed + Math.max(0f, deltaSeconds);
			return new LerpState(start, target, newElapsed, duration, active);
		}
	}

	private volatile LerpState lerpState = LerpState.INACTIVE;

	private CameraRuntime() {}

	public static CameraRuntime getInstance() {
		return INSTANCE;
	}

	public boolean isPlayerOwner() {
		return owner == Owner.PLAYER;
	}

	public boolean isTimelineOwner() {
		return owner == Owner.TIMELINE;
	}

	public TimelineCameraEvaluator.CameraSample getCurrentSample() {
		return currentSample;
	}

	public void updateFromGameCamera(Vec3d pos, float yaw, float pitch) {
		if (owner != Owner.PLAYER) return;
		this.currentSample = new TimelineCameraEvaluator.CameraSample(pos, yaw, pitch);
	}

	public void applyTimelineSample(TimelineCameraEvaluator.CameraSample sample) {
		if (sample == null) return;
		this.currentSample = sample;
	}

	public void applyLerpToPlayer(TimelineCameraEvaluator.CameraSample target, float durationSeconds) {
		if (target == null || durationSeconds <= EPSILON) {
			lerpState = LerpState.INACTIVE;
			return;
		}
		TimelineCameraEvaluator.CameraSample start = capturePlayerSample();
		lerpState = new LerpState(start, target, 0f, durationSeconds, true);
	}

	public void cancelPlayerLerp() {
		lerpState = LerpState.INACTIVE;
	}

	public void syncPlayerToSample(TimelineCameraEvaluator.CameraSample sample) {
		if (sample == null) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null) return;
		Vec3d bodyPos = eyeToBodyPosition(mc, sample.position());
		mc.player.setPos(bodyPos.x, bodyPos.y, bodyPos.z);
		mc.player.setYaw(sample.yawDeg());
		mc.player.setPitch(sample.pitchDeg());
		this.currentSample = sample;
	}

	public void tickPlayerLerp(float deltaSeconds) {
		LerpState state = this.lerpState;
		if (!state.active()) return;
		state = state.advance(deltaSeconds);
		float t = state.duration() <= EPSILON ? 1f : Math.min(1f, state.elapsed() / state.duration());
		TimelineCameraEvaluator.CameraSample blended = lerpSamples(state.start(), state.target(), t);

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			Vec3d bodyPos = eyeToBodyPosition(mc, blended.position());
			mc.player.setPos(bodyPos.x, bodyPos.y, bodyPos.z);
			mc.player.setYaw(blended.yawDeg());
			mc.player.setPitch(blended.pitchDeg());
		}
		this.currentSample = blended;

		if (t >= 1f - EPSILON) {
			this.lerpState = LerpState.INACTIVE;
		} else {
			this.lerpState = state;
		}
	}

	public void setOwner(Owner newOwner) {
		if (this.owner == newOwner) return;
		this.owner = newOwner;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;

		if (newOwner == Owner.TIMELINE) {
			lockPlayerInput(mc);
			LOGGER.debug("[CameraRuntime] Owner -> TIMELINE");
		} else {
			unlockPlayerInput(mc);
			LOGGER.debug("[CameraRuntime] Owner -> PLAYER");
		}
	}

	public void reset() {
		setOwner(Owner.PLAYER);
	}

	private void lockPlayerInput(MinecraftClient client) {
		if (client.player != null && client.player.input != null) {
			client.player.input.playerInput = new net.minecraft.util.PlayerInput(false, false, false, false, false, false, false);
		}
		if (client.options != null) {
			clearKey(client.options.forwardKey);
			clearKey(client.options.backKey);
			clearKey(client.options.leftKey);
			clearKey(client.options.rightKey);
			clearKey(client.options.jumpKey);
			clearKey(client.options.sneakKey);
			clearKey(client.options.sprintKey);
		}
	}

	private void unlockPlayerInput(MinecraftClient client) {}

	private void clearKey(net.minecraft.client.option.KeyBinding key) {
		if (key != null) key.setPressed(false);
	}

	private TimelineCameraEvaluator.CameraSample capturePlayerSample() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null) {
			return currentSample;
		}
		return new TimelineCameraEvaluator.CameraSample(mc.player.getEyePos(), mc.player.getYaw(), mc.player.getPitch());
	}

	private Vec3d eyeToBodyPosition(MinecraftClient mc, Vec3d eyePos) {
		if (mc == null || mc.player == null || eyePos == null) {
			return eyePos != null ? eyePos : Vec3d.ZERO;
		}
		double eyeOffset = mc.player.getEyeY() - mc.player.getY();
		return eyePos.subtract(0.0, eyeOffset, 0.0);
	}

	private TimelineCameraEvaluator.CameraSample lerpSamples(TimelineCameraEvaluator.CameraSample a, TimelineCameraEvaluator.CameraSample b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		Vec3d pos = new Vec3d(
			lerp(a.position().x, b.position().x, t),
			lerp(a.position().y, b.position().y, t),
			lerp(a.position().z, b.position().z, t)
		);
		float yaw = lerpAngle(a.yawDeg(), b.yawDeg(), t);
		float pitch = lerpAngle(a.pitchDeg(), b.pitchDeg(), t);
		return new TimelineCameraEvaluator.CameraSample(pos, yaw, pitch);
	}

	private float lerp(double a, double b, float t) {
		return (float) (a + (b - a) * t);
	}

	private float lerpAngle(float a, float b, float t) {
		float delta = wrapDegrees(b - a);
		return a + delta * t;
	}

	private float wrapDegrees(float degrees) {
		degrees = degrees % 360f;
		if (degrees >= 180f) degrees -= 360f;
		if (degrees < -180f) degrees += 360f;
		return degrees;
	}
}
