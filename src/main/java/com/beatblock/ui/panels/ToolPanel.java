package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.BeatBlockWorldPick;
import com.beatblock.client.input.BeatBlockInputSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 左侧工具面板：层次为「场景选区 → 自动化编排 → 动画场景对象 → 时间线调试」。
 * 方块选择由 {@link BeatBlockSelectionManager} 管理；StageObject 创建使用轴对齐包围盒，
 * 默认从当前方块选区的外接 AABB 一键填入，避免与「框选工具」语义重复。
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
	private final ImBoolean stageObjectIncludeAir = new ImBoolean(false);
	private final ImInt stageObjectSortingIndex = new ImInt(0);
	private final ImString stageObjectStaggerBuffer = new ImString(16);
	private BlockPos selectionPosA;
	private BlockPos selectionPosB;
	private String stageObjectMessage;
	private long stageObjectMessageTimeMs;
	private static final int MAX_STAGE_OBJECT_BLOCKS = 32768;
	private static final GroupSortingStrategy[] STAGE_GROUP_SORTING_VALUES = {
		GroupSortingStrategy.SEQUENTIAL,
		GroupSortingStrategy.RADIAL,
		GroupSortingStrategy.SPIRAL,
		GroupSortingStrategy.RANDOM,
		GroupSortingStrategy.ALL
	};
	private static final String[] STAGE_GROUP_SORTING_LABELS = {
		"顺序 (SEQUENTIAL)",
		"径向 (RADIAL)",
		"螺旋 (SPIRAL)",
		"随机 (RANDOM)",
		"同时 (ALL)"
	};
	private static final String[] MARKER_TYPE_LABELS = MarkerType.displayNames();

	private static final SelectionMode[] SELECTION_COMBO_ORDER = {
			SelectionMode.OFF,
			SelectionMode.CLICK,
			SelectionMode.BOX,
			SelectionMode.LINE,
			SelectionMode.BRUSH,
			SelectionMode.CONNECTED,
			SelectionMode.COLUMN,
			SelectionMode.PLANE_SLICE,
			SelectionMode.SELECTION_WAND,
			SelectionMode.LASSO
	};

	private final Runnable onSelectionToolChosen;

	public ToolPanel() {
		this(null);
	}

	public ToolPanel(Runnable onSelectionToolChosen) {
		this.onSelectionToolChosen = onSelectionToolChosen;
		stageObjectNameBuffer.set("selection_object");
		stageObjectStaggerBuffer.set("0.00");
	}

	/** 由菜单栏「演出 → Smart Auto Map」调用，打开设置弹窗 */
	public void setShowAutoMapSettings(boolean show) {
		this.showAutoMapSettings = show;
	}
	/** 上次生成统计 */
	private SmartAutoMapEngine.AutoMapResult lastAutoMapResult = null;

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text("工具");
			ImGui.separator();

			renderBlockSelectionTools();

			ImGui.spacing();
			ImGui.textDisabled("自动化编排");
			ImGui.separator();
			if (ImGui.button("Smart Auto Map")) {
				showAutoMapSettings = true;
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("根据已导入音乐生成时间线事件（方块动画、镜头、粒子等），与当前选区无绑定。");
			}
			if (lastAutoMapResult != null) {
				ImGui.sameLine();
				ImGui.textDisabled(String.format("上次：动 %d · 镜 %d · 粒 %d",
					lastAutoMapResult.getAnimationEvents(),
					lastAutoMapResult.getCameraEvents(),
					lastAutoMapResult.getParticleEvents()));
			}

			renderStageObjectCreator();
			renderLastActionExecutionStatus();

			renderMarkerManager();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.TOOL_PANEL_WINDOW);
		}

		if (showAutoMapSettings) {
			boolean done = autoMapSettingsPanel.render(res -> lastAutoMapResult = res);
			if (done) showAutoMapSettings = false;
		}
	}

	private void renderBlockSelectionTools() {
		ImGui.text("方块选择工具");
		var mgr = BeatBlockSelectionManager.get();
		ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
		if (ImGui.beginCombo("##bselCombo", selectionModeComboLabel(mgr.getMode()))) {
			for (SelectionMode m : SELECTION_COMBO_ORDER) {
				boolean selected = mgr.getMode() == m;
				if (ImGui.selectable(selectionModeComboLabel(m), selected)) {
					if (mgr.getMode() != m) {
						mgr.setMode(m);
						if (onSelectionToolChosen != null) {
							onSelectionToolChosen.run();
						}
					}
				}
				if (selected) {
					ImGui.setItemDefaultFocus();
				}
			}
			ImGui.endCombo();
		}
		ImGui.textWrapped("笔刷含球体/立方等形状：单击盖章或按住涂抹。框选/线选为两点；套索为拖画。线粗细等在「视图 → 选择属性」；换选择工具不会自动打开该面板。");
		ImGui.separator();
	}

	private static String selectionModeComboLabel(SelectionMode m) {
		return switch (m) {
			case OFF -> "关闭";
			case CLICK -> "点击选择";
			case BOX -> "框选（两角 + 预览）";
			case LINE -> "线选（两端点 + 预览）";
			case BRUSH -> "笔刷（球/立方，单击或涂抹）";
			case CONNECTED -> "魔棒（同色六邻域）";
			case COLUMN -> "整列（同 XZ）";
			case PLANE_SLICE -> "平面切片";
			case SELECTION_WAND -> "选区魔棒（盒内）";
			case LASSO -> "套索（屏幕多边形）";
		};
	}

	private void renderStageObjectCreator() {
		ImGui.spacing();
		ImGui.textDisabled("动画场景对象（StageObject）");
		ImGui.separator();
		ImGui.textWrapped(
				"时间线里的方块动画事件通过名称引用 StageObject。创建时需要一块「轴对齐长方体」内的方块："
						+ "请优先用上方「方块选择工具」做出选区（任意形状均可），再点下面按钮把该选区的外接包围盒填入；"
						+ "只有不打算用选区工具时，才展开「手动角点」用准星点两个角。");

		var selMgr = BeatBlockSelectionManager.get();
		int selCount = selMgr.getSelectionCount();
		if (selCount > 0) {
			ImGui.textDisabled(String.format(Locale.ROOT, "当前方块选区：%d 个方块（包围盒用于下方创建）", selCount));
		} else {
			ImGui.textDisabled("当前无方块选区：可先用框选/魔棒等建立选区，或展开「手动角点」。");
		}

		if (ImGui.button("用当前方块选区包围盒填入##stageFromSel", -1f, 0f)) {
			BlockPos smin = selMgr.getBoundingMin();
			BlockPos smax = selMgr.getBoundingMax();
			if (smin != null && smax != null && selCount > 0) {
				selectionPosA = smin.toImmutable();
				selectionPosB = smax.toImmutable();
				setStageObjectMessage("已从方块选区外接包围盒填入 A、B。");
			} else {
				setStageObjectMessage("没有可用的方块选区或包围盒。");
			}
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("与上方选区工具联动：取 BeatBlock 选择管理器中已选方块的最小/最大角作为创建包围盒（与框选完成后的结果一致，无需再点「设置 A/B」）。");
		}

		boolean canCreateFromSelection = selCount > 0;
		if (!canCreateFromSelection) ImGui.beginDisabled();
		if (ImGui.button("从当前方块选区直接创建（精确快照）##stageCreateFromSelection", -1f, 0f)) {
			createStageObjectFromCurrentSelectionSnapshot();
		}
		if (!canCreateFromSelection) ImGui.endDisabled();
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("按当前选中的方块集合直接建组（不扩成包围盒），并记录为 selection_snapshot 来源。");
		}

		ImGui.textDisabled("创建包围盒角点");
		ImGui.textDisabled("  A: " + formatPos(selectionPosA));
		ImGui.textDisabled("  B: " + formatPos(selectionPosB));
		long selectionVolume = estimateSelectionVolume(selectionPosA, selectionPosB);
		if (selectionVolume > 0) {
			ImGui.textDisabled(String.format(Locale.ROOT, "  包围盒体积（估算）: %d 方块", selectionVolume));
		}

		ImGui.setNextItemOpen(false, ImGuiCond.Once);
		if (ImGui.collapsingHeader("手动角点（准星拾取，可选）##stageManualHdr")) {
			ImGui.textWrapped("与「方块选择」独立：在场景区用准星对准方块，分别指定长方体的两个对角。");
			if (ImGui.button("准星 → A##stageObjSetA")) {
				selectionPosA = readCrosshairBlockPos();
				setStageObjectMessage(selectionPosA != null ? "已设置 A。" : "未命中方块。");
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("使用当前光标射线击中的方块坐标");
			}
			ImGui.sameLine();
			if (ImGui.button("准星 → B##stageObjSetB")) {
				selectionPosB = readCrosshairBlockPos();
				setStageObjectMessage(selectionPosB != null ? "已设置 B。" : "未命中方块。");
			}
			if (ImGui.button("清空手动角点##stageObjClearSelection")) {
				selectionPosA = null;
				selectionPosB = null;
				setStageObjectMessage("已清空 A/B。");
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("仅清除此处角点，不影响上方「方块选择工具」的选区");
			}
			BlockPos lastLeft = BeatBlockWorldPick.getLastLeftClickedBlock();
			if (lastLeft != null) {
				ImGui.textDisabled("最近左键方块: " + formatPos(lastLeft));
			}
		}

		ImGui.checkbox("包含空气方块##stageObjIncludeAir", stageObjectIncludeAir);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("关闭后仅采集非空气方块，推荐用于已有建筑对象");
		}

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("对象名称##stageObjName", stageObjectNameBuffer);

		ImGui.setNextItemWidth(-1f);
		ImGui.combo("组排序策略##stageGroupSorting", stageObjectSortingIndex, STAGE_GROUP_SORTING_LABELS);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText("组默认步进延迟(秒)##stageGroupStagger", stageObjectStaggerBuffer);

		boolean canCreate = selectionPosA != null && selectionPosB != null;
		if (!canCreate) ImGui.beginDisabled();
		if (ImGui.button("从选区创建 StageObject##stageObjCreate", -1f, 0f)) {
			createStageObjectFromSelection();
		}
		if (!canCreate) ImGui.endDisabled();

		if (stageObjectMessage != null && !stageObjectMessage.isBlank()
				&& System.currentTimeMillis() - stageObjectMessageTimeMs < 5000L) {
			ImGui.textWrapped(stageObjectMessage);
		}

		renderStageObjectList();
	}

	private void renderStageObjectList() {
		if (BeatBlock.blockAnimationEngine == null) return;
		var sys = BeatBlock.blockAnimationEngine.getStageObjectSystem();
		List<StageObject> objects = new ArrayList<>(sys.getAll());
		if (objects.isEmpty()) {
			ImGui.spacing();
			ImGui.textDisabled("暂无已注册的 StageObject。");
			return;
		}
		objects.sort(Comparator.comparing(StageObject::getName, String.CASE_INSENSITIVE_ORDER));

		ImGui.spacing();
		ImGui.text("已注册对象 (" + objects.size() + ")");
		String removeId = null;
		if (ImGui.beginChild("##StageObjectList", 0, Math.min(objects.size() * 22f + 8f, 160f), true)) {
			for (StageObject obj : objects) {
				String label = obj.getName() + "  [" + obj.getId() + "]  " + obj.getBlocks().size() + " blocks";
				ImGui.text(label);
				ImGui.sameLine();
				ImGui.textDisabled("(" + obj.getGroupSpec().getSourceType() + ")");
				ImGui.sameLine();
				if (ImGui.smallButton("Delete##stageObjDel_" + obj.getId())) {
					removeId = obj.getId();
				}
			}
		}
		ImGui.endChild();

		if (removeId != null) {
			sys.remove(removeId);
			setStageObjectMessage("已删除 StageObject: " + removeId);
		}
	}

	private void setStageObjectMessage(String msg) {
		stageObjectMessage = msg;
		stageObjectMessageTimeMs = System.currentTimeMillis();
	}

	private void createStageObjectFromSelection() {
		if (BeatBlock.blockAnimationEngine == null) {
			setStageObjectMessage("动画引擎未初始化，无法创建对象。");
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world == null) {
			setStageObjectMessage("当前无世界上下文，无法读取选区。");
			return;
		}
		if (selectionPosA == null || selectionPosB == null) {
			setStageObjectMessage("请先设置 A/B 两个选区点。");
			return;
		}

		List<BlockPos> blocks = collectSelectionBlocks(selectionPosA, selectionPosB, stageObjectIncludeAir.get());
		if (blocks.isEmpty()) {
			setStageObjectMessage(stageObjectIncludeAir.get()
				? "选区为空，未创建对象。"
				: "选区内没有非空气方块，未创建对象。");
			return;
		}

		String name = stageObjectNameBuffer.get() != null ? stageObjectNameBuffer.get().trim() : "";
		if (name.isEmpty()) name = "selection_object";
		String id = buildUniqueStageObjectId(name);

		GroupSortingStrategy sortingStrategy = currentStageObjectSortingStrategy();
		double staggerSeconds = parseStageObjectStaggerSeconds();
		StageObject obj = StageObjectSystem.fromBlocks(
			id,
			name,
			blocks,
			com.beatblock.engine.GroupSpec.fromSelectionCuboid(
				selectionPosA,
				selectionPosB,
				stageObjectIncludeAir.get(),
				sortingStrategy,
				staggerSeconds
			)
		);
		BeatBlock.blockAnimationEngine.getStageObjectSystem().register(obj);
		setStageObjectMessage(String.format(Locale.ROOT, "已创建 StageObject: %s (%d blocks)", id, blocks.size()));
	}

	private void createStageObjectFromCurrentSelectionSnapshot() {
		if (BeatBlock.blockAnimationEngine == null) {
			setStageObjectMessage("动画引擎未初始化，无法创建对象。");
			return;
		}
		var selMgr = BeatBlockSelectionManager.get();
		List<BlockPos> blocks = new ArrayList<>(selMgr.getSelectedBlocks());
		if (blocks.isEmpty()) {
			setStageObjectMessage("当前没有方块选区。请先使用选择工具。");
			return;
		}
		if (blocks.size() > MAX_STAGE_OBJECT_BLOCKS) {
			setStageObjectMessage(String.format(Locale.ROOT,
				"选区过大（%d blocks），上限为 %d。", blocks.size(), MAX_STAGE_OBJECT_BLOCKS));
			return;
		}

		String name = stageObjectNameBuffer.get() != null ? stageObjectNameBuffer.get().trim() : "";
		if (name.isEmpty()) name = "selection_object";
		String id = buildUniqueStageObjectId(name);

		GroupSortingStrategy sortingStrategy = currentStageObjectSortingStrategy();
		double staggerSeconds = parseStageObjectStaggerSeconds();
		StageObject obj = StageObjectSystem.fromSelectionSnapshot(id, name, blocks, sortingStrategy, staggerSeconds);
		BeatBlock.blockAnimationEngine.getStageObjectSystem().register(obj);
		setStageObjectMessage(String.format(Locale.ROOT, "已创建快照 StageObject: %s (%d blocks)", id, blocks.size()));
	}

	private GroupSortingStrategy currentStageObjectSortingStrategy() {
		int idx = Math.max(0, Math.min(stageObjectSortingIndex.get(), STAGE_GROUP_SORTING_VALUES.length - 1));
		return STAGE_GROUP_SORTING_VALUES[idx];
	}

	private double parseStageObjectStaggerSeconds() {
		String raw = stageObjectStaggerBuffer.get() != null ? stageObjectStaggerBuffer.get().trim() : "";
		if (raw.isEmpty()) return 0.0;
		try {
			return Math.max(0.0, Double.parseDouble(raw));
		} catch (Exception ex) {
			return 0.0;
		}
	}

	private List<BlockPos> collectSelectionBlocks(BlockPos a, BlockPos b, boolean includeAir) {
		List<BlockPos> out = new ArrayList<>();
		if (a == null || b == null) return out;
		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world == null) return out;

		int minX = Math.min(a.getX(), b.getX());
		int maxX = Math.max(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY());
		int maxY = Math.max(a.getY(), b.getY());
		int minZ = Math.min(a.getZ(), b.getZ());
		int maxZ = Math.max(a.getZ(), b.getZ());

		long volume = (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
		if (volume > MAX_STAGE_OBJECT_BLOCKS) {
			setStageObjectMessage(String.format(Locale.ROOT,
				"选区过大（%d blocks），上限为 %d。", volume, MAX_STAGE_OBJECT_BLOCKS));
			return out;
		}

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!includeAir && world.getBlockState(pos).isAir()) continue;
					out.add(pos);
				}
			}
		}
		return out;
	}

	private static long estimateSelectionVolume(BlockPos a, BlockPos b) {
		if (a == null || b == null) return 0;
		long dx = Math.abs((long) a.getX() - b.getX()) + 1L;
		long dy = Math.abs((long) a.getY() - b.getY()) + 1L;
		long dz = Math.abs((long) a.getZ() - b.getZ()) + 1L;
		return dx * dy * dz;
	}

	private String buildUniqueStageObjectId(String name) {
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
		if (base.isBlank()) base = "selection_object";
		if (base.startsWith("_")) base = base.substring(1);
		if (base.endsWith("_")) base = base.substring(0, base.length() - 1);
		if (base.isEmpty()) base = "selection_object";

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
		if (mc.currentScreen instanceof BeatBlockUIScreen) {
			BlockHitResult bhr = BeatBlockInputSystem.raycastFromImGui();
			if (bhr == null || bhr.getType() != HitResult.Type.BLOCK) return null;
			return bhr.getBlockPos().toImmutable();
		}
		HitResult hit = mc.crosshairTarget;
		if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return null;
		return bhr.getBlockPos().toImmutable();
	}

	private static String formatPos(BlockPos pos) {
		if (pos == null) return "(未设置)";
		return String.format(Locale.ROOT, "%d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
	}

	private void renderLastActionExecutionStatus() {
		ImGui.spacing();
		ImGui.textDisabled("时间线动作（调试）");
		ImGui.separator();

		BeatBlockClientDriver.TimelineActionExecutionReport report = BeatBlockClientDriver.getLastTimelineActionExecutionReport();
		if (report == null) {
			ImGui.textDisabled("暂无执行记录。");
			return;
		}
		long ageMs = Math.max(0L, System.currentTimeMillis() - report.timestampMs());
		ImGui.textDisabled(String.format(Locale.ROOT,
			"%s | %s | mutations=%d | %dms ago",
			report.actionMode().name(),
			report.status(),
			report.mutationCount(),
			ageMs));
		if (report.detail() != null && !report.detail().isBlank()) {
			ImGui.textWrapped("detail: " + report.detail());
		}
		if (report.targetObjectId() != null && !report.targetObjectId().isBlank()) {
			ImGui.textDisabled("target: " + report.targetObjectId());
		}
		if (report.eventId() != null && !report.eventId().isBlank()) {
			ImGui.textDisabled("event: " + report.eventId());
		}
	}

	private void renderMarkerManager() {
		Timeline timeline = BeatBlock.timeline;
		if (timeline == null) return;

		ImGui.spacing();
		ImGui.textDisabled("时间线 Marker");
		ImGui.separator();

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
				int abgr = marker.getType().getColorAbgr();
				ImGui.pushStyleColor(ImGuiCol.Text, abgrToR(abgr), abgrToG(abgr), abgrToB(abgr), abgrToA(abgr));
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

	private static float abgrToR(int abgr) { return ((abgr) & 0xFF) / 255f; }
	private static float abgrToG(int abgr) { return ((abgr >> 8) & 0xFF) / 255f; }
	private static float abgrToB(int abgr) { return ((abgr >> 16) & 0xFF) / 255f; }
	private static float abgrToA(int abgr) { return ((abgr >> 24) & 0xFF) / 255f; }

	private void setLoopIn(double timeSeconds) {
		if (BeatBlock.timelineEditor == null) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		toolbarState.setLoopInSeconds(timeSeconds);
		if (toolbarState.getLoopOutSeconds() <= timeSeconds) {
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
