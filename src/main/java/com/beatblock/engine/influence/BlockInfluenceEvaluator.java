package com.beatblock.engine.influence;

import com.beatblock.engine.AnimatedBlock;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.camera.CameraViewMath;
import com.beatblock.engine.influence.CurveLibrary;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 按 {@link BlockInfluencePreset} 通道对方块施加渲染层影响。
 */
public final class BlockInfluenceEvaluator {

	public void applyPreset(
		AnimatedBlock block,
		BlockInfluencePreset preset,
		float t,
		float energy,
		EffectContext ctx
	) {
		if (block == null || preset == null) return;
		for (ChannelSpec channel : preset.getChannels()) {
			if (channel == null || !channel.enabled()) continue;
			applyChannel(block, channel, t, energy, ctx);
		}
	}

	private void applyChannel(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		switch (channel.dimension()) {
			case TRANSFORM_POSITION -> applyPositionChannel(block, channel, t, energy, ctx);
			case TRANSFORM_SCALE -> applyScaleChannel(block, channel, t, energy, ctx);
			case TRANSFORM_ROTATION -> applyRotationChannel(block, channel, t, energy);
			case EXISTENCE, APPEARANCE, VFX -> { /* 世界层 / 期 3 */ }
		}
	}

	private void applyPositionChannel(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		switch (channel.path()) {
			case OFFSET_Y -> applyOffsetY(block, channel, t, energy, ctx);
			case WORLD_TRAJECTORY -> applyWorldTrajectory(block, channel, t, energy, ctx);
			case RADIAL_FROM_CENTER -> applyRadialFromCenter(block, channel, t, energy, ctx);
			case WAVE_Y -> applyWaveY(block, channel, t, energy, ctx);
			case SPIRAL_LIFT -> applySpiralLift(block, channel, t, energy, ctx);
			case ORBIT_HORIZONTAL -> applyOrbitHorizontal(block, channel, t, energy, ctx);
			default -> { }
		}
	}

	private void applyOffsetY(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		float to = resolveOffsetYEndpoint(channel, ctx);
		float from = channel.from();
		float factor = CurveLibrary.sample(channel.curve(), t);
		float delta = (from + (to - from) * factor) * Math.max(0f, energy);

		Vec3d pos = block.getPosition();
		double xOff = 0.0;
		double zOff = 0.0;
		float scatter = (float) ctx.paramDouble("meteorScatter", 0.0);
		if (scatter > 0f && to < 0f) {
			float e = Math.max(0f, energy);
			xOff = scatter * Math.sin(pos.x * 3.7 + t * 2) * e;
			zOff = scatter * Math.cos(pos.z * 3.7 + t * 2) * e;
		}
		block.setPosition(pos.x + xOff, pos.y + delta, pos.z + zOff);
	}

	private static float resolveOffsetYEndpoint(ChannelSpec channel, EffectContext ctx) {
		if (channel.to() < 0f) {
			return -(float) ctx.paramDouble("meteorHeight", -channel.to());
		}
		return (float) ctx.paramDouble("offsetYTo", channel.to());
	}

	private void applyWorldTrajectory(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		float height = (float) ctx.paramDouble("meteorHeight", channel.from());
		float scatter = (float) ctx.paramDouble("meteorScatter", 2.5);
		Vec3d pos = block.getPosition();
		double fall = CurveLibrary.gravityRemainingHeight(t, height, energy);
		double xOff = scatter > 0f
			? scatter * Math.sin(pos.x * 1.9 + pos.z * 0.7) * CurveLibrary.scatterEnvelope(t, energy)
			: 0.0;
		double zOff = scatter > 0f
			? scatter * Math.cos(pos.z * 1.9 + pos.x * 0.7) * CurveLibrary.scatterEnvelope(t, energy)
			: 0.0;
		block.setPosition(pos.x + xOff, pos.y + fall, pos.z + zOff);
	}

