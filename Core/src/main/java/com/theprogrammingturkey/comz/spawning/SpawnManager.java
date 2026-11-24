package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.game.features.Barrier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.Util;
import org.bukkit.Bukkit;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class SpawnManager
{
	private static final Map<RoundSpawnType, RoundSpawner> roundSpawnerMap = new HashMap<>();

	static
	{
		roundSpawnerMap.put(RoundSpawnType.REGULAR, new ZombieSpawner());
		roundSpawnerMap.put(RoundSpawnType.HELL_HOUNDS, new HellHoundSpawner());
	}

	private final Game game;
	private final List<SpawnPoint> points = new ArrayList<>();
	private final List<Mob> mobs = new ArrayList<>();
	// Idle zombies: when each last hit a player (or a barrier, or was close to a player), and when it was last respawned
	private final Map<Mob, Long> lastActive = new HashMap<>();
	private final Map<Mob, Long> lastRespawn = new HashMap<>();
	private int updatesSinceCrackRefresh;
	private RoundSpawner roundSpawner = new ZombieSpawner();
	private boolean canSpawn = false;
	private double spawnInterval;
	private final double spawnDelayFactor;
	private int mobsSpawned = 0;
	private int mobsToSpawn = 0;

	public SpawnManager(Game game)
	{
		this.game = game;
		spawnInterval = COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.zombieSpawnDelay");
		spawnDelayFactor = COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.zombieSpawnDelayFactor");
	}

	public void loadAllSpawnsToGame(JsonArray spawnsSaveJson)
	{
		points.clear();

		for(JsonElement spawnElem : spawnsSaveJson)
		{
			if(!spawnElem.isJsonObject())
				continue;
			Location loc = CustomConfig.getLocationWithWorld(spawnElem.getAsJsonObject(), "", game.getWorld());
			String id = CustomConfig.getString(spawnElem.getAsJsonObject(), "id", "MISSING");
			if(loc != null && !id.equals("MISSING"))
			{
				SpawnPoint point = new SpawnPoint(loc, game, loc.getBlock().getType(), id);
				JsonObject spawnJson = spawnElem.getAsJsonObject();
				point.setAlwaysActive(spawnJson.has("always_active") && spawnJson.get("always_active").getAsBoolean());
				points.add(point);
			}
			else
				COMZombies.log.log(Level.WARNING, "Failed to load zombie spawn! ID: {0} w/ Loc: {1}", new Object[]{id, loc});
		}
	}

	public JsonArray save()
	{
		JsonArray spawnsSaveJson = new JsonArray();
		for(SpawnPoint spawn : points)
		{
			JsonObject spawnJson = CustomConfig.locationToJson(spawn.getLocation());
			spawnJson.addProperty("id", spawn.getID());
			if(spawn.isAlwaysActive())
				spawnJson.addProperty("always_active", true);
			spawnsSaveJson.add(spawnJson);
		}

		return spawnsSaveJson;
	}

	public SpawnPoint getSpawnPoint(String id)
	{
		for(SpawnPoint p : points)
			if(id.equalsIgnoreCase(p.getID()))
				return p;
		return null;
	}

	public SpawnPoint getSpawnPoint(Location loc)
	{
		for(SpawnPoint point : points)
			if(point.getLocation().equals(loc))
				return point;
		return null;
	}

	public void removePoint(SpawnPoint point)
	{
		if(points.contains(point))
		{
			BlockUtils.setBlockToAir(point.getLocation());
			points.remove(point);
		}
		GameManager.INSTANCE.saveAllGames();
	}

	public List<SpawnPoint> getPoints()
	{
		return points;
	}

	public void killMob(Entity entity)
	{
		if(entity == null || entity instanceof Player)
			return;

		// Once: remove() on an entity that is already gone does not make it "dead", so a loop here could spin forever
		entity.remove();

		this.removeEntity(entity);
	}

	public void nuke()
	{
		killAll(false);
	}

	public void killAll(boolean nextWave)
	{
		for(int i = mobs.size() - 1; i >= 0; i--)
			killMob(this.mobs.get(i));

		mobs.clear();
		// (removeEntity already moves on when the round is done; only force it when asked and it has not happened yet)
		if(nextWave)
			game.nextWave();
	}

	public List<Mob> getEntities()
	{
		return mobs;
	}

	public void removeEntity(Entity entity)
	{
		if(!(entity instanceof Mob mob))
			return;

		mobs.remove(mob);
		lastActive.remove(mob);
		lastRespawn.remove(mob);

		if(mobs.isEmpty() && mobsSpawned >= mobsToSpawn)
			game.nextWave();

		game.scoreboard.update();
	}

	public boolean addPoint(SpawnPoint point)
	{
		if(game.getStatus() == GameStatus.DISABLED)
			return points.add(point);
		return false;
	}

	// Finds the closest locations to point loc, it results numToGet amount of
	// spawn points

	public int getTotalSpawns()
	{
		return points.size();
	}

	public Game getGame()
	{
		return game;
	}

	/**
	 * A random spawn among the open ones closest to the location, a different floor counting three times as far (a spawn
	 * right above or below is not "close"). Closer ones are picked a bit more often.
	 *
	 * @return null if no spawn is open
	 */
	private SpawnPoint nearOpenSpawn(Location at)
	{
		int count = Math.max(1, COMZombies.getPlugin().getConfig().getInt("config.gameSettings.spawnNearestCount", 4));
		List<SpawnPoint> open = new ArrayList<>();
		for(SpawnPoint sp : points)
			if(canSpawn(sp) && sp.getLocation().getWorld() != null && sp.getLocation().getWorld().equals(at.getWorld()))
				open.add(sp);
		if(open.isEmpty())
			return null;
		java.util.function.ToDoubleFunction<SpawnPoint> dist = sp ->
		{
			Location l = sp.getLocation();
			double dx = l.getX() - at.getX(), dz = l.getZ() - at.getZ(), dy = (l.getY() - at.getY()) * 3;
			return dx * dx + dy * dy + dz * dz;
		};
		open.sort(java.util.Comparator.comparingDouble(dist));
		List<SpawnPoint> closest = open.subList(0, Math.min(count, open.size()));
		// Weighted: the closest is the likeliest (weights count, count-1, ... 1)
		int total = 0;
		for(int i = 0; i < closest.size(); i++)
			total += closest.size() - i;
		int roll = COMZombies.rand.nextInt(total);
		for(int i = 0; i < closest.size(); i++)
		{
			roll -= closest.size() - i;
			if(roll < 0)
				return closest.get(i);
		}
		return closest.get(0);
	}

	/** Bumped whenever a round is (re)started, so spawn loops left over from an earlier round stop themselves. */
	private int spawnGen = 0;

	/** Debug: drops the current round's mobs and spawn counters so a new round can be started right away. */
	public void resetForForcedRound(int round)
	{
		// The spawn delay shrinks a little every round that is played, so jumping ahead has to shrink it as well, otherwise a
		// forced round 50 spawns at the speed of round 2. nextWave() divides once more, so this is the value before that.
		double base = COMZombies.getPlugin().getConfig().getInt("config.gameSettings.zombieSpawnDelay");
		spawnInterval = Math.max(0.5, base / Math.pow(spawnDelayFactor, Math.max(0, round - 1)));
		spawnGen++;
		canSpawn = false;
		mobsSpawned = 0;
		mobsToSpawn = 0;
	}

	/**
	 * Ticks between two spawns. Whole seconds as before, but never 0: below one second the old (int) cast gave 0 ticks, so
	 * the spawn loop re-ran inside the same server tick, and froze the server once the zombie cap was reached.
	 */
	private long spawnDelayTicks()
	{
		if(spawnInterval >= 1)
			return (int) spawnInterval * 20L;
		return Math.max(1L, (long) (spawnInterval * 20));
	}

	private boolean noOpenSpawnWarned;

	private void smartSpawn(final int wave)
	{
		final int gen = spawnGen;
		if(!this.canSpawn || wave != game.getWave())
			return;
		if(game.getStatus() != GameStatus.INGAME)
			return;
		if(this.mobsSpawned >= this.mobsToSpawn)
			return;

		if(mobs.size() >= ConfigManager.getMainConfig().maxZombies)
		{
			// Waiting for room: check back at most twice a second, never in the same tick (that would spin the server)
			game.scheduleMatchTask(Math.max(10L, spawnDelayTicks()), () ->
			{
				if(gen == spawnGen)
					smartSpawn(wave);
			});
			return;
		}

		int playersSize = game.getPlayersInGame().size();
		if(playersSize == 0)
			return;

		SpawnPoint selectPoint = null;
		Player player = game.getPlayersInGame().get(COMZombies.rand.nextInt(playersSize));
		// Only the spawns nearest to a player are normally used, which in early rounds never includes a spawn that is far
		// from everyone. So some of the time any open spawn is picked, whatever its distance.
		double farChance = COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.farSpawnChance", 0.25);
		if(farChance > 0 && COMZombies.rand.nextDouble() < farChance)
		{
			List<SpawnPoint> open = new ArrayList<>();
			for(SpawnPoint sp : game.spawnManager.getPoints())
				if(canSpawn(sp))
					open.add(sp);
			if(!open.isEmpty())
				selectPoint = open.get(COMZombies.rand.nextInt(open.size()));
		}
		// Normally: one of the few open spawns closest to the player (config.gameSettings.spawnNearestCount). "Nearest" used
		// to mean as many spawns as the round has zombies, which from round ~5 on was every spawn of the map.
		if(selectPoint == null)
			selectPoint = nearOpenSpawn(player.getLocation());
		// Any other player (one in another spot may have open spawns near them)
		for(int i = 0; selectPoint == null && i < playersSize; i++)
			selectPoint = nearOpenSpawn(game.getPlayersInGame().get(i).getLocation());
		if(selectPoint == null)
		{
			// No spawn is open right now (e.g. every spawn is behind a closed door): try again in 2 seconds. This used to
			// loop on the spot until it found one, which froze the whole server when there was none.
			if(!noOpenSpawnWarned)
			{
				noOpenSpawnWarned = true;
				COMZombies.log.log(java.util.logging.Level.WARNING, "No open zombie spawn in {0}; check that a spawn is linked outside the doors", new Object[]{game.getName()});
			}
			game.scheduleMatchTask(40L, () ->
			{
				if(gen == spawnGen)
					smartSpawn(wave);
			});
			return;
		}

		final SpawnPoint finalPoint = selectPoint;
		game.scheduleMatchTask(spawnDelayTicks(), () ->
		{
			if(gen != spawnGen || !this.canSpawn || wave != game.getWave())
				return;

			Mob ent = roundSpawner.spawnEntity(game, finalPoint, wave);
			mobs.add(ent);

			mobsSpawned++;
			smartSpawn(wave);
		});
	}

	public void update()
	{
		game.scheduleMatchTask(10, () ->
		{
			if(game.getStatus() != GameStatus.INGAME)
				return;

			for(int i = mobs.size() - 1; i >= 0; i--)
			{
				Mob mob = mobs.get(i);
				if(mob.isDead())
					removeEntity(mob);
				else
					checkIdle(mob); // (ZombieBrain walks it and does its hitting)
				if(game.getStatus() != GameStatus.INGAME)
					return;
			}

			// Runs every 5 seconds, well inside the 20 seconds before clients forget barrier cracks
			if(++updatesSinceCrackRefresh >= 10)
			{
				updatesSinceCrackRefresh = 0;
				game.barrierManager.refreshDamage();
			}

			update();
		});
	}

	/** The zombie hit a player (or a barrier): it is not idle. */
	public void markActive(Mob mob)
	{
		if(mobs.contains(mob))
			lastActive.put(mob, System.currentTimeMillis());
	}

	// A zombie this close to a player is in the fight (e.g. chasing a player who runs), not stuck
	private static final double ENGAGED_RANGE_SQUARED = 8 * 8;

	/**
	 * A zombie that has not hit anyone for config.gameSettings.idleRespawnSeconds (30) is respawned at a spawn near a
	 * player (it rises again there, same health); one that has not hit anyone for config.gameSettings.idleKillSeconds
	 * (120) despite that is removed, and counts as gone for the round. Hitting a barrier or being close to a player counts
	 * as fighting.
	 *
	 * @return true if the zombie was removed
	 */
	private boolean checkIdle(Mob mob)
	{
		long now = System.currentTimeMillis();
		if(mob instanceof org.bukkit.entity.Zombie z && com.theprogrammingturkey.comz.game.ZombieRise.isRising(z))
		{
			lastActive.putIfAbsent(mob, now);
			return false;
		}
		// Counted as fighting in the last 2 seconds: no need to check again yet (the line-of-sight check is costly, and the
		// idle timers are tens of seconds long)
		Long active = lastActive.get(mob);
		if(active != null && now - active < 2000)
			return false;
		// Close to a player with nothing between them (a zombie stuck behind a fence or wall next to a player is not fighting)
		for(Player p : game.getPlayersInGame())
			if(p.getWorld().equals(mob.getWorld()) && p.getLocation().distanceSquared(mob.getLocation()) <= ENGAGED_RANGE_SQUARED
					&& com.theprogrammingturkey.comz.game.ZombiePathing.canHit(mob, p, null))
			{
				lastActive.put(mob, now);
				return false;
			}
		var config = COMZombies.getPlugin().getConfig();
		long respawnMs = (long) (config.getDouble("config.gameSettings.idleRespawnSeconds", 30) * 1000);
		long killMs = (long) (config.getDouble("config.gameSettings.idleKillSeconds", 120) * 1000);
		long idle = now - lastActive.computeIfAbsent(mob, m -> now);
		if(killMs > 0 && idle >= killMs)
		{
			killMob(mob);
			return true;
		}
		long sinceRespawn = now - lastRespawn.getOrDefault(mob, 0L);
		if(respawnMs > 0 && idle >= respawnMs && sinceRespawn >= respawnMs)
		{
			lastRespawn.put(mob, now);
			respawnNearPlayer(mob);
		}
		return false;
	}

	/** Moves the zombie to a spawn near a player, where it rises again and picks its barrier afresh. */
	private void respawnNearPlayer(Mob mob)
	{
		List<Player> players = game.getPlayersInGame();
		if(players.isEmpty())
			return;
		Player player = players.get(COMZombies.rand.nextInt(players.size()));
		SpawnPoint point = nearOpenSpawn(player.getLocation());
		if(point == null || point.getLocation().getWorld() == null)
			return;
		Barrier held = game.barrierManager.heldBy(mob);
		if(held != null)
			held.forget(mob);
		for(Barrier b : game.barrierManager.getBarriers())
			b.forget(mob);
		Location at = point.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
		mob.setTarget(null);
		mob.teleport(at);
		com.theprogrammingturkey.comz.game.ZombieBrain.forget(mob);
		Barrier b = game.barrierManager.chooseBarrierFor(mob, point);
		if(b != null)
			b.initBarrier(mob);
		if(mob instanceof org.bukkit.entity.Zombie z)
			com.theprogrammingturkey.comz.game.ZombieRise.start(z, getNearestPlayer(mob));
	}

	public Player getNearestPlayer(Entity e)
	{
		Player closestPlayer = null;
		double dist = Double.MAX_VALUE;
		for(Player pl : game.getPlayersInGame())
		{
			if(!pl.isOnline() || pl.isDead() || !pl.getWorld().equals(e.getWorld())
					|| pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)
				continue;
			if(game.downedPlayerManager.isDownedPlayer(pl))
				continue;

			double dist2 = pl.getLocation().distanceSquared(e.getLocation()); // (squared: same order, no square root)
			if(dist > dist2)
			{
				closestPlayer = pl;
				dist = dist2;
			}
		}
		return closestPlayer;
	}

	public void setSpawnInterval(double interval)
	{
		this.spawnInterval = interval;
	}

	/** Is the spawn in use: always active, in no door's room, or in the room of a door that is open? */
	public boolean isSpawnActive(SpawnPoint point)
	{
		return canSpawn(point);
	}

	/** Spawns whose zombies found no way to any window or player: not used until this server tick. */
	private final java.util.Map<SpawnPoint, Integer> deadEnds = new java.util.HashMap<>();
	private static final int DEAD_END_TICKS = 1200;

	/**
	 * A zombie from this spawn has no way to any open window or to a player (walled in, or its window is in a room that is
	 * still shut): the spawn is not used for a minute (rooms open meanwhile), and it is logged once each time.
	 */
	public void markDeadEnd(SpawnPoint point, String why)
	{
		int now = org.bukkit.Bukkit.getCurrentTick();
		Integer until = deadEnds.get(point);
		if(until == null || until <= now)
			COMZombies.log.info("[MZ] " + game.getName() + ": spawn " + point.getID() + " not used for a minute: " + why);
		deadEnds.put(point, now + DEAD_END_TICKS);
	}

	private boolean canSpawn(SpawnPoint point)
	{
		if(point == null)
			return false;
		Integer deadUntil = deadEnds.get(point);
		if(deadUntil != null && deadUntil > org.bukkit.Bukkit.getCurrentTick())
			return false;
		if(point.isAlwaysActive())
			return true;
		boolean isContained = false;
		boolean maySpawn = false;
		for(Door door : game.doorManager.getDoors())
		{
			for(SpawnPoint p : door.getSpawnsInRoomDoorLeadsTo())
			{
				if(p.getLocation().equals(point.getLocation()))
				{
					if(door.isOpened())
						maySpawn = true;
					isContained = true;
				}
			}
		}
		if(!isContained)
			return true;
		return maySpawn;
	}

	public RoundSpawnType nextWave(int wave, final List<Player> players)
	{
		canSpawn = false;
		mobsSpawned = 0;

		if(game.getDogRoundEveryX() != -1 && game.getDogRoundEveryX() != 0 && wave % game.getDogRoundEveryX() == 0)
		{
			mobsToSpawn = 10;
			roundSpawner = roundSpawnerMap.get(RoundSpawnType.HELL_HOUNDS);
			setSpawnInterval(spawnInterval / spawnDelayFactor);
			if(spawnInterval < 0.5)
				spawnInterval = 0.5;
			return RoundSpawnType.HELL_HOUNDS;
		}
		else
		{
			mobsToSpawn = (int) ((wave * 0.15) * 30) + (2 * players.size());
			roundSpawner = roundSpawnerMap.get(RoundSpawnType.REGULAR);
			setSpawnInterval(spawnInterval / spawnDelayFactor);
			if(spawnInterval < 0.5)
				spawnInterval = 0.5;
			return RoundSpawnType.REGULAR;
		}
	}

	public void startWave(int wave)
	{
		canSpawn = true;

		if(game.getPlayersInGame().isEmpty() && game.getStatus() == GameStatus.INGAME)
		{
			this.game.endGame();
			Bukkit.broadcast(Colors.legacy(COMZombies.PREFIX + "SmartSpawn was sent a players list with no players in it! Game was ended"));
			return;
		}
		else if(game.getStatus() != GameStatus.INGAME)
		{
			return;
		}

		this.smartSpawn(wave);
	}

	public int getMobsToSpawn()
	{
		return this.mobsToSpawn;
	}

	public int getMobsSpawned()
	{
		return this.mobsSpawned;
	}

	public int getZombiesAlive()
	{
		return this.mobs.size();
	}

	public int getSpawnInterval()
	{
		return (int) this.spawnInterval;
	}

	public boolean isEntitySpawned(Mob ent)
	{
		return this.mobs.contains(ent);
	}

	public void reset()
	{
		this.mobs.clear();
		deadEnds.clear();
		// Per-zombie timers: a zombie that left the list another way would otherwise stay here (and keep its world loaded)
		lastActive.clear();
		lastRespawn.clear();
		noOpenSpawnWarned = false;
		this.canSpawn = false;
		this.mobsSpawned = 0;
		this.mobsToSpawn = 0;
		this.spawnInterval = COMZombies.getPlugin().getConfig().getInt("config.gameSettings.zombieSpawnDelay");
	}

	public String getNewSpawnPointNum()
	{
		return Util.genRandId();
	}
}
