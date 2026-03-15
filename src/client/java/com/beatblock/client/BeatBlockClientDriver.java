package com.beatblock.client;

import com.beatblock.BeatBlock;
import com.beatblock.animation.AnimationInstance;
import com.beatblock.animation.AnimationTemplate;
import com.beatblock.beat.BeatEvent;
import com.beatblock.beat.Beatmap;
import com.beatblock.stage.StageZone;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端驱动：每帧推进 MusicPlayer、派发 BeatEvent、更新动画并应用变换。
 * 负责在收到 BeatEvent 时生成 BlockDisplay、创建 AnimationInstance，动画结束时归还 Display 到池。
 */
public final class BeatBlockClientDriver {

	private static long lastTickNanos;
	private static boolean driving;

	public static void onClientTick() {
		if (!driving) return;
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level == null) return;

		// 计算 delta 时间（秒）
		long now = System.nanoTime();
		double delta = lastTickNanos > 0 ? (now - lastTickNanos) / 1e9 : 1.0 / 20.0;
		lastTickNanos = now;

		// 推进播放时间
		BeatBlock.musicPlayer.tick(delta);
		double currentTime = BeatBlock.musicPlayer.getCurrentTimeSeconds();

		// 派发节拍事件（会触发 eventHandler，创建动画实例）
		BeatBlock.beatScheduler.tick(currentTime);

		// 更新动画管理器（移除已结束的实例并触发 onInstanceEnded）
		BeatBlock.animationManager.tick(currentTime);

		// 以当前舞台中心为基准更新 BlockDisplay 变换；无舞台时用玩家位置
		Vec3 base = BeatBlock.stageManager.getCurrentStage()
			.map(s -> new Vec3(s.getCenterX(), s.getCenterY(), s.getCenterZ()))
			.orElse(mc.player != null ? mc.player.position() : Vec3.ZERO);
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

	/**
	 * 注册到 AnimationManager 的 BeatEvent 处理器：根据节拍类型生成 BlockDisplay 并创建动画实例。
	 */
	public static void setupBeatEventHandler() {
		BeatBlock.animationManager.setEventHandler((event, manager) -> {
			Minecraft mc = Minecraft.getInstance();
			Level level = mc.level;
			if (level == null) return;

			StageZone stage = BeatBlock.stageManager.getCurrentStage().orElse(null);
			if (stage == null) {
				// 无舞台时以玩家前方为中心创建临时区域
				Vec3 pos = mc.player != null ? mc.player.position() : Vec3.ZERO;
				Vec3 look = mc.player != null ? mc.player.getLookAngle() : Vec3.ZERO;
				double x = pos.x + look.x * 5;
				double y = pos.y;
				double z = pos.z + look.z * 5;
				stage = new StageZone("temp", new AABB(x - 2, y - 1, z - 2, x + 2, y + 3, z + 2));
			}

			AnimationTemplate template = templateFor(event.getType());
			if (template == null) return;

			Display.BlockDisplay display = BeatBlock.blockSpawner.spawnAtStage(
				level, stage,
				Blocks.DIAMOND_BLOCK.defaultBlockState(),
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

		// 动画结束时将 BlockDisplay 归还池
		BeatBlock.animationManager.setOnInstanceEnded(inst -> {
			Object target = inst.getDisplayTarget();
			if (target instanceof Display.BlockDisplay blockDisplay) {
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

	/**
	 * 开始播放：设置 Beatmap、时长并启动。若无当前舞台则用玩家前方创建临时舞台。
	 */
	public static void startPlayback(Beatmap beatmap) {
		if (beatmap == null) return;
		BeatBlock.beatScheduler.setBeatmap(beatmap);
		BeatBlock.beatScheduler.reset();
		BeatBlock.musicPlayer.setDurationSeconds(beatmap.getDurationSeconds());
		BeatBlock.musicPlayer.setCurrentTimeSeconds(0);
		BeatBlock.musicPlayer.play();
		// 若无舞台则设默认：玩家前方 5 格
		if (BeatBlock.stageManager.getCurrentStage().isEmpty()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				Vec3 pos = mc.player.position();
				Vec3 look = mc.player.getLookAngle();
				double x = pos.x + look.x * 5;
				double y = pos.y;
				double z = pos.z + look.z * 5;
				StageZone defaultStage = new StageZone("default", new AABB(x - 3, y - 1, z - 3, x + 3, y + 4, z + 3));
				BeatBlock.stageManager.addZone(defaultStage);
				BeatBlock.stageManager.setCurrentStage(defaultStage);
			}
		}
		startDriving();
	}

	/**
	 * 暂停 / 停止播放。
	 */
	public static void stopPlayback() {
		BeatBlock.musicPlayer.pause();
		BeatBlock.animationManager.clear();
		stopDriving();
	}

	/**
	 * 切换播放状态：若未在播则用默认 Beatmap 开始，否则暂停。
	 */
	public static void togglePlayback() {
		if (BeatBlock.musicPlayer.isPlaying()) {
			stopPlayback();
		} else {
			// 默认：120 BPM，30 秒
			Beatmap defaultMap = BeatBlock.beatmapGenerator.generateFromBpm("default", 120, 30);
			startPlayback(defaultMap);
		}
	}
}
