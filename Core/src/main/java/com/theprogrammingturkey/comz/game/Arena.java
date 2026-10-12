package com.theprogrammingturkey.comz.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.config.CustomConfig;

/**
 * Arena contained in every game.
 */
public class Arena
{

	/**
	 * Arena name for the game.
	 */
	private String name;

	/**
	 * Location min, order in 3d world does not matter
	 */
	private Location min;

	/**
	 * Location max, order in 3d world does not matter
	 */
	private Location max;

	/**
	 * World in which min / max are contained, and the world the game is in.
	 */
	private World world;

	/**
	 * Location players will teleport to when the game starts.
	 */
	private Location playerTPLocation;

	/**
	 * Location players will teleport to when they leave or die.
	 */
	private Location spectateLocation;

	/**
	 * Location in which players will teleport upon first join.
	 */
	private Location lobbyLocation;

	/**
	 * Optional WorldGuard region ID. If set and WorldGuard is installed,
	 * containment checks use the region instead of the min/max box, allowing
	 * non-rectangular (e.g. polyhedron) arena boundaries on complex maps.
	 */
	private String worldGuardRegionId;

	/**
	 * Constructs a new arena for a given game.
	 */
	public Arena(String name)
	{
		this.name = name;
	}

	/**
	 * gets the name of the game
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Sets the name of the arena
	 *
	 * @param name Name of the arena to be set to
	 */
	public void setName(String name)
	{
		this.name = name;
	}

	/**
	 * Sets the min to loc.
	 *
	 * @param loc to be assigned to min
	 */
	public void setMin(Location loc)
	{
		min = loc;
	}

	/**
	 * Sets the max to loc.
	 *
	 * @param loc to be assigned to max
	 */
	public void setMax(Location loc)
	{
		max = loc;
	}

	/**
	 * Sets the field world to the given world
	 *
	 * @param world to be assigned to this.world
	 */
	public void setWorld(World world)
	{
		this.world = world;
	}

	/**
	 * Returns the WorldGuard region ID if one is set, otherwise null.
	 *
	 * @return the WorldGuard region ID
	 */
	public String getWorldGuardRegionId()
	{
		return worldGuardRegionId;
	}

	/**
	 * Sets the WorldGuard region ID used for containment checks.
	 *
	 * @param regionId the WorldGuard region ID (e.g. "my-arena-region"), or null to disable
	 */
	public void setWorldGuardRegionId(String regionId)
	{
		this.worldGuardRegionId = regionId;
	}

	/**
	 * Checks to see if a given location is contained within min / max. The
	 * order in which min / maax do not affect this method since they are
	 * compared and assigned to x / y / z vales correctly.
	 *
	 * If a WorldGuard region is configured and WorldGuard is installed,
	 * the region's containment is used instead of the min/max box, which
	 * supports arbitrary region shapes (polyhedron, cylinder, etc.).
	 *
	 * @param currentLoc to check if contained.
	 * @return true if currentLoc is in the arena, false if not
	 */
	public boolean containsBlock(Location currentLoc)
	{
		// Short circuit eval.
		if(currentLoc == null || currentLoc.getWorld() == null)
			return false;

		if(currentLoc.getWorld() != world)
			return false;

		// Prefer WorldGuard region if configured and available
		if(worldGuardRegionId != null && !worldGuardRegionId.isEmpty())
		{
			Boolean wgResult = checkWorldGuardRegion(currentLoc);
			if(wgResult != null)
				return wgResult;
			// WorldGuard not installed or region not found — fall through to box check
		}

		// Arenas that are still being set up may not have both corners yet
		if(min == null || max == null)
			return false;

		double x = currentLoc.getX();
		double y = currentLoc.getY();
		double z = currentLoc.getZ();

		if(x <= Math.max(max.getBlockX(), min.getBlockX()) && x >= Math.min(max.getBlockX(), min.getBlockX()))
			if(y <= Math.max(max.getBlockY(), min.getBlockY()) && y >= Math.min(max.getBlockY(), min.getBlockY()))
				return z <= Math.max(max.getBlockZ(), min.getBlockZ()) && z >= Math.min(max.getBlockZ(), min.getBlockZ());
		return false;
	}

