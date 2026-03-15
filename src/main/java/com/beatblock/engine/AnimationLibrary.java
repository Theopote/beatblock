package com.beatblock.engine;

import com.beatblock.engine.effects.*;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动画模板库：内置 BlockJump、BlockRise、BlockDrop、BlockExplosion、WaveMotion、SpiralLift、Pulse、Orbit 等。
 */
public final class AnimationLibrary {

	private final Map<String, AnimationDefinition> animations = new LinkedHashMap<>();

	public AnimationLibrary() {
		registerBuiltIns();
	}

	private void registerBuiltIns() {
		register(new AnimationDefinition("BlockJump", "跳跃", 0.6f, List.of(new JumpEffect(2f))));
		register(new AnimationDefinition("BlockRise", "升起", 1f, List.of(new RiseEffect(3f))));
		register(new AnimationDefinition("BlockDrop", "落下", 0.5f, List.of(new DropEffect(2f))));
		register(new AnimationDefinition("BlockExplosion", "爆炸", 0.8f, List.of(new ExplosionEffect(Vec3d.ZERO, 4f))));
		register(new AnimationDefinition("WaveMotion", "波浪", 1.2f, List.of(new WaveEffect(0.5f, 0.5f))));
		register(new AnimationDefinition("SpiralLift", "螺旋升空", 1.5f, List.of(new SpiralEffect(Vec3d.ZERO, 2f))));
		register(new AnimationDefinition("Pulse", "脉冲", 0.4f, List.of(new PulseEffect(1.3f))));
		register(new AnimationDefinition("Orbit", "环绕", 2f, List.of(new OrbitEffect(Vec3d.ZERO, 3f))));
	}

	public void register(AnimationDefinition definition) {
		if (definition != null) animations.put(definition.getId(), definition);
	}

	public AnimationDefinition get(String id) {
		return animations.get(id);
	}

	public Map<String, AnimationDefinition> getAll() {
		return Collections.unmodifiableMap(animations);
	}
}
