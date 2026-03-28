package com.beatblock.client.selection;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * 套索：场景区按住左键拖动记录屏幕轨迹，松开后提交。
 */
public final class BeatBlockLassoInteraction {

	private static final double MIN_POINT_DIST_SQ = 4.0 * 4.0;

	private static final Object STROKE_LOCK = new Object();
	private static List<double[]> currentPoly = new ArrayList<>();
	private static boolean wasDown;

	/** 供 ImGui 叠加层绘制套索预览（帧缓冲像素坐标）。 */
	public static List<double[]> copyStrokeForOverlay() {
		synchronized (STROKE_LOCK) {
			return new ArrayList<>(currentPoly);
		}
	}

	private BeatBlockLassoInteraction() {}

	public static void onEndClientTick(MinecraftClient client) {
		var mgr = BeatBlockSelectionManager.get();
		if (mgr.getMode() != SelectionMode.LASSO) {
			synchronized (STROKE_LOCK) {
				clearStrokeLocked();
			}
			wasDown = false;
			return;
		}
		if (!(client.currentScreen instanceof BeatBlockUIScreen)) {
			synchronized (STROKE_LOCK) {
				clearStrokeLocked();
			}
			wasDown = false;
			return;
		}
		if (BeatBlockUIScreen.isMouseOverUI()) {
			synchronized (STROKE_LOCK) {
				clearStrokeLocked();
			}
			wasDown = false;
			return;
		}

		long win = client.getWindow().getHandle();
		boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		synchronized (STROKE_LOCK) {
			if (down) {
				double lx = client.mouse.getX() * client.getWindow().getFramebufferWidth()
						/ (double) Math.max(1, client.getWindow().getWidth());
				double ly = client.mouse.getY() * client.getWindow().getFramebufferHeight()
						/ (double) Math.max(1, client.getWindow().getHeight());

				if (!wasDown) {
					currentPoly.clear();
					currentPoly.add(new double[] { lx, ly });
				} else if (!currentPoly.isEmpty()) {
					double[] last = currentPoly.getLast();
					double dx = lx - last[0];
					double dy = ly - last[1];
					if (dx * dx + dy * dy >= MIN_POINT_DIST_SQ) {
						currentPoly.add(new double[] { lx, ly });
					}
				}
			} else {
				if (wasDown && currentPoly.size() >= 3) {
					boolean shift = BeatBlockLassoSelector.readShiftDown(win);
					BeatBlockLassoSelector.tryApply(client, new ArrayList<>(currentPoly), shift);
				} else if (wasDown) {
					mgr.setSelectionFeedback("套索：轨迹过短，请拖动画出闭合区域。");
				}
				clearStrokeLocked();
			}
		}

		wasDown = down;
	}

	private static void clearStrokeLocked() {
		currentPoly.clear();
	}
}
