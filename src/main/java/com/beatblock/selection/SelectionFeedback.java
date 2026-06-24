package com.beatblock.selection;

/**
 * 选区操作的用户可见反馈文案（与 UI 面板解耦，供 {@link BeatBlockSelectionManager} 使用）。
 */
public final class SelectionFeedback {

	private SelectionFeedback() {}

	public static String mergeAfterNew(SelectionMode mode, BrushShape brushShape, int count) {
		return switch (mode) {
			case BOX -> "新建框选：" + count + " 个方块";
			case LINE -> "新建线选：" + count + " 个方块";
			case CONNECTED -> "新建连通选区：" + count + " 个方块";
			case COLUMN -> "新建整列：" + count + " 个方块";
			case PLANE_SLICE -> "新建平面切片：" + count + " 个方块";
			case SELECTION_WAND -> "新建选区魔棒：" + count + " 个方块";
			case BRUSH -> "新建笔刷（" + brushShapeLabel(brushShape) + "）：" + count + " 个方块";
			case LASSO -> "新建套索：" + count + " 个方块";
			default -> "新建选区：" + count + " 个方块";
		};
	}

	public static String mergeAfterAdd(SelectionMode mode, int selectionSize) {
		return switch (mode) {
			case BOX -> "加选框后共 " + selectionSize + " 个方块";
			case LINE -> "加选线后共 " + selectionSize + " 个方块";
			case CONNECTED -> "加选连通区域后共 " + selectionSize + " 个方块";
			case COLUMN -> "加选整列后共 " + selectionSize + " 个方块";
			case PLANE_SLICE -> "加选切片后共 " + selectionSize + " 个方块";
			case SELECTION_WAND -> "加选选区魔棒后共 " + selectionSize + " 个方块";
			case BRUSH -> "加选笔刷后共 " + selectionSize + " 个方块";
			case LASSO -> "加选套索后共 " + selectionSize + " 个方块";
			default -> "加选后共 " + selectionSize + " 个方块";
		};
	}

	public static String mergeAfterSubtract(SelectionMode mode, int selectionSize) {
		return switch (mode) {
			case BOX -> "减选框后共 " + selectionSize + " 个方块";
			case LINE -> "减选线后共 " + selectionSize + " 个方块";
			case CONNECTED -> "减选连通区域后共 " + selectionSize + " 个方块";
			case COLUMN -> "减选整列后共 " + selectionSize + " 个方块";
			case PLANE_SLICE -> "减选切片后共 " + selectionSize + " 个方块";
			case SELECTION_WAND -> "减选选区魔棒后共 " + selectionSize + " 个方块";
			case BRUSH -> "减选笔刷后共 " + selectionSize + " 个方块";
			case LASSO -> "减选套索后共 " + selectionSize + " 个方块";
			default -> "减选后共 " + selectionSize + " 个方块";
		};
	}

	public static String mergeAfterIntersect(SelectionMode mode, int selectionSize) {
		return switch (mode) {
			case BOX -> "与框求交后共 " + selectionSize + " 个方块";
			case LINE -> "与线求交后共 " + selectionSize + " 个方块";
			case CONNECTED -> "与连通区域求交后共 " + selectionSize + " 个方块";
			case COLUMN -> "与整列求交后共 " + selectionSize + " 个方块";
			case PLANE_SLICE -> "与切片求交后共 " + selectionSize + " 个方块";
			case SELECTION_WAND -> "与选区魔棒结果求交后共 " + selectionSize + " 个方块";
			case BRUSH -> "与笔刷求交后共 " + selectionSize + " 个方块";
			case LASSO -> "与套索求交后共 " + selectionSize + " 个方块";
			default -> "求交后共 " + selectionSize + " 个方块";
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
			return "选区内方块均已属于某图层，无法加入选区。";
		}
		return mergeAfterNew(mode, brushShape, 0);
	}

	public static String appendSkippedLayerNotice(String message, int skippedLayerCount) {
		if (skippedLayerCount <= 0 || message == null || message.isBlank()) {
			return message;
		}
		return message + String.format("（已跳过 %d 个已属于图层的方块）", skippedLayerCount);
	}

	private static String brushShapeLabel(BrushShape brushShape) {
		return switch (brushShape != null ? brushShape : BrushShape.SPHERE) {
			case SPHERE -> "球体";
			case CUBE -> "立方";
		};
	}
}
