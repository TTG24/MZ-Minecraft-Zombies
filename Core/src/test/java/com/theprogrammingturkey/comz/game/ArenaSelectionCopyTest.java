package com.theprogrammingturkey.comz.game;

import java.lang.reflect.Proxy;

import org.bukkit.Location;
import org.bukkit.World;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;

class ArenaSelectionCopyTest
{
	private static final CuboidRegion REGION = new CuboidRegion(BlockVector3.at(5, -52, 8), BlockVector3.at(-3, -61, -2));
	private static final Arena ARENA = createArena();
	private static Arena createArena()
	{
		Arena arena = new Arena("test");
		arena.setMin(new Location(null, 5, -52, 8));
		arena.setMax(new Location(null, -3, -61, -2));
		arena.setWorldGuardRegionId("different-shape");
		return arena;
	}

	@Test
	void acceptsWarpsInsideSelection() throws Exception
	{
		ArenaSelectionCopy.validateLocations(JsonParser.parseString("{\"warps\":[{\"x\":-2.5,\"y\":-60.5,\"z\":-1.5},{\"x\":5.9,\"y\":-52,\"z\":8.9}]}"), REGION, "arena");
	}

	@Test
	void reportsConfigPathForOutOfSelectionWarp()
	{
		java.io.IOException e = assertThrows(java.io.IOException.class,
				() -> ArenaSelectionCopy.validateLocations(JsonParser.parseString("{\"lobby_spawn\":{\"x\":-3.1,\"y\":-60,\"z\":0}}"), REGION, "arena"));
		assertTrue(e.getMessage().contains("lobby_spawn"), "Exception must report its configuration path");
	}

	@Test
	void containsCopySelectionHandlesReversedCornersAndBoundaries()
	{
		assertTrue(ARENA.containsCopySelection(new Location(null, -2.5, -60.5, -1.5)), "Reversed corners and negative coordinates");
		assertTrue(ARENA.containsCopySelection(new Location(null, 5.9, -52, 8.9)), "Inclusive final block");
		assertFalse(ARENA.containsCopySelection(new Location(null, 6, -52, 8)), "Copy stops at corner boundary");
	}

	@Test
	void containsBlockUsesBlockCoordinatesLikeCopySelection()
	{
		// Regression: containsBlock compared the raw double x/y/z against the *block*
		// bounds, so a location like (5.9, -52, 8.9) was reported outside the arena
		// even though its block (5, -52, 8) is inside. Both helpers must agree and
		// must treat a fractional coordinate as belonging to its block.
		World proxy = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
				(proxyObj, method, values) -> method.getName().equals("getName") ? "test-world" : null);
		Arena boxOnly = new Arena("test");
		boxOnly.setWorld(proxy);
		boxOnly.setMin(new Location(proxy, 5, -52, 8));
		boxOnly.setMax(new Location(proxy, -3, -61, -2));
		assertTrue(boxOnly.containsBlock(new Location(proxy, -2.5, -60.5, -1.5)), "Reversed corners and negative coordinates");
		assertTrue(boxOnly.containsBlock(new Location(proxy, 5.9, -52, 8.9)), "Fractional coordinate in the last block row is still inside");
		assertFalse(boxOnly.containsBlock(new Location(proxy, 6, -52, 8)), "Block past the corner is outside");
		assertTrue(boxOnly.containsBlock(new Location(proxy, 5, -52, 8)), "Exact corner block is inside");
		assertTrue(boxOnly.containsBlock(new Location(proxy, -3, -61, -2)), "Opposite corner block is inside");
		assertFalse(boxOnly.containsBlock(null), "Null location is never inside");
	}

	@Test
	void voidGeneratorDisablesEveryGenerationStage()
	{
		InstanceVoidGenerator generator = new InstanceVoidGenerator();
		assertFalse(generator.shouldGenerateNoise(), "noise");
		assertFalse(generator.shouldGenerateSurface(), "surface");
		assertFalse(generator.shouldGenerateBedrock(), "bedrock");
		assertFalse(generator.shouldGenerateCaves(), "caves");
		assertFalse(generator.shouldGenerateDecorations(), "decorations");
		assertFalse(generator.shouldGenerateMobs(), "mobs");
		assertFalse(generator.shouldGenerateStructures(), "structures");
	}
}
