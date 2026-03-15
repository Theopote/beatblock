package com.beatblock.animation;

/**
 * 动画实例：绑定模板与开始时间，可查询当前进度与变换状态。
 */
public class AnimationInstance {

	public interface DisplayTarget {
		int getDisplayId();
	}

	private final AnimationTemplate template;
	private final double startTimeSeconds;
	private final Object displayTarget; // DisplayTarget 或实体 ID，由 Visual 层解析
	private final double offsetX, offsetY, offsetZ;
	private final double scaleFrom, scaleTo;

	public AnimationInstance(AnimationTemplate template, double startTimeSeconds, Object displayTarget,
	                         double offsetX, double offsetY, double offsetZ, double scaleFrom, double scaleTo) {
		this.template = template;
		this.startTimeSeconds = startTimeSeconds;
		this.displayTarget = displayTarget;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		this.scaleFrom = scaleFrom;
		this.scaleTo = scaleTo;
	}

	public AnimationTemplate getTemplate() {
		return template;
	}

	public double getStartTimeSeconds() {
		return startTimeSeconds;
	}

	public Object getDisplayTarget() {
		return displayTarget;
	}

	public boolean isActiveAt(double currentTimeSeconds) {
		double end = startTimeSeconds + template.getDurationSeconds();
		return currentTimeSeconds >= startTimeSeconds && currentTimeSeconds <= end;
	}

	public double getProgress(double currentTimeSeconds) {
		if (!isActiveAt(currentTimeSeconds)) {
			return currentTimeSeconds < startTimeSeconds ? 0 : 1;
		}
		double elapsed = currentTimeSeconds - startTimeSeconds;
		double t = elapsed / template.getDurationSeconds();
		return AnimationTemplate.applyEasing(t, template.getEasing());
	}

	/**
	 * 根据当前时间计算变换状态（相对于起始位置）。
	 */
	public TransformState sample(double currentTimeSeconds) {
		double p = getProgress(currentTimeSeconds);
		double scale = scaleFrom + (scaleTo - scaleFrom) * p;
		double x = offsetX * p;
		double y = offsetY * p;
		double z = offsetZ * p;
		double yaw = (float) (Math.PI * 0.25 * p);
		return new TransformState(x, y, z, yaw, 0, 0, scale, scale, scale);
	}
}
