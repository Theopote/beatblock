package com.beatblock.timeline;

import com.beatblock.ui.i18n.BBTexts;

/**
 * Marker 类型：决定语义和默认显示颜色。
 */
public enum MarkerType {
	GENERIC("普通", 0xEE_FF_D4_66),
	SECTION("段落", 0xEE_66_DD_FF),
	DROP("Drop", 0xEE_66_FF_88),
	CAMERA("镜头", 0xEE_FF_99_66),
	FX("特效", 0xEE_D2_88_FF);

	private final String displayName;
	private final int colorAbgr;

	MarkerType(String displayName, int colorAbgr) {
		this.displayName = displayName;
		this.colorAbgr = colorAbgr;
	}

	public String getDisplayName() {
		return BBTexts.get(switch (this) {
			case GENERIC -> "beatblock.marker_type.generic";
			case SECTION -> "beatblock.marker_type.section";
			case DROP -> "beatblock.marker_type.drop";
			case CAMERA -> "beatblock.marker_type.camera";
			case FX -> "beatblock.marker_type.fx";
		});
	}

	public int getColorAbgr() {
		return colorAbgr;
	}

	public static MarkerType fromName(String name) {
		if (name == null || name.isBlank()) return GENERIC;
		for (MarkerType type : values()) {
			if (type.name().equalsIgnoreCase(name.trim())) return type;
		}
		return GENERIC;
	}

	public static String[] displayNames() {
		return BBTexts.labels(
			"beatblock.marker_type.generic",
			"beatblock.marker_type.section",
			"beatblock.marker_type.drop",
			"beatblock.marker_type.camera",
			"beatblock.marker_type.fx"
		);
	}
}
