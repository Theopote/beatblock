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

/**
 * 客户端驱动：每帧推进 MusicPlayer、派发 BeatEvent、更新动画并应用变换。
 */
public final class BeatBlockClientDriver {

	private static long lastTickNanos;
	private static boolean driving;
	private static final Set<String> scheduledTimelineAnimationIds = new HashSet<>();
	private static final double TIMELINE_EVENT_EPSILON = 1e-4;
	private static double lastTimelineAnimationTime;
	private static final Map<BlockPos, BlockState> timelineMutationSnapshot = new HashMap<>();
	private static RegistryKey<World> timelineMutationWorldKey;

	public static void onClientTick() {
		if (!driving) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc.world;
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
			syncTimelineBlockAnimationEvents(currentTime);
			BeatBlock.blockAnimationEngine.tick(currentTime);
		}

		Vec3d base = BeatBlock.stageManager.getCurrentStage()
			.map(s -> new Vec3d(s.getCenterX(), s.getCenterY(), s.getCenterZ()))
			.orElse(mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : Vec3d.ZERO);
		BeatBlock.transformUpdater.setBasePosition(base);
		BeatBlock.transformUpdater.tick(BeatBlock.animationManager.getActiveInstances(), currentTime);
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
			case KICK -> BeatBlock.animationRegistry.get("bounce");
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

	private static void syncTimelineBlockAnimationEvents(double currentTime) {
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
			applyTimelineActionEvent(event);
		}
		lastTimelineAnimationTime = currentTime;
	}

	private static void applyTimelineActionEvent(TimelineAnimationEvent event) {
		if (event == null || BeatBlock.blockAnimationEngine == null) return;
		TimelineAnimationActionMode actionMode = event.getActionMode();
		if (actionMode == TimelineAnimationActionMode.ANIMATE) {
			BeatBlock.blockAnimationEngine.scheduleTimelineEvent(event);
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world == null) return;
		var mutations = BeatBlock.blockAnimationEngine.planControlMutations(event, world);
		if (mutations == null || mutations.isEmpty()) return;
		for (BlockControlExecutor.BlockMutation mutation : mutations) {
			captureTimelineMutationOriginalState(world, mutation.pos(), mutation.fromState());
		}
		BeatBlock.blockAnimationEngine.applyControlMutations(world, mutations);
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
		lastTimelineAnimationTime = 0.0;
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

	public static void togglePlayback() {
		if (BeatBlock.musicPlayer.isPlaying()) {
			stopPlayback();
		} else {
			Beatmap defaultMap = BeatBlock.beatmapGenerator.generateFromBpm("default", 120, 30);
			startPlayback(defaultMap);
		}
	}
}
