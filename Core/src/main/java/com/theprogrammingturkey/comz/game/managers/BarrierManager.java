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
			String side = CustomConfig.getString(barrierJson, "zombie_side", "");
			if(!side.isEmpty())
			{
				try
				{
					barrier.setZombieSide(BlockFace.valueOf(side));
				}
				catch(IllegalArgumentException e)
				{
					COMZombies.log.warning("Barrier " + barrierID + ": unknown zombie_side " + side + ", ignored");
				}
			}

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
			if(barrier.getZombieSide() != null)
				barrierJson.addProperty("zombie_side", barrier.getZombieSide().name());
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
			if(b.getBlocks().isEmpty() || toBarrier(at, b) > seek || !isOpenToPlayers(b) || !entryFor(b, at))
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

	/**
	 * May a zombie use this window as its way in at all? A window whose side is not known is used only while no window of
	 * the arena has one (an arena not checked yet: the old guesses); once the arena is checked, a window the check could not
	 * decide is not a way in until its side is set in barrier edit ("side"). Whether the zombie can get to the window's
	 * zombie side is the planner's to say (canWalkUpTo walks to that side only): which side of a straight line through the
	 * window it is on says nothing (a way round the end of the wall leads to the other side).
	 */
	private boolean entryFor(Barrier b, Location at)
	{
		return b.getZombieSide() != null || !sidesKnown();
	}

	/** Can the zombie walk to its nearest player without going through any window (it is inside already)? */
	public boolean hasOpenWay(org.bukkit.entity.Mob mob)
	{
		Player player = game.spawnManager.getNearestPlayer(mob);
		return player != null && openWayLength(mob, player.getLocation()) >= 0;
	}

	/** Has any window of this arena got its zombie side? */
	public boolean sidesKnown()
	{
		for(Barrier b : barriers)
			if(b.getZombieSide() != null)
				return true;
		return false;
	}

	/**
	 * Works out each window's zombie side from the map: every place players can walk to from the player spawn(s) and the
	 * teleporter arrivals, through doors (players open them) but never through a window, is inside. A window whose cells on
	 * one face are all outside, while the opposite face has inside cells, has its zombies on that first face. Windows that
	 * can't be told (both faces reachable by players, or neither) get no side and are listed.
	 *
	 * @param onlyMissing leave windows that already have a side as they are
	 * @param only        just this window (null: all)
	 * @return the windows that could not be decided
	 */
	public List<String> detectSides(boolean onlyMissing, Barrier only)
	{
		List<String> unclear = new ArrayList<>();
		org.bukkit.World world = game.getWorld();
		if(world == null || game.arena == null || game.arena.getMin() == null || game.arena.getMax() == null)
			return unclear;
		List<Location> seeds = new ArrayList<>();
		if(game.arena.getPlayerTPLocation() != null)
			seeds.add(game.arena.getPlayerTPLocation());
		if(game.arena.getSecondPlayerTPLocation() != null)
			seeds.add(game.arena.getSecondPlayerTPLocation());
		if(game.linkManager.getMainframe() != null)
			seeds.add(game.linkManager.getMainframe().arrival(world));
		for(var link : game.linkManager.getLinks())
			seeds.add(link.arrival(world));
		if(seeds.isEmpty())
			return unclear;
		java.util.Set<Block> walls = new java.util.HashSet<>();
		for(Barrier b : barriers)
			walls.addAll(b.getBlocks());
		java.util.Set<Block> doors = new java.util.HashSet<>();
		for(com.theprogrammingturkey.comz.game.features.Door door : game.doorManager.getDoors())
			doors.addAll(door.getBlocks());
		// The planner counts unloaded chunks as solid: load the arena's
		Location lo = game.arena.getMin(), hi = game.arena.getMax();
		for(int cx = (Math.min(lo.getBlockX(), hi.getBlockX()) - 2) >> 4; cx <= (Math.max(lo.getBlockX(), hi.getBlockX()) + 2) >> 4; cx++)
			for(int cz = (Math.min(lo.getBlockZ(), hi.getBlockZ()) - 2) >> 4; cz <= (Math.max(lo.getBlockZ(), hi.getBlockZ()) + 2) >> 4; cz++)
				world.getChunkAt(cx, cz);
		int maxDrop = Math.max(3, Math.min(32, COMZombies.getPlugin().getConfig().getInt("config.gameSettings.zombieSafeFallDistance", 16)));
		java.util.Set<Long> inside = com.theprogrammingturkey.comz.game.ZombiePathing.reachable(world, seeds, game.arena.getMin(),
				game.arena.getMax(), walls, doors, maxDrop);
		for(Barrier b : barriers)
		{
			if((only != null && b != only) || (onlyMissing && b.getZombieSide() != null) || b.getBlocks().isEmpty())
				continue;
			BlockFace found = null;
			int candidates = 0;
			for(BlockFace face : b.crossingFaces())
			{
				int in = 0, out = 0, opposite = 0;
				for(int[] c : b.cellsBeside(face))
				{
					if(inside.contains(com.theprogrammingturkey.comz.game.ZombiePathing.cellKey(c[0], c[1], c[2])))
						in++;
					else
						out++;
				}
				for(int[] c : b.cellsBeside(face.getOppositeFace()))
					if(inside.contains(com.theprogrammingturkey.comz.game.ZombiePathing.cellKey(c[0], c[1], c[2])))
						opposite++;
				if(in == 0 && out > 0 && opposite > 0)
				{
					found = face;
					candidates++;
				}
			}
			b.setZombieSide(candidates == 1 ? found : null);
			if(candidates != 1)
				unclear.add(b.getID());
		}
		return unclear;
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
	 * nearest player shortest (see {@link #routeCost}), and that it can actually walk up to (checked with MZ's planner).
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
				if(!b.getBlocks().isEmpty() && b.getBlocks().get(0).getWorld().equals(at.getWorld()) && isOpenToPlayers(b) && entryFor(b, at))
					ranked.add(b);
			if(ranked.isEmpty())
				return null;
		}
		java.util.Map<Barrier, Double> cost = new java.util.HashMap<>();
		for(Barrier b : ranked)
			cost.put(b, routeCost(at, b, player));
		ranked.sort(java.util.Comparator.comparingDouble(cost::get));

		// (A zombie with an open way to the player already went for the player above, so there is no open-way choice to make
		// here; the check that used to be here ran another full path search per spawn and could never pass.)

		// The best one it can really walk up to (to its zombie side). None: no window for it, rather than one it can't get to,
		// where it would stand for good; its spawn is a dead end for now and is not used for a while.
		int tried = 0;
		for(Barrier b : ranked)
		{
			if(tried++ >= 12)
				break;
			if(canWalkUpTo(mob, b))
				return b;
		}
		if(spawn != null)
			game.spawnManager.markDeadEnd(spawn, "its zombies can't walk to any open window or player");
		return null;
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
		if(best != null && !canWalkUpTo(mob, best))
			return null;
		return best;
	}

	/** Is there a route for the mob to a spot beside the barrier (MZ's planner, the one it walks with)? */
	private boolean canWalkUpTo(org.bukkit.entity.Mob mob, Barrier barrier)
	{
		int tried = 0;
		for(Location approach : barrier.approachLocations(mob.getLocation()))
		{
			if(tried++ >= 4)
				break;
			if(com.theprogrammingturkey.comz.game.ZombiePathing.routeLength(mob, approach, null) >= 0)
				return true;
		}
		return false;
	}

	private final java.util.Random random = new java.util.Random();

	/** Is there a route that really gets the mob to the location (MZ's planner: BlockBlocker barriers are solid)? */
	private static boolean reallyReaches(org.bukkit.entity.Mob mob, Location to)
	{
		return com.theprogrammingturkey.comz.game.ZombiePathing.routeLength(mob, to, null) >= 0;
	}

	/**
	 * How long the zombie's own walk to the location is when it needs no barrier at all: a route (MZ's planner) that does
	 * not go through any barrier of this arena, broken or not, nor any BlockBlocker barrier.
	 *
	 * @return the length in blocks, or -1 when there is no such route
	 */
	private double openWayLength(org.bukkit.entity.Mob mob, Location to)
	{
		java.util.Set<Block> barrierBlocks = new java.util.HashSet<>();
		for(Barrier b : barriers)
			barrierBlocks.addAll(b.getBlocks());
		return com.theprogrammingturkey.comz.game.ZombiePathing.routeLength(mob, to, barrierBlocks);
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
