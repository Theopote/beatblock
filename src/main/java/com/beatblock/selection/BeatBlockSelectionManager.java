package com.beatblock.selection;

import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
	private boolean connectedMatchFullState = true;
	private BrushShape brushShape = BrushShape.SPHERE;
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

	public boolean isConnectedMatchFullState() {
		return connectedMatchFullState;
	}

	public void setConnectedMatchFullState(boolean connectedMatchFullState) {
		this.connectedMatchFullState = connectedMatchFullState;
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
		connectedMatchFullState = true;
		brushShape = BrushShape.SPHERE;
		maxDistanceFromCamera = 128;
		maxMagicWandSpreadFromSeed = 64;
		interactionCameraPos = null;
		selectionFillEnabled = false;
		clearBrushAnchor();
		brushHadStroke = false;
		lastMessage = "";
	}

	/**
	 * 左键单击（含击中面）。平面切片依赖 {@link BlockHitResult#getSide()}。
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
			case PLANE_SLICE -> handlePlaneSliceTool(world, pos, side, shiftDown);
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
		BlockState anchor = world.getBlockState(start);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		HashSet<BlockPos> visited = new HashSet<>();
		List<BlockPos> result = new ArrayList<>();

		queue.add(startImm);
		visited.add(startImm);

		while (!queue.isEmpty()) {
			if (result.size() >= maxBlocks) {
				lastMessage = String.format("选区魔棒已达上限 %d，已选 %d 个方块（未完全展开）。", maxBlocks, result.size());
				break;
			}
			BlockPos p = queue.removeFirst();
			result.add(p);

			for (Direction d : Direction.values()) {
				BlockPos n = p.offset(d);
				if (visited.contains(n)) continue;
				if (!containsInBounds(n, bMin, bMax)) continue;
				if (!isWithinCameraReach(n)) continue;
				if (!isWithinWandSpreadFromSeed(startImm, n)) continue;
				BlockState st = world.getBlockState(n);
				if (!includeAir && st.isAir()) continue;
				if (!connectedMatches(st, anchor)) continue;
				visited.add(n);
				queue.add(n.toImmutable());
			}
		}

		return result;
	}

	private List<BlockPos> collectBrush(World world, BlockPos center) {
		return switch (brushShape) {
			case SPHERE -> collectSphere(world, center, sphereBrushRadius);
			case CUBE -> collectCube(world, center, sphereBrushRadius);
		};
	}

	private List<BlockPos> collectCube(World world, BlockPos center, int r) {
		long worst = (2L * r + 1) * (2L * r + 1) * (2L * r + 1);
		if (worst > maxBlocks) {
			lastMessage = String.format("立方笔刷包络约 %d 方块，超过上限 %d。", worst, maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = center.add(dx, dy, dz);
					if (!isWithinCameraReach(p)) continue;
					if (!includeAir && world.getBlockState(p).isAir()) continue;
					out.add(p.toImmutable());
					if (out.size() > maxBlocks) {
						lastMessage = String.format("立方笔刷超过上限 %d。", maxBlocks);
						return null;
					}
				}
			}
		}
		return out;
	}

	private List<BlockPos> collectBox(World world, BlockPos a, BlockPos b) {
		int x0 = Math.min(a.getX(), b.getX());
		int x1 = Math.max(a.getX(), b.getX());
		int y0 = Math.min(a.getY(), b.getY());
		int y1 = Math.max(a.getY(), b.getY());
		int z0 = Math.min(a.getZ(), b.getZ());
		int z1 = Math.max(a.getZ(), b.getZ());
		long dx = (long) x1 - x0 + 1L;
		long dy = (long) y1 - y0 + 1L;
		long dz = (long) z1 - z0 + 1L;
		long vol = dx * dy * dz;
		if (vol > maxBlocks) {
			lastMessage = String.format("框选体积 %d 超过上限 %d，已取消。", vol, maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>((int) vol);
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!isWithinCameraReach(p)) continue;
					if (!includeAir && world.getBlockState(p).isAir()) continue;
					out.add(p.toImmutable());
				}
			}
		}
		return out;
	}

	private List<BlockPos> collectLine(World world, BlockPos a, BlockPos b) {
		List<BlockPos> raw = BlockSelectionLine.between(a, b);
		if (raw.size() > maxBlocks) {
			lastMessage = String.format("线选经过 %d 格，超过上限 %d。", raw.size(), maxBlocks);
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
		int rr = r * r;
		long worst = (2L * r + 1) * (2L * r + 1) * (2L * r + 1);
		if (worst > maxBlocks) {
			lastMessage = String.format("球体包络方块数约 %d，超过上限 %d，请缩小半径。", worst, maxBlocks);
			return null;
		}
		List<BlockPos> out = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz > rr) continue;
					BlockPos p = center.add(dx, dy, dz);
					if (!isWithinCameraReach(p)) continue;
					if (!includeAir && world.getBlockState(p).isAir()) continue;
					out.add(p.toImmutable());
					if (out.size() > maxBlocks) {
						lastMessage = String.format("球体选区超过上限 %d。", maxBlocks);
						return null;
					}
				}
			}
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
		BlockState anchor = world.getBlockState(start);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		HashSet<BlockPos> visited = new HashSet<>();
		List<BlockPos> result = new ArrayList<>();

		queue.add(startImm);
		visited.add(startImm);

		while (!queue.isEmpty()) {
			if (result.size() >= maxBlocks) {
				lastMessage = String.format("魔棒已达上限 %d，已选 %d 个方块（未完全展开）。", maxBlocks, result.size());
				break;
			}
			BlockPos p = queue.removeFirst();
			result.add(p);

			for (Direction d : Direction.values()) {
				BlockPos n = p.offset(d);
				if (visited.contains(n)) continue;
				if (!isWithinCameraReach(n)) continue;
				if (!isWithinWandSpreadFromSeed(startImm, n)) continue;
				BlockState st = world.getBlockState(n);
				if (!includeAir && st.isAir()) continue;
				if (!connectedMatches(st, anchor)) continue;
				visited.add(n);
				queue.add(n.toImmutable());
			}
		}

		return result;
	}

	private boolean connectedMatches(BlockState state, BlockState anchor) {
		return connectedMatchFullState ? state.equals(anchor) : state.getBlock() == anchor.getBlock();
	}

	private void mergeBlockListIntoSelection(List<BlockPos> blocks, SelectionOperation op) {
		mergeBlockListIntoSelection(blocks, op, false);
	}

	private void mergeBlockListIntoSelection(List<BlockPos> blocks, SelectionOperation op, boolean quiet) {
		switch (op) {
			case NEW -> {
				selected.clear();
				selected.addAll(blocks);
				if (!quiet) {
					lastMessage = mergeMessageNew(blocks.size());
				}
			}
			case ADD -> {
				selected.addAll(blocks);
				if (!quiet) {
					lastMessage = mergeMessageAfterAdd();
				}
			}
			case SUBTRACT -> {
				blocks.forEach(selected::remove);
				if (!quiet) {
					lastMessage = mergeMessageAfterSubtract();
				}
			}
			case INTERSECT -> {
				selected.retainAll(Set.copyOf(blocks));
				if (!quiet) {
					lastMessage = mergeMessageAfterIntersect();
				}
			}
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
		selected.addAll(toAdd);
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
