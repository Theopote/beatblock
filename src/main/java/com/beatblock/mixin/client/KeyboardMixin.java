package com.beatblock.mixin.client;

import com.beatblock.client.BeatBlockUIScreen;
import imgui.ImGui;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BeatBlock 打开时：ImGui 要键盘则不让原版处理（与 ChronoBlocks 思路一致）；
 * 光标/焦点在场景区（{@code WantCaptureKeyboard} 为 false）时，显式同步 {@link KeyBinding}，使 WASD、Shift 等照常控制玩家。
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

	@Shadow @Final private MinecraftClient client;

	@Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
	private void beatblock$onKeyHead(long window, int action, KeyInput input, CallbackInfo ci) {
		if (!(client.currentScreen instanceof BeatBlockUIScreen)) return;
		if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard()) {
			ci.cancel();
		}
	}

	@Inject(method = "onKey", at = @At("RETURN"))
	private void beatblock$syncKeyBindingsWhenWorldFocused(long window, int action, KeyInput input, CallbackInfo ci) {
		if (!(client.currentScreen instanceof BeatBlockUIScreen)) return;
		if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard()) return;
		if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;
		try {
			InputUtil.Key key = InputUtil.fromKeyCode(input);
			KeyBinding.setKeyPressed(key, action == GLFW.GLFW_PRESS);
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
	private void beatblock$onCharHead(long window, CharInput input, CallbackInfo ci) {
		if (!(client.currentScreen instanceof BeatBlockUIScreen)) return;
		if (ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard()) {
			ci.cancel();
		}
	}
}
