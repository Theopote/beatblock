package com.beatblock.timeline.rendering;

import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.*;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 绘制时间线事件：动画块、关键帧、全局事件、特征轨柱状点。
 * 所有渲染方法假定传入列表已按 timeSeconds 升序，使用二分查找做视口裁剪，
 * 仅绘制 [viewStart, viewEnd] 范围内的事件，避免 O(n) 全量遍历。
 */
public final class EventRenderer {

	private static final int EVENT_DOT_COLOR    = 0xFF_AA_CC_FF;
	private static final int KEYFRAME_COLOR     = 0xFF_FF_CC_66;
	private static final int ACTION_PLACE_COLOR = 0xFF_57_C4_A0;
	private static final int ACTION_CLEAR_COLOR = 0xFF_66_66_FF;
	private static final int ACTION_BUILD_COLOR = 0xFF_FF_99_33;
	private static final int GLOBAL_EVENT_COLOR = 0xFF_AA_FF_AA;
	private static final int CAMERA_PATH_COLOR = 0xCC_FF_CC_66;
	private static final int CAMERA_DOLLY_COLOR = 0xCC_66_CC_FF;
	private static final int CAMERA_ORBIT_COLOR = 0xCC_DD_77_FF;
	private static final int CAMERA_CRANE_COLOR = 0xCC_77_DD_88;
	private static final int CAMERA_SHAKE_COLOR = 0xCC_FF_77_77;
	private static final int CAMERA_EDGE_HANDLE_COLOR = 0xEE_FF_FF_FF;
	private static final int SELECTED_BORDER_COLOR = 0xFF_FF_FF_00;
	private static final int STATUS_APPLIED_COLOR = 0xFF_57_C4_A0;
	private static final int STATUS_SKIPPED_COLOR = 0xFF_44_AA_FF;
	private static final int STATUS_ANIMATE_COLOR = 0xFF_FF_CC_66;
	private static final int STATUS_UNKNOWN_COLOR = 0xFF_99_99_99;
	private static final int INHERIT_GROUP_BADGE_COLOR = 0xFF_FF_DD_66;
	private static final int DISPATCH_STEP_BADGE_COLOR = 0xFF_66_C2_FF;
	private static final int DISPATCH_BURST_BADGE_COLOR = 0xFF_CC_AA_FF;
	private static final int FRUSTUM_GATING_BADGE_COLOR = 0xFF_FF_66_66;
	private static final int EDGE_PRIORITY_BADGE_COLOR = 0xFF_FF_AA_33;
	private static final float MIN_BAR_HALF_WIDTH = 1.25f;

