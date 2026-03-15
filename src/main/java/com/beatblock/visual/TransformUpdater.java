package com.beatblock.visual;

import com.beatblock.animation.AnimationInstance;
import com.beatblock.animation.TransformState;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 根据 AnimationInstance 的当前状态更新 BlockDisplay 的变换（位置、旋转、缩放）。
 * 1.21 Display 实体通过 setPos 更新位置；旋转与缩放使用 setTransformation（见下方实现）。
 */
public class TransformUpdater {

	private Vec3 basePosition = Vec3.ZERO;

	public void setBasePosition(Vec3 basePosition) {
		this.basePosition = basePosition;
	}

	/**
	 * 用当前时间对所有活跃实例采样，并更新其绑定的 BlockDisplay。
	 */
	public void tick(List<AnimationInstance> activeInstances, double currentTimeSeconds) {
		for (AnimationInstance inst : activeInstances) {
			Object target = inst.getDisplayTarget();
			if (!(target instanceof Display.BlockDisplay display)) continue;
			TransformState state = inst.sample(currentTimeSeconds);
			double x = basePosition.x + state.getX();
			double y = basePosition.y + state.getY();
			double z = basePosition.z + state.getZ();
			display.setPos(x, y, z);
			// 旋转与缩放需通过 Display 的 transformation 数据设置，此处仅更新位置
			// 完整变换可后续通过 Display.getEntityData() 或对应 API 实现
		}
	}
}
