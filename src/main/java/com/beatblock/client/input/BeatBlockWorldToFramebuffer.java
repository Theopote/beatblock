package com.beatblock.client.input;

import com.beatblock.mixin.client.GameRendererAccessor;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 世界坐标 → 帧缓冲像素（与 {@link BeatBlockInputSystem} 的投影一致），用于套索点内判断。
 */
public final class BeatBlockWorldToFramebuffer {

	private BeatBlockWorldToFramebuffer() {}

	/**
	 * @return 若在相机前方则返回 [x,y] 帧缓冲像素；否则 empty（在身后或退化）。
	 */
	public static Optional<double[]> projectWorldPoint(MinecraftClient client, Vec3d world) {
		if (client == null || client.world == null || client.gameRenderer == null || client.player == null) {
			return Optional.empty();
		}
		Camera camera = client.gameRenderer.getCamera();
		if (camera == null) {
			return Optional.empty();
		}
		int fbw = client.getWindow().getFramebufferWidth();
		int fbh = client.getWindow().getFramebufferHeight();
		if (fbw <= 0 || fbh <= 0) {
			return Optional.empty();
		}

		Vec3d camPos = camera.getCameraPos();
		Vec3d rel = world.subtract(camPos);
		Quaternionf q = new Quaternionf(camera.getRotation());
		Vector3f view = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
		q.conjugate().transform(view);

		GameRenderer gr = client.gameRenderer;
		float tickDelta = BeatBlockInputSystem.getRenderTickDeltaSafe();
		float fov;
		if (gr instanceof GameRendererAccessor accessor) {
			fov = accessor.beatblock$invokeGetFov(camera, tickDelta, true);
		} else {
			fov = (float) client.options.getFov().getValue();
		}
		Matrix4f proj = new Matrix4f(gr.getBasicProjectionMatrix(fov));
		Vector4f clip = new Vector4f(view.x, view.y, view.z, 1.0f);
		proj.transform(clip);
		if (clip.w <= 1e-4f) {
			return Optional.empty();
		}
		double ndcX = clip.x / clip.w;
		double ndcY = clip.y / clip.w;
		double sx = (ndcX * 0.5 + 0.5) * fbw;
		double sy = (1.0 - (ndcY * 0.5 + 0.5)) * fbh;
		return Optional.of(new double[] { sx, sy });
	}
}
