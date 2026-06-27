package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.runtime.BeatBlockContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

/**
 * Presenter 工厂：从 {@link BeatBlockContext} 注入依赖，避免 Panel 直接访问静态字段。
 */
public final class PresenterFactories {

	private static Supplier<BeatBlockContext> contextSource = BeatBlock::getContext;

	private PresenterFactories() {}

	/** 测试用：替换运行时上下文来源。 */
	static void setContextSourceForTests(Supplier<BeatBlockContext> source) {
		contextSource = source != null ? source : BeatBlock::getContext;
	}

	static void resetContextSourceForTests() {
		contextSource = BeatBlock::getContext;
	}

	private static BeatBlockContext ctx() {
		return contextSource.get();
	}

	public static BuildLayersPresenter buildLayersPresenter() {
		return buildLayersPresenter(ctx());
	}

	public static BuildLayersPresenter buildLayersPresenter(BeatBlockContext context) {
		return new BuildLayersPresenter(
			() -> context.commandManager(),
			() -> context.buildLayerManager()
		);
	}

	public static EventPropertiesPresenter eventPropertiesPresenter() {
		return eventPropertiesPresenter(ctx());
	}

	public static EventPropertiesPresenter eventPropertiesPresenter(BeatBlockContext context) {
		return EventPropertiesPresenterFactory.create(context);
	}

	public static SelectionPropertiesPresenter selectionPropertiesPresenter() {
		return new SelectionPropertiesPresenter(BeatBlockSelectionManager::get);
	}

	public static ToolPanelPresenter toolPanelPresenter() {
		return toolPanelPresenter(ctx());
	}

	public static ToolPanelPresenter toolPanelPresenter(BeatBlockContext context) {
		return ToolPanelPresenterFactory.create(context);
	}

	public static RhythmDropPanelPresenter rhythmDropPanelPresenter() {
		return rhythmDropPanelPresenter(ctx());
	}

	public static RhythmDropPanelPresenter rhythmDropPanelPresenter(BeatBlockContext context) {
		return new RhythmDropPanelPresenter(
			BeatBlockSelectionManager::get,
			context::timeline,
			context::timelineEditor,
			() -> context.blockAnimationEngine() != null
				? context.blockAnimationEngine().getStageObjectSystem()
				: null
		);
	}

	public static TimelinePanelPresenter timelinePanelPresenter() {
		return new TimelinePanelPresenter();
	}

	public static TimelineEditorPresenter timelineEditorPresenter() {
		return timelineEditorPresenter(ctx());
	}

	public static TimelineEditorPresenter timelineEditorPresenter(BeatBlockContext context) {
		return new TimelineEditorPresenter(
			context::timelineEditor,
			time -> {
				if (context.musicPlayer() != null) {
					context.musicPlayer().setCurrentTimeSeconds(time);
				}
			}
		);
	}

	public static MarkerPanelPresenter markerPanelPresenter() {
		return markerPanelPresenter(ctx());
	}

	public static MarkerPanelPresenter markerPanelPresenter(BeatBlockContext context) {
		return new MarkerPanelPresenter(timelineEditorPresenter(context), context::timeline);
	}

	public static MenuBarPresenter menuBarPresenter() {
		return menuBarPresenter(ctx());
	}

	public static MenuBarPresenter menuBarPresenter(BeatBlockContext context) {
		return new MenuBarPresenter(
			timelineEditorPresenter(context),
			context::timeline,
			context::timelineEditor,
			context::buildLayerManager,
			context::audioLoader
		);
	}

	public static TimelineTransportPresenter timelineTransportPresenter() {
		return timelineTransportPresenter(ctx());
	}

	public static TimelineTransportPresenter timelineTransportPresenter(BeatBlockContext context) {
		return new TimelineTransportPresenter(
			context::timelineEditor,
			context::timeline,
			context::musicPlayer,
			context::activeAudioPlayer,
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
		return timelineToolbarActionsPresenter(ctx());
	}

	public static TimelineToolbarActionsPresenter timelineToolbarActionsPresenter(BeatBlockContext context) {
		return new TimelineToolbarActionsPresenter(
			context::timeline,
			context::timelineEditor,
			PresenterFactories::currentCameraPositionOrZero
		);
	}

	public static TimelineToolbarConfigPresenter timelineToolbarConfigPresenter() {
		return timelineToolbarConfigPresenter(ctx());
	}

	public static TimelineToolbarConfigPresenter timelineToolbarConfigPresenter(BeatBlockContext context) {
		return new TimelineToolbarConfigPresenter(
			context::timeline,
			() -> FabricLoader.getInstance().getGameDir()
				.resolve("config").resolve("beatblock").resolve("ui.json")
		);
	}

	public static TimelineBindingEditorPresenter timelineBindingEditorPresenter() {
		return timelineBindingEditorPresenter(ctx());
	}

	public static TimelineBindingEditorPresenter timelineBindingEditorPresenter(BeatBlockContext context) {
		return new TimelineBindingEditorPresenter(
			context::timeline,
			context::timelineEditor,
			context::blockAnimationEngine
		);
	}

	public static TimelineToolbarFeedbackPresenter timelineToolbarFeedbackPresenter() {
		return new TimelineToolbarFeedbackPresenter();
	}

	public static AudioAnalysisPanelPresenter audioAnalysisPanelPresenter() {
		return audioAnalysisPanelPresenter(ctx());
	}

	public static AudioAnalysisPanelPresenter audioAnalysisPanelPresenter(BeatBlockContext context) {
		return new AudioAnalysisPanelPresenter(() -> context);
	}

	public static AutoMapSettingsPanelPresenter autoMapSettingsPanelPresenter() {
		return autoMapSettingsPanelPresenter(ctx());
	}

	public static AutoMapSettingsPanelPresenter autoMapSettingsPanelPresenter(BeatBlockContext context) {
		return new AutoMapSettingsPanelPresenter(() -> context);
	}

	private static Vec3d currentCameraPositionOrZero() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
			return client.gameRenderer.getCamera().getCameraPos();
		}
		return Vec3d.ZERO;
	}
}
