package com.beatblock.client.render;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.input.BeatBlockInputSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.BlockSelectionLine;
import com.beatblock.selection.SelectionMode;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

/**
 * 在世界中绘制当前选区的总包围盒线框（大选区或未启用逐块高亮时使用）。
 * 框选：AABB 预览；线选：沿体素路径的折线；笔刷：半径包络盒；平面切片：薄片 AABB。
 */
public final class BeatBlockSelectionRenderer {

	private static final int COLOR_ARGB = 0xE6FFB833;
	private static final int BOX_PREVIEW_ARGB = 0xAABBDDFF;
	private static final int LINE_PREVIEW_ARGB = 0xAAFFCC66;
	private static final int PLANE_SLICE_PREVIEW_ARGB = 0xAA88FFAA;
	private static final int BRUSH_PREVIEW_ARGB = 0xAAFFAA66;

	private BeatBlockSelectionRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		if (!(mc.currentScreen instanceof BeatBlockUIScreen)) return;

		var mgr = BeatBlockSelectionManager.get();

		int selCount = mgr.getSelectionCount();
		if (selCount > BeatBlockSelectedBlocksRenderer.MAX_BLOCKS_FOR_PER_BLOCK_RENDER) {
			BlockPos min = mgr.getBoundingMin();
			BlockPos max = mgr.getBoundingMax();
			if (min != null && max != null) {
				drawInclusiveBoundingBox(matrices, consumers, mc, min, max, COLOR_ARGB, 2.5f);
			}
		}

