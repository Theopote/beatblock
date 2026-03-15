package com.beatblock.animation;

/**
 * 单帧变换状态：供 Visual 系统应用到 BlockDisplay。
 */
public class TransformState {

	private final double x, y, z;
	private final double yaw, pitch, roll;
	private final double scaleX, scaleY, scaleZ;

	public TransformState(double x, double y, double z,
	                      double yaw, double pitch, double roll,
	                      double scaleX, double scaleY, double scaleZ) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		this.roll = roll;
		this.scaleX = scaleX;
		this.scaleY = scaleY;
		this.scaleZ = scaleZ;
	}

	public double getX() { return x; }
	public double getY() { return y; }
	public double getZ() { return z; }
	public double getYaw() { return yaw; }
	public double getPitch() { return pitch; }
	public double getRoll() { return roll; }
	public double getScaleX() { return scaleX; }
	public double getScaleY() { return scaleY; }
	public double getScaleZ() { return scaleZ; }

	public static TransformState identity() {
		return new TransformState(0, 0, 0, 0, 0, 0, 1, 1, 1);
	}
}
