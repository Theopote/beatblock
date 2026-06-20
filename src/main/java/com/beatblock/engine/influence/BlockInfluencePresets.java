package com.beatblock.engine.influence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置方块影响预设，与 {@link com.beatblock.engine.AnimationLibrary} 注册项一一对应（期 1 数据层）。
 */
public final class BlockInfluencePresets {

	private static final Map<String, BlockInfluencePreset> PRESETS = new LinkedHashMap<>();

	static {
		registerBuiltIns();
	}

	private BlockInfluencePresets() {}

	public static void register(BlockInfluencePreset preset) {
		if (preset != null) {
			PRESETS.put(preset.getId(), preset);
		}
	}

	public static BlockInfluencePreset get(String id) {
		return PRESETS.get(id);
	}

	public static Map<String, BlockInfluencePreset> getAll() {
		return Collections.unmodifiableMap(PRESETS);
	}

	private static void registerBuiltIns() {
		register(BlockInfluencePreset.builder("BlockTap", "跑酷踩点")
			.durationSeconds(0.35f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.OFFSET_Y,
				CurveKind.SINE_BUMP,
				0f,
				0.8f
			))
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_SCALE,
				PathKind.SCALE_UNIFORM,
				CurveKind.SINE_BUMP,
				1f,
				1.15f
			))
			.channel(ChannelSpec.enabled(
				InfluenceDimension.APPEARANCE,
				PathKind.BLOCK_STATE,
				CurveKind.SINE_BUMP,
				0f,
				1f
			))
			.build());

		register(BlockInfluencePreset.builder("BlockJump", "跳跃")
			.durationSeconds(0.6f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.OFFSET_Y,
				CurveKind.SINE_BUMP,
				0f,
				2f
			))
			.build());

		register(BlockInfluencePreset.builder("BlockRise", "升起")
			.durationSeconds(1f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.OFFSET_Y,
				CurveKind.LINEAR,
				0f,
				3f
			))
			.build());

		register(BlockInfluencePreset.builder("BlockDrop", "落下")
			.durationSeconds(0.5f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.OFFSET_Y,
				CurveKind.LINEAR,
				0f,
				-2f
			))
			.build());

		register(BlockInfluencePreset.builder("Pulse", "脉冲")
			.durationSeconds(0.4f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_SCALE,
				PathKind.SCALE_UNIFORM,
				CurveKind.SINE_BUMP,
				1f,
				1.3f
			))
			.build());

		register(BlockInfluencePreset.builder("Meteor", "流星坠落")
			.durationSeconds(1f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.WORLD_TRAJECTORY,
				CurveKind.GRAVITY_REMAINING,
				12f,
				0f
			))
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_SCALE,
				PathKind.SCALE_UNIFORM,
				CurveKind.LINEAR,
				0.5f,
				1f
			))
			.build());

		register(BlockInfluencePreset.builder("BlockExplosion", "爆炸")
			.durationSeconds(0.8f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.RADIAL_FROM_CENTER,
				CurveKind.LINEAR,
				0f,
				4f
			))
			.build());

		register(BlockInfluencePreset.builder("WaveMotion", "波浪")
			.durationSeconds(1.2f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.WAVE_Y,
				CurveKind.SINE_BUMP,
				0f,
				0.5f
			))
			.build());

		register(BlockInfluencePreset.builder("SpiralLift", "螺旋升空")
			.durationSeconds(1.5f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.SPIRAL_LIFT,
				CurveKind.LINEAR,
				0f,
				2f
			))
			.build());

		register(BlockInfluencePreset.builder("Orbit", "环绕")
			.durationSeconds(2f)
			.channel(ChannelSpec.enabled(
				InfluenceDimension.TRANSFORM_POSITION,
				PathKind.ORBIT_HORIZONTAL,
				CurveKind.LINEAR,
				0f,
				3f
			))
			.build());
	}
}
