package com.beatblock.client.render;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.selection.BeatBlockLassoInteraction;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import imgui.ImDrawList;
import imgui.ImGui;
import java.util.List;
import net.minecraft.client.MinecraftClient;

/**
 * 套索绘制过程中在屏幕上叠加折线预览（逻辑窗口坐标，与 ImGui 对齐）。
 */
public final class BeatBlockLassoOverlay {

	private BeatBlockLassoOverlay() {}

	public static void render() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || !(client.currentScreen instanceof BeatBlockUIScreen)) {
			return;
		}
		if (BeatBlockSelectionManager.get().getMode() != SelectionMode.LASSO) {
			return;
		}
		if (BeatBlockUIScreen.isMouseOverUI()) {
			return;
		}
		List<double[]> pts = BeatBlockLassoInteraction.copyStrokeForOverlay();
		if (pts.size() < 2) {
			return;
		}

		var win = client.getWindow();
		int lw = Math.max(1, win.getWidth());
		int lh = Math.max(1, win.getHeight());
		int fbw = Math.max(1, win.getFramebufferWidth());
		int fbh = Math.max(1, win.getFramebufferHeight());
		float scaleX = lw / (float) fbw;
		float scaleY = lh / (float) fbh;

		int argb = 0xFFEEDD66;
		float thickness = 2.5f;
		var drawList = ImGui.getForegroundDrawList();

		for (int i = 0; i < pts.size() - 1; i++) {
			double[] a = pts.get(i);
			double[] b = pts.get(i + 1);
			line(drawList, a[0] * scaleX, a[1] * scaleY, b[0] * scaleX, b[1] * scaleY, argb, thickness);
		}
		if (pts.size() >= 3) {
			double[] first = pts.getFirst();
			double[] last = pts.getLast();
			line(drawList, last[0] * scaleX, last[1] * scaleY, first[0] * scaleX, first[1] * scaleY, argb, thickness);
		}
	}

	private static void line(ImDrawList drawList, double x0, double y0, double x1, double y1, int col, float thick) {
		drawList.addLine((float) x0, (float) y0, (float) x1, (float) y1, col, thick);
	}
}
