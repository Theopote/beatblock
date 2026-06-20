package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第 3 层 — 客户端播放编排：按 Timeline 时钟推进音频预览与舞台/相机回放。
 * <p>
 * 只派发 {@link TimelineAnimationEvent} 给 {@link com.beatblock.engine.BlockAnimationEngine}；
 * 相机由 {@link com.beatblock.client.camera.TimelineCameraController} 独立处理。
 */
public final class BeatBlockClientDriver {
	public record TimelineActionExecutionReport(
		long timestampMs,
		String eventId,
		String targetObjectId,
		TimelineAnimationActionMode actionMode,
		int mutationCount,
		String status,
		String detail
	) {}

	private static long lastTickNanos;
	private static boolean driving;
	private static final Set<String> scheduledTimelineAnimationIds = new HashSet<>();
	private static final Set<String> scheduledAutoAnimationIds = new HashSet<>();
	private static final double TIMELINE_EVENT_EPSILON = 1e-4;
	private static double lastTimelineAnimationTime;
	private static double lastAutoAnimationTime;
	private static double lastStepBeatTickTime;
	private static final Map<BlockPos, BlockState> timelineMutationSnapshot = new HashMap<>();
	private static RegistryKey<World> timelineMutationWorldKey;
	private static volatile TimelineActionExecutionReport lastTimelineActionExecutionReport;
	private static final Map<String, TimelineActionExecutionReport> timelineActionReportByEventId = new ConcurrentHashMap<>();
	private static final int MAX_ACTION_REPORT_CACHE_SIZE = 4096;

	public static void onClientTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (BeatBlock.blockAnimationEngine != null && mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
			BeatBlock.blockAnimationEngine.setRuntimeCameraPosition(mc.gameRenderer.getCamera().getCameraPos());
		}
		com.beatblock.client.camera.TimelineCameraController.getInstance().tick();

		if (driving) {
			if (world == null) return;

			long now = System.nanoTime();
			double delta = lastTickNanos > 0 ? (now - lastTickNanos) / 1e9 : 1.0 / 20.0;
			lastTickNanos = now;

			BeatBlock.musicPlayer.tick(delta);
			syncStemMixerToMusicPlayer();
			double currentTime = BeatBlock.musicPlayer.getCurrentTimeSeconds();
			tickBlockAnimationEngine(currentTime, false, world);
			return;
		}

