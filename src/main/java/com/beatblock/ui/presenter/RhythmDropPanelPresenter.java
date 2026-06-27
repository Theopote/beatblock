package com.beatblock.ui.presenter;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.generation.RhythmDropGenerator;
import com.beatblock.timeline.generation.RhythmDropEventFactory;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 天降方块（RhythmDrop）生成：从当前方块选区 + 播放头/节拍网格写入 Timeline。
 */
public final class RhythmDropPanelPresenter {

	public record GenerateRequest(
		double fallDurationSeconds,
		double fallHeightBlocks,
		boolean startAtNextBeat,
		String targetObjectId
	) {}

	public record ViewState(
		int selectionCount,
		double playheadSeconds,
		boolean hasBeatGrid,
		String beatGridDescription,
		List<ToolPanelPresenter.StageObjectListItem> stageObjects
	) {}

	private final Supplier<BeatBlockSelectionManager> selectionManager;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<StageObjectSystem> stageObjectSystem;

	public RhythmDropPanelPresenter(
		Supplier<BeatBlockSelectionManager> selectionManager,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<StageObjectSystem> stageObjectSystem
	) {
		this.selectionManager = selectionManager;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.stageObjectSystem = stageObjectSystem;
	}

	public ViewState viewState() {
		BeatBlockSelectionManager sel = selectionManager.get();
		int count = sel != null ? sel.getSelectionCount() : 0;
		double playhead = currentPlayheadSeconds();
		Timeline tl = timeline.get();
		double[] beats = tl != null ? ReferenceBeatResolver.resolveBeatTimesSeconds(tl) : new double[0];
		String beatDesc = beats.length > 0
			? ReferenceBeatResolver.describePrimaryRhythmKey(tl) + "（" + beats.length + " 拍）"
			: "无特征轨节拍，将按 BPM 固定间隔";
		return new ViewState(
			count,
			playhead,
			beats.length > 0,
			beatDesc,
			listStageObjects()
		);
	}

	public PresenterResult generateFromSelection(GenerateRequest request) {
		BeatBlockSelectionManager sel = selectionManager.get();
		if (sel == null || sel.getSelectionCount() <= 0) {
			return PresenterResult.failure("请先选中至少一个落点方块");
		}
		Timeline tl = timeline.get();
		StageObjectSystem objects = stageObjectSystem.get();
		if (tl == null) {
			return PresenterResult.failure("时间线不可用");
		}
		if (objects == null) {
			return PresenterResult.failure("动画引擎未就绪");
		}

		GenerateRequest req = request != null ? request : defaultRequest();
		double fallDuration = req.fallDurationSeconds() > 0
			? req.fallDurationSeconds()
			: RhythmDropEventFactory.DEFAULT_FALL_DURATION_SECONDS;
		double fallHeight = req.fallHeightBlocks() > 0
			? req.fallHeightBlocks()
			: RhythmDropEventFactory.DEFAULT_FALL_HEIGHT_BLOCKS;
		String targetId = req.targetObjectId() != null ? req.targetObjectId().trim() : "";

		RhythmDropGenerator.Config config = new RhythmDropGenerator.Config(
			currentPlayheadSeconds(),
			req.startAtNextBeat(),
			fallDuration,
			fallHeight,
			targetId.isEmpty() ? RhythmDropGenerator.DEFAULT_ANCHOR_ID : targetId
		);

		List<BlockPos> positions = new ArrayList<>(sel.getSelectedBlocks());
		RhythmDropGenerator.Outcome outcome = RhythmDropGenerator.generate(tl, objects, positions, config);
		if (!outcome.success()) {
			return PresenterResult.failure(outcome.detail());
		}
		syncClockDuration();
		return PresenterResult.success(outcome.detail());
	}

	public PresenterResult generateFromSelectionWithDefaults() {
		return generateFromSelection(defaultRequest());
	}

	public static GenerateRequest defaultRequest() {
		return new GenerateRequest(
			RhythmDropEventFactory.DEFAULT_FALL_DURATION_SECONDS,
			RhythmDropEventFactory.DEFAULT_FALL_HEIGHT_BLOCKS,
			true,
			RhythmDropGenerator.DEFAULT_ANCHOR_ID
		);
	}

	private List<ToolPanelPresenter.StageObjectListItem> listStageObjects() {
		StageObjectSystem system = stageObjectSystem.get();
		if (system == null) return List.of();
		List<ToolPanelPresenter.StageObjectListItem> out = new ArrayList<>();
		for (StageObject obj : system.getAll()) {
			if (obj == null) continue;
			String source = obj.getGroupSpec() != null ? obj.getGroupSpec().getSourceType() : "manual";
			out.add(new ToolPanelPresenter.StageObjectListItem(
				obj.getId(),
				obj.getName(),
				obj.getBlocks().size(),
				source
			));
		}
		return out;
	}

	private double currentPlayheadSeconds() {
		TimelineEditor editor = timelineEditor.get();
		if (editor != null && editor.getClock() != null) {
			return Math.max(0.0, editor.getClock().getCurrentTimeSeconds());
		}
		return 0.0;
	}

	private void syncClockDuration() {
		TimelineEditor editor = timelineEditor.get();
		if (editor != null) {
			editor.syncClockDuration();
		}
	}
}
