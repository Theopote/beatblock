package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.engine.layer.BuildLayerManager;
import net.fabricmc.loader.api.FabricLoader;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

public final class PresenterFactories {

	private PresenterFactories() {}

	public static BuildLayersPresenter buildLayersPresenter() {
		return new BuildLayersPresenter(
			(Supplier<CommandManager>) () -> BeatBlock.timelineEditor != null
				? BeatBlock.timelineEditor.getCommandManager()
				: null,
			(Supplier<BuildLayerManager>) () -> BeatBlock.blockAnimationEngine != null
				? BeatBlock.blockAnimationEngine.getBuildLayerManager()
				: null
		);
	}

	public static EventPropertiesPresenter eventPropertiesPresenter() {
		return EventPropertiesPresenterFactory.create();
	}

	public static SelectionPropertiesPresenter selectionPropertiesPresenter() {
		return new SelectionPropertiesPresenter(BeatBlockSelectionManager::get);
	}

	public static ToolPanelPresenter toolPanelPresenter() {
		return ToolPanelPresenterFactory.create();
	}

	public static TimelinePanelPresenter timelinePanelPresenter() {
		return new TimelinePanelPresenter();
	}

	public static TimelineEditorPresenter timelineEditorPresenter() {
		return new TimelineEditorPresenter(
			() -> BeatBlock.timelineEditor,
			time -> {
				if (BeatBlock.musicPlayer != null) {
					BeatBlock.musicPlayer.setCurrentTimeSeconds(time);
				}
			}
		);
	}

	public static MarkerPanelPresenter markerPanelPresenter() {
		return new MarkerPanelPresenter(timelineEditorPresenter());
	}

	public static MenuBarPresenter menuBarPresenter() {
		return new MenuBarPresenter(
			timelineEditorPresenter(),
			() -> BeatBlock.timeline,
			() -> BeatBlock.timelineEditor,
			() -> BeatBlock.blockAnimationEngine != null
				? BeatBlock.blockAnimationEngine.getBuildLayerManager()
				: null,
			() -> BeatBlock.audioLoader
		);
	}

	public static TimelineTransportPresenter timelineTransportPresenter() {
		return new TimelineTransportPresenter(
			() -> BeatBlock.timelineEditor,
			() -> BeatBlock.timeline,
			() -> BeatBlock.musicPlayer,
			BeatBlock::getActiveAudioPlayer,
			new TimelineTransportPresenter.TimelineDriveControl() {
				@Override
				public boolean isDriving() {
					return BeatBlockClientDriver.isDriving();
				}

				@Override
				public void startDriving() {
					BeatBlockClientDriver.startDriving();
				}

				@Override
				public void stopDriving() {
					BeatBlockClientDriver.stopDriving();
				}
			}
		);
	}

	public static TimelineToolbarActionsPresenter timelineToolbarActionsPresenter() {
		return new TimelineToolbarActionsPresenter(
			() -> BeatBlock.timeline,
			() -> BeatBlock.timelineEditor,
			PresenterFactories::currentCameraPositionOrZero
		);
	}

	public static TimelineToolbarConfigPresenter timelineToolbarConfigPresenter() {
		return new TimelineToolbarConfigPresenter(
			() -> BeatBlock.timeline,
			() -> FabricLoader.getInstance().getGameDir()
				.resolve("config").resolve("beatblock").resolve("ui.json")
		);
	}

	public static TimelineBindingEditorPresenter timelineBindingEditorPresenter() {
		return new TimelineBindingEditorPresenter(
			() -> BeatBlock.timeline,
			() -> BeatBlock.timelineEditor,
			() -> BeatBlock.blockAnimationEngine
		);
	}

	public static TimelineToolbarFeedbackPresenter timelineToolbarFeedbackPresenter() {
		return new TimelineToolbarFeedbackPresenter();
	}

	private static Vec3d currentCameraPositionOrZero() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
			return client.gameRenderer.getCamera().getCameraPos();
		}
		return Vec3d.ZERO;
	}
}
