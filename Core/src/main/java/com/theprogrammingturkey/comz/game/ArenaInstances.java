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

	private static <T> void copyGameRule(World source, World destination, org.bukkit.GameRule<T> rule)
	{
		if(rule == null) return;
		destination.setGameRule(rule, source.getGameRuleValue(rule));
	}

	public boolean hasQueue(String template)
	{
		return queues.containsKey(template) && !queues.get(template).isEmpty();
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
			boolean worldGuard = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
			if(template.arena.getWorldGuardRegionId() != null && !worldGuard)
			{
				COMZombies.log.log(Level.WARNING, "WorldGuard must be enabled to prepare linked arena {0}", new Object[]{template.getName()});
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
			World source = template.getWorld();
			ArenaSelectionCopy snapshot;
			try { snapshot = ArenaSelectionCopy.capture(template); }
			catch(Exception | LinkageError failure)
			{
				COMZombies.log.log(Level.SEVERE, "Could not capture template " + template.getName(), failure);
				continue;
			}
			try(snapshot)
			{
			for(int slot = 1; slot <= template.getInstanceCount(); slot++)
			{
				String name = template.getName() + "~" + slot;
				if(GameManager.INSTANCE.getGames().stream().anyMatch(g -> g.getName().equalsIgnoreCase(name)))
				{
					COMZombies.log.log(Level.WARNING, "Instance name conflicts with existing arena: {0}", new Object[]{name});
					continue;
				}
				String id = UUID.nameUUIDFromBytes(template.getName().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)).toString();
				String worldName = "mz_instance_" + id + "_" + slot;
				try
				{
					if(Bukkit.getWorld(worldName) != null)
						throw new IOException("World is already loaded: " + worldName);
					Path container = Bukkit.getWorldContainer().toPath().toRealPath();
					Path destination = InstanceWorldFiles.destination(container, Bukkit.getWorlds().getFirst().getWorldFolder().toPath(), worldName);
					InstanceWorldFiles.migrateMarker(container.resolve(worldName), destination, id);
					InstanceWorldFiles.prepareEmpty(source.getWorldFolder().toPath(), destination, id);
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
					{
						copyGameRule(source, world, com.theprogrammingturkey.comz.util.GameRuleCompat.getByName(rule));
					}
					if(worldGuard)
						WorldGuardInstances.copy(source, world, template.arena.getWorldGuardRegionId());
					snapshot.paste(world);
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
				}
				catch(Exception | LinkageError failure)
				{
					COMZombies.log.log(Level.SEVERE, "Failed to prepare " + name + "; this slot is unavailable", failure);
				}
			}
			}
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
