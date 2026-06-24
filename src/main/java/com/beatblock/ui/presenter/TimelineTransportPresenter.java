package com.beatblock.ui.presenter;

import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.util.MusicTimeFormatter;
import com.beatblock.audio.MusicPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 时间线 Transport：播放/暂停/停止、跳转、事件导航、Marker 创建、循环区。
 */
public final class TimelineTransportPresenter {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineTransportPresenter.class);
	private static final double DEFAULT_FALLBACK_DURATION_SECONDS = 60.0;

	public record TransportViewState(
		boolean hasMusic,
		boolean playing,
		double bpm,
		double seekStep,
		double stepSeek,
		double currentTimeSeconds,
		double durationSeconds,
		String positionDisplay
	) {}

	public interface TimelineDriveControl {
		default boolean isDriving() {
			return false;
		}

		default void startDriving() {}

		default void stopDriving() {}
	}

	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<Timeline> timeline;
	private final Supplier<MusicPlayer> musicPlayer;
	private final Supplier<IAudioPlayer> activeAudioPlayer;
	private final TimelineDriveControl driveControl;

	public TimelineTransportPresenter(
		Supplier<TimelineEditor> timelineEditor,
		Supplier<Timeline> timeline,
		Supplier<MusicPlayer> musicPlayer,
		Supplier<IAudioPlayer> activeAudioPlayer,
		TimelineDriveControl driveControl
	) {
		this.timelineEditor = timelineEditor;
		this.timeline = timeline;
		this.musicPlayer = musicPlayer;
		this.activeAudioPlayer = activeAudioPlayer;
		this.driveControl = driveControl != null ? driveControl : new TimelineDriveControl() {};
	}

	public TransportViewState viewState(TimelineEditor editor, boolean shiftHeld) {
		if (editor == null) {
			return new TransportViewState(false, false, 0, 1.0, shiftHeld ? 5.0 : 1.0,
				0, DEFAULT_FALLBACK_DURATION_SECONDS, "");
		}
		Timeline currentTimeline = timeline.get();
		MusicPlayer music = musicPlayer.get();
		double bpm = currentTimeline != null ? currentTimeline.getBpm() : 0;
		double seekStep = resolveSeekStep(bpm);
		double stepSeek = shiftHeld ? 5.0 : seekStep;
		double currentTime = editor.getClock().getCurrentTimeSeconds();
		double duration = resolveDuration(editor);
		boolean hasMusic = music != null && currentTimeline != null && currentTimeline.getDurationSeconds() > 0;
		boolean playing = hasMusic && music.isPlaying();
		String positionDisplay = MusicTimeFormatter.formatPositionDisplay(currentTime, duration, bpm);
		return new TransportViewState(hasMusic, playing, bpm, seekStep, stepSeek,
			currentTime, duration, positionDisplay);
	}

	public static double resolveSeekStep(double bpm) {
		return bpm > 0 ? 60.0 / bpm : 1.0;
	}

	public void seekTo(TimelineEditor editor, double targetSeconds) {
		if (editor == null) {
			return;
		}
		double duration = resolveDuration(editor);
		double time = Math.max(0, Math.min(targetSeconds, duration));
		editor.getClock().seek(time);
		MusicPlayer music = musicPlayer.get();
		if (music != null) {
			ensureMusicDurationForPlayback(editor);
			music.setCurrentTimeSeconds(time);
		}
	}

	public void seekBy(TimelineEditor editor, double deltaSeconds) {
		if (editor == null) {
			return;
		}
		seekTo(editor, editor.getClock().getCurrentTimeSeconds() + deltaSeconds);
	}

	public void play(TimelineEditor editor) {
		ensureMusicDurationForPlayback(editor);
		MusicPlayer music = musicPlayer.get();
		if (music != null) {
			music.play();
		}
		IAudioPlayer active = activeAudioPlayer.get();
		if (active != null && active != music) {
			active.play();
		}
		if (!driveControl.isDriving()) {
			driveControl.startDriving();
		}
		TimelineEditor currentEditor = editor != null ? editor : timelineEditor.get();
		if (currentEditor != null) {
			currentEditor.getClock().play();
		}
	}

	public void pause() {
		MusicPlayer music = musicPlayer.get();
		if (music != null) {
			music.pause();
		}
		IAudioPlayer active = activeAudioPlayer.get();
		if (active != null && active != music) {
			active.pause();
		}
		TimelineEditor editor = timelineEditor.get();
		if (editor != null) {
			editor.getClock().pause();
		}
	}

	public void stop(TimelineEditor editor) {
		MusicPlayer music = musicPlayer.get();
		if (music != null) {
			music.stop();
		}
		IAudioPlayer active = activeAudioPlayer.get();
		if (active != null && active != music) {
			active.stop();
		}
		TimelineEditor currentEditor = editor != null ? editor : timelineEditor.get();
		if (currentEditor != null) {
			currentEditor.getClock().pause();
		}
		driveControl.stopDriving();
		seekTo(editor, 0);
	}

	public void jumpToNearbyEvent(TimelineEditor editor, boolean forward) {
		if (editor == null) {
			return;
		}
		Timeline currentTimeline = timeline.get();
		if (currentTimeline == null) {
			return;
		}
		List<Double> marks = collectNavigationTimes(currentTimeline);
		if (marks.isEmpty()) {
			return;
		}
		double current = editor.getClock().getCurrentTimeSeconds();
		double eps = 1e-6;
		double target = current;
		if (forward) {
			for (double t : marks) {
				if (t > current + eps) {
					target = t;
					break;
				}
			}
		} else {
			for (int i = marks.size() - 1; i >= 0; i--) {
				double t = marks.get(i);
				if (t < current - eps) {
					target = t;
					break;
				}
			}
		}
		seekTo(editor, target);
	}

	public boolean addMarkerAtCurrentTime(TimelineEditor editor) {
		if (editor == null) {
			return false;
		}
		Timeline currentTimeline = timeline.get();
		if (currentTimeline == null) {
			return false;
		}
		double time = editor.getClock().getCurrentTimeSeconds();
		int markerIndex = currentTimeline.getMarkers().size() + 1;
		currentTimeline.addMarker(new TimelineMarker(time, "Marker " + markerIndex));
		return true;
	}

	public double currentPlaybackSpeed(TimelineEditor editor) {
		MusicPlayer music = musicPlayer.get();
		if (music != null) {
			return music.getPlaybackSpeed();
		}
		return editor != null ? editor.getClock().getPlaybackSpeed() : 1.0;
	}

	public void setPlaybackSpeed(TimelineEditor editor, double speed) {
		if (editor != null) {
			editor.getClock().setPlaybackSpeed(speed);
		}
		MusicPlayer music = musicPlayer.get();
		if (music != null) {
			music.setPlaybackSpeed(speed);
		}
	}

	public void setLoopInAt(TimelineToolbarState toolbarState, double nowSeconds, double seekStep) {
		if (toolbarState == null) {
			return;
		}
		toolbarState.setLoopInSeconds(nowSeconds);
		if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= nowSeconds) {
			toolbarState.setLoopOutSeconds(nowSeconds + Math.max(0.1, seekStep));
		}
	}

	public void setLoopOutAt(TimelineToolbarState toolbarState, double nowSeconds, double seekStep) {
		if (toolbarState == null) {
			return;
		}
		double loopIn = toolbarState.getLoopInSeconds();
		toolbarState.setLoopOutSeconds(Math.max(nowSeconds, loopIn + Math.max(0.1, seekStep)));
	}

	public void clearLoopRange(TimelineToolbarState toolbarState) {
		if (toolbarState != null) {
			toolbarState.clearLoopRange();
		}
	}

	public double resolveDuration(TimelineEditor editor) {
		Timeline currentTimeline = timeline.get();
		double timelineDur = currentTimeline != null ? currentTimeline.getDurationSeconds() : 0;
		if (timelineDur > 0) {
			return timelineDur;
		}
		MusicPlayer music = musicPlayer.get();
		double playerDur = music != null ? music.getDurationSeconds() : 0;
		if (playerDur > 0) {
			return playerDur;
		}
		if (editor != null) {
			double clockDur = editor.getClock().getDurationSeconds();
			if (clockDur > 0) {
				return clockDur;
			}
		}
		return DEFAULT_FALLBACK_DURATION_SECONDS;
	}

	public void ensureMusicDurationForPlayback(TimelineEditor editor) {
		MusicPlayer music = musicPlayer.get();
		if (music == null || editor == null) {
			return;
		}
		Timeline currentTimeline = timeline.get();
		if (currentTimeline != null) {
			Object audioPath = currentTimeline.getMetadata("audioPath");
			if (audioPath instanceof String path && !path.isBlank()) {
				String loadedPath = music.getLoadedAudioPath();
				if (!path.equals(loadedPath)) {
					boolean loaded = music.loadAudio(path);
					if (loaded) {
						LOGGER.info("BeatBlock transport: auto-bound timeline audioPath={} before playback", path);
					} else {
						LOGGER.warn("BeatBlock transport: failed to auto-bind timeline audioPath={} reason={}",
							path, music.getLastLoadError());
					}
				}
			}
		}
		if (music.getDurationSeconds() > 0) {
			return;
		}
		double duration = resolveDuration(editor);
		if (duration > 0) {
			music.setDurationSeconds(duration);
		}
	}

	static List<Double> collectNavigationTimes(Timeline timeline) {
		if (timeline == null) {
			return List.of();
		}
		if (!timeline.getMarkers().isEmpty()) {
			List<Double> out = new ArrayList<>();
			for (TimelineMarker marker : timeline.getMarkers()) {
				if (marker != null) {
					out.add(marker.getTimeSeconds());
				}
			}
			Collections.sort(out);
			return out;
		}
		return collectEventTimes(timeline);
	}

	private static List<Double> collectEventTimes(Timeline timeline) {
		List<Double> out = new ArrayList<>();
		for (Track track : timeline.getTracks()) {
			if (track == null) {
				continue;
			}
			for (Clip clip : track.getClips()) {
				if (clip == null) {
					continue;
				}
				for (TimelineEvent event : clip.getEvents()) {
					if (event != null) {
						out.add(event.getTimeSeconds());
					}
				}
			}
		}
		Collections.sort(out);
		return out;
	}
}
