package com.beatblock.timeline.editor;

/**
 * 命令接口：Undo/Redo 基础。
 */
public interface Command {

	void execute();

	void undo();
}
