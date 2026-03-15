package com.beatblock.automap.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动镜头导演：根据音乐段落与风格在关键时间点生成镜头事件（Orbit / Zoom / Pan / Shake）。
 * Build → 慢慢靠近；Drop → 快速旋转/震动。
 */
public final class CameraDirector {

	/**
	 * 根据段落、BPM、总时长与风格生成镜头关键帧时间点及动作。
	 */
	public static List<CameraEvent> generate(List<MusicSection> sections, float bpm,
	                                          double durationSeconds, AutoMapStyle style, boolean enabled) {
		List<CameraEvent> out = new ArrayList<>();
		if (!enabled || sections == null || sections.isEmpty()) return out;
		double beatDuration = 60.0 / Math.max(1f, bpm);
		for (MusicSection sec : sections) {
			switch (sec.getType()) {
				case INTRO -> {
					out.add(new CameraEvent(sec.getStartSeconds(), CameraAction.HOLD));
					out.add(new CameraEvent(sec.getStartSeconds() + sec.getDurationSeconds() * 0.5, CameraAction.PAN));
				}
				case BUILD -> {
					out.add(new CameraEvent(sec.getStartSeconds(), CameraAction.ZOOM_IN));
					out.add(new CameraEvent(sec.getEndSeconds() - 0.5, CameraAction.HOLD));
				}
				case DROP -> {
					out.add(new CameraEvent(sec.getStartSeconds(), CameraAction.ORBIT));
					out.add(new CameraEvent(sec.getStartSeconds() + beatDuration * 4, CameraAction.SHAKE));
					out.add(new CameraEvent(sec.getEndSeconds() - 0.3, CameraAction.HOLD));
				}
				case BREAK -> {
					out.add(new CameraEvent(sec.getStartSeconds(), CameraAction.ZOOM_OUT));
					out.add(new CameraEvent(sec.getEndSeconds(), CameraAction.HOLD));
				}
				case OUTRO -> {
					out.add(new CameraEvent(sec.getStartSeconds(), CameraAction.PAN));
					out.add(new CameraEvent(durationSeconds - 0.5, CameraAction.HOLD));
				}
				default -> {
					out.add(new CameraEvent(sec.getStartSeconds(), CameraAction.HOLD));
				}
			}
		}
		// 去重：同一时间只保留一个
		List<CameraEvent> dedup = new ArrayList<>();
		double lastT = -1;
		for (CameraEvent e : out) {
			if (Math.abs(e.getTimeSeconds() - lastT) < 0.05) continue;
			dedup.add(e);
			lastT = e.getTimeSeconds();
		}
		return dedup;
	}
}