	private void applyRadialFromCenter(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		float strength = (float) ctx.paramDouble("impactRadius", channel.to());
		float burst = (float) ctx.paramDouble("impactBurst", 1.0);
		Vec3d center = ctx.getStageCenter();
		Vec3d pos = block.getPosition();
		Vec3d dir = pos.subtract(center);
		double len = dir.length();
		if (len < 1e-6) return;
		dir = dir.multiply(1.0 / len);
		double move = CurveLibrary.linearProgress(t, strength * burst, energy);
		block.setPosition(pos.add(dir.multiply(move)));
	}

	private void applyWaveY(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		float amplitude = (float) ctx.paramDouble("waveAmplitude", channel.to());
		float frequency = (float) ctx.paramDouble("waveFrequency", channel.from() > 0f ? channel.from() : 0.5f);
		float phase = (float) ctx.paramDouble("wavePhaseOffset", 0.0);
		Vec3d pos = block.getPosition();
		Vec3d cameraPos = ctx.getCameraPosition();

		if (cameraPos != null) {
			double nearDistance = ctx.paramDouble("cameraNearDistance", CameraViewMath.DEFAULT_NEAR_DISTANCE);
			double farDistance = ctx.paramDouble("cameraFarDistance", CameraViewMath.DEFAULT_FAR_DISTANCE);
			double nearScale = ctx.paramDouble("cameraNearScale", CameraViewMath.DEFAULT_NEAR_SCALE);
			double farScale = ctx.paramDouble("cameraFarScale", CameraViewMath.DEFAULT_FAR_SCALE);
			double viewCap = ctx.paramDouble("waveViewDistance", farDistance);
			double halfFov = ctx.paramDouble("waveViewHalfFovDeg", CameraViewMath.DEFAULT_VIEW_HALF_FOV_DEG);
			BlockPos orig = block.getOriginalPos();
			double wave = CameraViewMath.radialWaveOffsetY(
				orig,
				cameraPos,
				ctx.getCameraForward(),
				t,
				energy,
				amplitude,
				frequency,
				phase,
				nearDistance,
				farDistance,
				nearScale,
				farScale,
				viewCap,
				halfFov
			);
			block.setPosition(pos.x, pos.y + wave, pos.z);
			return;
		}

		double wave = Math.sin(pos.x * frequency + t * 6 + phase) * amplitude * Math.max(0f, energy);
		block.setPosition(pos.x, pos.y + wave, pos.z);
	}

	private void applySpiralLift(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		float radius = (float) ctx.paramDouble("spiralRadius", channel.to());
		Vec3d center = ctx.getStageCenter();
		double angle = CurveLibrary.linearProgress(t, 6f, energy);
		double x = center.x + Math.cos(angle) * radius;
		double z = center.z + Math.sin(angle) * radius;
		Vec3d pos = block.getPosition();
		double y = pos.y + CurveLibrary.linearProgress(t, 5f, energy);
		block.setPosition(x, y, z);
	}

	private void applyOrbitHorizontal(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		float radius = (float) ctx.paramDouble("orbitRadius", channel.to());
		Vec3d center = ctx.getStageCenter();
		double angle = CurveLibrary.linearProgress(t, (float) (Math.PI * 2), energy);
		double x = center.x + Math.cos(angle) * radius;
		double z = center.z + Math.sin(angle) * radius;
		Vec3d pos = block.getPosition();
		block.setPosition(x, pos.y, z);
	}

	private void applyScaleChannel(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy,
		EffectContext ctx
	) {
		if (channel.path() != PathKind.SCALE_UNIFORM) return;
		float factor = switch (channel.curve()) {
			case SINE_BUMP -> CurveLibrary.scaleSinePulse(t, channel.to(), energy);
			case LINEAR -> CurveLibrary.meteorApproachScale(t, energy);
			default -> channel.sample(t);
		};
		block.setScale(block.getScale() * factor);
	}

	private void applyRotationChannel(
		AnimatedBlock block,
		ChannelSpec channel,
		float t,
		float energy
	) {
		float delta = channel.sampleMagnitude(t, energy);
		block.setRotationYaw(block.getRotationYaw() + delta);
	}
}
