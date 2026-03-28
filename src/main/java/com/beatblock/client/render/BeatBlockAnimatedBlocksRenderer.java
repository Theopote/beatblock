package com.beatblock.client.render;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.engine.AnimatedBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * 将 {@link com.beatblock.engine.BlockAnimationEngine#getCurrentFrameBlocks()} 中的位移画进世界：
 * ANIMATE 模式不修改真实方块状态，此前缺少这一层导致播放时「看不见」动画。
 * 当前为线框 + 位移连线（后续可换成整块模型矩阵绘制）。
 */
public final class BeatBlockAnimatedBlocksRenderer {

	private static final int MOTION_LINE_ARGB = 0xCC44FFAA;
	private static final int GHOST_BOX_ARGB = 0x8866FFDD;
	private static final float LINE_WIDTH = 1.75f;
	private static final int MAX_DRAW_BLOCKS = 4096;
	private static final double MOTION_EPS_SQ = 1e-8;

	private BeatBlockAnimatedBlocksRenderer() {}

	public static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
		if (BeatBlock.blockAnimationEngine == null) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.gameRenderer == null) {
			return;
		}
		// 无播放驱动时通常无活跃帧；仍允许在暂停瞬间有残留帧时绘制
		if (!BeatBlockClientDriver.isDriving()) {
			return;
		}
		Map<BlockPos, AnimatedBlock> frame = BeatBlock.blockAnimationEngine.getCurrentFrameBlocks();
		if (frame == null || frame.isEmpty()) {
			return;
		}

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		int drawn = 0;
		for (AnimatedBlock ab : frame.values()) {
			if (drawn++ >= MAX_DRAW_BLOCKS) {
				break;
			}
			BlockPos orig = ab.getOriginalPos();
			Vec3d anim = ab.getPosition();
			Vec3d origCenter = Vec3d.ofCenter(orig);

			matrices.push();
			Matrix4f mat = matrices.peek().getPositionMatrix();

			if (origCenter.squaredDistanceTo(anim) > MOTION_EPS_SQ) {
				VertexConsumer lineBuf = consumers.getBuffer(RenderLayers.LINES);
				Vec3d a = origCenter.subtract(cam);
				Vec3d b = anim.subtract(cam);
				emitLineSegment(lineBuf, mat, a.x, a.y, a.z, b.x, b.y, b.z, MOTION_LINE_ARGB, LINE_WIDTH);
			}

			double ox = anim.x - 0.5 - cam.x;
			double oy = anim.y - cam.y;
			double oz = anim.z - 0.5 - cam.z;
			var unitCube = VoxelShapes.cuboid(0, 0, 0, 1, 1, 1);
			VertexConsumer outlineBuf = consumers.getBuffer(RenderLayers.LINES);
			VertexRendering.drawOutline(matrices, outlineBuf, unitCube, ox, oy, oz, GHOST_BOX_ARGB, LINE_WIDTH);
			matrices.pop();
		}
	}

	private static void emitLineSegment(VertexConsumer buf, Matrix4f mat,
			double x0, double y0, double z0, double x1, double y1, double z1, int argb, float lineWidth) {
		float ca = ((argb >>> 24) & 255) / 255f;
		float cr = ((argb >>> 16) & 255) / 255f;
		float cg = ((argb >>> 8) & 255) / 255f;
		float cb = (argb & 255) / 255f;
		buf.vertex(mat, (float) x0, (float) y0, (float) z0).color(cr, cg, cb, ca).normal(0f, 1f, 0f).lineWidth(lineWidth);
		buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(cr, cg, cb, ca).normal(0f, 1f, 0f).lineWidth(lineWidth);
	}
}
