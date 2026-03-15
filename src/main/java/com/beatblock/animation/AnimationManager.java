package com.beatblock.animation;

import com.beatblock.beat.BeatEvent;
import com.beatblock.beat.BeatScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 订阅 BeatScheduler，根据 BeatEvent 创建 AnimationInstance；每帧更新并暴露活跃实例供 TransformUpdater 使用。
 */
public class AnimationManager {

	private final List<AnimationInstance> activeInstances = new ArrayList<>();
	private BiConsumer<BeatEvent, AnimationManager> eventHandler; // 由外部设置：根据 event 创建并添加 instance
	private double currentTimeSeconds;

	public AnimationManager() {
		// 订阅由外部在 BeatBlock/Client 里连接
	}

	public void setEventHandler(BiConsumer<BeatEvent, AnimationManager> eventHandler) {
		this.eventHandler = eventHandler;
	}

	public void setBeatScheduler(BeatScheduler scheduler) {
		if (scheduler != null) {
			scheduler.addListener(this::onBeatEvent);
		}
	}

	private void onBeatEvent(BeatEvent event) {
		if (eventHandler != null) {
			eventHandler.accept(event, this);
		}
	}

	public void addInstance(AnimationInstance instance) {
		if (instance != null) {
			activeInstances.add(instance);
		}
	}

	public void tick(double currentTimeSeconds) {
		this.currentTimeSeconds = currentTimeSeconds;
		activeInstances.removeIf(inst -> !inst.isActiveAt(currentTimeSeconds));
	}

	public List<AnimationInstance> getActiveInstances() {
		return new ArrayList<>(activeInstances);
	}

	public double getCurrentTimeSeconds() {
		return currentTimeSeconds;
	}

	public void clear() {
		activeInstances.clear();
	}
}
