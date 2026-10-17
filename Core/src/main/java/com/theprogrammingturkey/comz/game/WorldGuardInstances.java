package com.theprogrammingturkey.comz.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/** Loaded only when WorldGuard is enabled. Region objects must never be shared across worlds. */
public final class WorldGuardInstances
{
	private WorldGuardInstances() {}

	public static void copy(org.bukkit.World source, org.bukkit.World destination, String linkedRegion) throws IOException
	{
		var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
		RegionManager from = container.get(BukkitAdapter.adapt(source));
		RegionManager to = container.get(BukkitAdapter.adapt(destination));
		if(from == null || to == null)
			throw new IOException("WorldGuard region support is unavailable for " + source.getName() + " or " + destination.getName());
		if(linkedRegion != null && from.getRegion(linkedRegion) == null)
			throw new IOException("Missing template WorldGuard region: " + linkedRegion);
		Map<String, ProtectedRegion> copies = copyRegions(from.getRegions(), source.getName(), BukkitAdapter.adapt(destination));
		// Replace stale instance protections as well as adding the current template regions.
		to.setRegions(copies);
		try { to.save(); }
		catch(Exception failure) { throw new IOException("Could not save instance WorldGuard regions", failure); }
	}

	static Map<String, ProtectedRegion> copyRegions(Map<String, ProtectedRegion> originals, String sourceWorld, World target) throws IOException
	{
		Map<String, ProtectedRegion> copies = new LinkedHashMap<>();
		for(ProtectedRegion region : originals.values())
		{
			ProtectedRegion copy;
			if(region instanceof GlobalProtectedRegion)
				copy = new GlobalProtectedRegion(region.getId());
			else if(region instanceof ProtectedCuboidRegion)
				copy = new ProtectedCuboidRegion(region.getId(), region.getMinimumPoint(), region.getMaximumPoint());
			else if(region instanceof ProtectedPolygonalRegion)
				copy = new ProtectedPolygonalRegion(region.getId(), new ArrayList<>(region.getPoints()),
							region.getMinimumPoint().y(), region.getMaximumPoint().y());
			else
				throw new IOException("Unsupported WorldGuard region type: " + region.getClass().getSimpleName());
			copy.setPriority(region.getPriority());
			copy.setOwners(new DefaultDomain(region.getOwners()));
			copy.setMembers(new DefaultDomain(region.getMembers()));
			Map<com.sk89q.worldguard.protection.flags.Flag<?>, Object> flags = new HashMap<>();
			region.getFlags().forEach((flag, value) -> flags.put(flag, copyFlag(value, sourceWorld, target)));
			copy.setFlags(flags);
			copies.put(copy.getId(), copy);
		}
		for(ProtectedRegion region : originals.values())
		{
			ProtectedRegion originalParent = region.getParent();
			if(originalParent == null)
				continue;
			ProtectedRegion parent = copies.get(originalParent.getId());
			if(parent == null) throw new IOException("Missing WorldGuard parent: " + originalParent.getId());
			try { copies.get(region.getId()).setParent(parent); }
			catch(ProtectedRegion.CircularInheritanceException failure) { throw new IOException("Invalid WorldGuard parent hierarchy", failure); }
		}
		return copies;
	}

	private static Object copyFlag(Object value, String sourceWorld, World target)
	{
		if(value instanceof Location location)
		{
			// The public flag codec handles WorldGuard's package-private lazy world references,
			// including destinations in unloaded external worlds, without resolving their extent.
			Object encoded = Flags.TELE_LOC.marshal(location);
			if(!(encoded instanceof Map<?, ?> map)) throw new IllegalArgumentException("Cannot copy location flag");
			Map<Object, Object> data = new HashMap<>(map);
			if(sourceWorld.equals(data.get("world"))) data.put("world", target.getName());
			return Flags.TELE_LOC.unmarshal(data);
		}
		if(value instanceof Set<?> set)
		{
			Set<Object> copy = new LinkedHashSet<>();
			set.forEach(item -> copy.add(copyFlag(item, sourceWorld, target)));
			return copy;
		}
		if(value instanceof List<?> list)
			return new ArrayList<>(list.stream().map(item -> copyFlag(item, sourceWorld, target)).toList());
		if(value instanceof Map<?, ?> map)
		{
			Map<Object, Object> copy = new LinkedHashMap<>();
			map.forEach((key, item) -> copy.put(key, copyFlag(item, sourceWorld, target)));
			return copy;
		}
		return value;
	}
}
