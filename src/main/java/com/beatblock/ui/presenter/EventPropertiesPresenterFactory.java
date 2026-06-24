package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class EventPropertiesPresenterFactory {

	private EventPropertiesPresenterFactory() {}

	static EventPropertiesPresenter create() {
		return new EventPropertiesPresenter(
			id -> BeatBlock.blockAnimationEngine != null
				&& BeatBlock.blockAnimationEngine.getStageObjectSystem().get(id) != null,
			blockId -> {
				Identifier parsed = Identifier.tryParse(blockId);
				return parsed != null && Registries.BLOCK.containsId(parsed);
			},
			() -> EventPropertiesPresenter.collectAnimationOptions(() -> {
				if (BeatBlock.blockAnimationEngine == null) {
					return Map.of();
				}
				return BeatBlock.blockAnimationEngine.getAnimationLibrary().getAll();
			}),
			() -> EventPropertiesPresenter.collectTargetOptions(() -> {
				if (BeatBlock.blockAnimationEngine == null) {
					return List.of();
				}
				return new ArrayList<>(BeatBlock.blockAnimationEngine.getStageObjectSystem().getAll());
			}),
			EventPropertiesPresenterFactory::readCameraView
		);
	}

	private static EventPropertiesPresenter.CameraViewSample readCameraView() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) {
			return null;
		}
		if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
			var camera = mc.gameRenderer.getCamera();
			var eye = camera.getCameraPos();
			return new EventPropertiesPresenter.CameraViewSample(
				eye.x, eye.y, eye.z, camera.getYaw(), camera.getPitch()
			);
		}
		if (mc.player != null) {
			var eye = mc.player.getEyePos();
			return new EventPropertiesPresenter.CameraViewSample(
				eye.x, eye.y, eye.z, mc.player.getYaw(), mc.player.getPitch()
			);
		}
		return null;
	}
}
