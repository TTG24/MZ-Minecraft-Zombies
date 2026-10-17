package com.theprogrammingturkey.comz.game;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.signs.SignRegistry;

/** Fixed, reusable match worlds. The source arena stays available for editing only. */
public final class ArenaInstances
{
	public static final ArenaInstances INSTANCE = new ArenaInstances();
	private final Map<String, World> worlds = new LinkedHashMap<>();
	private final Map<String, LinkedHashSet<UUID>> queues = new LinkedHashMap<>();
	private int queueTask = -1;
	private int soloWatchdog = -1;

	private static <T> void copyGameRule(World source, World destination, org.bukkit.GameRule<T> rule)
	{
		if(rule == null) return;
		destination.setGameRule(rule, source.getGameRuleValue(rule));
	}

	public boolean hasQueue(String template)
	{
		return queues.containsKey(template) && !queues.get(template).isEmpty();
	}

	public boolean isInstanceWorld(World world)
	{
		return world != null && worlds.containsKey(world.getName());
	}

	public void enqueue(Game template, org.bukkit.entity.Player player)
	{
		if(GameManager.INSTANCE.getGames().stream().noneMatch(g -> g.isInstance() && g.getTemplateName().equals(template.getName())))
		{
			player.sendMessage(COMZombies.PREFIX + "No copies are available. Ask an administrator to check the instance setup.");
			return;
		}
		cancelQueue(player.getUniqueId());
		LinkedHashSet<UUID> queue = queues.computeIfAbsent(template.getName(), key -> new LinkedHashSet<>());
		queue.add(player.getUniqueId());
		player.sendMessage(COMZombies.PREFIX + "Queued for " + template.getName() + " at position " + queue.size() + ". Use /zombies leave to cancel.");
		if(queueTask == -1)
			queueTask = COMZombies.scheduleTask(20, 20, this::drainQueues);
	}

	public boolean cancelQueue(UUID player)
	{
		boolean removed = false;
		for(Set<UUID> queue : queues.values()) removed |= queue.remove(player);
		return removed;
	}

	private void drainQueues()
	{
		for(Map.Entry<String, LinkedHashSet<UUID>> entry : queues.entrySet())
			for(Iterator<UUID> it = entry.getValue().iterator(); it.hasNext();)
			{
				org.bukkit.entity.Player player = Bukkit.getPlayer(it.next());
				if(player == null || GameManager.INSTANCE.isPlayerInGame(player)) { it.remove(); continue; }
				if(!com.theprogrammingturkey.comz.util.COMZPermission.JOIN_ARENA.hasPerm(player, entry.getKey()))
				{
					it.remove();
					continue;
				}
				Game match = GameManager.INSTANCE.findJoinable(entry.getKey());
				if(match == null) break;
				it.remove();
				match.addPlayer(player);
				player.sendMessage(COMZombies.PREFIX + "You joined " + match.getName() + "!");
			}
		// Drop templates whose queue has fully drained and stop the recurring task
		// when no players remain queued, so it does not keep ticking every 20 ticks.
		queues.values().removeIf(Set::isEmpty);
		if(queues.isEmpty()) stopQueueTask();
	}

	private void stopQueueTask()
	{
		if(queueTask != -1)
		{
			Bukkit.getScheduler().cancelTask(queueTask);
			queueTask = -1;
		}
	}

	public void loadPools()
	{
		for(Game template : new ArrayList<>(GameManager.INSTANCE.getGames()))
		{
			if(!template.isInstance() && template.getInstanceCount() == 0)
				removeDisabledPool(template);
			if(!template.isTemplate())
				continue;
			if(!template.arena.isSetupComplete() || template.spawnManager.getPoints().isEmpty())
			{
				COMZombies.log.log(Level.WARNING, "Cannot create instances for incomplete arena {0}", new Object[]{template.getName()});
				continue;
			}
			boolean fawe = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
			boolean worldEdit = Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
			if(!fawe && !worldEdit)
			{
				COMZombies.log.log(Level.SEVERE, "Arena instances require FastAsyncWorldEdit (recommended) or WorldEdit: {0}", new Object[]{template.getName()});
				continue;
			}
			if(!fawe)
			{
				COMZombies.log.log(Level.WARNING, "FastAsyncWorldEdit is recommended for {0}: arena copies will use WorldEdit and run synchronously on the main thread.", new Object[]{template.getName()});
			}
			ArenaSelectionCopy snapshot;
			try { snapshot = ArenaSelectionCopy.capture(template); }
			catch(Exception | LinkageError failure)
			{
				COMZombies.log.log(Level.SEVERE, "Could not capture template " + template.getName(), failure);
				continue;
			}
			try(snapshot)
			{
				for(int slotIndex = 1; slotIndex <= template.getInstanceCount(); slotIndex++)
				{
					final int slot = slotIndex;
					String name = template.getName() + "~" + slot;
					if(GameManager.INSTANCE.getGames().stream().anyMatch(g -> g.getName().equalsIgnoreCase(name)))
					{
						COMZombies.log.log(Level.WARNING, "Instance name conflicts with existing arena: {0}", new Object[]{name});
						continue;
					}
					if(createInstanceWorld(template, slot) == null)
						COMZombies.log.log(Level.SEVERE, "Failed to prepare {0}; this slot is unavailable", new Object[]{name});
				}
			}
		}
	}