		if (world != null && BeatBlock.blockAnimationEngine != null && BeatBlock.timeline != null) {
			tickBlockAnimationEngine(previewTimelineTimeSeconds(), true, world);
		}
	}

	private static void tickBlockAnimationEngine(double currentTime, boolean previewOnly, World world) {
		if (BeatBlock.blockAnimationEngine == null) return;
		if (currentTime + TIMELINE_EVENT_EPSILON < lastStepBeatTickTime) {
			lastStepBeatTickTime = currentTime;
		}
		syncTimelineBlockAnimationEvents(currentTime, previewOnly);
		syncTimelineAutoAnimationEvents(currentTime, previewOnly);
		BeatBlock.blockAnimationEngine.tickStepBeats(lastStepBeatTickTime, currentTime, readReferenceBeatTimes());
		BeatBlock.blockAnimationEngine.tick(currentTime);
		lastStepBeatTickTime = currentTime;
		if (!previewOnly && world != null) {
			BeatBlock.blockAnimationEngine.getBuildSequencer().tick(currentTime, world);
		}
	}

	private static double[] readReferenceBeatTimes() {
		if (BeatBlock.timeline == null) {
			return new double[0];
		}
		return ReferenceBeatResolver.resolveBeatTimesSeconds(BeatBlock.timeline);
	}

	private static void syncStemMixerToMusicPlayer() {
		if (BeatBlock.stemMixer == null || !BeatBlock.stemMixer.hasStems()) return;
		boolean musicPlaying = BeatBlock.musicPlayer.isPlaying();
		double musicTime = BeatBlock.musicPlayer.getCurrentTimeSeconds();
		double stemTime = BeatBlock.stemMixer.getCurrentTimeSeconds();

		if (Math.abs(stemTime - musicTime) > 0.05) {
			BeatBlock.stemMixer.setCurrentTimeSeconds(musicTime);
		}

		if (musicPlaying) {
			if (!BeatBlock.stemMixer.isPlaying()) {
				BeatBlock.stemMixer.setCurrentTimeSeconds(musicTime);
				BeatBlock.stemMixer.play();
			}
		} else if (BeatBlock.stemMixer.isPlaying()) {
			BeatBlock.stemMixer.pause();
		}
	}

	public static void startDriving() {
		lastTickNanos = 0;
		resetTimelineAnimationScheduling();
		driving = true;
	}

	public static void stopDriving() {
		driving = false;
		resetTimelineAnimationScheduling();
	}

	public static boolean isDriving() {
		return driving;
	}

	public static void stopPlayback() {
		BeatBlock.musicPlayer.pause();
		if (BeatBlock.stemMixer != null && BeatBlock.stemMixer.hasStems()) {
			BeatBlock.stemMixer.pause();
		}
		resetTimelineAnimationScheduling();
		stopDriving();
		com.beatblock.client.camera.TimelineCameraController.getInstance().onTimelineUiClosed();
	}

	public static double previewTimelineTimeSeconds() {
		if (BeatBlock.timelineEditor != null) {
			return BeatBlock.timelineEditor.getClock().getCurrentTimeSeconds();
		}
		return BeatBlock.musicPlayer != null ? BeatBlock.musicPlayer.getCurrentTimeSeconds() : 0.0;
	}

	private static void syncTimelineBlockAnimationEvents(double currentTime, boolean previewOnly) {
		if (BeatBlock.timeline == null || BeatBlock.blockAnimationEngine == null) return;
		if (currentTime + TIMELINE_EVENT_EPSILON < lastTimelineAnimationTime) {
			resetTimelineAnimationScheduling();
		}
		for (TimelineAnimationEvent event : BeatBlock.timeline.getBlockAnimationEvents()) {
			if (event.getTimeSeconds() > currentTime + TIMELINE_EVENT_EPSILON) {
				break;
			}
			String scheduleKey = scheduleKey(event);
			if (!scheduledTimelineAnimationIds.add(scheduleKey)) continue;
			applyTimelineActionEvent(event, previewOnly);
		}
		lastTimelineAnimationTime = currentTime;
	}

	private static void syncTimelineAutoAnimationEvents(double currentTime, boolean previewOnly) {
		if (BeatBlock.timeline == null || BeatBlock.blockAnimationEngine == null) return;
		if (currentTime + TIMELINE_EVENT_EPSILON < lastAutoAnimationTime) {
			scheduledAutoAnimationIds.clear();
			lastAutoAnimationTime = 0.0;
		}
		for (TimelineAnimationEvent event : BeatBlock.timeline.getAutoAnimationEvents()) {
			if (event.getTimeSeconds() > currentTime + TIMELINE_EVENT_EPSILON) {
				break;
			}
			String key = scheduleKey(event);
			if (!scheduledAutoAnimationIds.add(key)) continue;
			applyTimelineActionEvent(event, previewOnly);
		}
		lastAutoAnimationTime = currentTime;
	}

	private static void applyTimelineActionEvent(TimelineAnimationEvent event, boolean previewOnly) {
		if (event == null || BeatBlock.blockAnimationEngine == null) return;
		if (!passesEnergyThreshold(event)) {
			recordActionReport(event, 0, "SKIPPED", "energy-below-threshold");
			return;
		}
		TimelineAnimationActionMode actionMode = event.getActionMode();
		if (previewOnly && actionMode != TimelineAnimationActionMode.ANIMATE) {
			return;
		}
		if (actionMode == TimelineAnimationActionMode.ANIMATE) {
			BeatBlock.blockAnimationEngine.scheduleTimelineEvent(event);
			recordActionReport(event, 0, "ANIMATE", "scheduled");
			return;
		}

		if (actionMode == TimelineAnimationActionMode.BUILD) {
			var inst = BeatBlock.blockAnimationEngine.getBuildSequencer().schedule(event);
			if (inst != null) {
				recordActionReport(event, inst.getTotalBlocks(), "BUILD", "scheduled-" + inst.getTotalBlocks() + "-blocks");
			} else {
				recordActionReport(event, 0, "SKIPPED", "build-no-target");
			}
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world == null) {
			recordActionReport(event, 0, "SKIPPED", "no-world");
			return;
		}
		var plan = BeatBlock.blockAnimationEngine.planControl(event, world);
		var mutations = plan.mutations();
		if (mutations == null || mutations.isEmpty()) {
			String detail = plan.skipReason() != null
				? "skip-" + plan.skipReason().name().toLowerCase(Locale.ROOT)
				: "skip-no-change";
			recordActionReport(event, 0, "SKIPPED", detail);
			return;
		}
		for (BlockControlExecutor.BlockMutation mutation : mutations) {
			captureTimelineMutationOriginalState(world, mutation.pos(), mutation.fromState());
		}
		BeatBlock.blockAnimationEngine.applyControlMutations(world, mutations);
		recordActionReport(event, mutations.size(), "APPLIED", "ok");
	}

	private static void recordActionReport(TimelineAnimationEvent event, int mutationCount, String status, String detail) {
		if (event == null) return;
		TimelineActionExecutionReport report = new TimelineActionExecutionReport(
			System.currentTimeMillis(),
			event.getEventId(),
			event.getTargetObjectId(),
			event.getActionMode(),
			Math.max(0, mutationCount),
			status != null ? status : "UNKNOWN",
			detail != null ? detail : ""
		);
		lastTimelineActionExecutionReport = report;
		String eventId = event.getEventId();
		if (eventId != null && !eventId.isBlank()) {
			if (timelineActionReportByEventId.size() > MAX_ACTION_REPORT_CACHE_SIZE) {
				timelineActionReportByEventId.clear();
			}
			timelineActionReportByEventId.put(eventId, report);
		}
	}

	private static boolean passesEnergyThreshold(TimelineAnimationEvent event) {
		if (event == null) return false;
		Object raw = event.getParameters().get("energyThreshold");
		double threshold = 0.0;
		if (raw instanceof Number n) {
			threshold = n.doubleValue();
		} else if (raw != null) {
			try {
				threshold = Double.parseDouble(String.valueOf(raw).trim());
			} catch (Exception ignored) {
				threshold = 0.0;
			}
		}
		threshold = Math.max(0.0, Math.min(1.0, threshold));
		return event.getEnergy() + 1e-6 >= threshold;
	}

	private static void captureTimelineMutationOriginalState(World world, BlockPos pos, BlockState currentState) {
		if (!shouldRestoreTimelineMutations()) return;
		if (world == null || pos == null || currentState == null) return;
		RegistryKey<World> worldKey = world.getRegistryKey();
		if (timelineMutationWorldKey == null) {
			timelineMutationWorldKey = worldKey;
		} else if (!timelineMutationWorldKey.equals(worldKey)) {
			restoreTimelineMutationSnapshot();
			timelineMutationWorldKey = worldKey;
		}
		timelineMutationSnapshot.putIfAbsent(pos.toImmutable(), currentState);
	}

	private static void restoreTimelineMutationSnapshot() {
		if (timelineMutationSnapshot.isEmpty()) {
			timelineMutationWorldKey = null;
			return;
		}
		if (!shouldRestoreTimelineMutations()) {
			timelineMutationSnapshot.clear();
			timelineMutationWorldKey = null;
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world != null && timelineMutationWorldKey != null && timelineMutationWorldKey.equals(world.getRegistryKey())) {
			for (Map.Entry<BlockPos, BlockState> entry : timelineMutationSnapshot.entrySet()) {
				BlockPos pos = entry.getKey();
				if (!world.isChunkLoaded(pos)) continue;
				BlockState originalState = entry.getValue();
				if (!world.getBlockState(pos).equals(originalState)) {
					world.setBlockState(pos, originalState, 3);
				}
			}
		}
		timelineMutationSnapshot.clear();
		timelineMutationWorldKey = null;
	}

	private static boolean shouldRestoreTimelineMutations() {
		if (BeatBlock.timeline == null) return true;
		Object raw = BeatBlock.timeline.getMetadata("timelineActionRollbackMode");
		if (raw == null) return true;
		String mode = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
		return !"persistent".equals(mode) && !"performance".equals(mode);
	}

	private static void resetTimelineAnimationScheduling() {
		restoreTimelineMutationSnapshot();
		scheduledTimelineAnimationIds.clear();
		scheduledAutoAnimationIds.clear();
		lastTimelineAnimationTime = 0.0;
		lastAutoAnimationTime = 0.0;
		lastStepBeatTickTime = 0.0;
		if (BeatBlock.blockAnimationEngine != null) {
			BeatBlock.blockAnimationEngine.clear();
		}
	}

	private static String scheduleKey(TimelineAnimationEvent event) {
		if (event.getEventId() != null && !event.getEventId().isBlank()) {
			return event.getEventId();
		}
		return String.format(Locale.ROOT, "%s|%.6f|%s|%s",
			event.getActionMode().name(),
			event.getTimeSeconds(),
			event.getAnimationTypeId(),
			event.getTargetObjectId());
	}

	public static TimelineActionExecutionReport getLastTimelineActionExecutionReport() {
		return lastTimelineActionExecutionReport;
	}

	public static TimelineActionExecutionReport getTimelineActionExecutionReport(String eventId) {
		if (eventId == null || eventId.isBlank()) return null;
		return timelineActionReportByEventId.get(eventId);
	}

	public static void togglePlayback() {
		if (BeatBlock.musicPlayer.isPlaying()) {
			stopPlayback();
		} else {
			BeatBlock.musicPlayer.play();
			startDriving();
		}
	}
}
