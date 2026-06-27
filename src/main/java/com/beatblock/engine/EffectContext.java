package com.beatblock.engine;

import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.Map;

/**
 * 应用动画效果时的上下文，如舞台对象中心（用于爆炸、螺旋、环绕等）。
 * extraParams 携带绑定规则的额外参数（如 waveAmplitude、impactRadius 等），供各 Effect 读取覆盖默认值。
 * 可选 runtime 镜头位置/朝向，供 WaveMotion 视线内动态半径波浪使用。
 */
public final class EffectContext {

	private final Vec3d stageCenter;
	private final Map<String, Object> extraParams;
	private final Vec3d cameraPosition;
	private final Vec3d cameraForward;

	public EffectContext(Vec3d stageCenter) {
		this(stageCenter, Collections.emptyMap());
	}

	public EffectContext(Vec3d stageCenter, Map<String, Object> extraParams) {
		this(stageCenter, extraParams, null, null);
	}

	public EffectContext(
		Vec3d stageCenter,
		Map<String, Object> extraParams,
		Vec3d cameraPosition,
		Vec3d cameraForward
	) {
		this.stageCenter = stageCenter != null ? stageCenter : Vec3d.ZERO;
		this.extraParams = extraParams != null ? extraParams : Collections.emptyMap();
		this.cameraPosition = cameraPosition;
		this.cameraForward = cameraForward;
	}

	public Vec3d getStageCenter() {
		return stageCenter;
	}

	public Map<String, Object> getExtraParams() {
		return extraParams;
	}

	public Vec3d getCameraPosition() {
		return cameraPosition;
	}

	public Vec3d getCameraForward() {
		return cameraForward;
	}

	/** 从 extraParams 读 double 值，若不存在或格式异常返回 fallback */
	public double paramDouble(String key, double fallback) {
		Object raw = extraParams.get(key);
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}
}
