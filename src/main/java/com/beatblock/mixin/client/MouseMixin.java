package com.beatblock.mixin.client;

import com.beatblock.client.BeatBlockUIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BeatBlock 打开时：鼠标在 UI 上则只操作 ImGui；在场景区按住中键拖拽移动玩家视角。
 * 在 UI 上时阻止锁定光标，保持 ImGui 光标可见。
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

	@Shadow @Final private MinecraftClient client;
	@Unique private double beatblock$lastCursorX;
	@Unique private double beatblock$lastCursorY;
	@Unique private boolean beatblock$middleDragging;

	@Inject(method = "onCursorPos", at = @At("TAIL"))
	private void beatblock$onCursorPos(long window, double x, double y, CallbackInfo ci) {
		if (!(client.currentScreen instanceof BeatBlockUIScreen)) return;
		if (client.player == null) return;

		double dx = x - beatblock$lastCursorX;
		double dy = y - beatblock$lastCursorY;
		beatblock$lastCursorX = x;
		beatblock$lastCursorY = y;

		if (BeatBlockUIScreen.isMouseOverUI()) return;
		if (beatblock$middleDragging) {
			double sens = client.options.getMouseSensitivity().getValue() * 2.0;
			client.player.changeLookDirection(dx * sens, dy * sens);
		}
	}

	@Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
	private void beatblock$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
		int button = input.button();

		if (!(client.currentScreen instanceof BeatBlockUIScreen)) return;

		boolean overUI = BeatBlockUIScreen.isMouseOverUI();

		if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
			beatblock$middleDragging = (action == GLFW.GLFW_PRESS);
			ci.cancel();
			return;
		}

		if (overUI) {
			ci.cancel();
		}
	}

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void beatblock$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (!(client.currentScreen instanceof BeatBlockUIScreen)) return;
		if (BeatBlockUIScreen.isMouseOverUI()) {
			ci.cancel();
		}
	}

	@Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
	private void beatblock$preventLockWhenOverUI(CallbackInfo ci) {
		if (client.currentScreen instanceof BeatBlockUIScreen && BeatBlockUIScreen.isMouseOverUI()) {
			ci.cancel();
		}
	}
}
