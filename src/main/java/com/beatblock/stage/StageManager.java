package com.beatblock.stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 管理当前世界的舞台区域列表与当前选中的舞台。
 */
public class StageManager {

	private final List<StageZone> zones = new ArrayList<>();
	private StageZone currentStage;

	public void addZone(StageZone zone) {
		if (zone != null && !zones.contains(zone)) {
			zones.add(zone);
		}
	}

	public void removeZone(StageZone zone) {
		zones.remove(zone);
		if (currentStage == zone) {
			currentStage = zones.isEmpty() ? null : zones.getFirst();
		}
	}

	public List<StageZone> getZones() {
		return Collections.unmodifiableList(zones);
	}

	public void setCurrentStage(StageZone zone) {
		this.currentStage = zone;
	}

	public Optional<StageZone> getCurrentStage() {
		return Optional.ofNullable(currentStage);
	}

	public void clear() {
		zones.clear();
		currentStage = null;
	}
}
