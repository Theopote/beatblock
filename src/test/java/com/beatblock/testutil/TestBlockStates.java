package com.beatblock.testutil;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 不触发 Minecraft 注册表初始化的 {@link BlockState} 桩。
 */
public final class TestBlockStates {

	private TestBlockStates() {}

	public static Block mockBlock(String label) {
		Block block = mock(Block.class);
		when(block.toString()).thenReturn("TestBlock(" + label + ")");
		return block;
	}

	public static BlockState air() {
		BlockState state = mock(BlockState.class);
		when(state.isAir()).thenReturn(true);
		when(state.getBlock()).thenReturn(mockBlock("air"));
		when(state.equals(any())).thenAnswer(inv -> inv.getArgument(0) == state);
		return state;
	}

	public static BlockState ofBlock(Block block) {
		BlockState state = mock(BlockState.class);
		when(state.isAir()).thenReturn(false);
		when(state.getBlock()).thenReturn(block);
		when(state.equals(any())).thenAnswer(inv -> inv.getArgument(0) == state);
		return state;
	}

	public static BlockState ofBlock(String label) {
		return ofBlock(mockBlock(label));
	}
}
