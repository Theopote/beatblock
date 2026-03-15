package com.beatblock.engine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 动画系统不直接修改世界方块，通过 AnimatedBlock 在逻辑/渲染层表示单块的状态。
 * 原始世界不动，动画在渲染层应用变换。
 */
public final class AnimatedBlock {

	private final BlockPos originalPos;
	private Vec3d position;
	private Vec3d velocity;
	private float rotationYaw;
	private float rotationPitch;
	private float scale;

	public AnimatedBlock(BlockPos originalPos) {
		this.originalPos = originalPos != null ? originalPos : BlockPos.ORIGIN;
		this.position = new Vec3d(this.originalPos.getX() + 0.5, this.originalPos.getY(), this.originalPos.getZ() + 0.5);
		this.velocity = Vec3d.ZERO;
		this.rotationYaw = 0f;
		this.rotationPitch = 0f;
		this.scale = 1f;
	}

	public BlockPos getOriginalPos() {
		return originalPos;
	}

	public Vec3d getPosition() {
		return position;
	}

	public void setPosition(Vec3d position) {
		this.position = position != null ? position : this.position;
	}

	public void setPosition(double x, double y, double z) {
		this.position = new Vec3d(x, y, z);
	}

	public Vec3d getVelocity() {
		return velocity;
	}

	public void setVelocity(Vec3d velocity) {
		this.velocity = velocity != null ? velocity : Vec3d.ZERO;
	}

	public float getRotationYaw() {
		return rotationYaw;
	}

	public void setRotationYaw(float rotationYaw) {
		this.rotationYaw = rotationYaw;
	}

	public float getRotationPitch() {
		return rotationPitch;
	}

	public void setRotationPitch(float rotationPitch) {
		this.rotationPitch = rotationPitch;
	}

	public float getScale() {
		return scale;
	}

	public void setScale(float scale) {
		this.scale = Math.max(0.01f, scale);
	}

	/** 重置到原始位置与默认状态，用于每帧从原始坐标开始再叠加效果 */
	public void resetToOriginal() {
		position = new Vec3d(originalPos.getX() + 0.5, originalPos.getY(), originalPos.getZ() + 0.5);
		velocity = Vec3d.ZERO;
		rotationYaw = 0f;
		rotationPitch = 0f;
		scale = 1f;
	}
}
