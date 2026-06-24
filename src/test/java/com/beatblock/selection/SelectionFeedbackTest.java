package com.beatblock.selection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionFeedbackTest {

	@Test
	void mergeAfterNewUsesModeSpecificLabel() {
		assertEquals(
			"新建笔刷（球体）：3 个方块",
			SelectionFeedback.mergeAfterNew(SelectionMode.BRUSH, BrushShape.SPHERE, 3)
		);
		assertEquals(
			"新建框选：2 个方块",
			SelectionFeedback.mergeAfterNew(SelectionMode.BOX, BrushShape.SPHERE, 2)
		);
	}

	@Test
	void mergeAfterOperationDispatchesByOperation() {
		assertEquals(
			"加选套索后共 5 个方块",
			SelectionFeedback.mergeAfterOperation(SelectionMode.LASSO, BrushShape.CUBE, SelectionOperation.ADD, 2, 5)
		);
		assertEquals(
			"与线求交后共 1 个方块",
			SelectionFeedback.mergeAfterOperation(SelectionMode.LINE, BrushShape.SPHERE, SelectionOperation.INTERSECT, 0, 1)
		);
	}

	@Test
	void emptyMergeMessagePrefersLayerClaimedNotice() {
		assertEquals(
			"选区内方块均已属于某图层，无法加入选区。",
			SelectionFeedback.emptyMergeMessage(SelectionMode.BOX, BrushShape.SPHERE, 2)
		);
		assertEquals(
			"新建整列：0 个方块",
			SelectionFeedback.emptyMergeMessage(SelectionMode.COLUMN, BrushShape.SPHERE, 0)
		);
	}

	@Test
	void appendSkippedLayerNoticeAppendsSuffix() {
		String merged = SelectionFeedback.appendSkippedLayerNotice("新建框选：4 个方块", 3);
		assertTrue(merged.contains("已跳过 3 个"));
		assertEquals("新建框选：4 个方块", SelectionFeedback.appendSkippedLayerNotice("新建框选：4 个方块", 0));
	}
}
