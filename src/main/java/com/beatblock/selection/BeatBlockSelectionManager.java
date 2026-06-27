package com.beatblock.selection;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.selection.collect.BoxSelectionCollector;
import com.beatblock.selection.collect.BrushSelectionCollector;
import com.beatblock.selection.collect.ColumnSelectionCollector;
import com.beatblock.selection.collect.ConnectedSelectionCollector;
import com.beatblock.selection.collect.LineSelectionCollector;
import com.beatblock.selection.collect.PlaneSliceSelectionCollector;
import com.beatblock.selection.tools.SelectionToolHost;
import com.beatblock.selection.tools.SelectionToolRegistry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * BeatBlock 方块选区：点击、框选、线选、球/立方笔刷、连通魔棒、选区魔棒、整列、平面切片等。
 */
public final class BeatBlockSelectionManager implements SelectionToolHost {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockSelectionManager.class);

	private static final BeatBlockSelectionManager INSTANCE = new BeatBlockSelectionManager();

	public static BeatBlockSelectionManager get() {
		return INSTANCE;
	}

	// ── 常量定义 ──
	/** 默认最大选区方块数（防止内存溢出和性能问题） */
	public static final int DEFAULT_MAX_BLOCKS = 100_000;
	/** 默认相机最大距离（格）*/
	public static final int DEFAULT_MAX_CAMERA_DISTANCE = 128;
	/** 默认魔棒最大扩散半径（格）*/
	public static final int DEFAULT_MAX_MAGIC_WAND_SPREAD = 64;
	/** 默认球形笔刷半径（格）*/
	public static final int DEFAULT_SPHERE_BRUSH_RADIUS = 3;

	private SelectionMode mode = SelectionMode.OFF;
	private SelectionOperation operation = SelectionOperation.NEW;
	private final LinkedHashSet<BlockPos> selected = new LinkedHashSet<>();
	private BlockPos boxFirstCorner;
	private BlockPos lineFirstCorner;
	private boolean includeAir;
	private int maxBlocks = DEFAULT_MAX_BLOCKS;
	private int sphereBrushRadius = DEFAULT_SPHERE_BRUSH_RADIUS;
	/** 线选：0 = 仅体素中心折线；&gt;0 = 以两端中心连线为轴的圆柱半径（格）。 */
	private int lineThicknessRadius;
	/** false：同类型方块即可连通（魔棒更直观）；true：BlockState 完全一致才算「同色」。 */
	private boolean connectedMatchFullState;
	private BrushShape brushShape = BrushShape.SPHERE;
	/**
	 * 平面切片：非 null 时用此法向/轴向，忽略射线击中的面；null 表示跟随点击面。
	 */
	private Direction planeSliceFaceOverride;
	private BlockPos brushLastStampBlock;
	private boolean brushHadStroke;
	/**
	 * 相对相机：候选方块中心到该点的距离不得超过此值（格）。套索、魔棒、切片、球/列/框等均参与过滤，防止无界选区。
	 */
	private int maxDistanceFromCamera = DEFAULT_MAX_CAMERA_DISTANCE;
	/**
	 * 魔棒（全图与选区内）从点击种子起的最大欧氏扩散半径（格）。
	 */
	private int maxMagicWandSpreadFromSeed = DEFAULT_MAX_MAGIC_WAND_SPREAD;
	private Vec3d interactionCameraPos;
	/** 逐块半透明填充（仅当选区不大时绘制，开销高） */
	private boolean selectionFillEnabled;
	private String lastMessage = "";
	private Supplier<BeatBlockContext> contextSource = BeatBlock::getContext;

	private BeatBlockSelectionManager() {}

	public void bindContext(Supplier<BeatBlockContext> source) {
		this.contextSource = source != null ? source : BeatBlock::getContext;
	}

	static void resetContextBindingForTests() {
		INSTANCE.bindContext(BeatBlock::getContext);
	}

	private BeatBlockContext ctx() {
		return contextSource.get();
	}

	private BuildLayerManager buildLayerManager() {
		var engine = ctx().blockAnimationEngine();
		return engine != null ? engine.getBuildLayerManager() : null;
	}

	public SelectionMode getMode() {
		return mode;
	}

	public void setMode(SelectionMode mode) {
		this.mode = mode != null ? mode : SelectionMode.OFF;
		if (this.mode != SelectionMode.BOX) {
			boxFirstCorner = null;
		}
		if (this.mode != SelectionMode.LINE) {
			lineFirstCorner = null;
		}
		if (this.mode != SelectionMode.BRUSH) {
			clearBrushAnchor();
			brushHadStroke = false;
		}
	}

	public SelectionOperation getOperation() {
		return operation;
	}

	public void setOperation(SelectionOperation operation) {
		this.operation = operation != null ? operation : SelectionOperation.NEW;
	}

	public boolean isIncludeAir() {
		return includeAir;
	}

	public void setIncludeAir(boolean includeAir) {
		this.includeAir = includeAir;
	}

	public int getMaxBlocks() {
		return maxBlocks;
	}

	public void setMaxBlocks(int maxBlocks) {
		this.maxBlocks = Math.max(1024, maxBlocks);
	}

	public int getSphereBrushRadius() {
		return sphereBrushRadius;
	}

	public void setSphereBrushRadius(int sphereBrushRadius) {
		this.sphereBrushRadius = Math.min(32, Math.max(1, sphereBrushRadius));
	}

	public int getLineThicknessRadius() {
		return lineThicknessRadius;
	}

	public void setLineThicknessRadius(int lineThicknessRadius) {
		this.lineThicknessRadius = Math.min(32, Math.max(0, lineThicknessRadius));
	}

	public boolean isConnectedMatchFullState() {
		return connectedMatchFullState;
	}

	public void setConnectedMatchFullState(boolean connectedMatchFullState) {
		this.connectedMatchFullState = connectedMatchFullState;
	}

	/** 平面切片固定朝向；null 为自动（跟随击中面）。 */
	public Direction getPlaneSliceFaceOverride() {
		return planeSliceFaceOverride;
	}

	public void setPlaneSliceFaceOverride(Direction planeSliceFaceOverride) {
		this.planeSliceFaceOverride = planeSliceFaceOverride;
	}

	/** 预览与点击提交时：有覆盖则用覆盖，否则用射线给出的面。 */
	public Direction resolvePlaneSliceFace(Direction hitFace) {
		if (hitFace == null) {
			return planeSliceFaceOverride != null ? planeSliceFaceOverride : Direction.UP;
		}
		return planeSliceFaceOverride != null ? planeSliceFaceOverride : hitFace;
	}

	public BrushShape getBrushShape() {
		return brushShape;
	}

	public void setBrushShape(BrushShape brushShape) {
		this.brushShape = brushShape != null ? brushShape : BrushShape.SPHERE;
	}

	public int getMaxDistanceFromCamera() {
		return maxDistanceFromCamera;
	}

	public void setMaxDistanceFromCamera(int maxDistanceFromCamera) {
		this.maxDistanceFromCamera = Math.min(512, Math.max(8, maxDistanceFromCamera));
	}

	public int getMaxMagicWandSpreadFromSeed() {
		return maxMagicWandSpreadFromSeed;
	}

	public void setMaxMagicWandSpreadFromSeed(int maxMagicWandSpreadFromSeed) {
		this.maxMagicWandSpreadFromSeed = Math.min(256, Math.max(1, maxMagicWandSpreadFromSeed));
	}

	/** 客户端在点击/笔刷/套索前设置，用于距离过滤。 */
	public void setInteractionCameraPos(Vec3d cameraPos) {
		this.interactionCameraPos = cameraPos;
	}

	public void setSelectionFeedback(String message) {
		if (message != null && !message.isBlank()) {
			this.lastMessage = message;
		}
	}

	public boolean isSelectionFillEnabled() {
		return selectionFillEnabled;
	}

	public void setSelectionFillEnabled(boolean selectionFillEnabled) {
		this.selectionFillEnabled = selectionFillEnabled;
	}

	/** 由套索提交选中方块（当前模式应为 {@link SelectionMode#LASSO}）。 */
	public void commitLassoSelection(List<BlockPos> blocks, SelectionOperation op) {
		if (blocks == null || blocks.isEmpty()) {
			lastMessage = "套索：未选中任何方块。";
			return;
		}
		mergeBlockListIntoSelection(blocks, op);
	}

	public Set<BlockPos> getSelectedBlocks() {
		return Collections.unmodifiableSet(selected);
	}

	public int getSelectionCount() {
		return selected.size();
	}

	public BlockPos getBoxFirstCorner() {
		return boxFirstCorner;
	}

	public BlockPos getLineFirstCorner() {
		return lineFirstCorner;
	}

	public String getLastMessage() {
		return lastMessage;
	}

	public void clearMessage() {
		lastMessage = "";
	}

	public void clearSelection() {
		selected.clear();
		boxFirstCorner = null;
		lineFirstCorner = null;
		clearBrushAnchor();
		brushHadStroke = false;
		lastMessage = "已清空选区。";
	}

	public void cancelBoxCorner() {
		boxFirstCorner = null;
		lastMessage = "已取消框选第一点。";
	}

	public void cancelLineCorner() {
		lineFirstCorner = null;
		lastMessage = "已取消线选第一点。";
	}

	public void reset() {
		mode = SelectionMode.OFF;
		operation = SelectionOperation.NEW;
		selected.clear();
		boxFirstCorner = null;
		lineFirstCorner = null;
		includeAir = false;
		sphereBrushRadius = 3;
		connectedMatchFullState = false;
		brushShape = BrushShape.SPHERE;
		planeSliceFaceOverride = null;
		maxDistanceFromCamera = 128;
		maxMagicWandSpreadFromSeed = 64;
		lineThicknessRadius = 0;
		interactionCameraPos = null;
		selectionFillEnabled = false;
		clearBrushAnchor();
		brushHadStroke = false;
		lastMessage = "";
	}

	/**
	 * 左键单击（含击中面）。平面切片默认用 {@link BlockHitResult#getSide()}，可被
	 * {@link #setPlaneSliceFaceOverride(Direction)} 覆盖。
	 */
	public void handleBlockSelectClick(World world, BlockHitResult hit, boolean shiftDown) {
		if (mode == SelectionMode.OFF || world == null || hit == null) return;
		BlockPos pos = hit.getBlockPos();
		Direction side = hit.getSide();

		if (!includeAir && world.getBlockState(pos).isAir()) {
			lastMessage = "当前设置跳过空气方块。";
			return;
		}

		switch (mode) {
			case OFF, LASSO -> {}
			default -> SelectionToolRegistry.dispatchClick(mode, this, world, pos, side, shiftDown);
        }
	}

	/** 无击中面信息时使用（例如测试）；一般应使用 {@link #handleBlockSelectClick(World, BlockHitResult, boolean)}。 */
	public void handleLeftClick(World world, BlockPos pos, boolean shiftDown) {
		if (world == null || pos == null) return;
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
		handleBlockSelectClick(world, hit, shiftDown);
	}

	public void stampBrushIfNeeded(World world, BlockPos center, boolean shiftDown) {
		BlockPos imm = center.toImmutable();
		if (brushLastStampBlock != null && brushLastStampBlock.equals(imm)) {
			return;
		}
		brushLastStampBlock = imm;
		brushHadStroke = true;
		List<BlockPos> blocks = collectBrush(world, imm);
		if (blocks == null) {
			return;
		}
		SelectionOperation op = shiftDown ? SelectionOperation.ADD : operation;
		mergeBlockListIntoSelection(blocks, op, true);
	}

	public void clearBrushAnchor() {
		brushLastStampBlock = null;
	}

	/** 松开左键时由客户端 tick 调用，用于结束一次涂抹并提示。 */
	public void finishBrushStroke() {
		if (brushHadStroke) {
			lastMessage = "笔刷结束：当前选区 " + selected.size() + " 个方块。";
			brushHadStroke = false;
		}
		brushLastStampBlock = null;
	}

	/**
	 * 计算平面切片在「当前选区包围盒 ∩ 平面」或「当前区块 ∩ 平面」内的 AABB（用于预览与选区一致）。
	 */
	public PlaneSliceBounds computePlaneSliceBounds(World world, BlockPos pos, Direction face) {
		return computePlaneSliceBoundsInternal(world, pos, face);
	}

	private boolean isWithinCameraReach(BlockPos p) {
		return SelectionReach.isWithinCameraReach(p, interactionCameraPos, maxDistanceFromCamera);
	}

	@Override
	public SelectionOperation getDefaultOperation() {
		return operation;
	}

	@Override
	public SelectionOperation resolveOperation(boolean shiftDown) {
		return shiftDown ? SelectionOperation.ADD : operation;
	}

	@Override
	public void setMessage(String message) {
		lastMessage = message;
	}

	@Override
	public void handleDirectClick(World world, BlockPos pos, SelectionOperation op) {
		if (!isWithinCameraReach(pos)) {
			lastMessage = "点击选择：该方块超出「相对视角最大距离」。";
			return;
		}
		if (isClaimedByBuildLayer(pos)) {
			lastMessage = layerClaimedMessage(pos);
			return;
		}
		switch (op) {
			case NEW -> {
				selected.clear();
				selected.add(pos.toImmutable());
				lastMessage = "新建选区：1 个方块";
			}
			case ADD -> {
				selected.add(pos.toImmutable());
				lastMessage = "加选后共 " + selected.size() + " 个方块";
			}
			case SUBTRACT -> {
				selected.remove(pos);
				lastMessage = "减选后共 " + selected.size() + " 个方块";
			}
			case INTERSECT -> {
				if (selected.contains(pos)) {
					BlockPos p = pos.toImmutable();
					selected.clear();
					selected.add(p);
					lastMessage = "交集：保留 1 个方块";
				} else {
					selected.clear();
					lastMessage = "交集：该方块不在选区内，已清空";
				}
			}
		}
		LOGGER.debug("[BeatBlockSelection] click op={} size={}", op, selected.size());
	}

	@Override
	public void setBoxFirstCorner(BlockPos corner) {
		boxFirstCorner = corner;
	}

	@Override
	public void clearBoxFirstCorner() {
		boxFirstCorner = null;
	}

	@Override
	public void setLineFirstCorner(BlockPos corner) {
		lineFirstCorner = corner;
	}

	@Override
	public void clearLineFirstCorner() {
		lineFirstCorner = null;
	}

	@Override
	public void mergeFromBox(World world, BlockPos cornerA, BlockPos cornerB, SelectionOperation op) {
		List<BlockPos> blocks = collectBox(world, cornerA, cornerB);
		if (blocks != null) {
			mergeBlockListIntoSelection(blocks, op);
		}
	}

	@Override
	public void mergeFromLine(World world, BlockPos endA, BlockPos endB, SelectionOperation op) {
		List<BlockPos> blocks = collectLine(world, endA, endB);
		if (blocks != null) {
			mergeBlockListIntoSelection(blocks, op);
		}
	}

	@Override
	public void mergeFromBrush(World world, BlockPos center, SelectionOperation op) {
		List<BlockPos> blocks = collectBrush(world, center);
		if (blocks != null) {
			mergeBlockListIntoSelection(blocks, op);
		}
	}

	@Override
	public void mergeFromConnected(World world, BlockPos start, SelectionOperation op) {
		List<BlockPos> blocks = collectConnected(world, start);
		if (blocks != null) {
			mergeBlockListIntoSelection(blocks, op);
		}
	}

	@Override
	public void mergeFromColumn(World world, BlockPos pos, SelectionOperation op) {
		List<BlockPos> blocks = collectColumn(world, pos);
		if (blocks != null) {
			mergeBlockListIntoSelection(blocks, op);
		}
	}

	@Override
	public void mergeFromPlaneSlice(World world, BlockPos pos, Direction face, SelectionOperation op) {
		List<BlockPos> blocks = collectPlaneSlice(world, pos, face);
		if (blocks != null) {
			mergeBlockListIntoSelection(blocks, op);
		}
	}

	@Override
	public void mergeFromSelectionWand(World world, BlockPos pos, SelectionOperation op) {
		List<BlockPos> blocks = collectConnectedInBounds(
			world, pos, getBoundingMin(), getBoundingMax());
		if (blocks != null) {
			mergeBlockListIntoSelection(blocks, op);
		}
	}

	@Override
	public BlockPos getSelectionBoundingMin() {
		return getBoundingMin();
	}

	@Override
	public BlockPos getSelectionBoundingMax() {
		return getBoundingMax();
	}

	private PlaneSliceBounds computePlaneSliceBoundsInternal(World world, BlockPos pos, Direction face) {
		return PlaneSliceBounds.compute(
			pos,
			face,
			getBoundingMin(),
			getBoundingMax(),
			new ChunkPos(pos),
			world.getBottomY(),
			world.getBottomY() + world.getHeight() - 1
		);
	}

	private List<BlockPos> resolveCollectResult(SelectionCollectResult result) {
		if (result.failed()) {
			lastMessage = result.errorMessage();
			return null;
		}
		if (result.noticeMessage() != null) {
			lastMessage = result.noticeMessage();
		}
		return result.blocks();
	}

	private List<BlockPos> collectPlaneSlice(World world, BlockPos pos, Direction face) {
		return resolveCollectResult(PlaneSliceSelectionCollector.collect(
			world,
			pos,
			face,
			getBoundingMin(),
			getBoundingMax(),
			includeAir,
			maxBlocks,
			this::isWithinCameraReach
		));
	}

	private List<BlockPos> collectConnectedInBounds(World world, BlockPos start, BlockPos bMin, BlockPos bMax) {
		return resolveCollectResult(ConnectedSelectionCollector.collect(
			world::getBlockState,
			start,
			bMin,
			bMax,
			includeAir,
			connectedMatchFullState,
			maxBlocks,
			maxMagicWandSpreadFromSeed,
			this::isWithinCameraReach,
			"选区魔棒：起点超出「相对视角最大距离」。",
			"选区魔棒已达上限 %d，已选 %d 个方块（未完全展开）。"
		));
	}

	private List<BlockPos> collectBrush(World world, BlockPos center) {
		return resolveCollectResult(BrushSelectionCollector.collect(
			world,
			center,
			brushShape,
			sphereBrushRadius,
			includeAir,
			maxBlocks,
			this::isWithinCameraReach
		));
	}

	private List<BlockPos> collectBox(World world, BlockPos a, BlockPos b) {
		return resolveCollectResult(BoxSelectionCollector.collect(
			world, a, b, includeAir, maxBlocks, this::isWithinCameraReach
		));
	}

	private List<BlockPos> collectLine(World world, BlockPos a, BlockPos b) {
		return resolveCollectResult(LineSelectionCollector.collect(
			world, a, b, lineThicknessRadius, includeAir, maxBlocks, this::isWithinCameraReach
		));
	}

	private List<BlockPos> collectColumn(World world, BlockPos pos) {
		return resolveCollectResult(ColumnSelectionCollector.collect(
			world, pos, includeAir, maxBlocks, this::isWithinCameraReach
		));
	}

	private List<BlockPos> collectConnected(World world, BlockPos start) {
		return resolveCollectResult(ConnectedSelectionCollector.collect(
			world::getBlockState,
			start,
			null,
			null,
			includeAir,
			connectedMatchFullState,
			maxBlocks,
			maxMagicWandSpreadFromSeed,
			this::isWithinCameraReach,
			"魔棒：起点超出「相对视角最大距离」，请靠近或在属性面板调大范围。",
			"魔棒已达上限 %d，已选 %d 个方块（未完全展开）。"
		));
	}

	private void mergeBlockListIntoSelection(List<BlockPos> blocks, SelectionOperation op) {
		mergeBlockListIntoSelection(blocks, op, false);
	}

	private void mergeBlockListIntoSelection(List<BlockPos> blocks, SelectionOperation op, boolean quiet) {
		int skippedLayer = countLayerClaimed(blocks);
		blocks = excludeLayerClaimed(blocks);
		if (blocks.isEmpty()) {
			if (!quiet) {
				lastMessage = SelectionFeedback.emptyMergeMessage(mode, brushShape, skippedLayer);
			}
			return;
		}
		LinkedHashSet<BlockPos> merged = SelectionMerge.apply(selected, blocks, op);
		selected.clear();
		selected.addAll(merged);
		if (!quiet) {
			lastMessage = SelectionFeedback.mergeAfterOperation(mode, brushShape, op, blocks.size(), selected.size());
		}
		if (!quiet && skippedLayer > 0) {
			lastMessage = SelectionFeedback.appendSkippedLayerNotice(lastMessage, skippedLayer);
		}
		LOGGER.debug("[BeatBlockSelection] merge op={} size={}", op, selected.size());
	}

	public BlockPos getBoundingMin() {
		return SelectionBounds.fromPositions(selected).min();
	}

	public BlockPos getBoundingMax() {
		return SelectionBounds.fromPositions(selected).max();
	}

	public List<BlockPos> copySelectionAsList() {
		return new ArrayList<>(selected);
	}

	public void removeBlocks(Collection<BlockPos> toRemove) {
		selected.removeAll(toRemove);
	}

	public void addBlocks(Collection<BlockPos> toAdd) {
		if (toAdd == null || toAdd.isEmpty()) return;
		for (BlockPos pos : toAdd) {
			if (pos == null || isClaimedByBuildLayer(pos)) continue;
			selected.add(pos.toImmutable());
		}
	}

	private boolean isClaimedByBuildLayer(BlockPos pos) {
		if (pos == null) return false;
		BuildLayerManager manager = buildLayerManager();
		return manager != null && manager.isBlockClaimed(pos);
	}

	private String layerClaimedMessage(BlockPos pos) {
		BuildLayerManager manager = buildLayerManager();
		if (manager == null) return "该方块已属于某图层，无法选入选区。";
		BuildLayer owner = manager.getLayerOwningBlock(pos);
		if (owner == null) return "该方块已属于某图层，无法选入选区。";
		return "该方块已属于图层「" + owner.getName() + "」，无法选入选区。";
	}

	private List<BlockPos> excludeLayerClaimed(List<BlockPos> blocks) {
		return SelectionLayerBlocks.excludeClaimed(blocks, this::isClaimedByBuildLayer);
	}

	private int countLayerClaimed(List<BlockPos> blocks) {
		return SelectionLayerBlocks.countClaimed(blocks, this::isClaimedByBuildLayer);
	}
}
