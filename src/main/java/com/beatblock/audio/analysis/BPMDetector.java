package com.beatblock.audio.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BPM 估计：基于检测到的节拍时间间隔构建直方图，取主峰对应周期。
 * 改进版本：支持置信度输出，使用直方图峰值而非简单中位数，更鲁棒地处理离群值。
 */
public final class BPMDetector {

	/** 最小有效间隔（秒）：对应 300 BPM */
	private static final double MIN_INTERVAL = 0.2;
	/** 最大有效间隔（秒）：对应 30 BPM */
	private static final double MAX_INTERVAL = 2.0;
	/** 默认 BPM（检测失败时返回） */
	private static final float DEFAULT_BPM = 120f;
	/** 最小 BPM */
	private static final float MIN_BPM = 40f;
	/** 最大 BPM */
	private static final float MAX_BPM = 240f;
	/** 直方图桶大小（秒） */
	private static final double HISTOGRAM_BUCKET_SIZE = 0.02;

	/**
	 * BPM 估计结果，包含 BPM 值和置信度。
	 */
	public static class BPMEstimate {
		private final float bpm;
		private final float confidence;

		public BPMEstimate(float bpm, float confidence) {
			this.bpm = bpm;
			this.confidence = Math.max(0f, Math.min(1f, confidence));
		}

		public float getBpm() { return bpm; }
		public float getConfidence() { return confidence; }

		/** 是否可靠（置信度 >= 0.5） */
		public boolean isReliable() { return confidence >= 0.5f; }
	}

	/**
	 * 从已检测的节拍列表估计 BPM。
	 *
	 * @param beats 按时间排序的节拍
	 * @return 估计的 BPM，若无足够节拍则返回 120
	 * @deprecated 使用 {@link #estimateBPMWithConfidence(List)} 获取置信度
	 */
	@Deprecated
	public static float estimateBPM(List<DetectedBeat> beats) {
		return estimateBPMWithConfidence(beats).getBpm();
	}

	/**
	 * 从已检测的节拍列表估计 BPM，返回 BPM 和置信度。
	 *
	 * @param beats 按时间排序的节拍
	 * @return BPM 估计结果（包含置信度）
	 */
	public static BPMEstimate estimateBPMWithConfidence(List<DetectedBeat> beats) {
		if (beats == null || beats.size() < 3) {
			return new BPMEstimate(DEFAULT_BPM, 0f);
		}

		// 计算相邻节拍间隔
		List<Double> intervals = new ArrayList<>();
		for (int i = 1; i < beats.size(); i++) {
			double dt = beats.get(i).getTimeSeconds() - beats.get(i - 1).getTimeSeconds();
			if (dt >= MIN_INTERVAL && dt <= MAX_INTERVAL) {
				intervals.add(dt);
			}
		}

		if (intervals.isEmpty()) {
			return new BPMEstimate(DEFAULT_BPM, 0f);
		}

		// 构建间隔直方图
		Map<Integer, Integer> histogram = new HashMap<>();
		for (double interval : intervals) {
			int bucket = (int) (interval / HISTOGRAM_BUCKET_SIZE);
			histogram.put(bucket, histogram.getOrDefault(bucket, 0) + 1);
		}

		// 找到最高峰
		int maxCount = 0;
		int peakBucket = -1;
		for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
			if (entry.getValue() > maxCount) {
				maxCount = entry.getValue();
				peakBucket = entry.getKey();
			}
		}

		if (peakBucket < 0) {
			return new BPMEstimate(DEFAULT_BPM, 0f);
		}

		// 从峰值桶计算间隔
		double peakInterval = (peakBucket + 0.5) * HISTOGRAM_BUCKET_SIZE;
		float rawBpm = (float) (60.0 / peakInterval);

		// 智能八度音程修正：检查倍数/半数是否在合理范围
		float adjustedBpm = adjustOctave(rawBpm);

		// 计算置信度：峰值占比
		float confidence = (float) maxCount / intervals.size();
		// 如果峰值很明显（>30%），提高置信度
		if (confidence > 0.3f) {
			confidence = Math.min(1f, confidence * 1.5f);
		}

		return new BPMEstimate(
			Math.max(MIN_BPM, Math.min(MAX_BPM, adjustedBpm)),
			confidence
		);
	}

	/**
	 * 智能八度音程修正：如果 BPM 明显过低或过高，尝试倍增/减半。
	 */
	private static float adjustOctave(float bpm) {
		// 过低：可能检测到半拍
		if (bpm < 60) {
			float doubled = bpm * 2;
			if (doubled <= MAX_BPM) return doubled;
		}
		// 过高：可能检测到双倍拍
		if (bpm > 200) {
			float halved = bpm / 2;
			if (halved >= MIN_BPM) return halved;
		}
		// 在合理范围内，不修正
		return bpm;
	}
}
