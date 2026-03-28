package com.beatblock.client.input;

import com.beatblock.mixin.client.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * ImGui 鼠标位置 → 世界射线（与 ChronoBlocks 的 ChronoBlocksInputSystem 同源思路：投影矩阵反投影）。
 */
public final class BeatBlockInputSystem {

	private static final MinecraftClient MC = MinecraftClient.getInstance();
	/**
	 * 与 ChronoBlocks {@code ChronoBlocksInputSystem} 一致：光标射线长度，远大于原版手臂距离，
	 * 便于在打开 UI 时从任意屏幕位置指向远处方块（高亮、选点、左键拾取等）。
	 */
	public static final double MAX_RAYCAST_DISTANCE = 256.0;
	private static final double CACHE_POSITION_EPSILON = 0.001;

	private static Vec3d lastCameraPos;
	private static double lastMouseX = Double.NaN;
	private static double lastMouseY = Double.NaN;
	private static BlockHitResult cachedBlockRay;

	private BeatBlockInputSystem() {}

	public static BlockHitResult raycastFromImGui() {
		if (MC.world == null || MC.player == null || MC.getCameraEntity() == null) {
			return null;
		}

		Camera camera = MC.gameRenderer.getCamera();
		if (camera == null) {
			return null;
		}

		if (MC.isPaused() && cachedBlockRay != null) {
			return cachedBlockRay;
		}

		double mouseLogicalX = MC.mouse.getX();
		double mouseLogicalY = MC.mouse.getY();
		int windowW = MC.getWindow().getWidth();
		int windowH = MC.getWindow().getHeight();
		int fbw = MC.getWindow().getFramebufferWidth();
		int fbh = MC.getWindow().getFramebufferHeight();
		if (windowW <= 0 || windowH <= 0 || fbw <= 0 || fbh <= 0) {
			return null;
		}
		double mouseX = mouseLogicalX * fbw / (double) windowW;
		double mouseY = mouseLogicalY * fbh / (double) windowH;

		Vec3d cameraPos = camera.getCameraPos();
		if (cachedBlockRay != null
			&& lastCameraPos != null
			&& cameraPos.distanceTo(lastCameraPos) < CACHE_POSITION_EPSILON
			&& Math.abs(mouseX - lastMouseX) < 0.1
			&& Math.abs(mouseY - lastMouseY) < 0.1) {
			return cachedBlockRay;
		}

		double ndcX = (mouseX / (double) fbw) * 2.0 - 1.0;
		double ndcY = 1.0 - (mouseY / (double) fbh) * 2.0;

		BlockHitResult result = raycastFromNdc(camera, cameraPos, ndcX, ndcY);

		lastCameraPos = cameraPos;
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		cachedBlockRay = result;
		return result;
	}

	/**
	 * 指定帧缓冲像素坐标（与 {@link #raycastFromImGui()} 相同坐标系）做方块射线，不读写 ImGui 射线缓存。
	 */
	public static BlockHitResult raycastFromFramebufferPixels(double framebufferMouseX, double framebufferMouseY) {
		if (MC.world == null || MC.player == null || MC.getCameraEntity() == null) {
			return null;
		}
		Camera camera = MC.gameRenderer.getCamera();
		if (camera == null) {
			return null;
		}
		int fbw = MC.getWindow().getFramebufferWidth();
		int fbh = MC.getWindow().getFramebufferHeight();
		if (fbw <= 0 || fbh <= 0) {
			return null;
		}
		double ndcX = (framebufferMouseX / (double) fbw) * 2.0 - 1.0;
		double ndcY = 1.0 - (framebufferMouseY / (double) fbh) * 2.0;
		return raycastFromNdc(camera, camera.getCameraPos(), ndcX, ndcY);
	}

	private static BlockHitResult raycastFromNdc(Camera camera, Vec3d cameraPos, double ndcX, double ndcY) {
		GameRenderer gr = MC.gameRenderer;
		float tickDelta = getRenderTickDeltaSafe();
		float fov;
		if (gr instanceof GameRendererAccessor accessor) {
			fov = accessor.beatblock$invokeGetFov(camera, tickDelta, true);
		} else {
			fov = (float) MC.options.getFov().getValue();
		}
		Matrix4f projMatrix = new Matrix4f(gr.getBasicProjectionMatrix(fov));
		Matrix4f invProj = new Matrix4f(projMatrix).invert();

		Vector4f nearClip = new Vector4f((float) ndcX, (float) ndcY, -1.0f, 1.0f).mul(invProj);
		Vector4f farClip = new Vector4f((float) ndcX, (float) ndcY, 1.0f, 1.0f).mul(invProj);

		if (nearClip.w != 0.0f) nearClip.div(nearClip.w);
		if (farClip.w != 0.0f) farClip.div(farClip.w);

		Vec3d viewDir = new Vec3d(farClip.x - nearClip.x, farClip.y - nearClip.y, farClip.z - nearClip.z).normalize();
		var q = camera.getRotation();
		Vec3d rayDir = rotateByQuaternion(viewDir, q.x, q.y, q.z, q.w);

		Vec3d endPos = cameraPos.add(rayDir.multiply(MAX_RAYCAST_DISTANCE));
		return MC.world.raycast(new RaycastContext(
			cameraPos,
			endPos,
			RaycastContext.ShapeType.OUTLINE,
			RaycastContext.FluidHandling.NONE,
			MC.player
		));
	}

