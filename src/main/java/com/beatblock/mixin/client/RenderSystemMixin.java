package com.beatblock.mixin.client;

import com.beatblock.client.BeatBlockUIScreen;
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
 * 在 flipFrame(HEAD) 时绘制 ImGui，避免被后续渲染覆盖。
 */
@Mixin(RenderSystem.class)
public class RenderSystemMixin {

	@Inject(method = "flipFrame", at = @At("HEAD"))
	private static void beatblock$beforeFlipFrame(Window window, TracyFrameCapturer capturer, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.currentScreen == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;

		ImGuiRenderer renderer = ImGuiRenderer.getInstance();
		if (renderer.isInitialized() && renderer.hasPendingDrawData()) {
			renderer.renderPendingDrawData();
		}
	}
}
