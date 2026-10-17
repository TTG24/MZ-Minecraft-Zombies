package com.theprogrammingturkey.comz.game;

import java.io.IOException;

import org.bukkit.World;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;

/** One live map snapshot per template, reused for every slot. Requires WorldEdit or FAWE. */
public final class ArenaSelectionCopy implements AutoCloseable
{
	private final Clipboard clipboard;
	private ArenaSelectionCopy(Clipboard clipboard) { this.clipboard = clipboard; }

	@Override public void close()
	{
		// FAWE may back a clipboard with temporary files; ordinary WorldEdit needs no cleanup.
		if(clipboard instanceof AutoCloseable resource)
			try { resource.close(); }
			catch(Exception failure) { com.theprogrammingturkey.comz.COMZombies.log.log(java.util.logging.Level.WARNING, () -> "Could not close arena clipboard: " + failure.getMessage()); }
	}

	public static ArenaSelectionCopy capture(Game template) throws IOException
	{
		var min = template.arena.getMin();
		var max = template.arena.getMax();
		CuboidRegion region = new CuboidRegion(BukkitAdapter.adapt(template.getWorld()),
				BlockVector3.at(min.getBlockX(), min.getBlockY(), min.getBlockZ()),
				BlockVector3.at(max.getBlockX(), max.getBlockY(), max.getBlockZ()));
		long limit = com.theprogrammingturkey.comz.COMZombies.getPlugin().getConfig().getLong("config.instances.maxCopyBlocks", 5000000L);
		if(region.getVolume() > limit)
			throw new IOException("Arena box contains " + region.getVolume() + " blocks; exceeds config.instances.maxCopyBlocks=" + limit);
		validateLocations(template.saveGame(), region, "arena");
		ArenaSelectionCopy captured = null;
		try
		{
			Clipboard snapshot = new BlockArrayClipboard(region);
			captured = new ArenaSelectionCopy(snapshot);
			snapshot.setOrigin(region.getMinimumPoint());
			ForwardExtentCopy copy = new ForwardExtentCopy(BukkitAdapter.adapt(template.getWorld()), region, snapshot, region.getMinimumPoint());
			copy.setCopyingEntities(true);
			copy.setCopyingBiomes(true);
			Operations.complete(copy);
			return captured;
		}
		catch(Exception | LinkageError failure)
		{
			if(captured != null) captured.close();
			throw new IOException("Could not capture arena selection", failure);
		}
	}

	static void validateLocations(JsonElement value, CuboidRegion region, String path) throws IOException
	{
		if(value.isJsonObject())
		{
			JsonObject object = value.getAsJsonObject();
			if(object.has("x") && object.has("y") && object.has("z"))
			{
				BlockVector3 point = BlockVector3.at((int) Math.floor(object.get("x").getAsDouble()),
						(int) Math.floor(object.get("y").getAsDouble()), (int) Math.floor(object.get("z").getAsDouble()));
				if(!region.contains(point))
					throw new IOException(path + " at " + point + " is outside the arena copy box. Expand the arena's two corners to include it.");
			}
			for(var entry : object.entrySet()) validateLocations(entry.getValue(), region, path + "." + entry.getKey());
		}
		else if(value.isJsonArray())
		{
			int index = 0;
			for(JsonElement element : value.getAsJsonArray()) validateLocations(element, region, path + "[" + index++ + "]");
		}
	}

	public void paste(World destination) throws IOException
	{
		// Pasting with a direct ForwardExtentCopy (clipboard -> world) instead of a
		// ClipboardHolder is deliberate. A ClipboardHolder is Closeable, and closing it
		// would call Clipboard.close() on the shared snapshot, releasing FAWE's disk-backed
		// buffer. This snapshot is pasted once per instance slot, so it must stay open
		// until every slot is done; only the EditSession is closed here, and the single
		// shared clipboard is closed exactly once by the caller (ArenaInstances).
		try(var session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(destination)))
		{
			ForwardExtentCopy copy = new ForwardExtentCopy(clipboard, clipboard.getRegion(), session, clipboard.getOrigin());
			copy.setCopyingEntities(true);
			copy.setCopyingBiomes(true);
			Operations.complete(copy);
		}
		catch(Exception failure) { throw new IOException("Could not paste arena selection", failure); }
	}
}
