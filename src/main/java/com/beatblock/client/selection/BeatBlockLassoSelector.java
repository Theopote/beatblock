package com.beatblock.client.selection;

import com.beatblock.client.input.BeatBlockInputSystem;
import com.beatblock.client.input.BeatBlockWorldToFramebuffer;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/**
 * 屏幕空间套索：沿多边形边采样射线定世界 AABB，再对范围内方块做投影点内判断。
 */
public final class BeatBlockLassoSelector {

	private static final int EDGE_SAMPLES = 33;

	private BeatBlockLassoSelector() {}

	public static void tryApply(MinecraftClient client, List<double[]> polygonFramebuffer, boolean shiftDown) {
		var mgr = BeatBlockSelectionManager.get();
		if (client == null || client.world == null || client.gameRenderer == null || polygonFramebuffer == null) {
			return;
		}
		if (polygonFramebuffer.size() < 3) {
			mgr.setSelectionFeedback("套索：至少需要 3 个顶点（按住左键拖动绘制）。");
			return;
		}

		Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
		mgr.setInteractionCameraPos(cam);

		List<BlockPos> blocks = computeBlocksInsidePolygon(client, client.world, polygonFramebuffer);
		if (blocks == null) {
			return;
		}
		if (blocks.isEmpty()) {
			mgr.setSelectionFeedback("套索：多边形内（且范围内）没有可选方块。");
			return;
		}

		SelectionOperation op = shiftDown ? SelectionOperation.ADD : mgr.getOperation();
		mgr.commitLassoSelection(blocks, op);
	}

	private static List<BlockPos> computeBlocksInsidePolygon(
			MinecraftClient client, World world, List<double[]> poly) {
		var mgr = BeatBlockSelectionManager.get();
		int fbw = client.getWindow().getFramebufferWidth();
		int fbh = client.getWindow().getFramebufferHeight();
		if (fbw <= 0 || fbh <= 0) {
			mgr.setSelectionFeedback("套索：窗口尺寸无效。");
			return null;
		}

		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
		double maxReach = mgr.getMaxDistanceFromCamera();
		double maxReachSq = maxReach * maxReach;
		int maxB = mgr.getMaxBlocks();

		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		int hits = 0;

		int n = poly.size();
		for (int i = 0; i < n; i++) {
			double[] a = poly.get(i);
			double[] b = poly.get((i + 1) % n);
			for (int s = 0; s < EDGE_SAMPLES; s++) {
				double t = s / (double) (EDGE_SAMPLES - 1);
				double mx = a[0] + (b[0] - a[0]) * t;
				double my = a[1] + (b[1] - a[1]) * t;
				BlockHitResult hit = BeatBlockInputSystem.raycastFromFramebufferPixels(mx, my);
				if (hit == null || hit.getType() != HitResult.Type.BLOCK) continue;
				if (camera.squaredDistanceTo(hit.getPos()) > BeatBlockInputSystem.MAX_RAYCAST_DISTANCE
						* BeatBlockInputSystem.MAX_RAYCAST_DISTANCE) {
					continue;
				}
				BlockPos bp = hit.getBlockPos();
				if (!withinReachSq(bp, camera, maxReachSq)) continue;
				minX = Math.min(minX, bp.getX());
				minY = Math.min(minY, bp.getY());
				minZ = Math.min(minZ, bp.getZ());
				maxX = Math.max(maxX, bp.getX());
				maxY = Math.max(maxY, bp.getY());
				maxZ = Math.max(maxZ, bp.getZ());
				hits++;
			}
		}

		if (hits == 0) {
			mgr.setSelectionFeedback("套索：路径未击中视角范围内的方块，请画在可见表面上。");
			return null;
		}

		minX -= 2;
		minY -= 2;
		minZ -= 2;
		maxX += 2;
		maxY += 2;
		maxZ += 2;

		int cx = BlockPos.ofFloored(camera).getX();
		int cy = BlockPos.ofFloored(camera).getY();
		int cz = BlockPos.ofFloored(camera).getZ();
		int r = (int) Math.ceil(maxReach) + 2;
		minX = Math.max(minX, cx - r);
		maxX = Math.min(maxX, cx + r);
		minY = Math.max(minY, cy - r);
		maxY = Math.min(maxY, cy + r);
		minZ = Math.max(minZ, cz - r);
		maxZ = Math.min(maxZ, cz + r);

		if (minX > maxX || minY > maxY || minZ > maxZ) {
			mgr.setSelectionFeedback("套索：搜索范围无效。");
			return null;
		}

		long vol = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
		if (vol > maxB) {
			mgr.setSelectionFeedback(String.format("套索：搜索体积 %d 超过方块上限 %d，请缩小套索或提高上限。", vol, maxB));
			return null;
		}

		List<BlockPos> out = new ArrayList<>();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!withinReachSq(p, camera, maxReachSq)) continue;
					if (!mgr.isIncludeAir() && world.getBlockState(p).isAir()) continue;
					var screen = BeatBlockWorldToFramebuffer.projectWorldPoint(client, Vec3d.ofCenter(p));
					if (screen.isEmpty()) continue;
					double sx = screen.get()[0];
					double sy = screen.get()[1];
					if (!pointInPolygon(sx, sy, poly)) continue;
					out.add(p.toImmutable());
					if (out.size() > maxB) {
						mgr.setSelectionFeedback(String.format("套索：选中方块数超过上限 %d。", maxB));
						return null;
					}
				}
			}
		}
		return out;
	}

	private static boolean withinReachSq(BlockPos p, Vec3d camera, double maxReachSq) {
		return camera.squaredDistanceTo(Vec3d.ofCenter(p)) <= maxReachSq;
	}

	/**
	 * 射线法；多边形按顺序闭合（首尾由边连接）。
	 */
	public static boolean pointInPolygon(double x, double y, List<double[]> poly) {
		boolean inside = false;
		int n = poly.size();
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double xi = poly.get(i)[0], yi = poly.get(i)[1];
			double xj = poly.get(j)[0], yj = poly.get(j)[1];
			boolean intersect = ((yi > y) != (yj > y))
					&& (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi);
			if (intersect) inside = !inside;
		}
		return inside;
	}

	public static boolean readShiftDown(long window) {
		return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
	}
}
