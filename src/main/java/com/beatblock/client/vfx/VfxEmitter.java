package com.beatblock.client.vfx;

import com.beatblock.engine.influence.InfluenceFrame;
import com.beatblock.engine.influence.VfxTrigger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * 消费 {@link InfluenceFrame} 中的 {@link VfxTrigger}，在客户端生成粒子（与方块本体变化解耦）。
 */
public final class VfxEmitter {

	private VfxEmitter() {}

	public static void emit(MinecraftClient client, InfluenceFrame frame) {
		if (client == null || frame == null || frame.getVfxTriggers().isEmpty()) return;
		ClientWorld world = client.world;
		if (world == null) return;
		for (VfxTrigger trigger : frame.getVfxTriggers()) {
			if (trigger == null) continue;
			spawn(world, trigger);
		}
	}

	private static void spawn(ClientWorld world, VfxTrigger trigger) {
		Vec3d center = Vec3d.ofCenter(trigger.blockPos());
		float intensity = Math.max(0.05f, trigger.intensity());
		switch (trigger.kind()) {
			case "appearance_flash" -> burst(world, center, ParticleTypes.CRIT, 4 + Math.round(intensity * 4));
			case "existence_place" -> burst(world, center, ParticleTypes.HAPPY_VILLAGER, 2 + Math.round(intensity * 2));
			case "existence_dissolve" -> burst(world, center, ParticleTypes.POOF, 3 + Math.round(intensity * 2));
			default -> burst(world, center, ParticleTypes.END_ROD, 2);
		}
	}

	private static void burst(ClientWorld world, Vec3d center, net.minecraft.particle.ParticleEffect type, int count) {
		for (int i = 0; i < count; i++) {
			double vx = (world.random.nextDouble() - 0.5) * 0.12;
			double vy = 0.05 + world.random.nextDouble() * 0.08;
			double vz = (world.random.nextDouble() - 0.5) * 0.12;
			world.addParticleClient(type, center.x, center.y, center.z, vx, vy, vz);
		}
	}
}
