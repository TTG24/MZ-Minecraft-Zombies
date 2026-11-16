package com.theprogrammingturkey.comz.game.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.CustomConfig;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.Util;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class BarrierManager
{
	private final Game game;
	private final List<Barrier> barriers = new ArrayList<>();

	public BarrierManager(Game game)
	{
		this.game = game;
	}

	public void loadAllBarriersToGame(JsonArray barriersJson)
	{
		this.barriers.clear();

		for(JsonElement barrierElem : barriersJson)
		{
			if(!barrierElem.isJsonObject())
				continue;
			JsonObject barrierJson = barrierElem.getAsJsonObject();

			String barrierID = CustomConfig.getString(barrierJson, "id", "MISSING");
			Barrier barrier = new Barrier(barrierID, game);

			barrier.setRepairLoc(CustomConfig.getLocationWithWorld(barrierJson, "repair_loc", game.getWorld()));
			barrier.setSignFacing(BlockFace.valueOf(CustomConfig.getString(barrierJson, "repair_facing", "NORTH")));

			JsonArray barrierBlocks = barrierJson.get("blocks").getAsJsonArray();
			for(JsonElement blockElem : barrierBlocks)
			{
				if(!blockElem.isJsonObject())
					continue;
				JsonObject blockJson = blockElem.getAsJsonObject();

				Location loc = CustomConfig.getLocationWithWorld(blockJson, "", game.getWorld());
				if(loc != null)
				{
					Material mat = BlockUtils.getMaterialFromKey(CustomConfig.getString(blockJson, "material", ""));
					Block block = game.getWorld().getBlockAt(loc);
					barrier.addBarrierBlock(block, mat);
					barrier.setBlockData(block, CustomConfig.getString(blockJson, "data", null));
				}
				else
				{
					COMZombies.log.log(Level.WARNING, "Failed to load a block location for Barrier: " + barrierID + ", Json: " + barrierJson);
				}
			}

			barrier.addSpawnPoints(StreamSupport.stream(barrierJson.get("spawns").getAsJsonArray().spliterator(), false).map(sp -> game.spawnManager.getSpawnPoint(sp.getAsString())).filter(Objects::nonNull).collect(Collectors.toList()));

			barrier.setReward(CustomConfig.getInt(barrierJson, "reward", 1));

			this.barriers.add(barrier);
		}
	}

	public JsonArray save()
	{
		JsonArray saveJson = new JsonArray();
		for(Barrier barrier : barriers)
		{
			JsonObject barrierJson = new JsonObject();
			barrierJson.addProperty("id", barrier.getID());
			if(barrier.getRepairLoc() != null)
			{
				barrierJson.add("repair_loc", CustomConfig.locationToJsonNoWorld(barrier.getRepairLoc()));
				barrierJson.addProperty("repair_facing", barrier.getSignFacing().name());
			}

			JsonArray barrierBlocks = new JsonArray();
			barrierJson.add("blocks", barrierBlocks);
			for(Block block : barrier.getBlocks())
			{
				JsonObject blockJson = CustomConfig.locationToJsonNoWorld(block.getLocation());
				Material mat = barrier.getMaterial(block);
				if(mat == null)
					mat = block.getType();
				blockJson.addProperty("material", mat.getKey().getKey());
				String data = barrier.getBlockData(block);
				if(data != null)
					blockJson.addProperty("data", data);
				barrierBlocks.add(blockJson);
			}

			JsonArray spawnsArray = new JsonArray();
			barrier.getSpawnPoints().forEach(spawnPoint -> spawnsArray.add(spawnPoint.getID()));
			barrierJson.add("spawns", spawnsArray);

			barrierJson.addProperty("reward", barrier.getReward());
			saveJson.add(barrierJson);
		}

		return saveJson;
	}

	public Barrier getBarrier(Location loc)
	{
		for(Barrier b : barriers)
			for(Block block : b.getBlocks())
				if(block.getLocation().equals(loc))
					return b;
		return null;
	}

	public Barrier getBarrier(String id)
	{
		for(Barrier b : barriers)
			if(b.getID().equals(id))
				return b;
		return null;
	}

	public Barrier getBarrierFromRepair(Location loc)
	{
		for(Barrier b : barriers)
			if(loc.equals(b.getRepairLoc()))
				return b;
		return null;
	}

	/** Distance from a location to the closest block of a barrier; a floor up or down counts three times over. */
	private static double toBarrier(Location from, Barrier b)
	{
		double best = Double.MAX_VALUE;
		for(Block block : b.getBlocks())
		{
			if(!block.getWorld().equals(from.getWorld()))
				continue;
			double dx = block.getX() + 0.5 - from.getX();
			double dz = block.getZ() + 0.5 - from.getZ();
			double dy = (block.getY() + 0.5 - from.getY()) * 3;
			best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
		}
		return best;
	}

	/**
	 * How long the trip to the player is through this barrier: zombie to barrier, plus barrier to the player. The barrier
	 * that makes it shortest is the one a zombie goes for - the one between it and the player, not just the closest one.
	 */
	public double routeCost(Location zombie, Barrier b, Player player)
	{
		double cost = toBarrier(zombie, b);
		if(player != null && player.getWorld().equals(zombie.getWorld()))
			cost += toBarrier(player.getLocation(), b);
		return cost;
	}

	/**
	 * The barriers a zombie here may go for: within config.gameSettings.barrierSeekRange of it and, when any are, at its own
	 * height (config.gameSettings.barrierHeightTolerance blocks up or down), so a window on another floor is not picked.
	 */
	private List<Barrier> candidates(Location at)
	{
		double seek = COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.barrierSeekRange", 30);
		int tolerance = COMZombies.getPlugin().getConfig().getInt("config.gameSettings.barrierHeightTolerance", 2);
		List<Barrier> all = new ArrayList<>();
		List<Barrier> sameHeight = new ArrayList<>();
		for(Barrier b : barriers)
		{
			if(b.getBlocks().isEmpty() || toBarrier(at, b) > seek || !isOpenToPlayers(b))
				continue;
			all.add(b);
			int closestDy = Integer.MAX_VALUE;
			for(Block block : b.getBlocks())
				closestDy = Math.min(closestDy, Math.abs(block.getY() - at.getBlockY()));
			if(tolerance >= 0 && closestDy <= tolerance)
				sameHeight.add(b);
		}
		return sameHeight.isEmpty() ? all : sameHeight;
	}

	// How far (in blocks) a barrier's zombie spawn may be from it to count as that window's spawn
	private static final double OWN_SPAWN_RANGE = 12;

	/**
	 * Does this barrier lead into a part of the map the players can be in? A barrier into a room behind a door nobody has
	 * bought yet does not: a zombie that broke it would only end up in that closed room. Told from the barrier's spawns:
	 * the ones linked to it, or (none linked) the zombie spawn nearest to it, which is the one outside its window. Those
	 * spawns are in the room of the door that opens them, so they are active exactly when the barrier's room is open.
	 * A barrier with no spawn anywhere near counts as open. A barrier linked to doors (their "barriers") ignores all
	 * that: it is open once one of those doors is.
	 */
	public boolean isOpenToPlayers(Barrier b)
	{
		// Linked to doors (door setup/edit, "barriers"): usable once any of those doors is open
		boolean linked = false;
		for(com.theprogrammingturkey.comz.game.features.Door door : game.doorManager.getDoors())
		{
			if(!door.hasRoomBarrier(b))
				continue;
			// A power door's room is open to zombies all along: its barriers get broken even before the power is on
			if(door.isOpened() || door.requiresPower())
				return true;
			linked = true;
		}
		if(linked)
			return false;
		List<com.theprogrammingturkey.comz.spawning.SpawnPoint> own = b.getSpawnPoints();
		if(own.isEmpty())
		{
			com.theprogrammingturkey.comz.spawning.SpawnPoint nearest = null;
			double best = OWN_SPAWN_RANGE;
			for(com.theprogrammingturkey.comz.spawning.SpawnPoint sp : game.spawnManager.getPoints())
			{
				if(sp.getLocation() == null || sp.getLocation().getWorld() == null)
					continue;
				double d = toBarrier(sp.getLocation(), b);
				if(d <= best)
				{
					best = d;
					nearest = sp;
				}
			}
			if(nearest == null)
				return true;
			own = List.of(nearest);
		}
		for(com.theprogrammingturkey.comz.spawning.SpawnPoint sp : own)
			if(game.spawnManager.isSpawnActive(sp))
				return true;
		return false;
	}

	/**
	 * Which barrier a zombie that has just spawned goes for: of the barriers near it, the one that makes its way to the
	 * nearest player shortest (see {@link #routeCost}), and that it can actually walk up to (checked with the pathfinder).
	 * An intact one it breaks; an already broken one (later rounds) it just walks to and goes through. Zombies stick to this
	 * and do not go for the players until they are through it; {@link #betterBarrier} moves them to another barrier when the
	 * player moves.
	 *
	 * @return that barrier, or null when there is no barrier near it (the zombie then goes straight for the players)
	 */
	public Barrier chooseBarrierFor(org.bukkit.entity.Mob mob, SpawnPoint spawn)
	{
		Location at = mob.getLocation();
		List<Barrier> ranked = candidates(at);
		Player player = game.spawnManager.getNearestPlayer(mob);
		// A zombie that can walk straight to the player (it spawned inside the map, or its way in is already open and
		// needs no barrier) goes for the player. Only one that cannot gets a barrier to break in through.
		if(player != null && openWayLength(mob, player.getLocation()) >= 0)
			return null;
		if(ranked.isEmpty())
		{
			// No barrier within barrierSeekRange (a spawn far from its window). If the zombie cannot simply walk to the
			// player, it has to come in through a barrier all the same: consider every barrier, wherever it is.
			if(player == null || reallyReaches(mob, player.getLocation()))
				return null;
			for(Barrier b : barriers)
				if(!b.getBlocks().isEmpty() && b.getBlocks().get(0).getWorld().equals(at.getWorld()) && isOpenToPlayers(b))
					ranked.add(b);
			if(ranked.isEmpty())
				return null;
		}
		java.util.Map<Barrier, Double> cost = new java.util.HashMap<>();
		for(Barrier b : ranked)
			cost.put(b, routeCost(at, b, player));
		ranked.sort(java.util.Comparator.comparingDouble(cost::get));

		// A spawn with its own way to the players that needs no barrier at all (Der Riese: drop down, or go through the
		// window): when that way is not much longer than through the barrier, some zombies take it.
		if(player != null)
		{
			double open = openWayLength(mob, player.getLocation());
			if(open >= 0)
			{
				var config = COMZombies.getPlugin().getConfig();
				double chance = config.getDouble("config.gameSettings.openWayChance", 0.5);
				double slack = config.getDouble("config.gameSettings.openWayMaxExtra", 10);
				if(open <= cost.get(ranked.get(0)) + slack ? random.nextDouble() < chance : wantsOpenRoute(mob))
					return null;
			}
		}

		if(com.theprogrammingturkey.comz.util.PaperPathfinding.isAvailable())
		{
			int tried = 0;
			for(Barrier b : ranked)
			{
				if(tried++ >= 5)
					break;
				if(canWalkUpTo(mob, b))
					return b;
			}
		}
		return ranked.get(0);
	}

	/**
	 * Re-decided every couple of seconds for a zombie on its way to (or at) a barrier: when the player has moved so that
	 * another barrier now makes the way to them clearly shorter, that one. A zombie already at its barrier needs a bigger
	 * difference to give up on it, so zombies do not flip back and forth.
	 *
	 * @return the barrier to switch to, or null to keep the current one
	 */
	public Barrier betterBarrier(org.bukkit.entity.Mob mob, Barrier current, boolean atCurrent)
	{
		Player player = game.spawnManager.getNearestPlayer(mob);
		if(player == null)
			return null;
		boolean currentOpen = isOpenToPlayers(current);
		// Already at its barrier and breaking it: it finishes that one first, wherever the player has gone
		if(atCurrent && currentOpen && current.getStage() < 5)
			return null;
		Location at = mob.getLocation();
		// Heading for a barrier into a closed room: any open barrier is better
		double now = currentOpen ? routeCost(at, current, player) : Double.MAX_VALUE;
		double margin = currentOpen ? 3 : 0;
		Barrier best = null;
		double bestCost = now - margin;
		for(Barrier b : candidates(at))
		{
			if(b == current)
				continue;
			double c = routeCost(at, b, player);
			if(c < bestCost)
			{
				bestCost = c;
				best = b;
			}
		}
		if(best != null && com.theprogrammingturkey.comz.util.PaperPathfinding.isAvailable() && !canWalkUpTo(mob, best))
			return null;
		return best;
	}

	/** Is there a route for the mob to a spot beside the barrier? */
	private boolean canWalkUpTo(org.bukkit.entity.Mob mob, Barrier barrier)
	{
		int tried = 0;
		for(Location approach : barrier.approachLocations(mob.getLocation()))
		{
			if(tried++ >= 4)
				break;
			if(com.theprogrammingturkey.comz.util.PaperPathfinding.canReach(mob, approach))
				return true;
		}
		return false;
	}

	private final java.util.Random random = new java.util.Random();

	/** Is there a route that really gets the mob to the location (not just near it, with something in between)? */
	private static boolean reallyReaches(org.bukkit.entity.Mob mob, Location to)
	{
		if(!com.theprogrammingturkey.comz.util.PaperPathfinding.isAvailable())
			return false;
		com.destroystokyo.paper.entity.Pathfinder.PathResult path = mob.getPathfinder().findPath(to);
		return path != null && path.canReachFinalPoint() && path.getFinalPoint() != null
				&& com.theprogrammingturkey.comz.game.ZombiePathing.arrivesAt(path.getFinalPoint(), to);
	}

	/**
	 * How long the zombie's own walk to the location is when it needs no barrier at all: a real route that does not pass
	 * through any barrier of this arena (broken or not) nor any BlockBlocker barrier.
	 *
	 * @return the length in blocks, or -1 when there is no such route
	 */
	private double openWayLength(org.bukkit.entity.Mob mob, Location to)
	{
		if(!com.theprogrammingturkey.comz.util.PaperPathfinding.isAvailable())
			return -1;
		com.destroystokyo.paper.entity.Pathfinder.PathResult path = mob.getPathfinder().findPath(to);
		if(path == null || !path.canReachFinalPoint() || path.getFinalPoint() == null
				|| !com.theprogrammingturkey.comz.game.ZombiePathing.arrivesAt(path.getFinalPoint(), to))
			return -1;
		java.util.Set<Block> barrierBlocks = new java.util.HashSet<>();
		for(Barrier b : barriers)
			barrierBlocks.addAll(b.getBlocks());
		double length = 0;
		Location previous = mob.getLocation();
		for(Location point : path.getPoints())
		{
			Block feet = point.getBlock();
			if(barrierBlocks.contains(feet) || barrierBlocks.contains(feet.getRelative(org.bukkit.block.BlockFace.UP)))
				return -1;
			if(com.theprogrammingturkey.comz.game.ZombiePathing.pathStepBlocked(previous, point))
				return -1;
			length += previous.distance(point);
			previous = point;
		}
		return length;
	}

	/**
	 * Should this zombie take an already-broken barrier to the players instead of breaking one itself? Only when a real
	 * route to the nearest player exists right now (Paper's pathfinder says so), and then only some of the time
	 * (config.gameSettings.openRouteChance), so zombies keep working on barriers as well.
	 */
	public boolean wantsOpenRoute(org.bukkit.entity.Mob mob)
	{
		var config = COMZombies.getPlugin().getConfig();
		double chance = config.getDouble("config.gameSettings.openRouteChance", 0.35);
		if(chance <= 0 || random.nextDouble() >= chance)
			return false;
		// Early rounds stay orderly: every zombie breaks a barrier
		if(game.getWave() < config.getInt("config.gameSettings.openRouteMinRound", 5))
			return false;
		Player target = game.spawnManager.getNearestPlayer(mob);
		if(target == null)
			return false;

		// Only when the open way is not a big detour: it used to be taken whenever any route existed, even one all the way
		// around a building. The route may be at most openRouteMaxDetour times the straight distance (plus a little slack).
		double route = reallyReaches(mob, target.getLocation())
				? com.theprogrammingturkey.comz.util.PaperPathfinding.routeLength(mob, target.getLocation()) : -1;
		if(route < 0)
			return false;
		double maxDetour = config.getDouble("config.gameSettings.openRouteMaxDetour", 1.5);
		double straight = mob.getLocation().distance(target.getLocation());
		return route <= straight * maxDetour + 6;
	}

	/** A barrier other than {@code self} that the mob is currently touching (a broken one wins over an intact one), or null. */
	public Barrier barrierBeside(Barrier self, org.bukkit.entity.Mob mob)
	{
		Barrier intact = null;
		for(Barrier b : barriers)
		{
			if(b == self || !b.isBeside(mob))
				continue;
			if(b.getStage() >= 5)
				return b;
			if(intact == null)
				intact = b;
		}
		return intact;
	}

	/** How many barriers are completely broken right now (an open way in). */
	public int countBroken()
	{
		int n = 0;
		for(Barrier b : barriers)
			if(b.getStage() >= 5)
				n++;
		return n;
	}

	public Barrier getBarrier(SpawnPoint p)
	{
		for(Barrier b : barriers)
			for(SpawnPoint sp : b.getSpawnPoints())
				if(sp.getLocation().equals(p.getLocation()))
					return b;
		return null;
	}

	public void removeBarrier(Player player, Barrier barrier)
	{
		if(barriers.contains(barrier))
		{
			player.sendMessage(Colors.DARK_GREEN + "Barrier removed!");
			barrier.clearRepairSign();
			barriers.remove(barrier);
			GameManager.INSTANCE.saveAllGames();
		}
	}

	/** @return true if it was added (the arena is not in a game and none of its blocks belongs to another barrier) */
	public boolean addBarrier(Barrier barrier)
	{
		if(game.getStatus() == Game.GameStatus.DISABLED || game.getStatus() == Game.GameStatus.WAITING)
		{
			boolean same = false;
			for(Barrier b : barriers)
			{
				for(Block block : b.getBlocks())
				{
					if(barrier.hasBarrierLoc(block))
					{
						same = true;
						break;
					}
				}
			}
			if(!same)
			{
				barriers.add(barrier);
				GameManager.INSTANCE.saveAllGames();
				return true;
			}
		}
		return false;
	}

	public List<Barrier> getBarriers()
	{
		return barriers;
	}

	public int getTotalBarriers()
	{
		return barriers.size();
	}

	public Game getGame()
	{
		return game;
	}

	public String getNextBarrierNumber()
	{
		return Util.genRandId();
	}

	/**
	 * @return true if the entity is a zombie being held back at one of this game's barriers
	 */
	/** The barrier a zombie is held to (walking to it or breaking it), or null. */
	public Barrier heldBy(Entity ent)
	{
		for(Barrier b : barriers)
			if(b.isHeldBack(ent))
				return b;
		return null;
	}

	public boolean isHeldBack(Entity ent)
	{
		for(Barrier b : barriers)
			if(b.isHeldBack(ent))
				return true;
		return false;
	}

	public void refreshDamage()
	{
		for(Barrier b : barriers)
			b.refreshDamage();
	}

	public void unloadAllBarriers()
	{
		for(Barrier b : barriers)
			b.repairFull();
	}
}
