package com.beatblock.ui.preferences;

/** 各主题的 ImGui 面板配色。 */
public record UiThemeColors(
	float[] windowBg,
	float[] text,
	float[] titleBg,
	float[] titleBgActive,
	float[] titleBgCollapsed
) {

	public static UiThemeColors forTheme(UiTheme theme) {
		return switch (theme != null ? theme : UiTheme.DARK) {
			case LIGHT -> new UiThemeColors(
				rgba(0.94f, 0.94f, 0.96f, 1f),
				rgba(0.12f, 0.12f, 0.14f, 1f),
				rgba(0.88f, 0.88f, 0.90f, 1f),
				rgba(0.82f, 0.84f, 0.90f, 1f),
				rgba(0.90f, 0.90f, 0.92f, 1f)
			);
			case HIGH_CONTRAST -> new UiThemeColors(
				rgba(0.02f, 0.02f, 0.04f, 1f),
				rgba(1f, 1f, 1f, 1f),
				rgba(0.10f, 0.10f, 0.14f, 1f),
				rgba(0.20f, 0.35f, 0.65f, 1f),
				rgba(0.08f, 0.08f, 0.10f, 1f)
			);
			case DARK -> new UiThemeColors(
				rgba(0.09f, 0.09f, 0.10f, 1f),
				rgba(0.86f, 0.86f, 0.86f, 1f),
				rgba(0.125f, 0.125f, 0.14f, 1f),
				rgba(0.16f, 0.16f, 0.18f, 1f),
				rgba(0.11f, 0.11f, 0.12f, 1f)
			);
		};
	}

	private static float[] rgba(float r, float g, float b, float a) {
		return new float[] { r, g, b, a };
	}
}
