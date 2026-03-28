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
 * 打开 BeatBlock 期间始终使用 ImGui 光标，不锁定为原版十字准星（与 ChronoBlocks 在场景区可锁定不同）。
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

	@Shadow @Final private MinecraftClient client;
	@Unique private double beatblock$lastCursorX;
	@Unique private double beatblock$lastCursorY;
	@Unique private boolean beatblock$middleDragging;
	@Unique private boolean beatblock$cursorSampleInitialized;

	@Inject(method = "onCursorPos", at = @At("TAIL"))
	private void beatblock$onCursorPos(long window, double x, double y, CallbackInfo ci) {
		if (!(client.currentScreen instanceof BeatBlockUIScreen)) {
			beatblock$cursorSampleInitialized = false;
			return;
		}
		if (client.player == null) return;

		if (!beatblock$cursorSampleInitialized) {
			beatblock$lastCursorX = x;
			beatblock$lastCursorY = y;
			beatblock$cursorSampleInitialized = true;
			return;
		}

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
	private void beatblock$preventLockWhileBeatBlockScreenOpen(CallbackInfo ci) {
		if (client.currentScreen instanceof BeatBlockUIScreen) {
			ci.cancel();
		}
	}
}
