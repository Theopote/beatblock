package com.beatblock.ui.presenter;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.rendering.TimelineToolbarState;

import java.util.function.Supplier;

/**
 * 时间线编辑器命令：撤销/重做、播放头跳转、循环区设置。
 */
public final class TimelineEditorPresenter {

	public record UndoRedoViewState(boolean canUndo, boolean canRedo) {}

	@FunctionalInterface
	public interface MusicSeek {
		void seekToSeconds(double seconds);
	}

	private final Supplier<TimelineEditor> timelineEditor;
	private final MusicSeek musicSeek;

	public TimelineEditorPresenter(Supplier<TimelineEditor> timelineEditor, MusicSeek musicSeek) {
		this.timelineEditor = timelineEditor;
		this.musicSeek = musicSeek;
	}

	public UndoRedoViewState undoRedoState() {
		CommandManager commands = commandManager();
		if (commands == null) {
			return new UndoRedoViewState(false, false);
		}
		return new UndoRedoViewState(commands.canUndo(), commands.canRedo());
	}

	public boolean undo() {
		CommandManager commands = commandManager();
		if (commands == null || !commands.canUndo()) {
			return false;
		}
		commands.undo();
		return true;
	}

	public boolean redo() {
		CommandManager commands = commandManager();
		if (commands == null || !commands.canRedo()) {
			return false;
		}
		commands.redo();
		return true;
	}

	public boolean seekPlayback(double timeSeconds) {
		TimelineEditor editor = timelineEditor.get();
		if (editor == null) {
			return false;
		}
		double time = Math.max(0.0, timeSeconds);
		editor.getClock().seek(time);
		if (musicSeek != null) {
			musicSeek.seekToSeconds(time);
		}
		return true;
	}

	public boolean setLoopIn(double timeSeconds) {
		TimelineToolbarState toolbarState = toolbarState();
		if (toolbarState == null) {
			return false;
		}
		double time = Math.max(0.0, timeSeconds);
		toolbarState.setLoopInSeconds(time);
		if (toolbarState.getLoopOutSeconds() <= time) {
			toolbarState.setLoopOutSeconds(time + 0.1);
		}
		toolbarState.setLoop(true);
		return true;
	}

	public boolean setLoopOut(double timeSeconds) {
		TimelineToolbarState toolbarState = toolbarState();
		if (toolbarState == null) {
			return false;
		}
		double loopIn = toolbarState.getLoopInSeconds();
		toolbarState.setLoopOutSeconds(Math.max(timeSeconds, loopIn + 0.1));
		toolbarState.setLoop(true);
		return true;
	}

	public boolean applyLoopRange(double startSeconds, double endSeconds, boolean seekToStart) {
		TimelineToolbarState toolbarState = toolbarState();
		if (toolbarState == null) {
			return false;
		}
		double start = Math.min(startSeconds, endSeconds);
		double end = Math.max(startSeconds, endSeconds);
		if (end <= start) {
			return false;
		}
		toolbarState.setLoopInSeconds(start);
		toolbarState.setLoopOutSeconds(end);
		toolbarState.setLoop(true);
		if (seekToStart) {
			seekPlayback(start);
		}
		return true;
	}

	private CommandManager commandManager() {
		TimelineEditor editor = timelineEditor.get();
		return editor != null ? editor.getCommandManager() : null;
	}

	private TimelineToolbarState toolbarState() {
		TimelineEditor editor = timelineEditor.get();
		return editor != null ? editor.getToolbarState() : null;
	}

	public TimelineEditor editorOrNull() {
		return timelineEditor.get();
	}
}
