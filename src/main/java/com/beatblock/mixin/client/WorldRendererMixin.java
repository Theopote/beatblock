package com.beatblock.mixin.client;

import com.beatblock.client.BeatBlockUIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 打开 BeatBlock 时关闭原版「屏幕中心准星」指向方块的描边（黑色线框），与 ChronoBlocks
 * {@code WorldRendererMixin#onRenderTargetBlockOutline} 同理；自定义光标高亮由 {@link com.beatblock.client.render.BeatBlockHoverOutlineRenderer} 负责。
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

	@Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
	private void beatblock$hideVanillaTargetBlockOutline(
		VertexConsumerProvider.Immediate immediate,
		MatrixStack matrices,
		boolean renderBlockOutline,
		WorldRenderState renderStates,
		CallbackInfo ci
	) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.currentScreen instanceof BeatBlockUIScreen) {
			ci.cancel();
		}
	}
}
