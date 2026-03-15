package com.beatblock.automap.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart Auto-Map 弹窗配置：风格、复杂度、镜头/粒子开关、可选目标对象 ID 列表。
 */
public final class AutoMapSettings {

	private AutoMapStyle style;
	private Complexity complexity;
	private boolean cameraEnabled;
	private boolean particlesEnabled;
	private List<String> targetObjectIds;

	public AutoMapSettings() {
		this.style = AutoMapStyle.EDM;
		this.complexity = Complexity.MEDIUM;
		this.cameraEnabled = true;
		this.particlesEnabled = true;
		this.targetObjectIds = new ArrayList<>();
	}

	public AutoMapStyle getStyle() { return style; }
	public void setStyle(AutoMapStyle style) { this.style = style != null ? style : AutoMapStyle.EDM; }

	public Complexity getComplexity() { return complexity; }
	public void setComplexity(Complexity complexity) { this.complexity = complexity != null ? complexity : Complexity.MEDIUM; }

	public boolean isCameraEnabled() { return cameraEnabled; }
	public void setCameraEnabled(boolean cameraEnabled) { this.cameraEnabled = cameraEnabled; }

	public boolean isParticlesEnabled() { return particlesEnabled; }
	public void setParticlesEnabled(boolean particlesEnabled) { this.particlesEnabled = particlesEnabled; }

	public List<String> getTargetObjectIds() { return new ArrayList<>(targetObjectIds); }
	public void setTargetObjectIds(List<String> ids) { this.targetObjectIds = ids != null ? new ArrayList<>(ids) : new ArrayList<>(); }
}
