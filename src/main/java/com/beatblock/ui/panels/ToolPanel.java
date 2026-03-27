package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 左侧工具面板。提供 Smart Auto Map：点击后弹出设置（风格/复杂度/镜头/粒子），生成完整编排。
 */
public class ToolPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private boolean showAutoMapSettings = false;
	private final AutoMapSettingsPanel autoMapSettingsPanel = new AutoMapSettingsPanel();
	private String selectedMarkerId;
	private final ImString markerNameBuffer = new ImString(128);
	private final ImString markerTimeBuffer = new ImString(32);
	private final ImInt markerTypeIndex = new ImInt(0);
	private final ImString stageObjectNameBuffer = new ImString(64);
	private BlockPos selectionPosA;
	private BlockPos selectionPosB;
	private String stageObjectMessage;
	private static final int MAX_STAGE_OBJECT_BLOCKS = 32768;
	private static final String[] MARKER_TYPE_LABELS = MarkerType.displayNames();

	/** 由菜单栏「演出 → Smart Auto Map」调用，打开设置弹窗 */
	public void setShowAutoMapSettings(boolean show) {
		this.showAutoMapSettings = show;
	}
	/** 上次生成统计 */
	private SmartAutoMapEngine.AutoMapResult lastAutoMapResult = null;

	public void render() {
		if (!ImGui.begin(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}
		ImGui.text("工具");
		ImGui.separator();

		if (ImGui.button("Smart Auto Map")) {
			showAutoMapSettings = true;
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("自动编排：根据音乐生成方块动画、摄像机、粒子与节奏结构（先导入音乐）");
		}
		if (lastAutoMapResult != null) {
			ImGui.sameLine();
			ImGui.textDisabled(String.format("动画 %d, 镜头 %d, 粒子 %d",
				lastAutoMapResult.getAnimationEvents(),
				lastAutoMapResult.getCameraEvents(),
				lastAutoMapResult.getParticleEvents()));
		}
		renderStageObjectCreator();

		renderMarkerManager();

		ImGui.spacing();
		ImGui.textWrapped("选择、画笔、橡皮等工具将在此列出。");
		ImGui.end();

		// 设置弹窗（独立窗口）
		if (showAutoMapSettings) {
			boolean done = autoMapSettingsPanel.render(res -> lastAutoMapResult = res);
			if (done) showAutoMapSettings = false;
		}
	}

	private void renderStageObjectCreator() {
		ImGui.spacing();
		ImGui.separator();
		ImGui.text("Stage Object");

		if (stageObjectNameBuffer.get() == null || stageObjectNameBuffer.get().isBlank()) {
			stageObjectNameBuffer.set("selection_object");
		}

		if (ImGui.button("Set A (准星方块)##stageObjSetA")) {
			selectionPosA = readCrosshairBlockPos();
			stageObjectMessage = selectionPosA != null ? "已设置 A 点。" : "未命中方块，无法设置 A 点。";
		}
		ImGui.sameLine();
		if (ImGui.button("Set B (准星方块)##stageObjSetB")) {
			selectionPosB = readCrosshairBlockPos();
			stageObjectMessage = selectionPosB != null ? "已设置 B 点。" : "未命中方块，无法设置 B 点。";
		}
		ImGui.sameLine();
		if (ImGui.button("Clear##stageObjClearSelection")) {
			selectionPosA = null;
			selectionPosB = null;
			stageObjectMessage = "已清空选区。";
		}

		ImGui.textDisabled("A: " + formatPos(selectionPosA));
		ImGui.textDisabled("B: " + formatPos(selectionPosB));

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("对象名称##stageObjName", stageObjectNameBuffer);

		boolean canCreate = selectionPosA != null && selectionPosB != null;
		if (!canCreate) ImGui.beginDisabled();
		if (ImGui.button("从选区创建 StageObject##stageObjCreate", -1f, 0f)) {
			createStageObjectFromSelection();
		}
		if (!canCreate) ImGui.endDisabled();

		if (stageObjectMessage != null && !stageObjectMessage.isBlank()) {
			ImGui.textWrapped(stageObjectMessage);
		}
	}

	private void createStageObjectFromSelection() {
		if (BeatBlock.blockAnimationEngine == null) {
			stageObjectMessage = "动画引擎未初始化，无法创建对象。";
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world == null) {
			stageObjectMessage = "当前无世界上下文，无法读取选区。";
			return;
		}
		if (selectionPosA == null || selectionPosB == null) {
			stageObjectMessage = "请先设置 A/B 两个选区点。";
			return;
		}

		List<BlockPos> blocks = collectSelectionBlocks(selectionPosA, selectionPosB);
		if (blocks.isEmpty()) {
			stageObjectMessage = "选区为空，未创建对象。";
			return;
		}

		String name = stageObjectNameBuffer.get() != null ? stageObjectNameBuffer.get().trim() : "";
		if (name.isEmpty()) name = "selection_object";
		String id = buildUniqueStageObjectId(name);

		StageObject obj = StageObjectSystem.fromBlocks(id, name, blocks);
		BeatBlock.blockAnimationEngine.getStageObjectSystem().register(obj);
		stageObjectMessage = String.format(Locale.ROOT, "已创建 StageObject: %s (%d blocks)", id, blocks.size());
	}

	private List<BlockPos> collectSelectionBlocks(BlockPos a, BlockPos b) {
		List<BlockPos> out = new ArrayList<>();
		if (a == null || b == null) return out;

		int minX = Math.min(a.getX(), b.getX());
		int maxX = Math.max(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY());
		int maxY = Math.max(a.getY(), b.getY());
		int minZ = Math.min(a.getZ(), b.getZ());
		int maxZ = Math.max(a.getZ(), b.getZ());

		long volume = (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
		if (volume > MAX_STAGE_OBJECT_BLOCKS) {
			stageObjectMessage = String.format(Locale.ROOT,
				"选区过大（%d blocks），上限为 %d。", volume, MAX_STAGE_OBJECT_BLOCKS);
			return out;
		}

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					out.add(new BlockPos(x, y, z));
				}
			}
		}
		return out;
	}

	private String buildUniqueStageObjectId(String name) {
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
		if (base.isBlank()) base = "selection_object";
		if (base.startsWith("_")) base = base.substring(1);
		if (base.endsWith("_")) base = base.substring(0, base.length() - 1);

		var sys = BeatBlock.blockAnimationEngine.getStageObjectSystem();
		String candidate = base;
		int suffix = 2;
		while (sys.get(candidate) != null) {
			candidate = base + "_" + suffix;
			suffix++;
		}
		return candidate;
	}

	private BlockPos readCrosshairBlockPos() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null) return null;
		HitResult hit = mc.crosshairTarget;
		if (!(hit instanceof BlockHitResult bhr)) return null;
		return bhr.getBlockPos().toImmutable();
	}

	private static String formatPos(BlockPos pos) {
		if (pos == null) return "(未设置)";
		return String.format(Locale.ROOT, "%d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
	}

	private void renderMarkerManager() {
		Timeline timeline = BeatBlock.timeline;
		if (timeline == null) return;

		ImGui.spacing();
		ImGui.separator();
		ImGui.text("Marker");

		if (timeline.getMarkers().isEmpty()) {
			selectedMarkerId = null;
			ImGui.textDisabled("暂无 Marker。可在工具条创建，或双击标尺空白处创建。");
			return;
		}

		if (selectedMarkerId != null && timeline.findMarkerIndexById(selectedMarkerId) < 0) {
			selectedMarkerId = null;
		}

		if (ImGui.beginChild("##MarkerList", 0, 110, true)) {
			for (TimelineMarker marker : timeline.getMarkers()) {
				if (marker == null) continue;
				String markerId = marker.getId();
				boolean selected = markerId.equals(selectedMarkerId);
				String label = String.format(Locale.ROOT, "[%s] %.2fs  %s##%s",
					marker.getType().getDisplayName(),
					marker.getTimeSeconds(),
					marker.getName() == null || marker.getName().isBlank() ? "(unnamed)" : marker.getName(),
					markerId);
				ImGui.pushStyleColor(ImGuiCol.Text, abgrToR(marker.getType().getColorAbgr()), abgrToG(marker.getType().getColorAbgr()), abgrToB(marker.getType().getColorAbgr()), abgrToA(marker.getType().getColorAbgr()));
				if (ImGui.selectable(label, selected)) {
					selectedMarkerId = markerId;
					markerNameBuffer.set(marker.getName());
					markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", marker.getTimeSeconds()));
					markerTypeIndex.set(marker.getType().ordinal());
				}
				ImGui.popStyleColor();
			}
		}
		ImGui.endChild();

		int markerIndex = selectedMarkerId != null ? timeline.findMarkerIndexById(selectedMarkerId) : -1;
		if (markerIndex < 0) return;

		TimelineMarker marker = timeline.getMarkers().get(markerIndex);
		ImGui.textDisabled("选中 Marker");
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("名称##markerName", markerNameBuffer);
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("时间(秒)##markerTime", markerTimeBuffer);
		markerTypeIndex.set(Math.max(0, Math.min(marker.getType().ordinal(), MARKER_TYPE_LABELS.length - 1)));
		if (ImGui.combo("类型##markerType", markerTypeIndex, MARKER_TYPE_LABELS)) {
			applyMarkerEdits(timeline, selectedMarkerId, marker);
		}

		if (ImGui.button("Jump##toolMarkerJump")) {
			jumpToMarker(marker);
		}
		ImGui.sameLine();
		if (ImGui.button("Apply##toolMarkerApply")) {
			applyMarkerEdits(timeline, selectedMarkerId, marker);
		}
		ImGui.sameLine();
		if (ImGui.button("Delete##toolMarkerDelete")) {
			timeline.removeMarker(selectedMarkerId);
			selectedMarkerId = null;
		}

		ImGui.spacing();
		ImGui.textDisabled("循环区");
		if (ImGui.button("Set In##toolMarkerSetIn")) {
			setLoopIn(marker.getTimeSeconds());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("将当前 Marker 设为循环起点");
		}
		ImGui.sameLine();
		if (ImGui.button("Set Out##toolMarkerSetOut")) {
			setLoopOut(marker.getTimeSeconds());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("将当前 Marker 设为循环终点");
		}

		TimelineMarker prevMarker = markerIndex > 0 ? timeline.getMarkers().get(markerIndex - 1) : null;
		TimelineMarker nextMarker = markerIndex + 1 < timeline.getMarkers().size() ? timeline.getMarkers().get(markerIndex + 1) : null;

		if (ImGui.button("Prev->This##toolMarkerLoopPrev", 0, 0)) {
			applyLoopRange(prevMarker, marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(prevMarker != null ? "将上一个 Marker 到当前 Marker 设为循环区" : "没有上一个 Marker");
		}
		ImGui.sameLine();
		if (ImGui.button("This->Next##toolMarkerLoopNext", 0, 0)) {
			applyLoopRange(marker, nextMarker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(nextMarker != null ? "将当前 Marker 到下一个 Marker 设为循环区" : "没有下一个 Marker");
		}
	}

	private void jumpToMarker(TimelineMarker marker) {
		if (marker == null || BeatBlock.timelineEditor == null) return;
		double t = marker.getTimeSeconds();
		BeatBlock.timelineEditor.getClock().seek(t);
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setCurrentTimeSeconds(t);
		}
	}

	private void applyMarkerEdits(Timeline timeline, String markerId, TimelineMarker marker) {
		if (timeline == null || markerId == null || marker == null) return;
		String name = markerNameBuffer.get() == null ? "" : markerNameBuffer.get().trim();
		double timeSeconds = marker.getTimeSeconds();
		MarkerType type = MarkerType.values()[Math.max(0, Math.min(markerTypeIndex.get(), MarkerType.values().length - 1))];
		try {
			String raw = markerTimeBuffer.get();
			if (raw != null && !raw.isBlank()) {
				timeSeconds = Math.max(0, Double.parseDouble(raw.trim()));
			}
		} catch (NumberFormatException ignored) {
			markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", marker.getTimeSeconds()));
			return;
		}
		timeline.updateMarker(markerId, timeSeconds, name, type);
		int newIndex = timeline.findMarkerIndexById(markerId);
		if (newIndex >= 0) {
			TimelineMarker updated = timeline.getMarkers().get(newIndex);
			markerNameBuffer.set(updated.getName());
			markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", updated.getTimeSeconds()));
			markerTypeIndex.set(updated.getType().ordinal());
		}
	}

	private static float abgrToR(int abgr) { return ((abgr >> 0) & 0xFF) / 255f; }
	private static float abgrToG(int abgr) { return ((abgr >> 8) & 0xFF) / 255f; }
	private static float abgrToB(int abgr) { return ((abgr >> 16) & 0xFF) / 255f; }
	private static float abgrToA(int abgr) { return ((abgr >> 24) & 0xFF) / 255f; }

	private void setLoopIn(double timeSeconds) {
		if (BeatBlock.timelineEditor == null) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		toolbarState.setLoopInSeconds(timeSeconds);
		if (toolbarState.getLoopOutSeconds() > 0 && toolbarState.getLoopOutSeconds() <= timeSeconds) {
			toolbarState.setLoopOutSeconds(timeSeconds + 0.1);
		}
		toolbarState.setLoop(true);
	}

	private void setLoopOut(double timeSeconds) {
		if (BeatBlock.timelineEditor == null) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		double loopIn = toolbarState.getLoopInSeconds();
		toolbarState.setLoopOutSeconds(Math.max(timeSeconds, loopIn + 0.1));
		toolbarState.setLoop(true);
	}

	private void applyLoopRange(TimelineMarker startMarker, TimelineMarker endMarker) {
		if (startMarker == null || endMarker == null || BeatBlock.timelineEditor == null) return;
		double start = Math.min(startMarker.getTimeSeconds(), endMarker.getTimeSeconds());
		double end = Math.max(startMarker.getTimeSeconds(), endMarker.getTimeSeconds());
		if (end <= start) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		toolbarState.setLoopInSeconds(start);
		toolbarState.setLoopOutSeconds(end);
		toolbarState.setLoop(true);
		BeatBlock.timelineEditor.getClock().seek(start);
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setCurrentTimeSeconds(start);
		}
	}
}
