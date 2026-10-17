package com.theprogrammingturkey.comz.game;

import java.io.IOException;
import java.util.Set;

import org.bukkit.World;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;

/** One live map snapshot per template, reused for every slot. Requires WorldEdit or FAWE. */
public final class ArenaSelectionCopy implements AutoCloseable
{
	/**
	 * Entity types never copied into instances: players, dropped items and XP orbs,
	 * projectiles, falling blocks, and the hostile mobs that can be alive in a running
	 * arena. Decor entities (armor stands, item frames, paintings, boats, minecarts,
	 * passive mobs) are snapshotted as-is so they appear in every instance.
	 */
	private static final Set<String> EXCLUDED_ENTITIES = Set.of(
			"player", "falling_block",
			"item", "xp_orb",
			"arrow", "spectral_arrow", "snowball", "egg",
			"fireball", "small_fireball", "dragon_fireball",
			"trident", "wind_charge", "lightning_bolt",
			"shulker_bullet", "evoker_fangs",
			"zombie", "zombie_villager", "husk", "drowned",
			"skeleton", "stray", "wither_skeleton", "creeper",
			"spider", "cave_spider", "enderman", "witch",
			"blaze", "wither", "phantom", "slime",
			"magma_cube", "guardian", "elder_guardian",
			"pillager", "vindicator", "evoker", "ravager",
			"piglin", "piglin_brute", "hoglin", "zoglin",
			"zombified_piglin", "zombified_piglin_brute", "warden",
			"breeze", "bogged", "vex");

	/** True when the entity type should never be snapshotted into instances. */
	private static boolean isExcluded(Entity entity)
	{
		var type = entity.getType();
		if(type == null)
			return true; // Unknown type; safer to leave it out than to bake it into every copy.
		String name = type.getName();
		int ns = name.indexOf(':');
		String entityName = ns >= 0 ? name.substring(ns + 1) : name;
		return EXCLUDED_ENTITIES.contains(entityName);
	}

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
			// Blocks+biomes come from the ForwardExtentCopy. Entities are snapshotted separately
			// below instead: running FAWE's own entity retrieval against a raw BukkitWorld warns
			// about a missing IQueueExtent and only sees loaded chunks, while collecting them
			// explicitly lets us filter out live mobs, items and projectiles.
			copy.setCopyingEntities(false);
			copy.setCopyingBiomes(true);
			Operations.complete(copy);
			int skipped = 0;
			for(Entity entity : BukkitAdapter.adapt(template.getWorld()).getEntities(region))
			{
				if(isExcluded(entity))
					continue;
				// FAWE's adapter wraps a live entity in a lazy BaseEntity whose NBT is only
				// computed on first getNbt() call, via a version-specific NMS bridge that can
				// fail silently and yield null. Resolve it now, while the entity is live: an
				// entity we cannot serialize is skipped instead of crashing every paste.
				BaseEntity state = entity.getState();
				if(state == null || state.getNbt() == null)
				{
					skipped++;
					continue;
				}
				// ClipboardEntity requires the location's world to be the clipboard itself,
				// so re-anchor the entity's (WorldEdit-space) coordinates on the snapshot.
				var loc = entity.getLocation();
				((BlockArrayClipboard) snapshot).createEntity(
						new com.sk89q.worldedit.util.Location(snapshot, loc.x(), loc.y(), loc.z(), loc.getYaw(), loc.getPitch()),
						state);
			}
			if(skipped > 0)
			{
				final int skippedCount = skipped;
				final String templateName = template.getName();
				com.theprogrammingturkey.comz.COMZombies.log.log(java.util.logging.Level.WARNING,
						() -> "Instance snapshot: skipped " + skippedCount + " entity(ies) in " + templateName
								+ " that could not be serialized by WorldEdit/FAWE (e.g. unsupported on this server version).");
			}
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
			// Entity copying is safe here: the source is the clipboard (which FAWE never warns
			// about) and it already holds exactly the entities capture() snapshotted, so this
			// does not depend on which chunks are loaded when the instance is pasted.
			copy.setCopyingEntities(true);
			copy.setCopyingBiomes(true);
			Operations.complete(copy);
		}
		catch(Exception failure) { throw new IOException("Could not paste arena selection", failure); }
	}
}
