package com.beatblock.mixin.client;

import com.beatblock.client.BeatBlockUIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 打开 BeatBlock 窗口时完全隐藏原版十字准星，改用 ImGui 光标；关闭后恢复原版。
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void beatblock$hideCrosshairWhenUIOpen(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.currentScreen instanceof BeatBlockUIScreen) {
			ci.cancel();
		}
	}
}
