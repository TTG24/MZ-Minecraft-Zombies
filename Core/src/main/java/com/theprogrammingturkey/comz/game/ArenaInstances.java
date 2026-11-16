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

	// Pool slots past the first of each arena, prepared in the background a few ticks apart (see loadPools)
	private record PendingSlot(Game template, int slot, ArenaSelectionCopy snapshot) {}
	private final java.util.ArrayDeque<PendingSlot> pending = new java.util.ArrayDeque<>();
	// The arena snapshots the pending slots paste from; closed once every slot is done (or on shutdown)
	private final List<ArenaSelectionCopy> openSnapshots = new ArrayList<>();
	private int prepareTask = -1;

	private static <T> void copyGameRule(World source, World destination, org.bukkit.GameRule<T> rule)
	{
		if(rule == null) return;
		destination.setGameRule(rule, source.getGameRuleValue(rule));
	}

	/** config.gameSettings.instanceDifficulty, or the source world's difficulty for SOURCE (or anything unknown). */
	private static org.bukkit.Difficulty instanceDifficulty(World source)
	{
		String name = COMZombies.getPlugin().getConfig().getString("config.gameSettings.instanceDifficulty", "EASY");
		try
		{
			if(name != null && !name.equalsIgnoreCase("SOURCE"))
				return org.bukkit.Difficulty.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch(IllegalArgumentException e)
		{
			COMZombies.log.log(Level.WARNING, "Unknown instanceDifficulty {0}; using the arena world''s difficulty", new Object[]{name});
		}
		return source.getDifficulty();
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

	/** Tells everyone online (players notice a reload: games end, arenas are rebuilt). */
	private static void announce(String text)
	{
		Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&8[&4☠&8] &7" + text));
	}

	public void loadPools()
	{
		if(!Bukkit.getOnlinePlayers().isEmpty())
			announce("The arenas are being rebuilt... &cthe horde regroups.");
		stopPrepareTask();
		closeSnapshots();
		List<List<PendingSlot>> perArena = new ArrayList<>();
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
			openSnapshots.add(snapshot);
			List<PendingSlot> later = new ArrayList<>();
			for(int slotIndex = 1; slotIndex <= template.getInstanceCount(); slotIndex++)
			{
				final int slot = slotIndex;
				String name = template.getName() + "~" + slot;
				if(GameManager.INSTANCE.getGames().stream().anyMatch(g -> g.getName().equalsIgnoreCase(name)))
				{
					COMZombies.log.log(Level.WARNING, "Instance name conflicts with existing arena: {0}", new Object[]{name});
					continue;
				}
				// The first copy of each arena is ready right away, so every arena can be joined as soon as loading is done
				if(slot == 1)
				{
					if(createInstanceWorld(template, slot, snapshot) == null)
						COMZombies.log.log(Level.SEVERE, "Failed to prepare {0}; this slot is unavailable", new Object[]{name});
				}
				else
					later.add(new PendingSlot(template, slot, snapshot));
			}
			perArena.add(later);
		}
		// The other copies take turns between the arenas (dr~2, ndu~2, ..., dr~3, ...), one every few ticks, so the server
		// keeps running while they are made instead of freezing for all of them at once.
		while(perArena.stream().anyMatch(l -> !l.isEmpty()))
			for(List<PendingSlot> l : perArena)
				if(!l.isEmpty())
					pending.add(l.remove(0));
		if(pending.isEmpty())
		{
			closeSnapshots();
			if(!Bukkit.getOnlinePlayers().isEmpty())
				announce("All arenas are ready. &cThe dead are waiting.");
			return;
		}
		long interval = Math.max(1, COMZombies.getPlugin().getConfig().getLong("config.instances.prepareIntervalTicks", 5));
		COMZombies.log.log(Level.INFO, "Preparing {0} more arena copies in the background", new Object[]{pending.size()});
		if(!Bukkit.getOnlinePlayers().isEmpty())
			announce("Every arena can be joined again; more copies are being prepared in the background.");
		prepareTask = COMZombies.scheduleTask(interval, interval, this::prepareNext);
	}

	/** Makes the next background copy; stops (and frees the snapshots) once all are made. */
	private void prepareNext()
	{
		PendingSlot next = pending.poll();
		if(next != null && GameManager.INSTANCE.getGames().contains(next.template()))
		{
			String name = next.template().getName() + "~" + next.slot();
			if(GameManager.INSTANCE.getGames().stream().anyMatch(g -> g.getName().equalsIgnoreCase(name)))
				COMZombies.log.log(Level.WARNING, "Instance name conflicts with existing arena: {0}", new Object[]{name});
			else if(createInstanceWorld(next.template(), next.slot(), next.snapshot()) == null)
				COMZombies.log.log(Level.SEVERE, "Failed to prepare {0}; this slot is unavailable", new Object[]{name});
		}
		if(pending.isEmpty())
		{
			stopPrepareTask();
			closeSnapshots();
			COMZombies.log.log(Level.INFO, "All arena copies are ready");
			if(!Bukkit.getOnlinePlayers().isEmpty())
				announce("All arenas are ready. &cThe dead are waiting.");
		}
	}

	private void stopPrepareTask()
	{
		if(prepareTask != -1)
		{
			Bukkit.getScheduler().cancelTask(prepareTask);
			prepareTask = -1;
		}
		pending.clear();
	}

	private void closeSnapshots()
	{
		for(ArenaSelectionCopy snapshot : openSnapshots)
			snapshot.close();
		openSnapshots.clear();
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
		return createInstanceWorld(template, slot, null);
	}

	/** @param snapshot the arena copy to paste (shared by every pool slot), or null to take a fresh one */
	private Game createInstanceWorld(Game template, int slot, ArenaSelectionCopy snapshot)
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
			world.setDifficulty(instanceDifficulty(source));
			world.setTime(source.getTime());
			for(String rule : source.getGameRules())
				copyGameRule(source, world, com.theprogrammingturkey.comz.util.GameRuleCompat.getByName(rule));
			// The arena world may have fall damage off (e.g. for building); matches use config.gameSettings.playerFallDamage
			if(com.theprogrammingturkey.comz.util.ApiCompat.FALL_DAMAGE != null)
				world.setGameRule(com.theprogrammingturkey.comz.util.ApiCompat.FALL_DAMAGE,
						COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.playerFallDamage", true));
			if(worldGuard)
				WorldGuardInstances.copy(source, world, template.arena.getWorldGuardRegionId());
			if(snapshot != null)
				snapshot.paste(world);
			else
			{
				ArenaSelectionCopy capture = ArenaSelectionCopy.capture(template);
				try(capture) { capture.paste(world); }
			}
			copyBlockBlockerBarriers(source, world);
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

	/**
	 * BlockBlocker barriers are not blocks, so the arena paste does not carry them. If the BlockBlocker plugin is installed,
	 * ask it to copy the template world's barriers into the instance (same coordinates; temporary, removed when the instance
	 * world unloads). Called by reflection so MZ has no hard dependency on BlockBlocker, and a failure never breaks an instance.
	 */
	private static void copyBlockBlockerBarriers(World source, World instance)
	{
		try
		{
			org.bukkit.plugin.Plugin blockBlocker = Bukkit.getPluginManager().getPlugin("BlockBlocker");
			if(blockBlocker == null || !blockBlocker.isEnabled())
				return;
			blockBlocker.getClass().getMethod("copyBarriers", String.class, String.class).invoke(blockBlocker, source.getName(), instance.getName());
		}
		catch(Throwable t)
		{
			COMZombies.log.log(Level.WARNING, "Could not copy BlockBlocker barriers into " + instance.getName(), t);
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
		// Never tear down the world while someone is still spectating the post-match board;
		// otherwise the forced logout teleport kicks them to the main world mid-spectate.
		if(game.hasGameOverSpectators())
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
			// Keep the world alive while a player is still on the post-match leaderboard.
			if(game.hasGameOverSpectators())
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
		stopPrepareTask();
		closeSnapshots();
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
