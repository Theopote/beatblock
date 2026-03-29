package com.beatblock.mixin.client;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.client.camera.TimelineCameraEvaluator;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 时间线播放时应用摄像机轨采样结果（第三人称等仍由 update 前置逻辑处理，此处覆盖最终 pos/rotation）。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

	@Shadow
	protected abstract void setPos(Vec3d pos);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("RETURN"))
	private void beatblock$applyTimelineCamera(World world, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
		if (!BeatBlockClientDriver.shouldApplyTimelineCameraToView()) return;
		if (BeatBlock.timeline == null || BeatBlock.musicPlayer == null) return;
		double t = BeatBlock.musicPlayer.getCurrentTimeSeconds();
		Camera self = (Camera) (Object) this;
		Vec3d anchor = focusedEntity != null ? focusedEntity.getEyePos() : self.getCameraPos();
		float fy = focusedEntity != null ? focusedEntity.getYaw() : self.getYaw();
		float fp = focusedEntity != null ? focusedEntity.getPitch() : self.getPitch();
		TimelineCameraEvaluator.CameraSample sample = TimelineCameraEvaluator.evaluate(BeatBlock.timeline, t, anchor, fy, fp);
		if (sample == null) return;
		setPos(sample.position());
		setRotation(sample.yawDeg(), sample.pitchDeg());
	}
}