	/**
	 * Copies the template's box into a fresh void world and registers the resulting match.
	 *
	 * @param template the source arena
	 * @param slot     the instance number (1-based; the name becomes "{template}~{slot}")
	 * @return the created game, or null on failure
	 */
	private Game createInstanceWorld(Game template, int slot)
	{
		String name = template.getName() + "~" + slot;
		String worldName = "mz_instance_" + ownerId(template.getName()) + "_" + slot;
		try
		{
			if(Bukkit.getWorld(worldName) != null)
				throw new IOException("World is already loaded: " + worldName);
			World source = template.getWorld();
			boolean worldGuard = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
			if(!worldGuard && template.arena.getWorldGuardRegionId() != null)
				throw new IOException("This arena is linked to a WorldGuard region; WorldGuard must be enabled");
			Path container = Bukkit.getWorldContainer().toPath().toRealPath();
			Path destination = InstanceWorldFiles.destination(container, Bukkit.getWorlds().getFirst().getWorldFolder().toPath(), worldName);
			InstanceWorldFiles.migrateMarker(container.resolve(worldName), destination, ownerId(template.getName()));
			InstanceWorldFiles.prepareEmpty(source.getWorldFolder().toPath(), destination, ownerId(template.getName()));
			World world = new WorldCreator(worldName).environment(source.getEnvironment())
					.seed(source.getSeed()).generator(new InstanceVoidGenerator()).generateStructures(false)
					.createWorld();
			if(world == null)
				throw new IOException("Could not load " + worldName);
			world.setAutoSave(false);
			worlds.put(worldName, world);
			if(!world.getWorldFolder().toPath().toRealPath().equals(destination.toRealPath()))
				throw new IOException("Server loaded " + world.getWorldFolder() + " instead of copied instance directory " + destination);
			world.setDifficulty(source.getDifficulty());
			world.setTime(source.getTime());
			for(String rule : source.getGameRules())
				copyGameRule(source, world, com.theprogrammingturkey.comz.util.GameRuleCompat.getByName(rule));
			if(worldGuard)
				WorldGuardInstances.copy(source, world, template.arena.getWorldGuardRegionId());
			ArenaSelectionCopy capture = ArenaSelectionCopy.capture(template);
			try(capture) { capture.paste(world); }
			JsonObject data = template.saveGame().deepCopy();
			data.getAsJsonObject("save_data").addProperty("world_name", worldName);
			data.getAsJsonObject("settings").addProperty("instance_count", 0);
			Game match = new Game(name);
			match.setTemplateName(template.getName());
			if(!match.loadGame(data))
				throw new IOException("Could not load instance configuration " + name);
			GameManager.INSTANCE.addGame(match);
			SignRegistry.INSTANCE.copyToInstance(template, match);
			COMZombies.log.log(Level.INFO, "Prepared {0} in {1}", new Object[]{name, worldName});
			return match;
		}
		catch(Exception | LinkageError failure)
		{
			// Never leak a half-built world or a game whose world failed to load.
			World leaked = Bukkit.getWorld(worldName);
			if(leaked != null)
			{
				for(Player p : new ArrayList<>(leaked.getPlayers()))
					p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
				Bukkit.unloadWorld(leaked, false);
			}
			worlds.remove(worldName);
			InstanceWorldFiles.deleteOwnedQuietly(worldName);
			COMZombies.log.log(Level.SEVERE, "Failed to prepare " + name, failure);
			return null;
		}
	}

