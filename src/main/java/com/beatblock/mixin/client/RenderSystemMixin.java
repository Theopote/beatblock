package com.beatblock.mixin.client;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.export.VideoExportCoordinator;
import com.beatblock.client.imgui.ImGuiRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.tracy.TracyFrameCapturer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * flipFrame(HEAD)：视频导出时先捕获纯 Minecraft 画面，再合成 ImGui（编辑器 UI 不会进入导出视频）。
 */
@Mixin(RenderSystem.class)
public class RenderSystemMixin {

	@Inject(method = "flipFrame", at = @At("HEAD"))
	private static void beatblock$beforeFlipFrame(Window window, TracyFrameCapturer capturer, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.currentScreen == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;

		VideoExportCoordinator exportCoordinator = VideoExportCoordinator.getInstance();
		if (exportCoordinator.isActive()) {
			exportCoordinator.onBeforeFlipFrame();
		}

		ImGuiRenderer renderer = ImGuiRenderer.getInstance();
		if (renderer.isInitialized() && renderer.hasPendingDrawData()) {
			renderer.renderPendingDrawData();
		}
	}
}
