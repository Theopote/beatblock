package com.beatblock.mixin.client;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.export.VideoExportCoordinator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 打开 BeatBlock 窗口时隐藏原版十字准星；视频导出时隐藏全部原版 HUD，保证成片只有场景。
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void beatblock$hideHudDuringExport(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (VideoExportCoordinator.getInstance().isActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void beatblock$hideCrosshairWhenUIOpen(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.currentScreen instanceof BeatBlockUIScreen) {
			ci.cancel();
		}
	}
}