	private static String ownerId(String arenaName)
	{
		return UUID.nameUUIDFromBytes(arenaName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)).toString();
	}

	/**
	 * Spins up a private, single-player copy of an arena on demand.
	 *
	 * @param requester the player asking for a solo match
	 * @param template  the arena to copy
	 * @return the created solo match, or null on failure (the player has been told why)
	 */
	public Game createSolo(Player requester, Game template)
	{
		if(!COMZombies.getPlugin().getConfig().getBoolean("config.instances.soloInstances", true))
		{
			requester.sendMessage(COMZombies.PREFIX + "Solo arenas are disabled on this server.");
			return null;
		}
		if(template == null || template.isInstance() || !template.arena.isSetupComplete() || template.spawnManager.getPoints().isEmpty())
		{
			requester.sendMessage(COMZombies.PREFIX + "That arena is not set up to run a solo match.");
			return null;
		}
		if(GameManager.INSTANCE.isPlayerInGame(requester))
		{
			requester.sendMessage(COMZombies.PREFIX + "Leave your current game before starting a solo arena.");
			return null;
		}
		if(!com.theprogrammingturkey.comz.util.COMZPermission.SOLO_ARENA.hasPerm(requester, template.getName()))
		{
			com.theprogrammingturkey.comz.util.CommandUtil.noPermission(requester, "start a solo arena");
			return null;
		}
		if(!(Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit") || Bukkit.getPluginManager().isPluginEnabled("WorldEdit")))
		{
			requester.sendMessage(COMZombies.PREFIX + "Arena copies need FastAsyncWorldEdit or WorldEdit installed.");
			return null;
		}
		// Solo slots live in 1000-9999 so their world folders can never collide with the
		// persistent pool slots (1-16); retry a handful of times if one happens to be loaded.
		java.util.Random random = new java.util.Random();
		int slot = 1000 + random.nextInt(9000);
		for(int i = 0; i < 10 && Bukkit.getWorld("mz_instance_" + ownerId(template.getName()) + "_" + slot) != null; i++)
			slot = 1000 + random.nextInt(9000);
		Game created = createInstanceWorld(template, slot);
		if(created == null)
		{
			requester.sendMessage(COMZombies.PREFIX + "Could not create your solo arena; check the console for details.");
			return null;
		}
		created.setSoloInstance(true);
		// A solo copy always starts instantly for its single player.
		created.arena.setLobbyLocation(null);
		created.minPlayers = 1;
		created.maxPlayers = 1;
		if(soloWatchdog == -1)
			soloWatchdog = COMZombies.scheduleTask(200, 200, this::soloWatchdog);
		return created;
	}

	/** Called from {@link Game#removePlayer}; tears the instance down when the last person is gone. */
	public void onSoloPlayerRemoved(Game game, Player player)
	{
		if(!game.isSoloInstance() || game.getStatus() == Game.GameStatus.DISABLED)
			return;
		// A player who /zombies leaves (rather than quitting) is still online and inside the
		// instance world, so move them back to the default world; only then can the world empty.
		World world = game.getWorld();
		if(player.isOnline() && world != null && player.getWorld() == world)
			player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
		if(world != null && world.getPlayers().isEmpty())
			scheduleDisposeSolo(game);
	}

	/** Called from {@link Game#endGame}; the match is over, so dispose once the world is empty. */
	public void onSoloMatchEnded(Game game)
	{
		if(!game.isSoloInstance())
			return;
		scheduleDisposeSolo(game);
	}

	/**
	 * Called from {@link Game#playerDowned} for the solo player.
	 *
	 * <p>On death the player is sent to the spectate area but their body (or spectating form)
	 * stays inside the instance world, so the world never empties on its own. The match itself
	 * keeps running so they can rejoin on the next wave; if they simply quit or leave shortly
	 * after, {@link #onSoloPlayerRemoved} cleans the instance up. As a fallback, if they are
	 * still dead a short while later and the match is no longer in progress, dispose the instance.
	 */
	public void onSoloPlayerDowned(Game game, Player player)
	{
		COMZombies.scheduleTask(600, () ->
		{
			World world = game.getWorld();
			if(world == null || !worlds.containsKey(world.getName()) || !game.isSoloInstance())
				return;
			boolean stillDead = game.isPlayerDeath(player);
			if((stillDead && game.getStatus() != Game.GameStatus.INGAME)
					|| !game.isPlayerPlaying(player)
					|| world.getPlayers().isEmpty())
				disposeSolo(game);
		});
	}

	/**
	 * Disposes a solo instance after a short delay, retrying until the world is actually empty.
	 * This handles the async teleport in {@link #onSoloPlayerRemoved} (the player may not have
	 * left the world on the first pass) and any other trigger that empties the world gradually.
	 */
	private void scheduleDisposeSolo(Game game)
	{
		scheduleDisposeSolo(game, 0);
	}

	private void scheduleDisposeSolo(Game game, int attempt)
	{
		World world = game.getWorld();
		if(world == null || !worlds.containsKey(world.getName()))
			return;
		COMZombies.scheduleTask(10, () ->
		{
			if(!worlds.containsKey(world.getName()) || world.getPlayers().isEmpty())
			{
				disposeSolo(game);
			}
			else if(attempt < 20)
			{
				// The player may still be mid-teleport; retry a few times before giving up.
				scheduleDisposeSolo(game, attempt + 1);
			}
		});
	}

	private void disposeSolo(Game game)
	{
		World world = Bukkit.getWorld(game.getWorld().getName());
		if(world == null)
		{
			worlds.remove(game.getWorld().getName());
			GameManager.INSTANCE.getGames().remove(game);
			return;
		}
		for(Player player : new ArrayList<>(world.getPlayers()))
			player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
		SignRegistry.INSTANCE.removeInstanceWorld(world);
		if(!Bukkit.unloadWorld(world, false))
		{
			COMZombies.log.log(Level.SEVERE, "Could not unload solo instance world {0}", new Object[]{world.getName()});
			return;
		}
		worlds.remove(world.getName());
		InstanceWorldFiles.deleteOwnedQuietly(world.getName());
		GameManager.INSTANCE.getGames().remove(game);
		COMZombies.log.log(Level.INFO, "Removed solo arena {0}", new Object[]{game.getName()});
	}

	/**
	 * Periodic safety net: disposes a solo instance whose world is loaded and empty but was
	 * never cleaned up by an event hook (e.g. a missed leave or a crash mid-match). Only runs
	 * when the match is no longer actively in progress, so a respawn window can't dispose it.
	 */
	private void soloWatchdog()
	{
		for(Game game : new ArrayList<>(GameManager.INSTANCE.getGames()))
		{
			if(!game.isSoloInstance())
				continue;
			World world = game.getWorld();
			if(world == null || !worlds.containsKey(world.getName()))
				continue;
			if(game.getStatus() != Game.GameStatus.INGAME && world.getPlayers().isEmpty())
				disposeSolo(game);
		}
	}

	/** Applied after reload has ended matches and unloaded their worlds. */
	private void removeDisabledPool(Game template)
	{
		String owner = UUID.nameUUIDFromBytes(template.getName().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)).toString();
		try
		{
			Path container = Bukkit.getWorldContainer().toPath().toRealPath();
			for(int slot = 1; slot <= 16; slot++)
			{
				String name = "mz_instance_" + owner + "_" + slot;
				if(Bukkit.getWorld(name) != null)
				{
					COMZombies.log.log(Level.WARNING, "Cannot remove loaded instance {0}; retry after restart.", new Object[]{name});
					continue;
				}
				Path destination = InstanceWorldFiles.destination(container, Bukkit.getWorlds().getFirst().getWorldFolder().toPath(), name);
				Path legacy = container.resolve(name);
				try
				{
					InstanceWorldFiles.migrateMarker(legacy, destination, owner);
					for(Path path : new LinkedHashSet<>(List.of(destination, legacy)))
						if(Files.exists(path, LinkOption.NOFOLLOW_LINKS))
						{
							InstanceWorldFiles.deleteOwned(template.getWorld().getWorldFolder().toPath(), path, owner);
							COMZombies.log.log(Level.INFO, "Removed disabled arena instance {0}", new Object[]{path});
						}
				}
				catch(IOException failure) { COMZombies.log.log(Level.WARNING, "Could not remove instance " + name, failure); }
			}
		}
		catch(IOException failure) { COMZombies.log.log(Level.WARNING, "Could not clean up disabled pool " + template.getName(), failure); }
	}

	/** Called after matches end, before replacing game objects or disabling the plugin. */
	public void shutdown()
	{
		stopQueueTask();
		for(Set<UUID> queue : queues.values())
			for(UUID id : queue)
			{
				org.bukkit.entity.Player player = Bukkit.getPlayer(id);
				if(player != null) player.sendMessage(COMZombies.PREFIX + "Your arena queue was cleared during shutdown/reload.");
			}
		queues.clear();
		for(Iterator<Map.Entry<String, World>> it = worlds.entrySet().iterator(); it.hasNext();)
		{
			World world = it.next().getValue();
			for(org.bukkit.entity.Player player : world.getPlayers())
				player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
			SignRegistry.INSTANCE.removeInstanceWorld(world);
			if(Bukkit.unloadWorld(world, false))
				it.remove();
			else
				COMZombies.log.log(Level.SEVERE, "Could not unload instance world {0}; it will not be overwritten.", new Object[]{world.getName()});
		}
	}
}
