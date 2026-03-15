package com.beatblock.engine;

/**
 * Timeline 创建的动画实例：绑定模板、目标舞台对象、时间范围与能量。
 * Timeline Event → EngineAnimationInstance → AnimationPlayer 执行。
 */
public final class EngineAnimationInstance {

	private final AnimationDefinition definition;
	private final StageObject target;
	private final double startTimeSeconds;
	private final double endTimeSeconds;
	private final float energy;

	public EngineAnimationInstance(AnimationDefinition definition, StageObject target,
	                               double startTimeSeconds, double endTimeSeconds, float energy) {
		this.definition = definition;
		this.target = target;
		this.startTimeSeconds = startTimeSeconds;
		this.endTimeSeconds = Math.max(startTimeSeconds, endTimeSeconds);
		this.energy = Math.max(0f, Math.min(1f, energy));
	}

	public AnimationDefinition getDefinition() {
		return definition;
	}

	public StageObject getTarget() {
		return target;
	}

	public double getStartTimeSeconds() {
		return startTimeSeconds;
	}

	public double getEndTimeSeconds() {
		return endTimeSeconds;
	}

	public float getEnergy() {
		return energy;
	}

	public boolean isActiveAt(double timelineTimeSeconds) {
		return timelineTimeSeconds >= startTimeSeconds && timelineTimeSeconds <= endTimeSeconds;
	}

	/** 当前进度 0～1 */
	public float getProgress(double timelineTimeSeconds) {
		if (!isActiveAt(timelineTimeSeconds)) {
			return timelineTimeSeconds < startTimeSeconds ? 0f : 1f;
		}
		double elapsed = timelineTimeSeconds - startTimeSeconds;
		double dur = endTimeSeconds - startTimeSeconds;
		return dur <= 0 ? 1f : (float) (elapsed / dur);
	}
}
