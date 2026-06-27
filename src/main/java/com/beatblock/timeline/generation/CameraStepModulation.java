package com.beatblock.timeline.generation;

import com.beatblock.engine.camera.CameraViewMath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * STEP 派发：镜头视椎体门控（出屏块延后）与距离自适应推进（近快远慢）。
 */
public final class CameraStepModulation {

	private CameraStepModulation() {}

	public static List<BlockPos> reorderForFrustumGating(
		List<BlockPos> orderedBlocks,
		Vec3d cameraPos,
		Vec3d cameraForward,
		Map<String, Object> params
	) {
		if (!readBoolean(params != null ? params.get("cameraFrustumGating") : null, false)
			|| orderedBlocks == null
			|| orderedBlocks.isEmpty()
			|| cameraPos == null) {
			return orderedBlocks;
		}
		double maxDistance = readDouble(params.get("cameraFarDistance"), CameraViewMath.DEFAULT_FAR_DISTANCE);
		double halfFov = readDouble(params.get("waveViewHalfFovDeg"), CameraViewMath.DEFAULT_VIEW_HALF_FOV_DEG);

		List<BlockPos> visible = new ArrayList<>();
		List<BlockPos> hidden = new ArrayList<>();
		for (BlockPos block : orderedBlocks) {
			if (CameraViewMath.isInView(cameraPos, cameraForward, block, maxDistance, halfFov)) {
				visible.add(block);
			} else {
				hidden.add(block);
			}
		}
		if (hidden.isEmpty()) {
			return orderedBlocks;
		}
		List<BlockPos> merged = new ArrayList<>(orderedBlocks.size());
		merged.addAll(visible);
		merged.addAll(hidden);
		return merged;
	}

	public static List<StepSequencePlanner.PlannedStep> applyAdaptiveTiming(
		List<StepSequencePlanner.PlannedStep> planned,
		List<BlockPos> orderedBlocks,
		Vec3d cameraPos,
		Map<String, Object> params
	) {
		if (!readBoolean(params != null ? params.get("cameraAdaptiveStep") : null, false)
			|| planned == null
			|| planned.isEmpty()
			|| cameraPos == null) {
			return planned;
		}
		double nearDistance = readDouble(params.get("cameraNearDistance"), CameraViewMath.DEFAULT_NEAR_DISTANCE);
		double farDistance = readDouble(params.get("cameraFarDistance"), CameraViewMath.DEFAULT_FAR_DISTANCE);
		double nearScale = readDouble(params.get("cameraNearScale"), CameraViewMath.DEFAULT_NEAR_SCALE);
		double farScale = readDouble(params.get("cameraFarScale"), CameraViewMath.DEFAULT_FAR_SCALE);

		double anchor = planned.getFirst().startTimeSeconds();
		List<StepSequencePlanner.PlannedStep> adjusted = new ArrayList<>(planned.size());
		for (int i = 0; i < planned.size(); i++) {
			StepSequencePlanner.PlannedStep step = planned.get(i);
			BlockPos block = i < orderedBlocks.size() ? orderedBlocks.get(i) : step.block();
			double dist = CameraViewMath.distance3d(cameraPos, block);
			double scale = CameraViewMath.adaptiveTimeScale(dist, nearDistance, farDistance, nearScale, farScale);
			double offset = step.startTimeSeconds() - anchor;
			adjusted.add(new StepSequencePlanner.PlannedStep(
				step.block(),
				anchor + offset * scale
			));
		}
		return adjusted;
	}

	private static double readDouble(Object raw, double fallback) {
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}

	private static boolean readBoolean(Object raw, boolean fallback) {
		if (raw instanceof Boolean b) return b;
		if (raw instanceof Number n) return n.intValue() != 0;
		if (raw == null) return fallback;
		String s = String.valueOf(raw).trim();
		if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
		if ("false".equalsIgnoreCase(s) || "0".equals(s)) return false;
		return fallback;
	}
}
