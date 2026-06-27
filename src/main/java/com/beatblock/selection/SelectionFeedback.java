package com.beatblock.selection;

import com.beatblock.ui.i18n.BBTexts;

/**
 * 选区操作的用户可见反馈文案（与 UI 面板解耦，供 {@link BeatBlockSelectionManager} 使用）。
 */
public final class SelectionFeedback {

	private SelectionFeedback() {}

	public static String mergeAfterNew(SelectionMode mode, BrushShape brushShape, int count) {
		return switch (mode) {
			case BOX -> BBTexts.get("beatblock.selection.feedback.new.box", count);
			case LINE -> BBTexts.get("beatblock.selection.feedback.new.line", count);
			case CONNECTED -> BBTexts.get("beatblock.selection.feedback.new.connected", count);
			case COLUMN -> BBTexts.get("beatblock.selection.feedback.new.column", count);
			case PLANE_SLICE -> BBTexts.get("beatblock.selection.feedback.new.plane_slice", count);
			case SELECTION_WAND -> BBTexts.get("beatblock.selection.feedback.new.selection_wand", count);
			case BRUSH -> BBTexts.get(
				"beatblock.selection.feedback.new.brush",
				brushShapeLabel(brushShape),
				count
			);
			case LASSO -> BBTexts.get("beatblock.selection.feedback.new.lasso", count);
			default -> BBTexts.get("beatblock.selection.feedback.new.generic", count);
		};
	}

	public static String mergeAfterAdd(SelectionMode mode, int selectionSize) {
		return switch (mode) {
			case BOX -> BBTexts.get("beatblock.selection.feedback.add.box", selectionSize);
			case LINE -> BBTexts.get("beatblock.selection.feedback.add.line", selectionSize);
			case CONNECTED -> BBTexts.get("beatblock.selection.feedback.add.connected", selectionSize);
			case COLUMN -> BBTexts.get("beatblock.selection.feedback.add.column", selectionSize);
			case PLANE_SLICE -> BBTexts.get("beatblock.selection.feedback.add.plane_slice", selectionSize);
			case SELECTION_WAND -> BBTexts.get("beatblock.selection.feedback.add.selection_wand", selectionSize);
			case BRUSH -> BBTexts.get("beatblock.selection.feedback.add.brush", selectionSize);
			case LASSO -> BBTexts.get("beatblock.selection.feedback.add.lasso", selectionSize);
			default -> BBTexts.get("beatblock.selection.feedback.add.generic", selectionSize);
		};
	}

	public static String mergeAfterSubtract(SelectionMode mode, int selectionSize) {
		return switch (mode) {
			case BOX -> BBTexts.get("beatblock.selection.feedback.subtract.box", selectionSize);
			case LINE -> BBTexts.get("beatblock.selection.feedback.subtract.line", selectionSize);
			case CONNECTED -> BBTexts.get("beatblock.selection.feedback.subtract.connected", selectionSize);
			case COLUMN -> BBTexts.get("beatblock.selection.feedback.subtract.column", selectionSize);
			case PLANE_SLICE -> BBTexts.get("beatblock.selection.feedback.subtract.plane_slice", selectionSize);
			case SELECTION_WAND -> BBTexts.get("beatblock.selection.feedback.subtract.selection_wand", selectionSize);
			case BRUSH -> BBTexts.get("beatblock.selection.feedback.subtract.brush", selectionSize);
			case LASSO -> BBTexts.get("beatblock.selection.feedback.subtract.lasso", selectionSize);
			default -> BBTexts.get("beatblock.selection.feedback.subtract.generic", selectionSize);
		};
	}

	public static String mergeAfterIntersect(SelectionMode mode, int selectionSize) {
		return switch (mode) {
			case BOX -> BBTexts.get("beatblock.selection.feedback.intersect.box", selectionSize);
			case LINE -> BBTexts.get("beatblock.selection.feedback.intersect.line", selectionSize);
			case CONNECTED -> BBTexts.get("beatblock.selection.feedback.intersect.connected", selectionSize);
			case COLUMN -> BBTexts.get("beatblock.selection.feedback.intersect.column", selectionSize);
			case PLANE_SLICE -> BBTexts.get("beatblock.selection.feedback.intersect.plane_slice", selectionSize);
			case SELECTION_WAND -> BBTexts.get("beatblock.selection.feedback.intersect.selection_wand", selectionSize);
			case BRUSH -> BBTexts.get("beatblock.selection.feedback.intersect.brush", selectionSize);
			case LASSO -> BBTexts.get("beatblock.selection.feedback.intersect.lasso", selectionSize);
			default -> BBTexts.get("beatblock.selection.feedback.intersect.generic", selectionSize);
		};
	}

	public static String mergeAfterOperation(
		SelectionMode mode,
		BrushShape brushShape,
		SelectionOperation op,
		int incomingCount,
		int selectionSize
	) {
		return switch (op) {
			case NEW -> mergeAfterNew(mode, brushShape, incomingCount);
			case ADD -> mergeAfterAdd(mode, selectionSize);
			case SUBTRACT -> mergeAfterSubtract(mode, selectionSize);
			case INTERSECT -> mergeAfterIntersect(mode, selectionSize);
		};
	}

	public static String emptyMergeMessage(SelectionMode mode, BrushShape brushShape, int skippedLayerCount) {
		if (skippedLayerCount > 0) {
			return BBTexts.get("beatblock.selection.feedback.all_in_layers");
		}
		return mergeAfterNew(mode, brushShape, 0);
	}

	public static String appendSkippedLayerNotice(String message, int skippedLayerCount) {
		if (skippedLayerCount <= 0 || message == null || message.isBlank()) {
			return message;
		}
		return message + BBTexts.get("beatblock.selection.feedback.skipped_layers", skippedLayerCount);
	}

	private static String brushShapeLabel(BrushShape brushShape) {
		return switch (brushShape != null ? brushShape : BrushShape.SPHERE) {
			case SPHERE -> BBTexts.get("beatblock.tool.shape.sphere");
			case CUBE -> BBTexts.get("beatblock.tool.shape.cube");
		};
	}
}
