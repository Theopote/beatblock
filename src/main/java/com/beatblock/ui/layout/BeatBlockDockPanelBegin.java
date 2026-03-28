package com.beatblock.ui.layout;

import imgui.ImGui;
import imgui.type.ImBoolean;
import java.util.HashMap;
import java.util.Map;

/**
 * 停靠在 Dockspace 内时不向 {@link ImGui#begin(String, ImBoolean, int)} 传入 {@code pOpen}，
 * 避免标签上出现「×」；窗口拖出为浮动后，下一帧起传入 {@code pOpen} 以显示「×」。
 * 依据上一帧末 {@link ImGui#isWindowDocked()} 记录状态。
 */
public final class BeatBlockDockPanelBegin {

	private static final Object LOCK = new Object();
	private static final Map<String, Boolean> LAST_WAS_DOCKED = new HashMap<>();

	private BeatBlockDockPanelBegin() {}

	/**
	 * 面板本帧不渲染时调用，使下次打开时默认仍按「已停靠」处理（不先误显 ×）。
	 */
	public static void markClosed(String windowName) {
		synchronized (LOCK) {
			LAST_WAS_DOCKED.put(windowName, true);
		}
	}

	/**
	 * @return 若窗口未展开（折叠等）则为 false，且已调用 {@link ImGui#end()}，调用方勿再 end。
	 */
	public static boolean begin(String windowName, ImBoolean pOpen, int flags) {
		if (!pOpen.get()) {
			markClosed(windowName);
			return false;
		}
		boolean showClose;
		synchronized (LOCK) {
			showClose = !LAST_WAS_DOCKED.getOrDefault(windowName, true);
		}
		boolean opened;
		if (showClose) {
			opened = ImGui.begin(windowName, pOpen, flags);
		} else {
			opened = ImGui.begin(windowName, flags);
		}
		if (!opened) {
			ImGui.end();
			return false;
		}
		return true;
	}

	public static void endWithRecord(String windowName) {
		boolean docked = ImGui.isWindowDocked();
		ImGui.end();
		synchronized (LOCK) {
			LAST_WAS_DOCKED.put(windowName, docked);
		}
	}
}
