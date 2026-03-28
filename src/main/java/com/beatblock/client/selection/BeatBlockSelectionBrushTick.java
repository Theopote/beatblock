package com.beatblock.client.selection;

import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.input.BeatBlockInputSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * 笔刷模式：场景区按住左键时每 tick 沿光标盖章（避免在 Manager 中依赖客户端射线）。
 */
public final class BeatBlockSelectionBrushTick {

	private BeatBlockSelectionBrushTick() {}

	public static void onEndClientTick(MinecraftClient client) {
		var mgr = BeatBlockSelectionManager.get();
		if (mgr.getMode() != SelectionMode.BRUSH) {
			mgr.clearBrushAnchor();
			return;
		}
		if (!(client.currentScreen instanceof BeatBlockUIScreen)) {
			mgr.clearBrushAnchor();
			return;
		}
		if (BeatBlockUIScreen.isMouseOverUI()) {
			mgr.clearBrushAnchor();
			return;
		}
		long win = client.getWindow().getHandle();
		boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (!down) {
			mgr.finishBrushStroke();
			return;
		}
		if (client.world == null || client.player == null || client.gameRenderer == null) return;

		mgr.setInteractionCameraPos(client.gameRenderer.getCamera().getCameraPos());

		BlockHitResult hit = BeatBlockInputSystem.raycastFromImGui();
		if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

		Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
		double maxSq = BeatBlockInputSystem.MAX_RAYCAST_DISTANCE * BeatBlockInputSystem.MAX_RAYCAST_DISTANCE;
		if (cam.squaredDistanceTo(hit.getPos()) > maxSq) return;

		boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		mgr.stampBrushIfNeeded(client.world, hit.getBlockPos(), shift);
	}
}
