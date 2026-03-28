package com.beatblock.client.render;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.input.BeatBlockInputSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * BeatBlock UI 打开且鼠标在场景区时，沿 ImGui 光标射线绘制当前指向方块的描边（思路同 ChronoBlocks HoverHighlightRenderer）。
 */
public final class BeatBlockHoverOutlineRenderer {

	private BeatBlockHoverOutlineRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;
		if (BeatBlockUIScreen.isMouseOverUI()) return;

		BlockHitResult hit = BeatBlockInputSystem.raycastFromImGui();
		if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

		double reach = mc.player != null ? mc.player.getBlockInteractionRange() : 4.5;
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		if (cam.squaredDistanceTo(hit.getPos()) > reach * reach) return;

		BlockPos pos = hit.getBlockPos();
		var shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
		if (shape == null || shape.isEmpty()) return;

		long time = System.currentTimeMillis();
		float pulse = 0.35f + 0.45f * (0.5f + 0.5f * (float) Math.sin(time / 400.0));
		int alpha = (int) (255 * Math.clamp(pulse, 0.15f, 1.0f));
		int r = 64, g = 217, b = 255;
		int color = (alpha << 24) | (r << 16) | (g << 8) | b;

		VertexConsumer buffer = consumers.getBuffer(RenderLayers.LINES);
		matrices.push();
		double ox = pos.getX() - cam.x;
		double oy = pos.getY() - cam.y;
		double oz = pos.getZ() - cam.z;
		VertexRendering.drawOutline(matrices, buffer, shape, ox, oy, oz, color, 2.0f);
		matrices.pop();
	}
}
