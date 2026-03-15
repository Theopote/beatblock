package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.animation.AnimationInstance;
import com.beatblock.animation.AnimationTemplate;
import com.beatblock.beat.BeatEvent;
import com.beatblock.beat.Beatmap;
import com.beatblock.stage.StageZone;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 客户端驱动：每帧推进 MusicPlayer、派发 BeatEvent、更新动画并应用变换。
 */
public final class BeatBlockClientDriver {

	private static long lastTickNanos;
	private static boolean driving;

	public static void onClientTick() {
		if (!driving) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc.world;
		if (world == null) return;

		long now = System.nanoTime();
		double delta = lastTickNanos > 0 ? (now - lastTickNanos) / 1e9 : 1.0 / 20.0;
		lastTickNanos = now;

		BeatBlock.musicPlayer.tick(delta);
		double currentTime = BeatBlock.musicPlayer.getCurrentTimeSeconds();

		BeatBlock.beatScheduler.tick(currentTime);
		BeatBlock.animationManager.tick(currentTime);
		if (BeatBlock.blockAnimationEngine != null) {
			BeatBlock.blockAnimationEngine.tick(currentTime);
		}

		Vec3d base = BeatBlock.stageManager.getCurrentStage()
			.map(s -> new Vec3d(s.getCenterX(), s.getCenterY(), s.getCenterZ()))
			.orElse(mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : Vec3d.ZERO);
		BeatBlock.transformUpdater.setBasePosition(base);
		BeatBlock.transformUpdater.tick(BeatBlock.animationManager.getActiveInstances(), currentTime);
	}

	public static void startDriving() {
		lastTickNanos = 0;
		driving = true;
	}

	public static void stopDriving() {
		driving = false;
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
		BeatBlock.animationManager.clear();
		stopDriving();
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
