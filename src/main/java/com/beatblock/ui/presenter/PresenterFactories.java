package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.command.CommandManager;

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
}
