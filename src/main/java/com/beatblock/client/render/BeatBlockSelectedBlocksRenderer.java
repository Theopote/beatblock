package com.beatblock.client.render;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.selection.BeatBlockSelectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 为选中的每个方块绘制描边；超过 {@link #MAX_BLOCKS_FOR_PER_BLOCK_RENDER} 时仅依赖总包围盒渲染。
 */
public final class BeatBlockSelectedBlocksRenderer {

	public static final int MAX_BLOCKS_FOR_PER_BLOCK_RENDER = 6000;
	private static final double RENDER_DISTANCE_SQ = 192.0 * 192.0;
	private static final int OUTLINE_ARGB = 0xE6FFCC44;
	private static final float OUTLINE_WIDTH = 1.35f;

	private BeatBlockSelectedBlocksRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;

		var mgr = BeatBlockSelectionManager.get();
		int n = mgr.getSelectionCount();
		if (n == 0 || n > MAX_BLOCKS_FOR_PER_BLOCK_RENDER) return;

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		VertexConsumer lineBuffer = consumers.getBuffer(RenderLayers.LINES);

		for (BlockPos p : mgr.getSelectedBlocks()) {
			if (cam.squaredDistanceTo(Vec3d.ofCenter(p)) > RENDER_DISTANCE_SQ) continue;
			var state = mc.world.getBlockState(p);
			var shape = state.getOutlineShape(mc.world, p);
			if (shape == null || shape.isEmpty()) continue;

			double ox = p.getX() - cam.x;
			double oy = p.getY() - cam.y;
			double oz = p.getZ() - cam.z;

			matrices.push();
			VertexRendering.drawOutline(matrices, lineBuffer, shape, ox, oy, oz, OUTLINE_ARGB, OUTLINE_WIDTH);
			matrices.pop();

			if (mgr.isSelectionFillEnabled()) {
				drawTranslucentCubeShell(matrices, consumers, cam, p);
			}
		}
	}

	/**
	 * 在方块位置绘制略缩小的半透明立方体面（debug 填充层），与描边叠加。
	 */
	private static void drawTranslucentCubeShell(
			MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam, BlockPos p) {
		VertexConsumer buf = consumers.getBuffer(RenderLayers.debugFilledBox());
		var entry = matrices.peek();
		var mat = entry.getPositionMatrix();
		var nmat = entry.getNormalMatrix();
		float eps = 0.002f;
		float x0 = (float) (p.getX() - cam.x + eps);
		float y0 = (float) (p.getY() - cam.y + eps);
		float z0 = (float) (p.getZ() - cam.z + eps);
		float x1 = (float) (p.getX() - cam.x + 1f - eps);
		float y1 = (float) (p.getY() - cam.y + 1f - eps);
		float z1 = (float) (p.getZ() - cam.z + 1f - eps);
		float r = 1.0f, g = 0.78f, b = 0.15f, a = 0.14f;

		emitQuad(buf, mat, nmat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r, g, b, a);
		emitQuad(buf, mat, nmat, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, r, g, b, a);
		emitQuad(buf, mat, nmat, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, r, g, b, a);
		emitQuad(buf, mat, nmat, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, r, g, b, a);
		emitQuad(buf, mat, nmat, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, r, g, b, a);
		emitQuad(buf, mat, nmat, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, r, g, b, a);
	}

	private static void emitQuad(VertexConsumer buf, org.joml.Matrix4f m, org.joml.Matrix3f nmat,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			float nx, float ny, float nz, float r, float g, float b, float a) {
		var nn = new org.joml.Vector3f(nx, ny, nz);
		nmat.transform(nn);
		buf.vertex(m, x0, y0, z0).color(r, g, b, a).normal(nn.x, nn.y, nn.z);
		buf.vertex(m, x1, y1, z1).color(r, g, b, a).normal(nn.x, nn.y, nn.z);
		buf.vertex(m, x2, y2, z2).color(r, g, b, a).normal(nn.x, nn.y, nn.z);
		buf.vertex(m, x3, y3, z3).color(r, g, b, a).normal(nn.x, nn.y, nn.z);
	}
}
