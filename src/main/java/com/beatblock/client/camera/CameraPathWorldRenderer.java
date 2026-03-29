package com.beatblock.client.camera;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 在 Minecraft 世界中绘制摄像机运动轨迹（与轨道 UI 分离）。
 * 按片段 {@link CameraPathMetadata} 的「显示路径」开关决定是否绘制。
 */
public final class CameraPathWorldRenderer {

	private static final int COLOR_PATH = 0xCC_FF_CC_66;
	private static final int COLOR_DOLLY = 0xCC_66_CC_FF;
	private static final int COLOR_ORBIT = 0xCC_DD_77_FF;
	private static final int COLOR_CRANE = 0xCC_77_DD_88;
	private static final int COLOR_KEYFRAME = 0xFF_FF_AA_33;
	private static final float LINE_WIDTH = 2.0f;
	private static final float KEY_CROSS = 0.18f;
	private static final int SAMPLE_STEPS = 56;

	private CameraPathWorldRenderer() {}

	public static void renderIfNeeded(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) return;
		Timeline timeline = BeatBlock.timeline;
		if (timeline == null) return;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null || cam.getClips().isEmpty()) return;

		Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
		matrices.push();
		Matrix4f mat = matrices.peek().getPositionMatrix();

		for (Clip clip : cam.getClips()) {
			if (clip == null) continue;
			if (!CameraPathMetadata.isPathVisible(timeline, clip.getId())) continue;
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(clip);
			if (seg == null) continue;
			CameraSegmentKind kind = CameraSegmentKind.fromParam(seg.getParameters().get("kind"));
			if (kind == CameraSegmentKind.SHAKE) continue;

			int lineArgb = switch (kind) {
				case PATH -> COLOR_PATH;
				case DOLLY -> COLOR_DOLLY;
				case ORBIT -> COLOR_ORBIT;
				case CRANE -> COLOR_CRANE;
				default -> COLOR_PATH;
			};

			double t0 = clip.getStartTimeSeconds();
			double t1 = clip.getEndTimeSeconds();
			if (t1 <= t0) continue;

			Vec3d prev = null;
			VertexConsumer buf = consumers.getBuffer(RenderLayers.LINES);
			for (int i = 0; i <= SAMPLE_STEPS; i++) {
				double u = i / (double) SAMPLE_STEPS;
				double t = t0 + (t1 - t0) * u;
				TimelineCameraEvaluator.CameraSample sm = TimelineCameraEvaluator.evaluate(timeline, t, Vec3d.ZERO, 0f, 0f);
				if (sm == null) continue;
				Vec3d p = sm.position();
				if (prev != null) {
					emitLine(buf, mat, camPos, prev, p, lineArgb);
				}
				prev = p;
			}

			if (kind == CameraSegmentKind.PATH) {
				List<TimelineEvent> kf = new ArrayList<>();
				for (TimelineEvent e : clip.getEvents()) {
					if (e.getType() == EventType.CAMERA_KEYFRAME) kf.add(e);
				}
				kf.sort(Comparator.comparingDouble(TimelineEvent::getTimeSeconds));
				VertexConsumer kbuf = consumers.getBuffer(RenderLayers.LINES);
				for (TimelineEvent e : kf) {
					Vec3d kp = keyframePos(e);
					drawCross(kbuf, mat, camPos, kp, KEY_CROSS, COLOR_KEYFRAME);
				}
			}
		}

		matrices.pop();
	}

	private static Vec3d keyframePos(TimelineEvent e) {
		Map<String, Object> p = e.getParameters();
		return new Vec3d(num(p, "x", 0), num(p, "y", 0), num(p, "z", 0));
	}

	private static double num(Map<String, Object> p, String key, double def) {
		if (p == null) return def;
		Object o = p.get(key);
		if (o instanceof Number n) return n.doubleValue();
		if (o != null) {
			try {
				return Double.parseDouble(String.valueOf(o).trim());
			} catch (Exception ignored) {
				return def;
			}
		}
		return def;
	}

	private static void emitLine(VertexConsumer buf, Matrix4f mat, Vec3d cam, Vec3d a, Vec3d b, int argb) {
		Vec3d ra = a.subtract(cam);
		Vec3d rb = b.subtract(cam);
		emitLineSegment(buf, mat, ra.x, ra.y, ra.z, rb.x, rb.y, rb.z, argb, LINE_WIDTH);
	}

	private static void drawCross(VertexConsumer buf, Matrix4f mat, Vec3d cam, Vec3d center, double h, int argb) {
		Vec3d c = center.subtract(cam);
		emitLineSegment(buf, mat, c.x - h, c.y, c.z, c.x + h, c.y, c.z, argb, LINE_WIDTH);
		emitLineSegment(buf, mat, c.x, c.y - h, c.z, c.x, c.y + h, c.z, argb, LINE_WIDTH);
		emitLineSegment(buf, mat, c.x, c.y, c.z - h, c.x, c.y, c.z + h, argb, LINE_WIDTH);
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
