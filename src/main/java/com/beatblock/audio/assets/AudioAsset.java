package com.beatblock.audio.assets;

import com.beatblock.audio.analysis.AudioFeatureTimeline;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.UUID;

/**
 * 单个音频资产：路径 + 解析结果 + 进度状态。
 */
public final class AudioAsset {

	private final String id;
	private final Path path;
	private final String fileName;

	private double durationSeconds;
	private int sampleRate;

	private float bpm;
	private int beatCount;
	private int sectionCount;
	private int lowCount;
	private int midCount;
	private int highCount;

	private AudioAssetStatus status = AudioAssetStatus.PENDING;
	private final EnumSet<AudioAnalysisStep> finishedSteps = EnumSet.noneOf(AudioAnalysisStep.class);
	private String errorMessage;

	private AudioFeatureTimeline featureTimeline;

	public AudioAsset(Path path) {
		this.id = UUID.randomUUID().toString();
		this.path = path;
		String name = path != null ? path.getFileName().toString() : "";
		this.fileName = name != null ? name : "";
	}

	public String getId() { return id; }
	public Path getPath() { return path; }
	public String getFileName() { return fileName; }

	public double getDurationSeconds() { return durationSeconds; }
	public void setDurationSeconds(double durationSeconds) { this.durationSeconds = Math.max(0, durationSeconds); }

	public int getSampleRate() { return sampleRate; }
	public void setSampleRate(int sampleRate) { this.sampleRate = Math.max(1, sampleRate); }

	public float getBpm() { return bpm; }
	public void setBpm(float bpm) { this.bpm = bpm; }

	public int getBeatCount() { return beatCount; }
	public void setBeatCount(int beatCount) { this.beatCount = Math.max(0, beatCount); }

	public int getSectionCount() { return sectionCount; }
	public void setSectionCount(int sectionCount) { this.sectionCount = Math.max(0, sectionCount); }

	public int getLowCount() { return lowCount; }
	public void setLowCount(int lowCount) { this.lowCount = Math.max(0, lowCount); }

	public int getMidCount() { return midCount; }
	public void setMidCount(int midCount) { this.midCount = Math.max(0, midCount); }

	public int getHighCount() { return highCount; }
	public void setHighCount(int highCount) { this.highCount = Math.max(0, highCount); }

	public AudioAssetStatus getStatus() { return status; }
	public void setStatus(AudioAssetStatus status) { this.status = status != null ? status : AudioAssetStatus.PENDING; }

	public EnumSet<AudioAnalysisStep> getFinishedSteps() { return finishedSteps; }
	public void markStepFinished(AudioAnalysisStep step) {
		if (step != null) finishedSteps.add(step);
	}

	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

	public AudioFeatureTimeline getFeatureTimeline() { return featureTimeline; }
	public void setFeatureTimeline(AudioFeatureTimeline featureTimeline) { this.featureTimeline = featureTimeline; }
}

