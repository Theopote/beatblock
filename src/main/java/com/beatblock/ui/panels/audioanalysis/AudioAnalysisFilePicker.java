package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.ui.util.AudioFilePicker;
import imgui.type.ImString;

import java.util.function.Consumer;

/** 委托至 {@link AudioFilePicker}。 */
final class AudioAnalysisFilePicker {

	private AudioAnalysisFilePicker() {
	}

	static String choose(ImString importPath, Consumer<String> onError) {
		return AudioFilePicker.choose(importPath, onError);
	}
}