		renderBoxDragPreviewIfNeeded(matrices, consumers, mc, mgr);
		renderLineDragPreviewIfNeeded(matrices, consumers, mc, mgr);
		renderPlaneSlicePreviewIfNeeded(matrices, consumers, mc, mgr);
		renderBrushPreviewIfNeeded(matrices, consumers, mc, mgr);
	}

	private static void renderBoxDragPreviewIfNeeded(
			MatrixStack matrices, VertexConsumerProvider consumers,
			MinecraftClient mc, BeatBlockSelectionManager mgr) {
		if (mgr.getMode() != SelectionMode.BOX || mgr.getBoxFirstCorner() == null) return;
		BlockHitResult hit = raycastForPreview(mc);
		if (hit == null) return;
		drawAabbBetweenCorners(matrices, consumers, mc, mgr.getBoxFirstCorner(), hit.getBlockPos(), BOX_PREVIEW_ARGB, 2.0f);
	}

	private static void renderLineDragPreviewIfNeeded(
			MatrixStack matrices, VertexConsumerProvider consumers,
			MinecraftClient mc, BeatBlockSelectionManager mgr) {
		if (mgr.getMode() != SelectionMode.LINE || mgr.getLineFirstCorner() == null) return;
		BlockHitResult hit = raycastForPreview(mc);
		if (hit == null) return;
		List<BlockPos> cells = BlockSelectionLine.between(mgr.getLineFirstCorner(), hit.getBlockPos());
		drawVoxelCenterPolyline(matrices, consumers, mc, cells, LINE_PREVIEW_ARGB);
	}

	private static void renderPlaneSlicePreviewIfNeeded(
			MatrixStack matrices, VertexConsumerProvider consumers,
			MinecraftClient mc, BeatBlockSelectionManager mgr) {
		if (mgr.getMode() != SelectionMode.PLANE_SLICE) return;
		if (mc.world == null) return;
		BlockHitResult hit = raycastForPreview(mc);
		if (hit == null) return;
		var b = mgr.computePlaneSliceBounds(mc.world, hit.getBlockPos(), mgr.resolvePlaneSliceFace(hit.getSide()));
		if (b.isEmpty()) return;
		drawInclusiveBoundingBox(matrices, consumers, mc,
				new BlockPos(b.minX(), b.minY(), b.minZ()),
				new BlockPos(b.maxX(), b.maxY(), b.maxZ()),
				PLANE_SLICE_PREVIEW_ARGB, 2.0f);
	}

	private static void renderBrushPreviewIfNeeded(
			MatrixStack matrices, VertexConsumerProvider consumers,
			MinecraftClient mc, BeatBlockSelectionManager mgr) {
		if (mgr.getMode() != SelectionMode.BRUSH) return;
		BlockHitResult hit = raycastForPreview(mc);
		if (hit == null) return;
		drawRadiusPreviewAabb(matrices, consumers, mc, hit.getBlockPos(), mgr.getSphereBrushRadius(),
				1.85f);
	}

	private static void drawRadiusPreviewAabb(
			MatrixStack matrices, VertexConsumerProvider consumers, MinecraftClient mc,
			BlockPos c, int r, float lineWidth) {
		int minX = c.getX() - r;
		int minY = c.getY() - r;
		int minZ = c.getZ() - r;
		int maxX = c.getX() + r;
		int maxY = c.getY() + r;
		int maxZ = c.getZ() + r;
		drawInclusiveBoundingBox(matrices, consumers, mc,
				new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), BeatBlockSelectionRenderer.BRUSH_PREVIEW_ARGB, lineWidth);
	}

	private static BlockHitResult raycastForPreview(MinecraftClient mc) {
		if (BeatBlockUIScreen.isMouseOverUI()) return null;
		BlockHitResult hit = BeatBlockInputSystem.raycastFromImGui();
		if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		double maxSq = BeatBlockInputSystem.MAX_RAYCAST_DISTANCE * BeatBlockInputSystem.MAX_RAYCAST_DISTANCE;
		if (cam.squaredDistanceTo(hit.getPos()) > maxSq) return null;
		return hit;
	}

	private static void drawAabbBetweenCorners(
			MatrixStack matrices, VertexConsumerProvider consumers, MinecraftClient mc,
			BlockPos first, BlockPos second, int argb, float lineWidth) {
		int minX = Math.min(first.getX(), second.getX());
		int minY = Math.min(first.getY(), second.getY());
		int minZ = Math.min(first.getZ(), second.getZ());
		int maxX = Math.max(first.getX(), second.getX());
		int maxY = Math.max(first.getY(), second.getY());
		int maxZ = Math.max(first.getZ(), second.getZ());
		drawInclusiveBoundingBox(matrices, consumers, mc,
				new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), argb, lineWidth);
	}

	/** 沿体素线段中心折线（与最终线选一致），每段单独取 LINES 缓冲以防与其它层交错。 */
	private static void drawVoxelCenterPolyline(
			MatrixStack matrices, VertexConsumerProvider consumers,
			MinecraftClient mc, List<BlockPos> cells, int argb) {
		if (cells == null || cells.size() < 2) {
			return;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		matrices.push();
		Matrix4f mat = matrices.peek().getPositionMatrix();
		for (int i = 0; i < cells.size() - 1; i++) {
			Vec3d a = Vec3d.ofCenter(cells.get(i)).subtract(cam);
			Vec3d b = Vec3d.ofCenter(cells.get(i + 1)).subtract(cam);
			VertexConsumer buf = consumers.getBuffer(RenderLayers.LINES);
			emitLineSegment(buf, mat, a.x, a.y, a.z, b.x, b.y, b.z, argb);
		}
		matrices.pop();
	}

	private static void emitLineSegment(VertexConsumer buf, Matrix4f mat,
			double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
		float ca = ((argb >>> 24) & 255) / 255f;
		float cr = ((argb >>> 16) & 255) / 255f;
		float cg = ((argb >>> 8) & 255) / 255f;
		float cb = (argb & 255) / 255f;
		buf.vertex(mat, (float) x0, (float) y0, (float) z0).color(cr, cg, cb, ca).normal(0f, 1f, 0f);
		buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(cr, cg, cb, ca).normal(0f, 1f, 0f);
	}

	private static void drawInclusiveBoundingBox(
			MatrixStack matrices, VertexConsumerProvider consumers, MinecraftClient mc,
			BlockPos minInclusive, BlockPos maxInclusive, int argb, float lineWidth) {
		var cam = mc.gameRenderer.getCamera().getCameraPos();
		double ox = minInclusive.getX() - cam.x;
		double oy = minInclusive.getY() - cam.y;
		double oz = minInclusive.getZ() - cam.z;
		double dx = maxInclusive.getX() - minInclusive.getX() + 1.0;
		double dy = maxInclusive.getY() - minInclusive.getY() + 1.0;
		double dz = maxInclusive.getZ() - minInclusive.getZ() + 1.0;

		var shape = VoxelShapes.cuboid(0, 0, 0, dx, dy, dz);
		VertexConsumer buffer = consumers.getBuffer(RenderLayers.LINES);
		matrices.push();
		VertexRendering.drawOutline(matrices, buffer, shape, ox, oy, oz, argb, lineWidth);
		matrices.pop();
	}
}
