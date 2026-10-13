package com.theprogrammingturkey.comz.game;

import com.google.gson.JsonParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Location;

public final class ArenaSelectionCopyTest
{
	public static void main(String[] args) throws Exception
	{
		CuboidRegion region = new CuboidRegion(BlockVector3.at(5, -52, 8), BlockVector3.at(-3, -61, -2));
		ArenaSelectionCopy.validateLocations(JsonParser.parseString("{\"warps\":[{\"x\":-2.5,\"y\":-60.5,\"z\":-1.5},{\"x\":5.9,\"y\":-52,\"z\":8.9}]}"), region, "arena");
		boolean rejected = false;
		try { ArenaSelectionCopy.validateLocations(JsonParser.parseString("{\"lobby_spawn\":{\"x\":-3.1,\"y\":-60,\"z\":0}}"), region, "arena"); }
		catch(java.io.IOException expected) { rejected = expected.getMessage().contains("lobby_spawn"); }
		check(rejected, "Out-of-selection warp must report its configuration path");
		Arena arena = new Arena("test");
		arena.setMin(new Location(null, 5, -52, 8));
		arena.setMax(new Location(null, -3, -61, -2));
		arena.setWorldGuardRegionId("different-shape");
		check(arena.containsCopySelection(new Location(null, -2.5, -60.5, -1.5)), "Reversed corners and negative coordinates");
		check(arena.containsCopySelection(new Location(null, 5.9, -52, 8.9)), "Inclusive final block");
		check(!arena.containsCopySelection(new Location(null, 6, -52, 8)), "Copy stops at corner boundary");
		InstanceVoidGenerator generator = new InstanceVoidGenerator();
		check(!generator.shouldGenerateNoise() && !generator.shouldGenerateSurface() && !generator.shouldGenerateBedrock()
				&& !generator.shouldGenerateCaves() && !generator.shouldGenerateDecorations() && !generator.shouldGenerateMobs()
				&& !generator.shouldGenerateStructures(), "Blank world must disable every generation stage");
		System.out.println("PASS: exact box selection, negative coordinates, external warp rejection and void generation");
	}
	private static void check(boolean result, String message) { if(!result) throw new AssertionError(message); }
}
