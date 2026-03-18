package com.beatblock.ui.icons;

/**
 * BeatBlock UI Icons (BeatBlock.ttf).
 * <p>
 * This mod uses a custom icon font and references icons via PUA codepoints (U+Fxxx),
 * instead of relying on system emoji rendering.
 */
public final class Icons {
	private Icons() {
		throw new AssertionError("Cannot instantiate Icons");
	}

	public static final String EYE = "\uF067";        // 👁
	public static final String LOCK = "\uF095";       // 🔒
	public static final String CHECK = "\uF054";      // ✔
	public static final String MENU = "\uF102";       // ☰
	public static final String MUSIC_NOTE = "\uF0ED"; // ♪
}

