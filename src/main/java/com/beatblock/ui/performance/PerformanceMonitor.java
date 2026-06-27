package com.beatblock.ui.performance;

import com.beatblock.BeatBlock;
import com.beatblock.engine.AnimationPlayer;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;

import org.jspecify.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Deque;

/** 运行时性能指标采集（FPS、内存、时间线规模等）。 */
public final class PerformanceMonitor {

	private static final int FPS_SAMPLE_COUNT = 90;
	private static final Deque<Long> frameTimestampsNs = new ArrayDeque<>();

	private PerformanceMonitor() {
	}

	public static void markFrame() {
		long now = System.nanoTime();
		frameTimestampsNs.addLast(now);
		while (frameTimestampsNs.size() > FPS_SAMPLE_COUNT) {
			frameTimestampsNs.removeFirst();
		}
	}

	public static double getFps() {
		if (frameTimestampsNs.size() < 2) {
			return 0.0;
		}
		long oldest = frameTimestampsNs.peekFirst();
		long newest = frameTimestampsNs.peekLast();
		double seconds = (newest - oldest) / 1_000_000_000.0;
		if (seconds <= 0.0) {
			return 0.0;
		}
		return (frameTimestampsNs.size() - 1) / seconds;
	}

	public static long getHeapUsedMb() {
		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		return used / (1024 * 1024);
	}

	public static long getHeapMaxMb() {
		return Runtime.getRuntime().maxMemory() / (1024 * 1024);
	}

	public static long getProcessUptimeSeconds() {
		return ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
	}

	public static Snapshot snapshot(@Nullable BeatBlockContext context) {
		if (context == null) {
			context = BeatBlock.getContext();
		}
		Timeline timeline = context != null ? context.timeline() : null;
		BlockAnimationEngine engine = context != null ? context.blockAnimationEngine() : null;
		AnimationPlayer player = engine != null ? engine.getAnimationPlayer() : null;
		StageObjectSystem stageObjects = engine != null ? engine.getStageObjectSystem() : null;

		int animationEvents = timeline != null ? timeline.getBlockAnimationEvents().size() : 0;
		int autoEvents = timeline != null ? timeline.getAutoAnimationEvents().size() : 0;
		int activeInstances = player != null ? player.getActiveInstances().size() : 0;
		int animatedBlocks = player != null ? player.getCurrentFrameBlocks().size() : 0;
		int stageObjectCount = stageObjects != null ? stageObjects.getAll().size() : 0;
		double duration = timeline != null ? timeline.getDurationSeconds() : 0.0;

		return new Snapshot(
			getFps(),
			getHeapUsedMb(),
			getHeapMaxMb(),
			getProcessUptimeSeconds(),
			animationEvents,
			autoEvents,
			activeInstances,
			animatedBlocks,
			stageObjectCount,
			duration
		);
	}

	public record Snapshot(
		double fps,
		long heapUsedMb,
		long heapMaxMb,
		long uptimeSeconds,
		int manualAnimationEvents,
		int autoAnimationEvents,
		int activeAnimationInstances,
		int animatedBlocksThisFrame,
		int stageObjectCount,
		double timelineDurationSeconds
	) {}
}
