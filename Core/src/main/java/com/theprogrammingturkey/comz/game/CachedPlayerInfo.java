package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A player's state from before they joined a game (location, gamemode, inventory...). It is kept in memory and also on
 * disk, so a player who quits, crashes or is caught by a server stop mid-game still gets their things back and is sent
 * home the next time they join.
 */
public class CachedPlayerInfo
{
	public static Map<UUID, CachedPlayerInfo> savedPlayerInfo = new HashMap<>();

	private static final java.util.Set<UUID> quitting = new java.util.HashSet<>();

	private Location oldLoc;
	private GameMode gameMode;
	private boolean flying;
	private int totalExp;
	private ItemStack[] invContents;
	private ItemStack[] armorContents;

	private static File fileFor(UUID id)
	{
		return new File(new File(COMZombies.getPlugin().getDataFolder(), "player-cache"), id + ".yml");
	}

	public static void savePlayerInfo(Player player)
	{
		CachedPlayerInfo info = new CachedPlayerInfo();
		info.oldLoc = player.getLocation().clone();
		info.gameMode = player.getGameMode();
		info.flying = player.isFlying();
		info.totalExp = player.getTotalExperience();
		// Real copies: getContents() hands back views of the live items, which the game kit could otherwise change
		info.invContents = copy(player.getInventory().getContents());
		info.armorContents = copy(player.getInventory().getArmorContents());
		//don't overwrite existing info
		if(!savedPlayerInfo.containsKey(player.getUniqueId()))
		{
			savedPlayerInfo.put(player.getUniqueId(), info);
			info.writeToDisk(player.getUniqueId());
		}
	}

	private static ItemStack[] copy(ItemStack[] stacks)
	{
		ItemStack[] out = new ItemStack[stacks.length];
		for(int i = 0; i < stacks.length; i++)
			out[i] = stacks[i] == null ? null : stacks[i].clone();
		return out;
	}

	private void writeToDisk(UUID id)
	{
		try
		{
			File file = fileFor(id);
			file.getParentFile().mkdirs();
			YamlConfiguration yaml = new YamlConfiguration();
			yaml.set("location", oldLoc);
			yaml.set("gamemode", gameMode.name());
			yaml.set("flying", flying);
			yaml.set("exp", totalExp);
			yaml.set("inventory", Arrays.asList(invContents));
			yaml.set("armor", Arrays.asList(armorContents));
			yaml.save(file);
		}
		catch(IOException | RuntimeException failure)
		{
			COMZombies.log.log(Level.WARNING, "Could not save the pre-game state of " + id, failure);
		}
	}

	private static CachedPlayerInfo readFromDisk(UUID id)
	{
		File file = fileFor(id);
		if(!file.isFile())
			return null;
		try
		{
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
			CachedPlayerInfo info = new CachedPlayerInfo();
			info.oldLoc = yaml.getLocation("location");
			info.gameMode = GameMode.valueOf(yaml.getString("gamemode", "SURVIVAL"));
			info.flying = yaml.getBoolean("flying");
			info.totalExp = yaml.getInt("exp");
			info.invContents = toStacks(yaml.getList("inventory"));
			info.armorContents = toStacks(yaml.getList("armor"));
			return info;
		}
		catch(RuntimeException failure)
		{
			COMZombies.log.log(Level.WARNING, "Could not read the saved pre-game state of " + id, failure);
			return null;
		}
	}

	private static ItemStack[] toStacks(List<?> list)
	{
		ItemStack[] stacks = new ItemStack[list == null ? 0 : list.size()];
		for(int i = 0; i < stacks.length; i++)
			stacks[i] = list.get(i) instanceof ItemStack stack ? stack : null;
		return stacks;
	}

	private static void deleteFile(UUID id)
	{
		File file = fileFor(id);
		if(file.isFile() && !file.delete())
			file.deleteOnExit();
	}

	/** Marks a player who is in the middle of quitting: their restore teleports at once (a scheduled one would never run). */
	public static void setQuitting(UUID id, boolean value)
	{
		if(value)
			quitting.add(id);
		else
			quitting.remove(id);
	}

	public static boolean isQuitting(UUID id)
	{
		return quitting.contains(id);
	}

	/** True when a pre-game state is waiting for this player (in memory or left on disk by a crash/stop). */
	public static boolean hasSavedInfo(UUID id)
	{
		return savedPlayerInfo.containsKey(id) || fileFor(id).isFile();
	}

	/** Puts the player back the way they were and sends them to where they came from (scheduled for the next tick). */
	public static void restorePlayerInfo(Player player)
	{
		restore(player, false);
	}

	/**
	 * Same, but teleports right now. Used when the player is quitting or the plugin is shutting down, where a scheduled
	 * task would never run.
	 */
	public static void restorePlayerInfoNow(Player player)
	{
		restore(player, true);
	}

	private static void restore(Player player, boolean immediate)
	{
		UUID id = player.getUniqueId();
		CachedPlayerInfo info = savedPlayerInfo.get(id);
		if(info == null)
			info = readFromDisk(id);
		if(info == null)
			return;
		savedPlayerInfo.remove(id);
		deleteFile(id);

		player.setGameMode(info.gameMode);
		if(player.getAllowFlight())
			player.setFlying(info.flying);
		player.setTotalExperience(info.totalExp);
		player.getInventory().setContents(info.invContents);
		player.getInventory().setArmorContents(info.armorContents);

		final Location target = safeLocation(info.oldLoc);
		if(immediate || quitting.contains(id) || !COMZombies.getPlugin().isEnabled())
			home(player, target);
		else
			COMZombies.scheduleTask(() -> home(player, target));
	}

	/** The game sending a player home: allowed through GameOverGuard. */
	private static void home(Player player, Location target)
	{
		com.theprogrammingturkey.comz.listeners.GameOverGuard.sendingHome = true;
		try
		{
			player.teleport(target);
		}
		finally
		{
			com.theprogrammingturkey.comz.listeners.GameOverGuard.sendingHome = false;
		}
	}

	/**
	 * The saved location, or the main world's spawn if it is missing or inside an arena instance. With
	 * config.gameSettings.leaveToSpawn (the default) it is always the spawn: players who joined through a portal would
	 * otherwise be put back inside it and join again.
	 */
	public static Location safeLocation(Location saved)
	{
		if(COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.leaveToSpawn", true))
			return Bukkit.getWorlds().getFirst().getSpawnLocation();
		if(saved != null && saved.getWorld() != null && !ArenaInstances.INSTANCE.isInstanceWorld(saved.getWorld()))
			return saved;
		return Bukkit.getWorlds().getFirst().getSpawnLocation();
	}
}
