package com.beatblock;

import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.camera.CameraPathWorldRenderer;
import com.beatblock.client.render.BeatBlockAnimatedBlocksRenderer;
import com.beatblock.client.render.BeatBlockHoverOutlineRenderer;
import com.beatblock.client.render.BeatBlockSelectedBlocksRenderer;
import com.beatblock.client.render.BeatBlockSelectionRenderer;
import com.beatblock.client.selection.BeatBlockLassoInteraction;
import com.beatblock.client.selection.BeatBlockSelectionBrushTick;
import com.beatblock.ui.EditorScreen;
import com.beatblock.ui.HUD;
import com.beatblock.ui.ImportScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeatBlockClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(BeatBlock.MOD_ID + "-client");

	public static HUD hud;
	public static EditorScreen editorScreen;
	public static ImportScreen importScreen;

	private static KeyBinding keyTogglePlayback;

	@Override
	public void onInitializeClient() {
		BeatBlockClientDriver.install(BeatBlock::getContext);
		com.beatblock.client.camera.TimelineCameraController.getInstance().bindContext(BeatBlock::getContext);

		hud = new HUD();
		editorScreen = new EditorScreen();
		importScreen = new ImportScreen();

		BeatBlock.openUICallback = () -> MinecraftClient.getInstance().setScreen(new BeatBlockUIScreen());

		WorldRenderEvents.END_MAIN.register(context -> {
			var consumers = context.consumers();
			var matrices = context.matrices();
			BeatBlockSelectedBlocksRenderer.renderIfNeeded(matrices, consumers);
			BeatBlockSelectionRenderer.renderIfNeeded(matrices, consumers);
			BeatBlockHoverOutlineRenderer.renderIfNeeded(matrices, consumers);
			BeatBlockAnimatedBlocksRenderer.render(matrices, consumers);
			CameraPathWorldRenderer.renderIfNeeded(matrices, consumers);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			BeatBlockClientDriver.onClientTick();
			BeatBlockSelectionBrushTick.onEndClientTick(client);
			BeatBlockLassoInteraction.onEndClientTick(client);
		});

		keyTogglePlayback = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.beatblock.toggle_playback",
			net.minecraft.client.util.InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_B,
			KeyBinding.Category.create(Identifier.of(BeatBlock.MOD_ID, "misc"))
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (keyTogglePlayback.wasPressed()) {
				BeatBlockClientDriver.togglePlayback();
			}
		});
		
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LOGGER.info("BeatBlock client stopping: releasing timeline and audio background resources");
			var ctx = BeatBlock.getContext();
			if (ctx.timelineEditor() != null) {
				ctx.timelineEditor().shutdown();
			}
			if (ctx.externalAudioAnalyzer() != null) {
				ctx.externalAudioAnalyzer().shutdown();
			}
			if (ctx.audioConversionService() != null) {
				ctx.audioConversionService().shutdown();
			}
			LOGGER.info("BeatBlock client stopping: background resource cleanup complete");
		});

		LOGGER.info("BeatBlock 客户端已初始化 — 按 B 键切换时间轴播放");
	}
}
