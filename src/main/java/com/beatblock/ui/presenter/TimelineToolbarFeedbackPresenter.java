package com.beatblock.ui.presenter;

import java.util.function.LongSupplier;

/**
 * 时间线工具栏短暂反馈（Auto Map / Binding / 模板应用等操作结果）。
 * ImGui 渲染留在 {@link com.beatblock.timeline.rendering.TimelineToolbar}。
 */
public final class TimelineToolbarFeedbackPresenter {

	public static final long HOLD_MS = 1700L;
	public static final long FADE_MS = 1300L;

	public record FeedbackViewState(String message, boolean success, float alpha) {
		public static final FeedbackViewState EMPTY = new FeedbackViewState("", false, 0f);

		public boolean visible() {
			return message != null && !message.isBlank() && alpha > 0f;
		}
	}

	private static final class Slot {
		String message = "";
		boolean success;
		long atMs;
	}

	private final LongSupplier clock;
	private final Slot toolAction = new Slot();
	private final Slot templateApply = new Slot();

	public TimelineToolbarFeedbackPresenter() {
		this(System::currentTimeMillis);
	}

	TimelineToolbarFeedbackPresenter(LongSupplier clock) {
		this.clock = clock;
	}

	public void setToolActionFeedback(String message, boolean success) {
		apply(toolAction, message, success);
	}

	public void setTemplateApplyFeedback(String message, boolean success) {
		apply(templateApply, message, success);
	}

	public FeedbackViewState viewToolActionFeedback() {
		return view(toolAction, clock.getAsLong());
	}

	public FeedbackViewState viewTemplateApplyFeedback() {
		return view(templateApply, clock.getAsLong());
	}

	private void apply(Slot slot, String message, boolean success) {
		slot.message = message != null ? message : "";
		slot.success = success;
		slot.atMs = clock.getAsLong();
	}

	private FeedbackViewState view(Slot slot, long nowMs) {
		if (slot.message == null || slot.message.isBlank()) {
			return FeedbackViewState.EMPTY;
		}
		long ageMs = Math.max(0L, nowMs - slot.atMs);
		long ttlMs = HOLD_MS + FADE_MS;
		if (ageMs >= ttlMs) {
			slot.message = "";
			return FeedbackViewState.EMPTY;
		}

		float alpha = 1.0f;
		if (ageMs > HOLD_MS) {
			float t = (ageMs - HOLD_MS) / (float) FADE_MS;
			alpha = Math.max(0f, 1.0f - t);
		}
		return new FeedbackViewState(slot.message, slot.success, alpha);
	}
}
