package com.beatblock.selection;

import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectedCellLookupTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void fromMaterialGridReturnsConfiguredMaterials() {
		BlockPos stone = new BlockPos(0, 64, 0);
		BlockPos dirt = new BlockPos(1, 64, 0);
		ConnectedCellLookup lookup = ConnectedCellLookup.fromMaterialGrid(Map.of(
			stone, 1,
			dirt, 2
		));

		assertEquals(1, lookup.materialAt(stone));
		assertEquals(2, lookup.materialAt(dirt));
		assertEquals(0, lookup.materialAt(new BlockPos(9, 64, 0)));
	}

	@Test
	void fromBlockStateLookupTreatsAirAsZero() {
		ConnectedCellLookup lookup = ConnectedCellLookup.fromBlockStateLookup(
			pos -> Blocks.AIR.getDefaultState(), false);
		assertEquals(0, lookup.materialAt(new BlockPos(0, 64, 0)));
	}
}
