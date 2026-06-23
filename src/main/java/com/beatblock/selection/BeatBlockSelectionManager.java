package com.beatblock.selection;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayer;
import net.minecraft.block.BlockState;
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
import java.util.Objects;
import java.util.Set;

/**
 * BeatBlock 方块选区：点击、框选、线选、球/立方笔刷、连通魔棒、选区魔棒、整列、平面切片等。
 */
public final class BeatBlockSelectionManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeatBlockSelectionManager.class);

	private static final BeatBlockSelectionManager INSTANCE = new BeatBlockSelectionManager();

	public static BeatBlockSelectionManager get() {
		return INSTANCE;
	}

	private SelectionMode mode = SelectionMode.OFF;
	private SelectionOperation operation = SelectionOperation.NEW;
	private final LinkedHashSet<BlockPos> selected = new LinkedHashSet<>();
	private BlockPos boxFirstCorner;
	private BlockPos lineFirstCorner;
	private boolean includeAir;
	private int maxBlocks = 100_000;
	private int sphereBrushRadius = 3;
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
	private int maxDistanceFromCamera = 128;
	/**
	 * 魔棒（全图与选区内）从点击种子起的最大欧氏扩散半径（格）。
	 */
	private int maxMagicWandSpreadFromSeed = 64;
	private Vec3d interactionCameraPos;
	/** 逐块半透明填充（仅当选区不大时绘制，开销高） */
	private boolean selectionFillEnabled;
	private String lastMessage = "";

	private BeatBlockSelectionManager() {}

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
			case CLICK -> handleClickTool(world, pos, shiftDown);
			case BOX -> handleBoxTool(world, pos);
			case LINE -> handleLineTool(world, pos);
			case BRUSH -> handleBrushClick(world, pos, shiftDown);
			case CONNECTED -> handleConnectedTool(world, pos, shiftDown);
			case COLUMN -> handleColumnTool(world, pos, shiftDown);
			case PLANE_SLICE -> handlePlaneSliceTool(world, pos, resolvePlaneSliceFace(side), shiftDown);
			case SELECTION_WAND -> handleSelectionWandTool(world, pos, shiftDown);
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
		if (interactionCameraPos == null) {
			return true;
		}
		double r = maxDistanceFromCamera;
		return interactionCameraPos.squaredDistanceTo(Vec3d.ofCenter(p)) <= r * r;
	}

	private boolean isWithinWandSpreadFromSeed(BlockPos seed, BlockPos p) {
		double r = maxMagicWandSpreadFromSeed;
		return Vec3d.ofCenter(seed).squaredDistanceTo(Vec3d.ofCenter(p)) <= r * r;
	}

	private void handleClickTool(World world, BlockPos pos, boolean shiftDown) {
		if (!isWithinCameraReach(pos)) {
			lastMessage = "点击选择：该方块超出「相对视角最大距离」。";
			return;
		}
		if (isClaimedByBuildLayer(pos)) {
			lastMessage = layerClaimedMessage(pos);
			return;
		}
		SelectionOperation op = shiftDown ? SelectionOperation.ADD : operation;
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

	private void handleBoxTool(World world, BlockPos pos) {
		BlockPos immutable = pos.toImmutable();
		if (boxFirstCorner == null) {
			boxFirstCorner = immutable;
			lastMessage = "框选：已设角点 A，再点角点 B";
			return;
		}

		BlockPos a = boxFirstCorner;
		BlockPos b = immutable;
		boxFirstCorner = null;

		List<BlockPos> boxBlocks = collectBox(world, a, b);
		if (boxBlocks == null) {
			return;
		}
		mergeBlockListIntoSelection(boxBlocks, operation);
	}

	private void handleLineTool(World world, BlockPos pos) {
		BlockPos immutable = pos.toImmutable();
		if (lineFirstCorner == null) {
			lineFirstCorner = immutable;
			lastMessage = "线选：已设端点 A，再点端点 B";
			return;
		}

		BlockPos a = lineFirstCorner;
		BlockPos b = immutable;
		lineFirstCorner = null;

		List<BlockPos> lineBlocks = collectLine(world, a, b);
		if (lineBlocks == null) {
			return;
		}
		mergeBlockListIntoSelection(lineBlocks, operation);
	}

	private void handleBrushClick(World world, BlockPos pos, boolean shiftDown) {
		SelectionOperation op = shiftDown ? SelectionOperation.ADD : operation;
		List<BlockPos> blocks = collectBrush(world, pos);
		if (blocks == null) {
			return;
		}
		mergeBlockListIntoSelection(blocks, op);
	}

	private void handleConnectedTool(World world, BlockPos pos, boolean shiftDown) {
		SelectionOperation op = shiftDown ? SelectionOperation.ADD : operation;
		List<BlockPos> blocks = collectConnected(world, pos);
		if (blocks == null) {
			return;
		}
		mergeBlockListIntoSelection(blocks, op);
	}

	private void handleColumnTool(World world, BlockPos pos, boolean shiftDown) {
		SelectionOperation op = shiftDown ? SelectionOperation.ADD : operation;
		List<BlockPos> blocks = collectColumn(world, pos);
		if (blocks == null) {
			return;
		}
		mergeBlockListIntoSelection(blocks, op);
	}

	private void handlePlaneSliceTool(World world, BlockPos pos, Direction face, boolean shiftDown) {
		SelectionOperation op = shiftDown ? SelectionOperation.ADD : operation;
		List<BlockPos> blocks = collectPlaneSlice(world, pos, face);
		if (blocks == null) {
			return;
		}
		mergeBlockListIntoSelection(blocks, op);
	}

	private void handleSelectionWandTool(World world, BlockPos pos, boolean shiftDown) {
		BlockPos bMin = getBoundingMin();
		BlockPos bMax = getBoundingMax();
		if (bMin == null || bMax == null) {
			lastMessage = "选区魔棒：请先建立选区（需要有效包围盒）。";
			return;
		}
		if (!containsInBounds(pos, bMin, bMax)) {
			lastMessage = "选区魔棒：请点击当前选区包围盒内的方块。";
			return;
		}
		SelectionOperation op = shiftDown ? SelectionOperation.ADD : operation;
		List<BlockPos> blocks = collectConnectedInBounds(world, pos, bMin, bMax);
		if (blocks == null) {
			return;
		}
		mergeBlockListIntoSelection(blocks, op);
	}

	private static boolean containsInBounds(BlockPos p, BlockPos bMin, BlockPos bMax) {
		return p.getX() >= bMin.getX() && p.getX() <= bMax.getX()
				&& p.getY() >= bMin.getY() && p.getY() <= bMax.getY()
				&& p.getZ() >= bMin.getZ() && p.getZ() <= bMax.getZ();
	}

	private PlaneSliceBounds computePlaneSliceBoundsInternal(World world, BlockPos pos, Direction face) {
		Direction.Axis axis = face.getAxis();
		BlockPos bMin = getBoundingMin();
		BlockPos bMax = getBoundingMax();
		boolean useSel = bMin != null && bMax != null;
		int bottom = world.getBottomY();
		int top = bottom + world.getHeight() - 1;

		ChunkPos cp = new ChunkPos(pos);
		int cx0 = cp.getStartX();
		int cx1 = cx0 + 15;
		int cz0 = cp.getStartZ();
		int cz1 = cz0 + 15;

		return switch (axis) {
			case Y -> {
				int y = pos.getY();
				if (useSel) {
					BlockPos smin = Objects.requireNonNull(bMin);
					BlockPos smax = Objects.requireNonNull(bMax);
					if (y < smin.getY() || y > smax.getY()) {
						yield PlaneSliceBounds.EMPTY;
					}
					yield new PlaneSliceBounds(smin.getX(), smax.getX(), y, y, smin.getZ(), smax.getZ());
				}
				yield new PlaneSliceBounds(cx0, cx1, y, y, cz0, cz1);
			}
			case X -> {
				int x = pos.getX();
				if (useSel) {
					BlockPos smin = Objects.requireNonNull(bMin);
					BlockPos smax = Objects.requireNonNull(bMax);
					if (x < smin.getX() || x > smax.getX()) {
						yield PlaneSliceBounds.EMPTY;
					}
					yield new PlaneSliceBounds(x, x, smin.getY(), smax.getY(), smin.getZ(), smax.getZ());
				}
				yield new PlaneSliceBounds(x, x, bottom, top, cz0, cz1);
			}
			case Z -> {
				int z = pos.getZ();
				if (useSel) {
					BlockPos smin = Objects.requireNonNull(bMin);
					BlockPos smax = Objects.requireNonNull(bMax);
					if (z < smin.getZ() || z > smax.getZ()) {
						yield PlaneSliceBounds.EMPTY;
					}
					yield new PlaneSliceBounds(smin.getX(), smax.getX(), smin.getY(), smax.getY(), z, z);
				}
				yield new PlaneSliceBounds(cx0, cx1, bottom, top, z, z);
			}
		};
	}

	private List<BlockPos> collectPlaneSlice(World world, BlockPos pos, Direction face) {
		PlaneSliceBounds b = computePlaneSliceBoundsInternal(world, pos, face);
		if (b.isEmpty()) {
			lastMessage = "平面切片：该平面与当前范围无交集（可先有选区再切，或对准区块内）。";
			return null;
		}
		long vol = b.volume();
		if (vol > maxBlocks) {
			lastMessage = String.format("平面切片体积 %d 超过上限 %d。", vol, maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>((int) vol);
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int y = b.minY(); y <= b.maxY(); y++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!isWithinCameraReach(p)) continue;
					if (!includeAir && world.getBlockState(p).isAir()) continue;
					out.add(p.toImmutable());
					if (out.size() > maxBlocks) {
						lastMessage = String.format("平面切片超过上限 %d。", maxBlocks);
						return null;
					}
				}
			}
		}
		return out;
	}

	private List<BlockPos> collectConnectedInBounds(World world, BlockPos start, BlockPos bMin, BlockPos bMax) {
		BlockPos startImm = start.toImmutable();
		if (!isWithinCameraReach(startImm)) {
			lastMessage = "选区魔棒：起点超出「相对视角最大距离」。";
			return null;
		}
		ConnectedSelectionFloodFill.Result result = ConnectedSelectionFloodFill.collect(
			world::getBlockState,
			new ConnectedSelectionFloodFill.Request(
				startImm,
				bMin,
				bMax,
				includeAir,
				connectedMatchFullState,
				maxBlocks,
				maxMagicWandSpreadFromSeed,
				this::isWithinCameraReach
			)
		);
		if (result.truncated()) {
			lastMessage = String.format("选区魔棒已达上限 %d，已选 %d 个方块（未完全展开）。", maxBlocks, result.blocks().size());
		}
		return result.blocks();
	}

	private List<BlockPos> collectBrush(World world, BlockPos center) {
		return switch (brushShape) {
			case SPHERE -> collectSphere(world, center, sphereBrushRadius);
			case CUBE -> collectCube(world, center, sphereBrushRadius);
		};
	}

	private List<BlockPos> collectCube(World world, BlockPos center, int r) {
		List<BlockPos> raw = SelectionBrushRegions.cubePositions(center, r, maxBlocks);
		if (raw == null) {
			long worst = (2L * r + 1) * (2L * r + 1) * (2L * r + 1);
			lastMessage = String.format("立方笔刷包络约 %d 方块，超过上限 %d。", worst, maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>(raw.size());
		for (BlockPos p : raw) {
			if (!isWithinCameraReach(p)) continue;
			if (!includeAir && world.getBlockState(p).isAir()) continue;
			out.add(p.toImmutable());
		}
		return out;
	}

	private List<BlockPos> collectBox(World world, BlockPos a, BlockPos b) {
		List<BlockPos> raw = SelectionRegions.cuboidPositions(a, b, maxBlocks);
		if (raw == null) {
			lastMessage = String.format("框选体积 %d 超过上限 %d，已取消。", SelectionRegions.cuboidVolume(a, b), maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>(raw.size());
		for (BlockPos p : raw) {
			if (!isWithinCameraReach(p)) continue;
			if (!includeAir && world.getBlockState(p).isAir()) continue;
			out.add(p.toImmutable());
		}
		return out;
	}

	private List<BlockPos> collectLine(World world, BlockPos a, BlockPos b) {
		List<BlockPos> raw = BlockSelectionLine.blocksForSegment(a, b, lineThicknessRadius, maxBlocks);
		if (raw == null) {
			lastMessage = String.format("线选候选方块超过上限 %d（可缩小线粗细或框选上限）。", maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : raw) {
			if (!isWithinCameraReach(p)) continue;
			if (!includeAir && world.getBlockState(p).isAir()) continue;
			out.add(p.toImmutable());
		}
		return out;
	}

	private List<BlockPos> collectSphere(World world, BlockPos center, int r) {
		List<BlockPos> raw = SelectionBrushRegions.spherePositions(center, r, maxBlocks);
		if (raw == null) {
			long worst = (2L * r + 1) * (2L * r + 1) * (2L * r + 1);
			lastMessage = String.format("球体包络方块数约 %d，超过上限 %d，请缩小半径。", worst, maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>(raw.size());
		for (BlockPos p : raw) {
			if (!isWithinCameraReach(p)) continue;
			if (!includeAir && world.getBlockState(p).isAir()) continue;
			out.add(p.toImmutable());
		}
		return out;
	}

	private List<BlockPos> collectColumn(World world, BlockPos pos) {
		int x = pos.getX();
		int z = pos.getZ();
		int minY = world.getBottomY();
		int maxY = minY + world.getHeight() - 1;
		int span = maxY - minY + 1;
		if (span > maxBlocks) {
			lastMessage = String.format("整列高度 %d 超过上限 %d。", span, maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>(Math.min(span, 4096));
		for (int y = minY; y <= maxY; y++) {
			BlockPos p = new BlockPos(x, y, z);
			if (!isWithinCameraReach(p)) continue;
			if (!includeAir && world.getBlockState(p).isAir()) continue;
			out.add(p.toImmutable());
		}
		return out;
	}

	private List<BlockPos> collectConnected(World world, BlockPos start) {
		BlockPos startImm = start.toImmutable();
		if (!isWithinCameraReach(startImm)) {
			lastMessage = "魔棒：起点超出「相对视角最大距离」，请靠近或在属性面板调大范围。";
			return null;
		}
		ConnectedSelectionFloodFill.Result result = ConnectedSelectionFloodFill.collect(
			world::getBlockState,
			new ConnectedSelectionFloodFill.Request(
				startImm,
				null,
				null,
				includeAir,
				connectedMatchFullState,
				maxBlocks,
				maxMagicWandSpreadFromSeed,
				this::isWithinCameraReach
			)
		);
		if (result.truncated()) {
			lastMessage = String.format("魔棒已达上限 %d，已选 %d 个方块（未完全展开）。", maxBlocks, result.blocks().size());
		}
		return result.blocks();
	}

	private void mergeBlockListIntoSelection(List<BlockPos> blocks, SelectionOperation op) {
		mergeBlockListIntoSelection(blocks, op, false);
	}

	private void mergeBlockListIntoSelection(List<BlockPos> blocks, SelectionOperation op, boolean quiet) {
		int skippedLayer = countLayerClaimed(blocks);
		blocks = excludeLayerClaimed(blocks);
		if (blocks.isEmpty()) {
			if (!quiet) {
				lastMessage = skippedLayer > 0
					? "选区内方块均已属于某图层，无法加入选区。"
					: mergeMessageNew(0);
			}
			return;
		}
		LinkedHashSet<BlockPos> merged = SelectionMerge.apply(selected, blocks, op);
		selected.clear();
		selected.addAll(merged);
		if (!quiet) {
			lastMessage = switch (op) {
				case NEW -> mergeMessageNew(blocks.size());
				case ADD -> mergeMessageAfterAdd();
				case SUBTRACT -> mergeMessageAfterSubtract();
				case INTERSECT -> mergeMessageAfterIntersect();
			};
		}
		if (!quiet && skippedLayer > 0) {
			lastMessage = lastMessage + String.format("（已跳过 %d 个已属于图层的方块）", skippedLayer);
		}
		LOGGER.debug("[BeatBlockSelection] merge op={} size={}", op, selected.size());
	}

	private String brushShapeLabel() {
		return switch (brushShape) {
			case SPHERE -> "球体";
			case CUBE -> "立方";
		};
	}

	private String mergeMessageNew(int count) {
		return switch (mode) {
			case BOX -> "新建框选：" + count + " 个方块";
			case LINE -> "新建线选：" + count + " 个方块";
			case CONNECTED -> "新建连通选区：" + count + " 个方块";
			case COLUMN -> "新建整列：" + count + " 个方块";
			case PLANE_SLICE -> "新建平面切片：" + count + " 个方块";
			case SELECTION_WAND -> "新建选区魔棒：" + count + " 个方块";
			case BRUSH -> "新建笔刷（" + brushShapeLabel() + "）：" + count + " 个方块";
			case LASSO -> "新建套索：" + count + " 个方块";
			default -> "新建选区：" + count + " 个方块";
		};
	}

	private String mergeMessageAfterAdd() {
		int n = selected.size();
		return switch (mode) {
			case BOX -> "加选框后共 " + n + " 个方块";
			case LINE -> "加选线后共 " + n + " 个方块";
			case CONNECTED -> "加选连通区域后共 " + n + " 个方块";
			case COLUMN -> "加选整列后共 " + n + " 个方块";
			case PLANE_SLICE -> "加选切片后共 " + n + " 个方块";
			case SELECTION_WAND -> "加选选区魔棒后共 " + n + " 个方块";
			case BRUSH -> "加选笔刷后共 " + n + " 个方块";
			case LASSO -> "加选套索后共 " + n + " 个方块";
			default -> "加选后共 " + n + " 个方块";
		};
	}

	private String mergeMessageAfterSubtract() {
		int n = selected.size();
		return switch (mode) {
			case BOX -> "减选框后共 " + n + " 个方块";
			case LINE -> "减选线后共 " + n + " 个方块";
			case CONNECTED -> "减选连通区域后共 " + n + " 个方块";
			case COLUMN -> "减选整列后共 " + n + " 个方块";
			case PLANE_SLICE -> "减选切片后共 " + n + " 个方块";
			case SELECTION_WAND -> "减选选区魔棒后共 " + n + " 个方块";
			case BRUSH -> "减选笔刷后共 " + n + " 个方块";
			case LASSO -> "减选套索后共 " + n + " 个方块";
			default -> "减选后共 " + n + " 个方块";
		};
	}

	private String mergeMessageAfterIntersect() {
		int n = selected.size();
		return switch (mode) {
			case BOX -> "与框求交后共 " + n + " 个方块";
			case LINE -> "与线求交后共 " + n + " 个方块";
			case CONNECTED -> "与连通区域求交后共 " + n + " 个方块";
			case COLUMN -> "与整列求交后共 " + n + " 个方块";
			case PLANE_SLICE -> "与切片求交后共 " + n + " 个方块";
			case SELECTION_WAND -> "与选区魔棒结果求交后共 " + n + " 个方块";
			case BRUSH -> "与笔刷求交后共 " + n + " 个方块";
			case LASSO -> "与套索求交后共 " + n + " 个方块";
			default -> "求交后共 " + n + " 个方块";
		};
	}

	public BlockPos getBoundingMin() {
		if (selected.isEmpty()) return null;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		for (BlockPos p : selected) {
			minX = Math.min(minX, p.getX());
			minY = Math.min(minY, p.getY());
			minZ = Math.min(minZ, p.getZ());
		}
		return new BlockPos(minX, minY, minZ);
	}

	public BlockPos getBoundingMax() {
		if (selected.isEmpty()) return null;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : selected) {
			maxX = Math.max(maxX, p.getX());
			maxY = Math.max(maxY, p.getY());
			maxZ = Math.max(maxZ, p.getZ());
		}
		return new BlockPos(maxX, maxY, maxZ);
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

	private static boolean isClaimedByBuildLayer(BlockPos pos) {
		if (pos == null || BeatBlock.blockAnimationEngine == null) return false;
		return BeatBlock.blockAnimationEngine.getBuildLayerManager().isBlockClaimed(pos);
	}

	private static String layerClaimedMessage(BlockPos pos) {
		if (BeatBlock.blockAnimationEngine == null) return "该方块已属于某图层，无法选入选区。";
		BuildLayer owner = BeatBlock.blockAnimationEngine.getBuildLayerManager().getLayerOwningBlock(pos);
		if (owner == null) return "该方块已属于某图层，无法选入选区。";
		return "该方块已属于图层「" + owner.getName() + "」，无法选入选区。";
	}

	private static List<BlockPos> excludeLayerClaimed(List<BlockPos> blocks) {
		if (blocks == null || blocks.isEmpty()) return List.of();
		List<BlockPos> out = new ArrayList<>(blocks.size());
		for (BlockPos pos : blocks) {
			if (pos == null || isClaimedByBuildLayer(pos)) continue;
			out.add(pos.toImmutable());
		}
		return out;
	}

	private static int countLayerClaimed(List<BlockPos> blocks) {
		if (blocks == null || blocks.isEmpty()) return 0;
		int count = 0;
		for (BlockPos pos : blocks) {
			if (pos != null && isClaimedByBuildLayer(pos)) count++;
		}
		return count;
	}

	/** 与 {@link #computePlaneSliceBounds(World, BlockPos, Direction)} 一致，供预览使用。 */
	public record PlaneSliceBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
		public static final PlaneSliceBounds EMPTY = new PlaneSliceBounds(1, 0, 1, 0, 1, 0);

		public boolean isEmpty() {
			return minX > maxX || minY > maxY || minZ > maxZ;
		}

		public long volume() {
			if (isEmpty()) return 0;
			return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
		}
	}
}
