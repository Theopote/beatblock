package com.beatblock;

import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.ui.EditorScreen;
import com.beatblock.ui.HUD;
import com.beatblock.ui.ImportScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeatBlockClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(BeatBlock.MOD_ID + "-client");

	public static HUD hud;
	public static EditorScreen editorScreen;
	public static ImportScreen importScreen;

	private static KeyMapping keyTogglePlayback;

	@Override
	public void onInitializeClient() {
		hud = new HUD();
		editorScreen = new EditorScreen();
		importScreen = new ImportScreen();

		// 客户端驱动：BeatEvent -> BlockDisplay + AnimationInstance，每帧更新变换
		BeatBlockClientDriver.setupBeatEventHandler();

		// 每帧 tick：MusicPlayer / BeatScheduler / AnimationManager / TransformUpdater
		ClientTickEvents.END_CLIENT_TICK.register(client -> BeatBlockClientDriver.onClientTick());

		// 按键：B 切换播放/暂停（默认 120 BPM 30 秒 Beatmap）
		keyTogglePlayback = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.beatblock.toggle_playback",
			com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_B,
			Category.MISC
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (keyTogglePlayback.consumeClick()) {
				BeatBlockClientDriver.togglePlayback();
			}
		});

		LOGGER.info("BeatBlock 客户端已初始化 — 按 B 键切换节拍播放");
	}
}