	/**
	 * Attempts to check containment using a WorldGuard region by ID.
	 * Returns the result if WorldGuard is installed and the region exists,
	 * or null to signal the caller should fall back to the box check.
	 *
	 * @param loc the location to test
	 * @return true/false for inside/outside, or null if WorldGuard is unavailable
	 */
	private Boolean checkWorldGuardRegion(Location loc)
	{
		// Guard: only load WG classes if the plugin is actually present.
		// This also prevents NoClassDefFoundError on servers without WG.
		if(Bukkit.getPluginManager().getPlugin("WorldGuard") == null)
			return null;

		try
		{
			com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
			com.sk89q.worldguard.protection.regions.RegionContainer container = wg.getPlatform().getRegionContainer();
			com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(loc.getWorld()));

			if(rm == null)
				return null;

			com.sk89q.worldguard.protection.regions.ProtectedRegion region = rm.getRegion(worldGuardRegionId);
			if(region == null)
				return null;

			// ProtectedRegion.contains expects block coordinates, not a Location
			return region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
		}
		catch(Throwable t)
		{
			// WorldGuard not installed, missing transitive WorldEdit, or
			// API mismatch — fall back to the box check.
			return null;
		}
	}

	/**
	 * Get the world the arena is contained in.
	 *
	 * @return world
	 */
	public World getWorld()
	{
		return world;
	}

	/**
	 * Get the world name the arena is contained in.
	 *
	 * @return world name
	 */
	public String getWorldName()
	{
		return world.getName();
	}

	/**
	 * Is the max location set.
	 *
	 * @return max is not equal to null
	 */
	public boolean hasMax()
	{
		return max != null;
	}

	/**
	 * Get the max location.
	 *
	 * @return max
	 */
	public Location getMax()
	{
		return max;
	}

	/**
	 * Is the min location set.
	 *
	 * @return min is not equal to null
	 */
	public boolean hasMin()
	{
		return min != null;
	}

	/**
	 * Get the min location.
	 *
	 * @return min
	 */
	public Location getMin()
	{
		return min;
	}

	/**
	 * Is player teleport location in the arena not null
	 *
	 * @return playerTPLocation is not null
	 */
	public boolean hasPlayerTPLocation()
	{
		return playerTPLocation != null;
	}

	/**
	 * Get the location to teleport the player in the arena when the game starts.
	 *
	 * @return playerTPLocation
	 */
	public Location getPlayerTPLocation()
	{
		return playerTPLocation;
	}

	/**
	 * Set the location to teleport the player in the arena when the game starts.
	 */
	public void setPlayerTPLocation(Location loc)
	{
		this.playerTPLocation = loc;
	}

	/**
	 * Is the location to teleport players when spectating set.
	 *
	 * @return spectateLocation is not null
	 */
	public boolean hasSpectateLocation()
	{
		return spectateLocation != null;
	}

	/**
	 * Get the location to teleport players when spectating.
	 *
	 * @return spectateLocation
	 */
	public Location getSpectateLocation()
	{
		return spectateLocation;
	}

	/**
	 * Set the location to teleport players when spectating.
	 */
	public void setSpectateLocation(Location location)
	{
		this.spectateLocation = location;
	}

	/**
	 * Is the location of the lobby to teleport players set.
	 *
	 * @return spectateLocation
	 */
	public boolean hasLobbyLocation()
	{
		return lobbyLocation != null;
	}

	/**
	 * Get the location of the lobby to teleport players.
	 *
	 * @return spectateLocation
	 */
	public Location getLobbyLocation()
	{
		return lobbyLocation;
	}

	/**
	 * Get the location of the lobby to teleport players.
	 */
	public void setLobbyLocation(Location location)
	{
		this.lobbyLocation = location;
	}

	/**
	 * Get is both the min and max locations are not null
	 *
	 * @return If min and max are not null
	 */
	public boolean areMinAndMaxSet()
	{
		return this.hasMin() && this.hasMax();
	}

	/**
	 * Get if all 3 teleport locations are not null
	 *
	 * @return If playerTP, Lobby, and Spectate locations are not null
	 */
	public boolean areAllLocationsSet()
	{
		return this.hasPlayerTPLocation() && this.hasLobbyLocation() && this.hasSpectateLocation();
	}

	/**
	 * Get if all points and locations are set
	 *
	 * @return If min, max, playerTP, Lobby, and Spectate locations are not null
	 */
	public boolean isSetupComplete()
	{
		return this.areMinAndMaxSet() && this.areAllLocationsSet();
	}

	public void loadArena(JsonObject arenaSaveJson, World world)
	{
		this.setWorld(world);
		this.setMin(CustomConfig.getLocationWithWorld(arenaSaveJson, "p1", world));
		this.setMax(CustomConfig.getLocationWithWorld(arenaSaveJson, "p2", world));
		this.setPlayerTPLocation(CustomConfig.getLocationWithWorld(arenaSaveJson, "player_spawn", world));
		this.setSpectateLocation(CustomConfig.getLocationWithWorld(arenaSaveJson, "spectator_spawn", world));
		this.setLobbyLocation(CustomConfig.getLocationWithWorld(arenaSaveJson, "lobby_spawn", world));
		if(arenaSaveJson.has("worldguard_region"))
			this.setWorldGuardRegionId(CustomConfig.getString(arenaSaveJson, "worldguard_region", null));
	}

	public void saveArena(JsonObject arenaSaveJson)
	{
		arenaSaveJson.addProperty("world_name", getWorldName());
		arenaSaveJson.add("p1", CustomConfig.locationToJsonNoWorld(min));
		arenaSaveJson.add("p2", CustomConfig.locationToJsonNoWorld(max));
		arenaSaveJson.add("player_spawn", CustomConfig.locationToJsonNoWorld(playerTPLocation));
		arenaSaveJson.add("spectator_spawn", CustomConfig.locationToJsonNoWorld(spectateLocation));
		arenaSaveJson.add("lobby_spawn", CustomConfig.locationToJsonNoWorld(lobbyLocation));
		if(worldGuardRegionId != null && !worldGuardRegionId.isEmpty())
			arenaSaveJson.addProperty("worldguard_region", worldGuardRegionId);
	}
}
