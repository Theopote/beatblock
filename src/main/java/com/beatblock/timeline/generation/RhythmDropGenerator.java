package com.beatblock.timeline.generation;

import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从落点坐标 + 节拍排布生成 {@link RhythmDropEventFactory} 事件并写入 Timeline「方块动画」轨道。
 */
public final class RhythmDropGenerator {

	public static final String DEFAULT_ANCHOR_ID = "rhythm_drop_anchor";

	public record Config(
		double anchorTimeSeconds,
		boolean startAtNextBeat,
		double fallDurationSeconds,
		double fallHeightBlocks,
		String targetObjectId
	) {
		public static Config defaults(double anchorTimeSeconds) {
			return new Config(
				anchorTimeSeconds,
				true,
				RhythmDropEventFactory.DEFAULT_FALL_DURATION_SECONDS,
				RhythmDropEventFactory.DEFAULT_FALL_HEIGHT_BLOCKS,
				DEFAULT_ANCHOR_ID
			);
		}
	}

	public record Outcome(int eventCount, String targetObjectId, String detail) {
		public boolean success() {
			return eventCount > 0;
		}
	}

	private RhythmDropGenerator() {}

	public static Outcome generate(
		Timeline timeline,
		StageObjectSystem stageObjects,
		List<BlockPos> landingPositions,
		Config config
	) {
		if (timeline == null) {
			return new Outcome(0, "", "时间线不可用");
		}
		if (stageObjects == null) {
			return new Outcome(0, "", "动画引擎未就绪");
		}
		if (landingPositions == null || landingPositions.isEmpty()) {
			return new Outcome(0, "", "请先选中至少一个落点方块");
		}

		Config effective = config != null ? config : Config.defaults(0.0);
		List<BlockPos> ordered = sortLandingPositions(landingPositions);
		String targetId = resolveTargetObjectId(stageObjects, ordered, effective.targetObjectId());
		if (stageObjects.get(targetId) == null) {
			return new Outcome(0, targetId, "目标 StageObject 不存在: " + targetId);
		}

		double[] beats = ReferenceBeatResolver.resolveBeatTimesSeconds(timeline);
		double bpm = timeline.getBpm() > 0 ? timeline.getBpm() : 120.0;
		double anchor = Math.max(0.0, effective.anchorTimeSeconds());
		PacingRequest pacing = new PacingRequest(
			ordered.size(),
			anchor,
			!effective.startAtNextBeat(),
			beats,
			bpm,
			60.0 / bpm
		);
		List<Double> landingTimes = PacingStrategy.beatGrid().computeTimestamps(pacing);
		if (landingTimes.size() < ordered.size()) {
			return new Outcome(0, targetId, "未能为全部落点计算命中时间");
		}

		List<TimelineAnimationEvent> events = RhythmDropEventFactory.build(
			ordered,
			landingTimes,
			targetId,
			effective.fallDurationSeconds(),
			effective.fallHeightBlocks()
		);
		if (events.isEmpty()) {
			return new Outcome(0, targetId, "未生成任何事件");
		}

		int count = TimelineDraftWriter.writeEvents(
			timeline,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			events,
			TimelineEventOrigin.AUTO_GENERATED
		);
		if (count <= 0) {
			return new Outcome(0, targetId, "写入时间线失败");
		}

		String beatDetail = beats.length > 0
			? ReferenceBeatResolver.describePrimaryRhythmKey(timeline) + " 节拍"
			: "固定 BPM 间隔（无特征轨节拍）";
		return new Outcome(
			count,
			targetId,
			count + " 个 RhythmDrop 事件（" + beatDetail + "，目标 " + targetId + "）"
		);
	}

	private static String resolveTargetObjectId(
		StageObjectSystem stageObjects,
		List<BlockPos> ordered,
		String requestedId
	) {
		String id = requestedId != null && !requestedId.isBlank()
			? requestedId.trim()
			: DEFAULT_ANCHOR_ID;
		if (DEFAULT_ANCHOR_ID.equals(id)) {
			stageObjects.register(StageObjectSystem.fromSelectionSnapshot(
				DEFAULT_ANCHOR_ID,
				"Rhythm Drop",
				ordered,
				GroupSortingStrategy.SEQUENTIAL,
				0.0
			));
		}
		return id;
	}

	static List<BlockPos> sortLandingPositions(List<BlockPos> landingPositions) {
		List<BlockPos> ordered = new ArrayList<>(landingPositions);
		ordered.sort(Comparator
			.comparingInt(BlockPos::getX)
			.thenComparingInt(BlockPos::getZ)
			.thenComparingInt(BlockPos::getY));
		return ordered;
	}
}
