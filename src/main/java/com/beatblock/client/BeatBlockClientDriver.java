package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.animation.AnimationInstance;
import com.beatblock.animation.AnimationTemplate;
import com.beatblock.audio.BeatBlockRuntime;
import com.beatblock.beat.BeatEvent;
import com.beatblock.beat.Beatmap;
import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.stage.StageZone;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端驱动：每帧推进 MusicPlayer、派发 BeatEvent、更新动画并应用变换。
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
	private static final Map<BlockPos, BlockState> timelineMutationSnapshot = new HashMap<>();
	private static RegistryKey<World> timelineMutationWorldKey;
	private static volatile TimelineActionExecutionReport lastTimelineActionExecutionReport;
	private static final Map<String, TimelineActionExecutionReport> timelineActionReportByEventId = new ConcurrentHashMap<>();
	private static final int MAX_ACTION_REPORT_CACHE_SIZE = 4096;

	public static void onClientTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;

		if (driving) {
			if (world == null) return;

			long now = System.nanoTime();
			double delta = lastTickNanos > 0 ? (now - lastTickNanos) / 1e9 : 1.0 / 20.0;
			lastTickNanos = now;

			BeatBlock.musicPlayer.tick(delta);
			syncStemMixerToMusicPlayer();
			double currentTime = BeatBlock.musicPlayer.getCurrentTimeSeconds();

			BeatBlockRuntime runtime = BeatBlockRuntime.getInstance();
			if (BeatBlock.musicPlayer.isPlaying()) {
				if (!runtime.isPlaying()) runtime.play();
			} else if (runtime.isPlaying()) {
				runtime.pause();
			}
			runtime.onServerTick();

			BeatBlock.beatScheduler.tick(currentTime);
			BeatBlock.animationManager.tick(currentTime);
			if (BeatBlock.blockAnimationEngine != null) {
				syncTimelineBlockAnimationEvents(currentTime, false);
				syncTimelineAutoAnimationEvents(currentTime, false);
				BeatBlock.blockAnimationEngine.tick(currentTime);
				MinecraftClient mc2 = MinecraftClient.getInstance();
				World tickWorld = mc2 != null ? mc2.world : null;
				if (tickWorld != null) {
					BeatBlock.blockAnimationEngine.getBuildSequencer().tick(currentTime, tickWorld);
				}
			}

			Vec3d base = BeatBlock.stageManager.getCurrentStage()
				.map(s -> new Vec3d(s.getCenterX(), s.getCenterY(), s.getCenterZ()))
				.orElse(mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : Vec3d.ZERO);
			BeatBlock.transformUpdater.setBasePosition(base);
			BeatBlock.transformUpdater.tick(BeatBlock.animationManager.getActiveInstances(), currentTime);
			return;
		}

		// 未点播放时仍按时间轴时钟推进 ANIMATE 预览（拖动标尺/播放头时 clock 已 seek，与 scrub 同步）
		if (world != null && BeatBlock.blockAnimationEngine != null && BeatBlock.timeline != null) {
			double previewTime = previewTimelineTimeSeconds();
			syncTimelineBlockAnimationEvents(previewTime, true);
			syncTimelineAutoAnimationEvents(previewTime, true);
			BeatBlock.blockAnimationEngine.tick(previewTime);
		}
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

	/**
	 * 是否应用时间线摄像机覆盖玩家视角：仅在「正在播放」时生效（时钟或主音乐播放器其一在播），
	 * 避免仅存在摄像机片段或曾点过播放后暂停时仍锁死鼠标视角。
	 */
	public static boolean shouldApplyTimelineCameraToView() {
		if (!driving) return false;
		if (BeatBlock.timelineEditor != null && BeatBlock.timelineEditor.getClock().isPlaying()) return true;
		return BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.isPlaying();
	}

	public static void setupBeatEventHandler() {
		BeatBlock.animationManager.setEventHandler((event, manager) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			World world = mc.world;
			if (world == null) return;

			StageZone stage = BeatBlock.stageManager.getCurrentStage().orElse(null);
			if (stage == null) {
				Vec3d pos = mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : Vec3d.ZERO;
				Vec3d look = mc.player != null ? mc.player.getRotationVec(1f) : Vec3d.ZERO;
				double x = pos.getX() + look.getX() * 5;
				double y = pos.getY();
				double z = pos.getZ() + look.getZ() * 5;
				stage = new StageZone("temp", new Box(x - 2, y - 1, z - 2, x + 2, y + 3, z + 2));
			}

			AnimationTemplate template = templateFor(event.getType());
			if (template == null) return;

			DisplayEntity.BlockDisplayEntity display = BeatBlock.blockSpawner.spawnAtStage(
				world, stage,
				Blocks.DIAMOND_BLOCK.getDefaultState(),
				BeatBlock.blockDisplayPool
			);
			if (display == null) return;

			double startTime = event.getTimestamp();
			double offsetY = 0.5 + event.getIntensity() * 0.5;
			AnimationInstance instance = new AnimationInstance(
				template, startTime, display,
				0, offsetY, 0,
				0.5, 1.0 + event.getIntensity() * 0.5
			);
			manager.addInstance(instance);
		});

		BeatBlock.animationManager.setOnInstanceEnded(inst -> {
			Object target = inst.getDisplayTarget();
			if (target instanceof DisplayEntity.BlockDisplayEntity blockDisplay) {
				BeatBlock.blockDisplayPool.returnToPool(blockDisplay);
			}
		});
	}

	private static AnimationTemplate templateFor(BeatEvent.Type type) {
		return switch (type) {
            case SNARE -> BeatBlock.animationRegistry.get("slide");
			case HIHAT, BASS, MELODY -> BeatBlock.animationRegistry.get("pulse");
			default -> BeatBlock.animationRegistry.get("bounce");
		};
	}

	public static void startPlayback(Beatmap beatmap) {
		if (beatmap == null) return;
		BeatBlock.beatScheduler.setBeatmap(beatmap);
		BeatBlock.beatScheduler.reset();
		BeatBlock.musicPlayer.setDurationSeconds(beatmap.getDurationSeconds());
		BeatBlock.musicPlayer.setCurrentTimeSeconds(0);
		if (BeatBlock.stemMixer != null && BeatBlock.stemMixer.hasStems()) {
			BeatBlock.stemMixer.setCurrentTimeSeconds(0);
		}
		BeatBlock.musicPlayer.play();
		if (BeatBlock.stageManager.getCurrentStage().isEmpty()) {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player != null) {
				Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
				Vec3d look = mc.player.getRotationVec(1f);
				double x = pos.getX() + look.getX() * 5;
				double y = pos.getY();
				double z = pos.getZ() + look.getZ() * 5;
				StageZone defaultStage = new StageZone("default", new Box(x - 3, y - 1, z - 3, x + 3, y + 4, z + 3));
				BeatBlock.stageManager.addZone(defaultStage);
				BeatBlock.stageManager.setCurrentStage(defaultStage);
			}
		}
		startDriving();
	}

	public static void stopPlayback() {
		BeatBlock.musicPlayer.pause();
		if (BeatBlock.stemMixer != null && BeatBlock.stemMixer.hasStems()) {
			BeatBlock.stemMixer.pause();
		}
		BeatBlockRuntime.getInstance().stop();
		BeatBlock.animationManager.clear();
		resetTimelineAnimationScheduling();
		stopDriving();
	}

	/** 与时间轴 UI 一致的时间源，便于未播放时拖动标尺仍能预览 ANIMATE。 */
	private static double previewTimelineTimeSeconds() {
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
			Beatmap defaultMap = BeatBlock.beatmapGenerator.generateFromBpm("default", 120, 30);
			startPlayback(defaultMap);
		}
	}
}
