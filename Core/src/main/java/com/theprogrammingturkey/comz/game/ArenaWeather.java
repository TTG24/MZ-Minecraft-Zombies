package com.theprogrammingturkey.comz.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Keeps arena weather correct for players in a game. WorldGuard's weather-lock flag is only applied when a player
 * crosses a region boundary, and an instance world copies the template's regions under the same ids, so a player
 * teleported into an instance can keep the previous world's weather (the arena snow "sometimes" not playing).
 * This re-applies the region's weather-lock to every player in a game and hands weather back when they leave.
 */
public final class ArenaWeather
{
	private static final Set<UUID> applied = new HashSet<>();
	/** The world each player's weather was last sent in: moving to another world clears it on their screen. */
	private static final Map<UUID, UUID> appliedWorld = new HashMap<>();

	private ArenaWeather() {}

	public static void start(Plugin plugin)
	{
		if(!Bukkit.getPluginManager().isPluginEnabled("WorldGuard"))
			return;
		Bukkit.getScheduler().runTaskTimer(plugin, ArenaWeather::tick, 40L, 20L);
	}

	private static void tick()
	{
		for(Player player : Bukkit.getOnlinePlayers())
		{
			boolean inGame = GameManager.INSTANCE.isPlayerInGame(player);
			if(inGame)
			{
				Boolean lock;
				try
				{
					lock = WorldGuardInstances.weatherLock(player);
					// Standing where no region sets weather (the lobby spot, an edge of the map): the arena's own region
					// decides it, so the whole game has the arena's weather from the moment it starts.
					if(lock == null)
					{
						Game game = GameManager.INSTANCE.getGame(player);
						if(game != null)
							lock = WorldGuardInstances.regionWeatherLock(player.getWorld(), game.arena.getWorldGuardRegionId());
					}
				}
				catch(Exception | LinkageError failure) { continue; }
				if(lock == null)
					continue;
				WeatherType wanted = lock ? WeatherType.DOWNFALL : WeatherType.CLEAR;
				UUID world = player.getWorld().getUID();
				boolean newWorld = !world.equals(appliedWorld.get(player.getUniqueId()));
				// Joining a game moves the player to the arena copy's world, and a world change clears the weather on their
				// screen while the server still thinks it is set, so it was never sent again. In a new world it is always
				// cleared and sent afresh.
				if(newWorld && player.getPlayerWeather() == wanted)
					player.resetPlayerWeather();
				if(player.getPlayerWeather() != wanted)
					player.setPlayerWeather(wanted);
				appliedWorld.put(player.getUniqueId(), world);
				applied.add(player.getUniqueId());
			}
			else if(applied.remove(player.getUniqueId()))
			{
				// Left the game: let WorldGuard / the server decide the weather again.
				player.resetPlayerWeather();
				appliedWorld.remove(player.getUniqueId());
			}
		}
		applied.removeIf(id -> Bukkit.getPlayer(id) == null);
		appliedWorld.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
	}
}