	/**
	 * 与打开 BeatBlock UI 时的交互一致：沿光标射线先判定方块（距离上限同 {@link #MAX_RAYCAST_DISTANCE}，与 ChronoBlocks 一致），否则检测实体（仍用原版实体交互距离）。
	 */
	public static HitResult pickTargetFromImGui() {
		if (MC.world == null || MC.player == null || MC.getCameraEntity() == null) {
			return null;
		}
		Camera camera = MC.gameRenderer.getCamera();
		if (camera == null) {
			return null;
		}

		BlockHitResult blockHit = raycastFromImGui();
		Vec3d camPos = camera.getCameraPos();
		double blockReachSq = MAX_RAYCAST_DISTANCE * MAX_RAYCAST_DISTANCE;
		double entityReach = MC.player.getEntityInteractionRange();

		if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
			if (camPos.squaredDistanceTo(blockHit.getPos()) <= blockReachSq) {
				return blockHit;
			}
		}

		Vec3d rayDir;
		{
			double mouseLogicalX = MC.mouse.getX();
			double mouseLogicalY = MC.mouse.getY();
			int windowW = MC.getWindow().getWidth();
			int windowH = MC.getWindow().getHeight();
			int fbw = MC.getWindow().getFramebufferWidth();
			int fbh = MC.getWindow().getFramebufferHeight();
			if (windowW <= 0 || windowH <= 0 || fbw <= 0 || fbh <= 0) {
				return blockHit;
			}
			double mouseX = mouseLogicalX * fbw / (double) windowW;
			double mouseY = mouseLogicalY * fbh / (double) windowH;
			double ndcX = (mouseX / (double) fbw) * 2.0 - 1.0;
			double ndcY = 1.0 - (mouseY / (double) fbh) * 2.0;
			GameRenderer gr = MC.gameRenderer;
			float tickDelta = getRenderTickDeltaSafe();
			float fov;
			if (gr instanceof GameRendererAccessor accessor) {
				fov = accessor.beatblock$invokeGetFov(camera, tickDelta, true);
			} else {
				fov = (float) MC.options.getFov().getValue();
			}
			Matrix4f projMatrix = new Matrix4f(gr.getBasicProjectionMatrix(fov));
			Matrix4f invProj = new Matrix4f(projMatrix).invert();
			Vector4f nearClip = new Vector4f((float) ndcX, (float) ndcY, -1.0f, 1.0f).mul(invProj);
			Vector4f farClip = new Vector4f((float) ndcX, (float) ndcY, 1.0f, 1.0f).mul(invProj);
			if (nearClip.w != 0.0f) nearClip.div(nearClip.w);
			if (farClip.w != 0.0f) farClip.div(farClip.w);
			Vec3d viewDir = new Vec3d(farClip.x - nearClip.x, farClip.y - nearClip.y, farClip.z - nearClip.z).normalize();
			var q = camera.getRotation();
			rayDir = rotateByQuaternion(viewDir, q.x, q.y, q.z, q.w);
		}

		Vec3d end = camPos.add(rayDir.multiply(entityReach));
		Box box = MC.player.getBoundingBox().stretch(rayDir.multiply(entityReach)).expand(1.0);
		EntityHitResult entityHit = ProjectileUtil.raycast(
			MC.player,
			camPos,
			end,
			box,
			(Entity e) -> !e.isSpectator() && e.canHit(),
			entityReach * entityReach
		);
		if (entityHit != null) {
			return entityHit;
		}
		if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
			&& camPos.squaredDistanceTo(blockHit.getPos()) <= blockReachSq) {
			return blockHit;
		}
		return null;
	}

	public static void clearCache() {
		cachedBlockRay = null;
		lastCameraPos = null;
		lastMouseX = Double.NaN;
		lastMouseY = Double.NaN;
	}

	private static Vec3d rotateByQuaternion(Vec3d viewDir, float qx, float qy, float qz, float qw) {
		float x = (float) viewDir.x;
		float y = (float) viewDir.y;
		float z = (float) viewDir.z;
		float cx1 = qy * z - qz * y;
		float cy1 = qz * x - qx * z;
		float cz1 = qx * y - qy * x;
		float rx = x + 2.0f * (qy * cz1 - qz * cy1 + qw * cx1);
		float ry = y + 2.0f * (qz * cx1 - qx * cz1 + qw * cy1);
		float rz = z + 2.0f * (qx * cy1 - qy * cx1 + qw * cz1);
		return new Vec3d(rx, ry, rz).normalize();
	}

	/** 供 {@link BeatBlockWorldToFramebuffer} 等与渲染 tick 对齐的投影使用。 */
	public static float getRenderTickDeltaSafe() {
		try {
			Object rtc = MC.getRenderTickCounter();
			if (rtc == null) return 0.0f;
			try {
				var m = rtc.getClass().getMethod("getTickProgress", boolean.class);
				Object v = m.invoke(rtc, false);
				if (v instanceof Float f) return f;
			} catch (ReflectiveOperationException ignored) {}
			try {
				var m = rtc.getClass().getMethod("getDynamicDeltaTicks");
				Object v = m.invoke(rtc);
				if (v instanceof Float f) return f;
			} catch (ReflectiveOperationException ignored) {}
			try {
				var m = rtc.getClass().getMethod("getFixedDeltaTicks");
				Object v = m.invoke(rtc);
				if (v instanceof Float f) return f;
			} catch (ReflectiveOperationException ignored) {}
		} catch (Throwable ignored) {}
		return 0.0f;
	}
}