	private static int withAlpha(int abgr, int alpha) {
		return (abgr & 0x00FF_FFFF) | ((alpha & 0xFF) << 24);
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	/**
	 * 在时间排序列表中返回第一个满足 key(item) >= target 的索引（若全部小于则返回 size）。
	 */
	private static <E> int lowerBound(List<E> list, ToDoubleFunction<E> key, double target) {
		int lo = 0, hi = list.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (key.applyAsDouble(list.get(mid)) < target) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	/**
	 * Feature-track bar rendering (kick/snare/hihat or any named track).
	 */
	public void renderFeatureBars(
		float rowY,
		float rowHeight,
		List<FeatureEvent> events,
		TimelineLayout layout,
		TimelineViewState view,
		int color,
		double bpm,
		float widthRatio,
		float dotScale
	) {
		if (view == null || layout == null || events == null || events.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY();
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();

		float pxPerSecond = view.timeToScreen(1.0) - view.timeToScreen(0.0);
		double beatDur = bpm > 1e-6 ? (60.0 / bpm) : 0.5;
		float beatWidthPx = (float) (pxPerSecond * beatDur);
		float barHalfW = Math.max(MIN_BAR_HALF_WIDTH, beatWidthPx * Math.max(0.08f, widthRatio) * 0.5f);

		float bottomY = baseY + rowHeight - 2f;
		float maxBarH = Math.max(6f, rowHeight - 6f);
		int barColor = withAlpha(color, 0x66);
		int fillColor = withAlpha(color, 0x24);

		int start = lowerBound(events, FeatureEvent::getTimeSeconds, vs);
		for (int i = start; i < events.size(); i++) {
			FeatureEvent e = events.get(i);
			double t = e.getTimeSeconds();
			if (t > ve) break;
			float x = baseX + view.timeToScreen(t);
			float energy = clamp01(e.getEnergy());
			float barH = 3f + energy * (maxBarH - 3f);
			float y0 = bottomY - barH;

			ImGui.getWindowDrawList().addRectFilled(x - barHalfW, y0, x + barHalfW, bottomY, barColor, 1.5f);
			ImGui.getWindowDrawList().addRectFilled(x - barHalfW, bottomY - maxBarH, x + barHalfW, bottomY, fillColor, 1f);

			float dotR = Math.max(1.2f, (1.2f + energy * 2.8f) * Math.max(0.7f, dotScale));
			ImGui.getWindowDrawList().addCircleFilled(x, bottomY, dotR, color);
		}

		ImGui.setCursorPosY(rowY + rowHeight);
	}

	public void renderAnimationEventBlocks(float rowY, List<TimelineAnimationEvent> events, TimelineLayout layout, TimelineViewState view, SelectionState selection) {
		renderAnimationEventBlocks(rowY, events, layout, view, selection, KEYFRAME_COLOR);
	}

	public void renderAnimationEventBlocks(float rowY, List<TimelineAnimationEvent> events, TimelineLayout layout, TimelineViewState view, SelectionState selection, int fillColor) {
		if (view == null || layout == null || events.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		// 动画块有时长：起点 < vs 的块可能仍与视口重叠，不能跳过，下界从 0 开始；
		// 起点 > ve 的块之后全部不可见（列表有序），可以 break 提前退出。
        for (TimelineAnimationEvent e : events) {
            double t = e.getTimeSeconds();
            if (t > ve) break;
            double end = e.getEndTimeSeconds();
            if (end < vs) continue;
            float x = view.timeToScreen(t);
            float w = (float) (e.getDurationSeconds() * view.getZoom());
            w = Math.max(8f, Math.min(w, layout.timelineWidth - x + 1));
            float y0 = baseY - layout.rowHeight * 0.35f;
            float y1 = baseY + layout.rowHeight * 0.35f;
            int resolvedFillColor = switch (e.getActionMode()) {
                case PLACE -> ACTION_PLACE_COLOR;
                case CLEAR -> ACTION_CLEAR_COLOR;
                case BUILD -> ACTION_BUILD_COLOR;
                case ANIMATE -> fillColor;
            };
            ImGui.getWindowDrawList().addRectFilled(baseX + x, y0, baseX + x + w, y1, resolvedFillColor, 2f);
			renderDispatchBadge(baseX + x, y0, baseX + x + w, y1, e);
			renderGroupSpatialBadge(baseX + x, y0, baseX + x + w, y1, e);
			renderFrustumGatingBadge(baseX + x, y0, baseX + x + w, y1, e);
			renderEdgePriorityBadge(baseX + x, y0, baseX + x + w, y1, e);
            BeatBlockClientDriver.TimelineActionExecutionReport report = BeatBlockClientDriver.getTimelineActionExecutionReport(e.getEventId());
            renderRuntimeBadge(baseX + x, y0, baseX + x + w, y1, e, report);
            if (selection != null && selection.isEventSelected(e.getEventId())) {
                ImGui.getWindowDrawList().addRect(baseX + x, y0, baseX + x + w, y1, SELECTED_BORDER_COLOR, 0f, 0, 2f);
            }
        }
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	private void renderDispatchBadge(float x0, float y0, float x1, float y1, TimelineAnimationEvent event) {
		if (event == null) return;
		String model = String.valueOf(event.getParameters().getOrDefault("dispatchModel", "BURST"));
		boolean step = "STEP".equalsIgnoreCase(model);
		int color = step ? DISPATCH_STEP_BADGE_COLOR : DISPATCH_BURST_BADGE_COLOR;
		String label = step ? "S" : "B";

		float bx1 = x1 - 2f;
		float by0 = y0 + 2f;
		float bx0 = Math.max(x0 + 2f, bx1 - 10f);
		float by1 = Math.min(y1 - 2f, by0 + 10f);
		if (bx1 <= bx0 || by1 <= by0) return;

		ImGui.getWindowDrawList().addRectFilled(bx0, by0, bx1, by1, withAlpha(color, 0xD8), 2f);
		ImGui.getWindowDrawList().addText(bx0 + 2f, by0 - 1f, 0xFF_11_11_11, label);

		if (ImGui.isMouseHoveringRect(bx0, by0, bx1, by1)) {
			if (step) {
				ImGui.setTooltip("Dispatch: STEP\nAdvances sequence on each beat event.");
			} else {
				ImGui.setTooltip("Dispatch: BURST\nTriggers the group animation immediately at event time.");
			}
		}
	}

	private void renderGroupSpatialBadge(float x0, float y0, float x1, float y1, TimelineAnimationEvent event) {
		if (event == null) return;
		if (!readBoolean(event.getParameters().get("inheritGroupSpatial"), true)) return;

		float bx0 = x0 + 2f;
		float by0 = y0 + 2f;
		float bx1 = Math.min(x1 - 2f, bx0 + 10f);
		float by1 = Math.min(y1 - 2f, by0 + 10f);
		if (bx1 <= bx0 || by1 <= by0) return;

		ImGui.getWindowDrawList().addRectFilled(bx0, by0, bx1, by1, withAlpha(INHERIT_GROUP_BADGE_COLOR, 0xD8), 2f);
		ImGui.getWindowDrawList().addText(bx0 + 2f, by0 - 1f, 0xFF_11_11_11, "G");

		if (ImGui.isMouseHoveringRect(bx0, by0, bx1, by1)) {
			ImGui.setTooltip("Inherit group spatial: ON\nspatialMode/sequentialDelaySeconds are resolved from target group when not overridden.");
		}
	}

	private void renderFrustumGatingBadge(float x0, float y0, float x1, float y1, TimelineAnimationEvent event) {
		if (event == null) return;
		// Only show this badge for STEP mode with frustum gating enabled
		boolean step = "STEP".equalsIgnoreCase(String.valueOf(event.getParameters().getOrDefault("dispatchModel", "BURST")));
		if (!step) return;
		if (!readBoolean(event.getParameters().get("cameraFrustumGating"), false)) return;

		// Position at bottom-right
		float bx1 = x1 - 2f;
		float by1 = y1 - 2f;
		float bx0 = Math.max(x0 + 2f, bx1 - 10f);
		float by0 = Math.max(y0 + 2f, by1 - 10f);
		if (bx1 <= bx0 || by1 <= by0) return;

		ImGui.getWindowDrawList().addRectFilled(bx0, by0, bx1, by1, withAlpha(FRUSTUM_GATING_BADGE_COLOR, 0xD8), 2f);
		ImGui.getWindowDrawList().addText(bx0 + 2f, by0 - 1f, 0xFF_11_11_11, "V");

		if (ImGui.isMouseHoveringRect(bx0, by0, bx1, by1)) {
			ImGui.setTooltip("Frustum Gating: ON\nPauses STEP progression when target group is outside camera view.");
		}
	}

	private void renderEdgePriorityBadge(float x0, float y0, float x1, float y1, TimelineAnimationEvent event) {
		if (event == null) return;
		// Only show for STEP mode with edge priority > 0
		boolean step = "STEP".equalsIgnoreCase(String.valueOf(event.getParameters().getOrDefault("dispatchModel", "BURST")));
		if (!step) return;
		double edgePriority = readDouble(event.getParameters().get("cameraEdgePriority"), 0.0);
		if (edgePriority <= 0.0) return;

		// Position at right edge, below the dispatch badge
		float bx1 = x1 - 2f;
		float by0 = y0 + 14f; // Below the dispatch badge
		float bx0 = Math.max(x0 + 2f, bx1 - 10f);
		float by1 = Math.min(y1 - 2f, by0 + 10f);
		if (bx1 <= bx0 || by1 <= by0) return;

		ImGui.getWindowDrawList().addRectFilled(bx0, by0, bx1, by1, withAlpha(EDGE_PRIORITY_BADGE_COLOR, 0xD8), 2f);
		ImGui.getWindowDrawList().addText(bx0 + 2f, by0 - 1f, 0xFF_11_11_11, "E");

		if (ImGui.isMouseHoveringRect(bx0, by0, bx1, by1)) {
			ImGui.setTooltip(String.format("Edge Priority: %.0f%%%nPrioritizes silhouette blocks early in progression.", edgePriority * 100.0));
		}
	}

	private static boolean readBoolean(Object raw, boolean fallback) {
		if (raw instanceof Boolean b) return b;
		if (raw instanceof Number n) return n.intValue() != 0;
		if (raw == null) return fallback;
		String s = String.valueOf(raw).trim();
		if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return true;
		if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return false;
		return fallback;
	}

	private static double readDouble(Object raw, double fallback) {
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}

	private void renderRuntimeBadge(float x0, float y0, float x1, float y1,
	                              TimelineAnimationEvent event,
	                              BeatBlockClientDriver.TimelineActionExecutionReport report) {
		if (event == null || report == null) return;
		String eventId = event.getEventId();
		if (eventId.isBlank() || !eventId.equals(report.eventId())) return;

		int badgeColor = switch (report.status()) {
			case "APPLIED" -> STATUS_APPLIED_COLOR;
			case "SKIPPED" -> STATUS_SKIPPED_COLOR;
			case "ANIMATE" -> STATUS_ANIMATE_COLOR;
			default -> STATUS_UNKNOWN_COLOR;
		};

		float cx = x1 - 5f;
		float cy = y0 + 5f;
		ImGui.getWindowDrawList().addCircleFilled(cx, cy, 3f, badgeColor);

		if (ImGui.isMouseHoveringRect(Math.max(x0, x1 - 12f), y0, x1, Math.min(y1, y0 + 12f))) {
			long ageMs = Math.max(0L, System.currentTimeMillis() - report.timestampMs());
			String detail = report.detail() != null ? report.detail() : "";
			ImGui.setTooltip(String.format("%s | mutations=%d | %dms ago%s%s",
				report.status(),
				report.mutationCount(),
				ageMs,
				detail.isBlank() ? "" : " | ",
				detail));
		}
	}

	public void renderCameraKeyframeRow(float rowY, List<CameraKeyframe> keyframes, TimelineLayout layout, TimelineViewState view) {
		if (view == null || layout == null || keyframes.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		int start = lowerBound(keyframes, CameraKeyframe::getTimeSeconds, vs);
		for (int i = start; i < keyframes.size(); i++) {
			CameraKeyframe k = keyframes.get(i);
			double t = k.getTimeSeconds();
			if (t > ve) break;
			float x = view.timeToScreen(t);
			ImGui.getWindowDrawList().addTriangleFilled(
				baseX + x, baseY - 6,
				baseX + x - 5, baseY + 5,
				baseX + x + 5, baseY + 5,
				KEYFRAME_COLOR
			);
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	/**
	 * 摄像机轨：镜头片段条、左右边缘把手、按片段元数据显示路径关键帧与折线。
	 */
	public void renderCameraTrackRow(
		float rowY,
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState view,
		SelectionState selection
	) {
		if (view == null || layout == null || timeline == null) return;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		var dl = ImGui.getWindowDrawList();

		for (Clip clip : cam.getClips()) {
			if (clip == null) continue;
			double cs = clip.getStartTimeSeconds();
			double ce = clip.getEndTimeSeconds();
			if (ce < vs || cs > ve) continue;
			TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(clip);
			if (seg != null) {
				CameraSegmentKind kind = CameraSegmentKind.fromParam(seg.getParameters().get("kind"));
				int fill = switch (kind) {
					case PATH -> CAMERA_PATH_COLOR;
					case DOLLY -> CAMERA_DOLLY_COLOR;
					case ORBIT -> CAMERA_ORBIT_COLOR;
					case CRANE -> CAMERA_CRANE_COLOR;
					case SHAKE -> CAMERA_SHAKE_COLOR;
				};
				float x0 = baseX + view.timeToScreen(cs);
				float x1 = baseX + view.timeToScreen(ce);
				x1 = Math.max(x0 + 6f, x1);
				float y0 = baseY - layout.rowHeight * 0.35f;
				float y1 = baseY + layout.rowHeight * 0.35f;
				dl.addRectFilled(x0, y0, x1, y1, fill, 2f);
				dl.addRectFilled(x0 - 1f, y0 + 2f, x0 + 1f, y1 - 2f, CAMERA_EDGE_HANDLE_COLOR);
				dl.addRectFilled(x1 - 1f, y0 + 2f, x1 + 1f, y1 - 2f, CAMERA_EDGE_HANDLE_COLOR);
				if (selection != null && selection.isClipSelected(clip.getId())) {
					dl.addRect(x0, y0, x1, y1, SELECTED_BORDER_COLOR, 0f, 0, 2f);
				}
				if (selection != null && selection.isEventSelected(seg.getId())) {
					dl.addRect(x0, y0, x1, y1, SELECTED_BORDER_COLOR, 0f, 0, 2f);
				}

				boolean pathVis = CameraPathMetadata.isPathVisible(timeline, clip.getId());
				// 运动轨迹在世界中绘制（CameraPathWorldRenderer）；轨道上仅保留关键帧时间标记
				if (pathVis && kind == CameraSegmentKind.PATH) {
					List<TimelineEvent> kf = new ArrayList<>();
					for (TimelineEvent e : clip.getEvents()) {
						if (e.getType() == EventType.CAMERA_KEYFRAME) kf.add(e);
					}
					kf.sort(Comparator.comparingDouble(TimelineEvent::getTimeSeconds));
					for (TimelineEvent e : kf) {
						double t = e.getTimeSeconds();
						if (t < vs || t > ve) continue;
						float x = baseX + view.timeToScreen(t);
						dl.addTriangleFilled(
							x, baseY - 6,
							x - 5, baseY + 5,
							x + 5, baseY + 5,
							KEYFRAME_COLOR
						);
						if (selection != null && selection.isEventSelected(e.getId())) {
							dl.addRect(x - 6, baseY - 7, x + 6, baseY + 6, SELECTED_BORDER_COLOR, 0f, 0, 1.5f);
						}
					}
				}
			}
		}

		for (Clip clip : cam.getClips()) {
			if (clip == null) continue;
			if (CameraTrackFactory.findSegmentHeadEvent(clip) != null) continue;
			if (!CameraPathMetadata.isPathVisible(timeline, clip.getId())) continue;
			for (TimelineEvent e : clip.getEvents()) {
				if (e.getType() != EventType.CAMERA_KEYFRAME) continue;
				double t = e.getTimeSeconds();
				if (t < vs || t > ve) continue;
				float x = baseX + view.timeToScreen(t);
				dl.addTriangleFilled(
					x, baseY - 6,
					x - 5, baseY + 5,
					x + 5, baseY + 5,
					KEYFRAME_COLOR
				);
				if (selection != null && selection.isEventSelected(e.getId())) {
					dl.addRect(x - 6, baseY - 7, x + 6, baseY + 6, SELECTED_BORDER_COLOR, 0f, 0, 1.5f);
				}
			}
		}

		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}

	public void renderGlobalEventRow(float rowY, List<GlobalEvent> events, TimelineLayout layout, TimelineViewState view) {
		if (view == null || layout == null || events.isEmpty()) return;
		ImGui.setCursorPosY(rowY);
		float baseX = layout.contentLeft;
		float baseY = ImGui.getCursorScreenPosY() + layout.rowHeight * 0.5f;
		double vs = view.getViewStartTimeSeconds();
		double ve = view.getViewEndTimeSeconds();
		int start = lowerBound(events, GlobalEvent::getTimeSeconds, vs);
		for (int i = start; i < events.size(); i++) {
			GlobalEvent e = events.get(i);
			double t = e.getTimeSeconds();
			if (t > ve) break;
			ImGui.getWindowDrawList().addCircleFilled(baseX + view.timeToScreen(t), baseY, 5f, GLOBAL_EVENT_COLOR);
		}
		ImGui.setCursorPosY(rowY + layout.rowHeight);
	}
}
