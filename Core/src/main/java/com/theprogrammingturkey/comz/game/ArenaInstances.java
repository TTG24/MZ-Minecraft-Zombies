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

	// One snapshot per arena, kept while the plugin runs: every copy (made at startup or later, on demand) pastes from it
	private final Map<String, ArenaSelectionCopy> snapshots = new LinkedHashMap<>();
	// Snapshots of arenas without a pool, taken the first time someone starts a solo match there
	private final Map<String, ArenaSelectionCopy> soloSnapshots = new LinkedHashMap<>();
	// Players whose solo arena is being made (one at a time each)
	private final Set<UUID> soloPending = new java.util.HashSet<>();
	/** A copy being made, a part per tick (see buildTick). */
	private static final class BuildJob
	{
		final Game template;
		final int slot;
		final ArenaSelectionCopy snapshot;
		World world;
		List<com.sk89q.worldedit.regions.CuboidRegion> slices;
		int next;
		// Called with the finished match (null if it failed); used by solo arenas
		java.util.function.Consumer<Game> onDone;

		BuildJob(Game template, int slot, ArenaSelectionCopy snapshot)
		{
			this.template = template;
			this.slot = slot;
			this.snapshot = snapshot;
		}
	}
	private final java.util.ArrayDeque<BuildJob> jobs = new java.util.ArrayDeque<>();
	private BuildJob active;
	private int gapTicks;
	private int poolTask = -1, buildTask = -1;
	private boolean startupDone;
	// When each free copy became free (extra copies are removed after a while)
	private final Map<Game, Long> idleSince = new java.util.HashMap<>();

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
		if(!snapshots.containsKey(template.getName())
				&& GameManager.INSTANCE.getGames().stream().noneMatch(g -> g.isInstance() && g.getTemplateName().equals(template.getName())))
		{
			player.sendMessage(COMZombies.PREFIX + "No copies are available. Ask an administrator to check the instance setup.");
			return;
		}
		cancelQueue(player.getUniqueId());
		LinkedHashSet<UUID> queue = queues.computeIfAbsent(template.getName(), key -> new LinkedHashSet<>());
		queue.add(player.getUniqueId());
		// A new copy only comes when one can be made: otherwise they wait for a match to end, and are told so
		int copies = poolCopies(template).size();
		// (a solo copy being made is someone else's)
		boolean building = active != null && active.template == template && active.onDone == null
				|| jobs.stream().anyMatch(j -> j.template == template && j.onDone == null);
		boolean canMake = snapshots.containsKey(template.getName()) && copies < Math.max(1, template.getInstanceCount());
		if(building || canMake)
			tell(player, "Creating arena... &cyou'll be thrown in as soon as it's ready. &8(/zombies leave to cancel)");
		else
			tell(player, "Every " + template.getName() + " arena is full right now. &cYou're next in line &7when a match ends. &8(/zombies leave to cancel)");
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

	/**
	 * Tells the people a reload concerns: admins who can reload, players in or queued for a match, and players standing in an
	 * arena. Everyone else on the server is left alone (this used to go to the whole server).
	 */
	private void announce(String text)
	{
		String line = org.bukkit.ChatColor.translateAlternateColorCodes('&', "&8[&4☠&8] &7" + text);
		java.util.Set<UUID> queued = new java.util.HashSet<>();
		for(Set<UUID> q : queues.values())
			queued.addAll(q);
		for(Player p : Bukkit.getOnlinePlayers())
			if(com.theprogrammingturkey.comz.util.COMZPermission.RELOAD.hasPerm(p) || queued.contains(p.getUniqueId())
					|| GameManager.INSTANCE.isPlayerInGame(p) || isInstanceWorld(p.getWorld()) || GameManager.INSTANCE.isLocationInGame(p.getLocation()))
				p.sendMessage(line);
	}

	private static void tell(Player player, String text)
	{
		player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&8[&4☠&8] &7" + text));
	}

	/** How many copies of each arena are kept (at most the arena's instance count). */
	private static int readyCopies()
	{
		return Math.max(1, COMZombies.getPlugin().getConfig().getInt("config.instances.readyCopies", 4));
	}

	/** How many empty copies of each arena are kept ready for the next players (built in the background when used). */
	private static int spareCopies()
	{
		return Math.max(0, COMZombies.getPlugin().getConfig().getInt("config.instances.spareCopies", 1));
	}

	/**
	 * Builds the pool: takes one snapshot per arena (kept for as long as the plugin runs, for copies made later) and lets
	 * the pool keeper make the copies, each one pasted slowly over a few seconds (see {@link #buildTick}).
	 */
	public void loadPools()
	{
		if(!Bukkit.getOnlinePlayers().isEmpty())
			announce("The arenas are being rebuilt... &cthe horde regroups.");
		stopBuilding();
		closeSnapshots();
		for(Game template : new ArrayList<>(GameManager.INSTANCE.getGames()))
		{
			if(!template.isInstance() && template.getInstanceCount() == 0)
				removeDisabledPool(template);
			if(!template.isTemplate() || template.getInstanceCount() == 0)
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
			try { snapshots.put(template.getName(), ArenaSelectionCopy.capture(template)); }
			catch(Exception | LinkageError failure)
			{
				COMZombies.log.log(Level.SEVERE, "Could not capture template " + template.getName(), failure);
			}
		}
		startupDone = false;
		poolTask = COMZombies.scheduleTask(1, 20, this::keepPools);
		buildTask = COMZombies.scheduleTask(1, 1, this::buildTick);
	}

	/** Every second: each arena gets its ready copies, plus a new one whenever its spare copies are in use. */
	private void keepPools()
	{
		long now = System.currentTimeMillis();
		long removeAfter = Math.max(30, COMZombies.getPlugin().getConfig().getLong("config.instances.removeExtraAfterSeconds", 180)) * 1000L;
		for(Game template : new ArrayList<>(GameManager.INSTANCE.getGames()))
		{
			ArenaSelectionCopy snapshot = snapshots.get(template.getName());
			if(snapshot == null || !template.isTemplate() || !GameManager.INSTANCE.getGames().contains(template))
				continue;
			int cap = Math.max(1, template.getInstanceCount());
			int base = Math.min(readyCopies(), cap);
			List<Game> copies = poolCopies(template);
			int building = (int) jobs.stream().filter(j -> j.template == template).count() + (active != null && active.template == template ? 1 : 0);
			List<Game> free = copies.stream().filter(ArenaInstances::isFree).toList();
			int total = copies.size() + building;
			if(total < base)
				queueBuild(template, snapshot, false);
			else if(free.size() + building < spareCopies() && total < cap)
			{
				queueBuild(template, snapshot, true);
				// Players waiting in the copy that just filled up (and anyone queued) are told what is happening
				for(Game g : copies)
					if(g.getStatus() == Game.GameStatus.WAITING || g.getStatus() == Game.GameStatus.STARTING)
						for(Player p : g.getPlayersInGame())
							tell(p, "Creating arena... &cdigging a fresh grave for the next survivors.");
			}
			// Extra copies (past the ready ones) left empty for a while are taken down again
			for(Game g : copies)
			{
				if(!isFree(g))
				{
					idleSince.remove(g);
					continue;
				}
				idleSince.putIfAbsent(g, now);
			}
			if(copies.size() > base && free.size() > spareCopies())
			{
				Game extra = free.stream().max(java.util.Comparator.comparingInt(ArenaInstances::slotOf)).orElse(null);
				if(extra != null && now - idleSince.getOrDefault(extra, now) >= removeAfter)
				{
					idleSince.remove(extra);
					disposeInstance(extra, "extra arena copy");
				}
			}
		}
		idleSince.keySet().removeIf(g -> !GameManager.INSTANCE.getGames().contains(g));
	}

	private static boolean isFree(Game g)
	{
		return g.getStatus() == Game.GameStatus.WAITING && g.getPlayersInGame().isEmpty() && !g.hasGameOverSpectators();
	}

	/** The template's pool copies (not solo ones). */
	private static List<Game> poolCopies(Game template)
	{
		List<Game> out = new ArrayList<>();
		for(Game g : GameManager.INSTANCE.getGames())
			if(g.isInstance() && !g.isSoloInstance() && g.getTemplateName().equals(template.getName()))
				out.add(g);
		return out;
	}

	private static int slotOf(Game g)
	{
		int i = g.getName().lastIndexOf('~');
		try { return i < 0 ? 0 : Integer.parseInt(g.getName().substring(i + 1)); }
		catch(NumberFormatException e) { return 0; }
	}

	private void queueBuild(Game template, ArenaSelectionCopy snapshot, boolean demanded)
	{
		int cap = Math.max(1, template.getInstanceCount());
		Set<Integer> used = new java.util.HashSet<>();
		for(Game g : poolCopies(template))
			used.add(slotOf(g));
		for(BuildJob j : jobs)
			if(j.template == template)
				used.add(j.slot);
		if(active != null && active.template == template)
			used.add(active.slot);
		for(int slot = 1; slot <= cap; slot++)
			if(!used.contains(slot))
			{
				BuildJob job = new BuildJob(template, slot, snapshot);
				// A copy players are waiting for goes before the ones made at startup
				if(demanded)
					jobs.addFirst(job);
				else
					jobs.addLast(job);
				return;
			}
	}

	/**
	 * One copy at a time, a small part each tick: first the empty world, then one slab of the arena per tick (about
	 * {@code config.instances.pasteTicks} of them), then the decor entities and the match itself.
	 */
	private void buildTick()
	{
		if(active == null)
		{
			if(gapTicks > 0)
			{
				gapTicks--;
				return;
			}
			active = jobs.poll();
			if(active == null)
			{
				if(!startupDone)
				{
					startupDone = true;
					COMZombies.log.log(Level.INFO, "All arena copies are ready");
					if(!Bukkit.getOnlinePlayers().isEmpty())
						announce("All arenas are ready. &cThe dead are waiting.");
				}
				return;
			}
		}
		BuildJob job = active;
		String name = job.template.getName() + "~" + job.slot;
		try
		{
			if(!GameManager.INSTANCE.getGames().contains(job.template)
					|| !snapshots.containsValue(job.snapshot) && !soloSnapshots.containsValue(job.snapshot))
				throw new IOException("The arena was removed while its copy was being made");
			if(job.world == null)
			{
				if(GameManager.INSTANCE.getGames().stream().anyMatch(g -> g.getName().equalsIgnoreCase(name)))
					throw new IOException("Instance name conflicts with existing arena: " + name);
				job.world = beginWorld(job.template, job.slot);
				job.slices = job.snapshot.slices(Math.max(1, COMZombies.getPlugin().getConfig().getInt("config.instances.pasteTicks", 60)));
				return;
			}
			if(job.next < job.slices.size())
			{
				job.snapshot.pasteSlice(job.world, job.slices.get(job.next++));
				return;
			}
			job.snapshot.pasteEntities(job.world);
			Game made = finishWorld(job.template, job.slot, job.world);
			if(job.onDone != null)
				job.onDone.accept(made);
		}
		catch(Exception | LinkageError failure)
		{
			// Only a world this job made itself is taken down (a name clash means the world belongs to another match)
			if(job.world != null)
				cleanUpFailed(job.world.getName(), name, failure);
			else
				COMZombies.log.log(Level.SEVERE, "Failed to prepare " + name, failure);
			if(job.onDone != null)
				job.onDone.accept(null);
		}
		active = null;
		gapTicks = (int) Math.max(0, COMZombies.getPlugin().getConfig().getLong("config.instances.prepareIntervalTicks", 5));
	}

	private void stopBuilding()
	{
		if(poolTask != -1)
		{
			Bukkit.getScheduler().cancelTask(poolTask);
			poolTask = -1;
		}
		if(buildTask != -1)
		{
			Bukkit.getScheduler().cancelTask(buildTask);
			buildTask = -1;
		}
		for(BuildJob j : jobs)
			if(j.onDone != null)
				j.onDone.accept(null);
		jobs.clear();
		if(active != null && active.onDone != null)
			active.onDone.accept(null);
		if(active != null && active.world != null)
			cleanUpFailed(active.world.getName(), active.template.getName() + "~" + active.slot, null);
		active = null;
		idleSince.clear();
	}

	private void closeSnapshots()
	{
		for(ArenaSelectionCopy snapshot : snapshots.values())
			snapshot.close();
		snapshots.clear();
		for(ArenaSelectionCopy snapshot : soloSnapshots.values())
			snapshot.close();
		soloSnapshots.clear();
	}

	private static String worldNameOf(Game template, int slot)
	{
		return "mz_instance_" + ownerId(template.getName()) + "_" + slot;
	}

	/** The empty instance world, with the arena world's rules and the WorldGuard region; the arena is pasted after. */
	private World beginWorld(Game template, int slot) throws IOException
	{
		String worldName = worldNameOf(template, slot);
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
		try
		{
			setUpWorld(template, world, source, destination, worldGuard);
		}
		catch(IOException | RuntimeException | LinkageError failure)
		{
			// Made here, so taken down here (the caller only knows about worlds that were fully set up)
			cleanUpFailed(worldName, template.getName() + "~" + slot, null);
			throw failure;
		}
		return world;
	}

	private void setUpWorld(Game template, World world, World source, Path destination, boolean worldGuard) throws IOException
	{
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
	}

	/** Once the arena is pasted: BlockBlocker barriers, the match itself and its signs. */
	private Game finishWorld(Game template, int slot, World world) throws IOException
	{
		String name = template.getName() + "~" + slot;
		copyBlockBlockerBarriers(template.getWorld(), world);
		JsonObject data = template.saveGame().deepCopy();
		data.getAsJsonObject("save_data").addProperty("world_name", world.getName());
		data.getAsJsonObject("settings").addProperty("instance_count", 0);
		Game match = new Game(name);
		match.setTemplateName(template.getName());
		if(!match.loadGame(data))
			throw new IOException("Could not load instance configuration " + name);
		GameManager.INSTANCE.addGame(match);
		SignRegistry.INSTANCE.copyToInstance(template, match);
		COMZombies.log.log(Level.INFO, "Prepared {0} in {1}", new Object[]{name, world.getName()});
		return match;
	}

	/** Never leak a half-built world or a game whose world failed to load. */
	private void cleanUpFailed(String worldName, String name, Throwable failure)
	{
		World leaked = Bukkit.getWorld(worldName);
		if(leaked != null)
		{
			for(Player p : new ArrayList<>(leaked.getPlayers()))
				p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
			Bukkit.unloadWorld(leaked, false);
		}
		worlds.remove(worldName);
		InstanceWorldFiles.deleteOwnedQuietly(worldName);
		if(failure != null)
			COMZombies.log.log(Level.SEVERE, "Failed to prepare " + name, failure);
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
	public boolean createSolo(Player requester, Game template)
	{
		if(!COMZombies.getPlugin().getConfig().getBoolean("config.instances.soloInstances", true))
		{
			requester.sendMessage(COMZombies.PREFIX + "Solo arenas are disabled on this server.");
			return false;
		}
		if(template == null || template.isInstance() || !template.arena.isSetupComplete() || template.spawnManager.getPoints().isEmpty())
		{
			requester.sendMessage(COMZombies.PREFIX + "That arena is not set up to run a solo match.");
			return false;
		}
		if(GameManager.INSTANCE.isPlayerInGame(requester))
		{
			requester.sendMessage(COMZombies.PREFIX + "Leave your current game before starting a solo arena.");
			return false;
		}
		if(!com.theprogrammingturkey.comz.util.COMZPermission.SOLO_ARENA.hasPerm(requester, template.getName()))
		{
			com.theprogrammingturkey.comz.util.CommandUtil.noPermission(requester, "start a solo arena");
			return false;
		}
		if(!(Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit") || Bukkit.getPluginManager().isPluginEnabled("WorldEdit")))
		{
			requester.sendMessage(COMZombies.PREFIX + "Arena copies need FastAsyncWorldEdit or WorldEdit installed.");
			return false;
		}
		if(!soloPending.add(requester.getUniqueId()))
		{
			tell(requester, "Your solo arena is still being made...");
			return false;
		}
		ArenaSelectionCopy snapshot = snapshots.get(template.getName());
		if(snapshot == null)
		{
			snapshot = soloSnapshots.get(template.getName());
			if(snapshot == null)
			{
				try { snapshot = ArenaSelectionCopy.capture(template); }
				catch(Exception | LinkageError failure)
				{
					soloPending.remove(requester.getUniqueId());
					COMZombies.log.log(Level.SEVERE, "Could not capture template " + template.getName(), failure);
					requester.sendMessage(COMZombies.PREFIX + "Could not create your solo arena; check the console for details.");
					return false;
				}
				soloSnapshots.put(template.getName(), snapshot);
			}
		}
		// Solo slots live in 1000-9999 so their world folders can never collide with the persistent pool slots (1-16)
		java.util.Random random = new java.util.Random();
		int slot = 1000 + random.nextInt(9000);
		for(int i = 0; i < 20 && soloSlotTaken(template, slot); i++)
			slot = 1000 + random.nextInt(9000);
		if(soloSlotTaken(template, slot))
		{
			soloPending.remove(requester.getUniqueId());
			requester.sendMessage(COMZombies.PREFIX + "Could not find a free solo slot; try again in a moment.");
			return false;
		}
		// Made like every other copy, a slice per tick, so it does not freeze the server for everyone else
		BuildJob job = new BuildJob(template, slot, snapshot);
		UUID who = requester.getUniqueId();
		job.onDone = created ->
		{
			soloPending.remove(who);
			Player player = Bukkit.getPlayer(who);
			if(created == null)
			{
				if(player != null)
					player.sendMessage(COMZombies.PREFIX + "Could not create your solo arena; check the console for details.");
				return;
			}
			created.setSoloInstance(true);
			// A solo copy always starts instantly for its single player.
			created.arena.setLobbyLocation(null);
			created.minPlayers = 1;
			created.maxPlayers = 1;
			if(soloWatchdog == -1)
				soloWatchdog = COMZombies.scheduleTask(200, 200, this::soloWatchdog);
			if(player == null || !player.isOnline() || GameManager.INSTANCE.isPlayerInGame(player))
			{
				// They left (or joined another game) meanwhile: nobody needs this copy
				disposeSolo(created);
				return;
			}
			tell(player, "Your solo arena is ready. &cGood luck.");
			created.addPlayer(player);
		};
		jobs.addFirst(job);
		if(buildTask == -1)
			buildTask = COMZombies.scheduleTask(1, 1, this::buildTick);
		tell(requester, "Creating arena... &cdigging your own grave.");
		return true;
	}

	private boolean soloSlotTaken(Game template, int slot)
	{
		if(Bukkit.getWorld(worldNameOf(template, slot)) != null)
			return true;
		for(BuildJob j : jobs)
			if(j.template == template && j.slot == slot)
				return true;
		return active != null && active.template == template && active.slot == slot;
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
		disposeInstance(game, "solo arena");
	}

	/** Unloads an instance world (empty), deletes its files and forgets the match. */
	private void disposeInstance(Game game, String what)
	{
		World world = Bukkit.getWorld(game.getWorld().getName());
		if(world == null)
		{
			worlds.remove(game.getWorld().getName());
			game.retire();
			GameManager.INSTANCE.getGames().remove(game);
			return;
		}
		for(Player player : new ArrayList<>(world.getPlayers()))
			player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
		SignRegistry.INSTANCE.removeInstanceWorld(world);
		if(!Bukkit.unloadWorld(world, false))
		{
			COMZombies.log.log(Level.SEVERE, "Could not unload instance world {0}", new Object[]{world.getName()});
			return;
		}
		worlds.remove(world.getName());
		InstanceWorldFiles.deleteOwnedQuietly(world.getName());
		// Its timers and delayed work stop with it (it used to keep its start timer and power-ups going)
		game.retire();
		GameManager.INSTANCE.getGames().remove(game);
		COMZombies.log.log(Level.INFO, "Removed {0} {1}", new Object[]{what, game.getName()});
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
		stopBuilding();
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
