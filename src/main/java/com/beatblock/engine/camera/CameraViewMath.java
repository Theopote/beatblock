package com.beatblock.engine.camera;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 视线范围与镜头距离调制：场景二「视线内动态半径波浪」及 STEP 镜头自适应的纯函数工具。
 */
public final class CameraViewMath {

	public static final double DEFAULT_NEAR_DISTANCE = 8.0;
	public static final double DEFAULT_FAR_DISTANCE = 48.0;
	public static final double DEFAULT_NEAR_SCALE = 0.6;
	public static final double DEFAULT_FAR_SCALE = 1.5;
	public static final double DEFAULT_VIEW_HALF_FOV_DEG = 55.0;

	private CameraViewMath() {}

	public static Vec3d forwardFromRotation(float yawDegrees, float pitchDegrees) {
		double yawRad = Math.toRadians(yawDegrees);
		double pitchRad = Math.toRadians(pitchDegrees);
		double x = -Math.sin(yawRad) * Math.cos(pitchRad);
		double y = -Math.sin(pitchRad);
		double z = Math.cos(yawRad) * Math.cos(pitchRad);
		return new Vec3d(x, y, z);
	}

	public static Vec3d blockCenter(BlockPos pos) {
		return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	public static double horizontalDistance(Vec3d cameraPos, BlockPos block) {
		double dx = (block.getX() + 0.5) - cameraPos.x;
		double dz = (block.getZ() + 0.5) - cameraPos.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	public static double distance3d(Vec3d cameraPos, BlockPos block) {
		return blockCenter(block).distanceTo(cameraPos);
	}

	public static boolean isInView(
		Vec3d cameraPos,
		Vec3d cameraForward,
		BlockPos block,
		double maxDistance,
		double halfFovDegrees
	) {
		if (cameraPos == null || block == null) {
			return true;
		}
		Vec3d toBlock = blockCenter(block).subtract(cameraPos);
		double dist = toBlock.length();
		if (dist > maxDistance) {
			return false;
		}
		if (cameraForward == null || dist < 1e-6) {
			return true;
		}
		double dot = toBlock.normalize().dotProduct(cameraForward.normalize());
		return dot >= Math.cos(Math.toRadians(Math.max(1.0, halfFovDegrees)));
	}

	/** 镜头距离 → STEP 时间倍率：近处 &lt; 1 更快，远处 &gt; 1 更慢。 */
	public static double adaptiveTimeScale(
		double distance,
		double nearDistance,
		double farDistance,
		double nearScale,
		double farScale
	) {
		if (distance <= nearDistance) {
			return nearScale;
		}
		if (distance >= farDistance) {
			return farScale;
		}
		double t = (distance - nearDistance) / Math.max(1e-6, farDistance - nearDistance);
		return nearScale + (farScale - nearScale) * t;
	}

	/**
	 * 动画进度 0→1 时，波浪前沿半径从近端扩至视线远端（受 near/far scale 调制）。
	 */
	public static double dynamicWaveRadius(
		float animationProgress,
		double nearDistance,
		double farDistance,
		double nearScale,
		double farScale,
		double viewDistanceCap
	) {
		double minRadius = Math.max(0.5, nearDistance * nearScale);
		double maxRadius = Math.min(viewDistanceCap, farDistance * farScale);
		if (maxRadius < minRadius) {
			maxRadius = minRadius;
		}
		float t = Math.max(0f, Math.min(1f, animationProgress));
		return minRadius + (maxRadius - minRadius) * t;
	}

	/**
	 * 视线内、半径前沿内的径向波浪 Y 偏移；范围外返回 0。
	 */
	public static double radialWaveOffsetY(
		BlockPos block,
		Vec3d cameraPos,
		Vec3d cameraForward,
		float animationProgress,
		float energy,
		double amplitude,
		double frequency,
		double phase,
		double nearDistance,
		double farDistance,
		double nearScale,
		double farScale,
		double viewDistanceCap,
		double halfFovDegrees
	) {
		if (cameraPos == null || block == null || amplitude == 0.0) {
			return 0.0;
		}
		if (!isInView(cameraPos, cameraForward, block, viewDistanceCap, halfFovDegrees)) {
			return 0.0;
		}
		double waveRadius = dynamicWaveRadius(
			animationProgress, nearDistance, farDistance, nearScale, farScale, viewDistanceCap);
		double radialDist = horizontalDistance(cameraPos, block);
		if (radialDist > waveRadius) {
			return 0.0;
		}
		double envelope = 1.0 - (radialDist / Math.max(waveRadius, 1e-6)) * 0.35;
		return Math.sin(radialDist * frequency + animationProgress * 6.0 + phase)
			* amplitude
			* Math.max(0f, energy)
			* envelope;
	}
}
