package com.theprogrammingturkey.comz.game.features;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.config.ConfigManager;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.PeacefulArenaMobs;

public class Barrier
{
	// A zombie must be next to a barrier block to break it: within this horizontal distance (in blocks) of the
	// block's centre, which covers touching it from the side or diagonally, and at about the same height
	private static final double BREAK_RANGE_SQUARED = 1.5 * 1.5;

	private final Map<Block, Material> blocks = new HashMap<>();
	/** Each block's full state (BlockUtils.dataOf), so it is put back exactly as built. Missing: the material alone. */
	private final Map<Block, String> blockData = new HashMap<>();
	private Location repairLoc;
	private BlockFace signFacing = BlockFace.NORTH;
	private final List<SpawnPoint> spawns = new ArrayList<>();

	private int stage;
	private long lastRepairNanos;
	private boolean breaking = false;
	// Lets an old breaking loop notice a newer one has replaced it, so two never run at once
	private int breakingLoopId = 0;

	private final String id;

	private final Game game;

	private int reward;

	private final java.util.Random random = new java.util.Random();

	private final List<Entity> ents = new ArrayList<>();
	// Per zombie: which side of the wall it spawned on, how many barriers were broken when it last decided what to do,
	// and when it last hit a player through the barrier. Cleaned up with the zombie.
	private final Map<Entity, double[]> zombieSides = new HashMap<>();
	private final Map<Entity, Integer> brokenSeen = new HashMap<>();
	private final Map<Entity, Long> lastBarrierHit = new HashMap<>();
	// Zombies from this barrier's spawns that are free to chase players
	private final Set<Entity> releasedZombies = new HashSet<>();
	/** The spot beside the barrier each held zombie has taken (its feet block), so they spread out instead of all piling onto the nearest one. */
	private final java.util.Map<Entity, Block> slots = new java.util.HashMap<>();
	/** When each zombie took its spot: its place in the line there is by this (first come, first at the boards). */
	private final Map<Entity, Long> slotOrder = new HashMap<>();
	private long slotSeq;
	/**
	 * The side zombies come from (NORTH, EAST, SOUTH or WEST), saved with the arena: they wait and break it only from there
	 * and climb through to the other side, which is the inside. Null: not known (an arena not checked yet, or a window the
	 * check could not decide; see BarrierManager.detectSides), and the old guess from where each zombie spawned is used.
	 */
	private BlockFace zombieSide;

	public BlockFace getZombieSide()
	{
		return zombieSide;
	}

	public void setZombieSide(BlockFace side)
	{
		this.zombieSide = side == BlockFace.NORTH || side == BlockFace.SOUTH || side == BlockFace.EAST || side == BlockFace.WEST ? side : null;
	}

	/** The stored side as {axis, direction, centre x, centre z} (see {@link #wallSide}), or null when there is none. */
	private double[] storedSide()
	{
		if(zombieSide == null || blocks.isEmpty())
			return null;
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for(Block b : blocks.keySet())
		{
			minX = Math.min(minX, b.getX() + 0.5);
			maxX = Math.max(maxX, b.getX() + 0.5);
			minZ = Math.min(minZ, b.getZ() + 0.5);
			maxZ = Math.max(maxZ, b.getZ() + 0.5);
		}
		boolean alongX = zombieSide == BlockFace.EAST || zombieSide == BlockFace.WEST;
		return new double[] {alongX ? 0 : 1, alongX ? zombieSide.getModX() : zombieSide.getModZ(), (minX + maxX) / 2, (minZ + maxZ) / 2};
	}

	/**
	 * Is this location on the zombies' side of the window (true when that is not known)? For choosing a window: a zombie
	 * on the inside never goes for one.
	 */
	public boolean isOnZombieSide(Location loc)
	{
		double[] side = storedSide();
		return side == null || sideOffset(side, loc) > 0;
	}

	/**
	 * The cells a zombie can stand in right beside this window on one side (the face given), at the window's floor or a
	 * block up or down: {x, cell y, z}. For the side check and to show the zombie side while editing.
	 */
	public List<int[]> cellsBeside(BlockFace face)
	{
		List<int[]> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		Map<Long, Double> floors = new HashMap<>();
		for(Block block : blocks.keySet())
			for(int dy = -1; dy <= 1; dy++)
			{
				Block feet = block.getRelative(face).getRelative(0, dy, 0);
				if(blocks.containsKey(feet) || !seen.add(com.theprogrammingturkey.comz.game.ZombiePathing.cellKey(feet.getX(), feet.getY(), feet.getZ())))
					continue;
				if(!Double.isNaN(com.theprogrammingturkey.comz.game.ZombiePathing.floorAt(feet.getWorld(), feet.getX(), feet.getY(), feet.getZ(), floors)))
					out.add(new int[] {feet.getX(), feet.getY(), feet.getZ()});
			}
		return out;
	}

	/** The faces a window can be crossed through: across a row of blocks, or any of four for a single column. */
	public List<BlockFace> crossingFaces()
	{
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for(Block b : blocks.keySet())
		{
			minX = Math.min(minX, b.getX());
			maxX = Math.max(maxX, b.getX());
			minZ = Math.min(minZ, b.getZ());
			maxZ = Math.max(maxZ, b.getZ());
		}
		if(maxX - minX > maxZ - minZ)
			return List.of(BlockFace.NORTH, BlockFace.SOUTH);
		if(maxZ - minZ > maxX - minX)
			return List.of(BlockFace.EAST, BlockFace.WEST);
		return List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
	}

	/**
	 * Which crossing face of this window a location is on (the face pointing towards it), for setting the zombie side by
	 * standing on it. Null when it is level with the window.
	 */
	public BlockFace faceTowards(Location loc)
	{
		double[] side = wallSide(loc.getX(), loc.getZ());
		if(side == null)
			return null;
		BlockFace f = side[0] == 0 ? (side[1] > 0 ? BlockFace.EAST : BlockFace.WEST) : (side[1] > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
		return crossingFaces().contains(f) ? f : null;
	}

	public Barrier(String id, Game game)
	{
		// Match repairFull(): untouched barriers must not offer a repair or award points.
		stage = -1;
		this.id = id;
		this.game = game;
	}

	public boolean damage()
	{
		// A fully broken barrier takes no more damage; without this the breaking loop
		// (which keeps running while its zombies are alive) would replay the break sound
		// every tick as long as a zombie lingers at, or walks past, the barrier.
		if(stage >= 5)
			return true;

		stage++;

		if(stage > 5)
			stage = 5;

		game.updateBarrierDamage(stage, blocks.keySet());

		if(stage >= 5)
		{
			for(Block b : blocks.keySet())
				BlockUtils.setBlockToAir(b);
			playAtBarrier(ConfigManager.getMainConfig().barrierBreakSound, 1f);
			return true;
		}
		else
		{
			// The per-hit damage sound is the bundled resource-pack snaps (mz:zombie_damage),
			// which pick a random clip each play. The config list is only a mute switch
			// (an empty list = no sound); clients without the pack hear nothing.
			List<Sound> sounds = ConfigManager.getMainConfig().barrierDamageSounds;
			if(sounds != null && !sounds.isEmpty())
				playAtBarrier("mz:zombie_damage", 1f);
			return false;
		}
	}

	public boolean repair(Player player)
	{
		if(stage <= -1)
			return true;
		long now = System.nanoTime();
		// Shared by all players and repair inputs: at most one repair per second.
		if(lastRepairNanos != 0 && now - lastRepairNanos < 1_000_000_000L)
			return false;
		lastRepairNanos = now;
		stage--;

		if(stage < -1)
			stage = -1;

		game.updateBarrierDamage(stage, blocks.keySet());
		// Every successful stage pays, including repeated repairs in the same round.
		int pay = effectiveReward();
		if(pay > 0)
		{
			PointManager.INSTANCE.addPoints(player, pay);
			PointManager.INSTANCE.notifyPlayer(player);
		}

		if(stage == -1)
			clearRepairSign();

		restoreMissing();
		// The repair sound is now the bundled resource-pack slams (mz:repair), which pick a
		// random clip each play. The config value is only a mute switch (empty = no sound).
		if(ConfigManager.getMainConfig().barrierRepairSound != null)
			playAtBarrier("mz:repair", 1f);
		return stage <= -1;
	}

	/**
	 * Plays a sound at a random barrier block so it's heard near the barrier itself.
	 * A null sound (empty config value) mutes the barrier.
	 */
	private void playAtBarrier(Sound sound, float pitch)
	{
		if(sound == null || blocks.isEmpty())
			return;
		List<Block> list = new ArrayList<>(blocks.keySet());
		Block b = list.get(random.nextInt(list.size()));
		game.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), sound, 1f, pitch);
	}

	/**
	 * Plays a custom resource-pack sound name (e.g. "mz:repair") at a random barrier block.
	 * Minecraft picks a random clip from the event's sound list each play, so a single call
	 * gives the variety. Clients without the pack simply hear nothing.
	 */
	private void playAtBarrier(String sound, float pitch)
	{
		if(sound == null || blocks.isEmpty())
			return;
		List<Block> list = new ArrayList<>(blocks.keySet());
		Block b = list.get(random.nextInt(list.size()));
		game.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), sound, 1f, pitch);
	}

	public void repairFull()
	{
		stage = -1;
		lastRepairNanos = 0;

		game.updateBarrierDamage(-1, blocks.keySet());

		restoreMissing();

		clearRepairSign();
		// The breaking loop is left running: it stops by itself once its zombies are dead (e.g. at game end),
		// and stopping it here would leave a barrier unbreakable after a mid-game Carpenter power-up.
	}

	/**
	 * Re-sends the crack animation. Clients drop a block's cracks after 20 seconds without an update,
	 * so without this they vanish during quiet periods like the break between rounds.
	 */
	/** Puts back the barrier's blocks that are gone (air), each exactly as it was built. */
	private void restoreMissing()
	{
		Map<Block, Material> missing = new HashMap<>();
		for(Map.Entry<Block, Material> e : blocks.entrySet())
			if(e.getKey().getType().isAir())
				missing.put(e.getKey(), e.getValue());
		BlockUtils.restoreBlocks(missing, blockData);
	}

	public void refreshDamage()
	{
		// 0 is an undamaged barrier and 5 has no blocks left to show cracks on
		if(stage > 0 && stage < 5)
			game.updateBarrierDamage(stage, blocks.keySet());
	}

	public void addBarrierBlock(Location loc)
	{
		Block block = loc.getBlock();
		this.addBarrierBlock(block, block.getType());
	}

	public void addBarrierBlock(Block block, Material mat)
	{
		blocks.put(block, mat);
		if(mat != null && block.getType() == mat)
			blockData.put(block, BlockUtils.dataOf(block));
	}

	/** The saved full state of a block (null if none), for saving. */
	public String getBlockData(Block b)
	{
		String d = blockData.get(b);
		return BlockUtils.parseData(d, blocks.get(b)) != null ? d : null;
	}

	public void setBlockData(Block b, String data)
	{
		if(data != null && !data.isEmpty())
			blockData.put(b, data);
	}

	public void removeBarrierBlock(Block block)
	{
		blocks.remove(block);
	}

	/**
	 * Takes the blocks as they are in the world right now, at the end of an edit: a block that was swapped for another one
	 * (a temporary block replaced by the real one) remembers the new material. A spot that is air now (a temporary block
	 * that was only there to click it) stays selected and stays air. Without this the material from the moment the block
	 * was clicked was kept, so temporary blocks came back on every repair and reload.
	 *
	 * @return how many selected spots are air
	 */
	public int syncWithWorld()
	{
		int air = 0;
		for(Map.Entry<Block, Material> e : blocks.entrySet())
		{
			Material now = e.getKey().getType();
			e.setValue(now.isAir() ? Material.AIR : now);
			if(!now.isAir())
				blockData.put(e.getKey(), BlockUtils.dataOf(e.getKey()));
			if(now.isAir())
				air++;
		}
		return air;
	}

	public void removeSpawnPoint(SpawnPoint sp)
	{
		spawns.remove(sp);
	}

	/** A copy of the block -> original material map, so the barrier editor can undo. */
	public Map<Block, Material> copyBlockMaterials()
	{
		return new HashMap<>(blocks);
	}

	/** Puts the barrier back to a state taken earlier (blocks, spawns, reward). */
	public void restore(Map<Block, Material> savedBlocks, List<SpawnPoint> savedSpawns, int savedReward)
	{
		blocks.clear();
		blocks.putAll(savedBlocks);
		spawns.clear();
		spawns.addAll(savedSpawns);
		reward = savedReward;
	}

	public List<Block> getBlocks()
	{
		return new ArrayList<>(blocks.keySet());
	}

	public boolean hasBarrierLoc(Block b)
	{
		return blocks.containsKey(b);
	}

	public Material getMaterial(Block b)
	{
		return blocks.get(b);
	}

	public int getStage()
	{
		return stage;
	}

	public void addSpawnPoints(List<SpawnPoint> sps)
	{
		spawns.addAll(sps);
	}

	public void addSpawnPoint(SpawnPoint sp)
	{
		spawns.add(sp);
	}

	public boolean hasSpawnPoint(SpawnPoint sp)
	{
		return spawns.contains(sp);
	}

	public List<SpawnPoint> getSpawnPoints()
	{
		return spawns;
	}

	public String getID()
	{
		return id;
	}

	public int getReward()
	{
		return reward;
	}

	/** Whether every barrier pays config.gameSettings.barrierReward instead of its own reward. */
	public static boolean rewardFromConfig()
	{
		return COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.barrierRewardFromConfig", true);
	}

	public static int configReward()
	{
		return COMZombies.getPlugin().getConfig().getInt("config.gameSettings.barrierReward", 10);
	}

	/** The points a repair stage actually pays: the config value when barrierRewardFromConfig is on, else its own. */
	public int effectiveReward()
	{
		return rewardFromConfig() ? configReward() : reward;
	}

	public void setReward(int reward)
	{
		this.reward = reward;
	}

	public Location getRepairLoc()
	{
		return repairLoc;
	}

	/** Old arenas may still contain a repair sign; new barriers have no sign location. */
	public void clearRepairSign()
	{
		if(repairLoc != null && BlockUtils.isSign(repairLoc.getBlock()))
			BlockUtils.setBlockToAir(repairLoc);
	}

	public void setRepairLoc(Location repairLoc)
	{
		this.repairLoc = repairLoc;
	}

	public BlockFace getSignFacing()
	{
		return signFacing;
	}

	public void setSignFacing(BlockFace signFacing)
	{
		if(signFacing == BlockFace.UP || signFacing == BlockFace.DOWN)
			signFacing = BlockFace.NORTH;
		this.signFacing = signFacing;
	}

	public Game getGame()
	{
		return game;
	}

	/**
	 * Called when a zombie spawns behind this barrier. Starts the breaking loop if it isn't already running.
	 */
	public void initBarrier(Entity ent)
	{
		ents.add(ent);
		// Which side of the wall is "outside" is judged from where THIS zombie spawned, so it works for any barrier, even one
		// with no spawn points set up. A zombie that ends up on the other side has got through. (null: spawned level with it.)
		Location at = ent.getLocation();
		zombieSides.put(ent, wallSide(at.getX(), at.getZ()));
		brokenSeen.put(ent, game.barrierManager.countBroken());
		if(!breaking)
		{
			breaking = true;
			int loopId = ++breakingLoopId;
			COMZombies.scheduleTask(60, () -> breakTick(loopId));
			COMZombies.scheduleTask(1, () -> steerTick(loopId));
		}
	}

	/**
	 * Zombies going for this barrier are held back (walked to it and kept off players) until they are through it,
	 * otherwise they chase the nearest player and can end up stuck at the wrong wall.
	 */
	public boolean isHeldBack(Entity ent)
	{
		return ents.contains(ent) && !releasedZombies.contains(ent);
	}

	/**
	 * Runs every half second alongside {@link #breakTick}, walking held back zombies to this barrier.
	 */
	private void steerTick(int loopId)
	{
		if(!breaking || loopId != breakingLoopId)
			return;

		// Re-deciding which barrier asks the pathfinder, so only a few zombies do it per pass (each every couple of seconds)
		int rechecks = 3;
		for(Entity ent : new ArrayList<>(ents))
		{
			if(!(ent instanceof Mob) || ent.isDead() || releasedZombies.contains(ent))
				continue;
			Mob mob = (Mob) ent;

			// A zombie is held to its barrier until it is through it: it never goes for a player before that (players within
			// reach still get hit across the barrier, below). Through means on the far side of the wall, which it can only
			// reach once the barrier is broken (or by some other open way).
			double[] side = sideFor(mob);
			boolean atOwn = isAtBarrier(mob);
			double offset = side != null ? sideOffset(side, mob.getLocation()) : Double.NaN;
			if(zombieSide != null)
			{
				// Its side is known: once past the middle of the wall, right at the window, it is inside, and that is final. It
				// never claws at a window from the inside, and never goes back out to one. (Only right at it: further away,
				// which side of a straight line through the window it is on says nothing, the way can lead round the end of
				// the wall. Away from it, it counts as inside when it can walk to a player without a window.)
				if(offset < -0.6 && isNear(mob, 2.5))
				{
					release(mob);
					continue;
				}
			}
			else
			{
				// (An unchecked window: the old rules, sides guessed from where it spawned)
				boolean pastWall = side != null && offset < -0.75;
				if(pastWall && !atOwn)
				{
					release(mob);
					continue;
				}
				if(stage >= 5 && (atOwn || (side != null && offset < 0.75)))
				{
					release(mob);
					continue;
				}
			}

			// The player moved: if another barrier now makes the way to them clearly shorter, go for that one instead
			// (not while it is climbing through this one)
			boolean climbing = zombieSide != null && stage >= 5 && atOwn;
			if(!climbing && rechecks > 0 && (mob.getTicksLived() / 10) % 4 == (Math.abs(mob.getUniqueId().hashCode()) % 4))
			{
				rechecks--;
				// Already inside (it got in some other way): it hunts, it doesn't go back out to its window
				if(zombieSide != null && offset < -0.6 && game.barrierManager.hasOpenWay(mob))
				{
					release(mob);
					continue;
				}
				Barrier better = game.barrierManager.betterBarrier(mob, this, atOwn);
				if(better != null)
				{
					handOff(mob, better);
					continue;
				}
			}

			// A player within reach of the zombie gets hit, even with the barrier between them (not while it's shocked).
			// (A zombie caught on something on its way is put back on its route by ZombieBrain, which logs it.)
			if(mob.hasAI())
				hurtPlayerInReach(mob);

		}

		COMZombies.scheduleTask(10, () -> steerTick(loopId));
	}

	/** Is the entity touching this barrier (within its break range)? */
	public boolean isBeside(Entity ent)
	{
		return isAtBarrier(ent);
	}

	/** Drops the zombie from this barrier (it is being respawned somewhere else). */
	public void forget(Entity ent)
	{
		ents.remove(ent);
		slots.remove(ent);
		slotOrder.remove(ent);
		zombieSides.remove(ent);
		brokenSeen.remove(ent);
		lastBarrierHit.remove(ent);
		releasedZombies.remove(ent);
	}

	/** Passes a held zombie over to another barrier that it is standing at, so that one gets broken. */
	private void handOff(Mob mob, Barrier other)
	{
		ents.remove(mob);
		slots.remove(mob);
		slotOrder.remove(mob);
		zombieSides.remove(mob);
		brokenSeen.remove(mob);
		lastBarrierHit.remove(mob);
		other.initBarrier(mob);
	}

	private void release(Mob mob)
	{
		slots.remove(mob);
		slotOrder.remove(mob);
		releasedZombies.add(mob);
	}

	/** A place to wait beside the window: the cell (what zombies share as "their spot") and where its floor really is. */
	private record Spot(Block cell, Location stand) {}

	/** How near its spot (blocks) a zombie has to come to take its place in the line there. */
	private static final double ARRIVED = 2.5;

	/**
	 * Where the zombie is walking to at this window. Broken, with its zombie side known: through it, to the inside cell
	 * nearest it (once past the middle of the wall it is released to hunt). Otherwise its spot beside the window on its
	 * own side, and its place in the line there.
	 */
	public Location approachFor(Mob mob)
	{
		if(zombieSide != null && stage >= 5)
		{
			Location in = climbTarget(mob);
			if(in != null)
				return in;
		}
		List<Spot> spots = ownSideSpots(mob);
		if(spots.isEmpty())
			return getNearestBlockCenter(mob);
		// Each zombie keeps the spot it took; a new one takes the spot with the fewest zombies at it (the nearest of those)
		slots.keySet().removeIf(e -> !ents.contains(e) || releasedZombies.contains(e) || e.isDead());
		slotOrder.keySet().retainAll(slots.keySet());
		Block mine = slots.get(mob);
		Spot spot = null;
		if(mine != null)
			for(Spot sp : spots)
				if(sp.cell().equals(mine))
					spot = sp;
		if(spot == null)
		{
			int fewest = Integer.MAX_VALUE;
			for(Spot sp : spots)
			{
				int n = 0;
				for(Block b : slots.values())
					if(b.equals(sp.cell()))
						n++;
				if(n < fewest)
				{
					fewest = n;
					spot = sp;
				}
			}
			slots.put(mob, spot.cell());
			slotOrder.remove(mob);
		}
		// Its place in the line: first come, first at the boards. The order is taken once, when it gets near the spot, and
		// never changes (worked out from distances every tick, two zombies swapped places back and forth, and in a narrow way
		// in they shoved each other with nobody at the boards). One still on its way queues behind all that are there.
		Location me = mob.getLocation();
		if(!slotOrder.containsKey(mob) && flatDistSquared(me, spot.stand()) < ARRIVED * ARRIVED)
			slotOrder.put(mob, ++slotSeq);
		long myOrder = slotOrder.getOrDefault(mob, Long.MAX_VALUE);
		int rank = 0;
		for(java.util.Map.Entry<Entity, Block> e : slots.entrySet())
		{
			if(e.getKey() == mob || !e.getValue().equals(spot.cell()))
				continue;
			long other = slotOrder.getOrDefault(e.getKey(), Long.MAX_VALUE);
			if(other < myOrder || (other == myOrder && e.getKey().getEntityId() < mob.getEntityId()))
				rank++;
		}
		Location base = spot.stand();
		double[] at = standSpot(base.getX(), base.getZ(), sideFor(mob), rank, mob.getEntityId());
		Location stand = new Location(base.getWorld(), at[0], base.getY(), at[1]);
		// Behind it may be a wall or a drop: then as near the barrier as there is room
		while(rank > 0 && !standableAt(stand))
		{
			rank--;
			at = standSpot(base.getX(), base.getZ(), sideFor(mob), rank, mob.getEntityId());
			stand = new Location(base.getWorld(), at[0], base.getY(), at[1]);
		}
		return stand;
	}

	/** The inside cell (the side away from the zombies) beside this window nearest the zombie, at its real floor; or null. */
	private Location climbTarget(Mob mob)
	{
		Location best = null;
		double bestDist = Double.MAX_VALUE;
		for(int[] c : cellsBeside(zombieSide.getOppositeFace()))
		{
			double floor = com.theprogrammingturkey.comz.game.ZombiePathing.floorAt(mob.getWorld(), c[0], c[1], c[2], new HashMap<>());
			Location l = new Location(mob.getWorld(), c[0] + 0.5, floor, c[2] + 0.5);
			double d = l.distanceSquared(mob.getLocation());
			if(d < bestDist)
			{
				bestDist = d;
				best = l;
			}
		}
		return best;
	}

	private static double flatDistSquared(Location a, Location b)
	{
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** Can a zombie stand at this point (its block, at the height of its feet: real floors, snow and slabs included)? */
	private static boolean standableAt(Location stand)
	{
		int cy = com.theprogrammingturkey.comz.game.ZombiePathing.cellOf(stand.getY());
		return !Double.isNaN(com.theprogrammingturkey.comz.game.ZombiePathing.floorAt(stand.getWorld(), stand.getBlockX(), cy, stand.getBlockZ(), new HashMap<>()));
	}

	/**
	 * Where a zombie waiting at a spot beside the barrier stands (x, z): the first one (rank 0) at the spot, a little to
	 * one side or the other so two never stand on the same point; each next one 0.9 blocks further back from the wall
	 * ({@code side}: see {@link #spawnSide}, null when not known: then they spread along x), alternating sides.
	 */
	static double[] standSpot(double x, double z, double[] side, int rank, int salt)
	{
		double jitter = (Math.floorMod(salt * 37, 5) - 2) / 10.0; // -0.2 .. 0.2
		double lateral = rank == 0 ? jitter : (rank % 2 == 1 ? 0.35 : -0.35) + jitter * 0.3;
		// The first right up against the barrier (its spot's middle is a block from the barrier's; mobs are stopped at the
		// barrier block's edge, half a block away, so 0.15 in still leaves room), the others 0.9 apart behind it
		double back = rank * 0.9 - 0.15;
		if(side == null)
			// Which way is back isn't known (it could be into the wall): side by side instead
			return new double[] {x + jitter * 0.5 + (rank + 1) / 2 * 0.6 * (rank % 2 == 1 ? 1 : -1), z};
		boolean acrossX = side[0] == 0; // the wall is crossed along x: back is along x, sideways along z
		double dir = side[1];
		return acrossX ? new double[] {x + back * dir, z + lateral} : new double[] {x + lateral, z + back * dir};
	}

	/**
	 * Places to wait beside this barrier on the zombie's own side, nearest first. With the zombie side known: the cells in
	 * front of the window on that side. Otherwise (an unchecked window): in front of each block on every side, then only
	 * those on the side the zombie spawned on.
	 */
	private List<Spot> ownSideSpots(Mob mob)
	{
		List<Spot> spots = new ArrayList<>();
		org.bukkit.World world = mob.getWorld();
		Map<Long, Double> floors = new HashMap<>();
		List<BlockFace> faces = zombieSide != null ? List.of(zombieSide) : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
		for(BlockFace face : faces)
			for(int[] c : cellsBeside(face))
			{
				double floor = com.theprogrammingturkey.comz.game.ZombiePathing.floorAt(world, c[0], c[1], c[2], floors);
				spots.add(new Spot(world.getBlockAt(c[0], c[1], c[2]), new Location(world, c[0] + 0.5, floor, c[2] + 0.5)));
			}
		// Only the zombie's own side: if the way to the near side fails for a moment, the next nearest spot used to be one on
		// the player's side, reached by walking through some other open barrier.
		double[] side = sideFor(mob);
		if(zombieSide == null && side != null)
		{
			List<Spot> behind = new ArrayList<>();
			for(Spot sp : spots)
				if(sideOffset(side, sp.stand()) > 0.5)
					behind.add(sp);
			if(!behind.isEmpty())
				spots = behind;
		}
		spots.sort(Comparator.comparingDouble(sp -> sp.stand().distanceSquared(mob.getLocation())));
		return spots;
	}

	/**
	 * Works out which side of the barrier the zombies spawn on: {axis (0 = x, 1 = z) across the wall, direction
	 * (+1/-1) towards the spawn side, centre x, centre z}. Null when it can't be told (no spawn points, or the spawns
	 * sit level with the wall).
	 */
	private double[] spawnSide()
	{
		if(blocks.isEmpty() || spawns.isEmpty())
			return null;
		double sx = 0, sz = 0;
		for(SpawnPoint sp : spawns)
		{
			sx += sp.getLocation().getX();
			sz += sp.getLocation().getZ();
		}
		return wallSide(sx / spawns.size(), sz / spawns.size());
	}

	/** The sides of this wall judged from a reference point (e.g. where a zombie spawned). Null if it can't be told. */
	private double[] wallSide(double refX, double refZ)
	{
		if(blocks.isEmpty())
			return null;
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for(Block b : blocks.keySet())
		{
			minX = Math.min(minX, b.getX() + 0.5);
			maxX = Math.max(maxX, b.getX() + 0.5);
			minZ = Math.min(minZ, b.getZ() + 0.5);
			maxZ = Math.max(maxZ, b.getZ() + 0.5);
		}
		return wallSide(minX, maxX, minZ, maxZ, refX, refZ);
	}

	/**
	 * Pure geometry behind the side tests: {axis (0 = x, 1 = z) across the wall, direction (+1/-1) towards the reference
	 * point, centre x, centre z} for a wall whose block centres span minX..maxX and minZ..maxZ. Null when the reference
	 * point is level with the wall (can't tell the sides apart).
	 */
	static double[] wallSide(double minX, double maxX, double minZ, double maxZ, double refX, double refZ)
	{
		double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
		double sx = refX - cx, sz = refZ - cz;
		// A wall that is longer along x is crossed along z, and the other way round; a single block or a square
		// barrier is crossed along whichever axis the reference point is further out on.
		int axis;
		if(maxX - minX > maxZ - minZ)
			axis = 1;
		else if(maxZ - minZ > maxX - minX)
			axis = 0;
		else
			axis = Math.abs(sx) >= Math.abs(sz) ? 0 : 1;
		double toward = axis == 0 ? sx : sz;
		if(Math.abs(toward) < 0.5)
			return null;
		return new double[] {axis, Math.signum(toward), cx, cz};
	}

	/** The side data for this zombie: judged from where it spawned, else from the barrier's spawn points. */
	private double[] sideFor(Entity ent)
	{
		if(zombieSide != null)
			return storedSide();
		double[] own = zombieSides.get(ent);
		return own != null ? own : spawnSide();
	}

	/** How far a location is from the barrier on the spawn side (negative = on the far side, near 0 = in the wall). */
	private static double sideOffset(double[] side, Location loc)
	{
		return sideOffset(side, loc.getX(), loc.getZ());
	}

	static double sideOffset(double[] side, double x, double z)
	{
		double offset = side[0] == 0 ? x - side[2] : z - side[3];
		return offset * side[1];
	}

	/**
	 * A zombie held at a barrier still hurts a player who is within reach of it (config.gameSettings.barrierAttackRange),
	 * with the barrier in between, about once a second. Its AI target stays off so it keeps working on the barrier.
	 */
	private void hurtPlayerInReach(Mob mob)
	{
		double reach = COMZombies.getPlugin().getConfig().getDouble("config.gameSettings.barrierAttackRange", 2.2);
		if(reach <= 0)
			return;
		long now = System.nanoTime();
		if(now - lastBarrierHit.getOrDefault(mob, 0L) < 1_000_000_000L)
			return;
		Player nearest = null;
		double best = reach * reach;
		Location zombie = mob.getLocation().add(0, 0.9, 0);
		for(Player pl : game.getPlayersInGame())
		{
			if(!pl.isOnline() || pl.isDead() || !pl.getWorld().equals(mob.getWorld())
					|| pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR
					|| game.downedPlayerManager.isDownedPlayer(pl))
				continue;
			double d = pl.getLocation().add(0, 0.9, 0).distanceSquared(zombie);
			if(d <= best)
			{
				best = d;
				nearest = pl;
			}
		}
		if(nearest == null)
			return;
		// Through this barrier's own boards (as in the original), but not through or over any other block or BlockBlocker barrier
		if(!com.theprogrammingturkey.comz.game.ZombiePathing.canHit(mob, nearest, blocks.keySet()))
			return;
		lastBarrierHit.put(mob, now);
		PeacefulArenaMobs.attackPlayer(mob, nearest);
	}

	public Location getNearestBlockCenter(Entity ent)
	{
		Location nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for(Block block : blocks.keySet())
		{
			Location center = block.getLocation().add(0.5, 0, 0.5);
			double dist = center.distanceSquared(ent.getLocation());
			if(dist < nearestDist)
			{
				nearest = center;
				nearestDist = dist;
			}
		}
		return nearest;
	}

	/**
	 * Runs every 3 seconds while this barrier's zombies are alive. It only does damage when one of them is actually
	 * at the barrier; it used to damage it as long as they were alive, wherever they were. It also keeps running
	 * after the barrier is fully broken, so zombies start breaking it again if players repair it.
	 */
	private void breakTick(int loopId)
	{
		if(!breaking || loopId != breakingLoopId)
			return;

		ents.removeIf(entity -> entity == null || entity.isDead());
		releasedZombies.removeIf(entity -> entity == null || entity.isDead());
		zombieSides.keySet().removeIf(entity -> entity == null || entity.isDead());
		brokenSeen.keySet().removeIf(entity -> entity == null || entity.isDead());
		lastBarrierHit.keySet().removeIf(entity -> entity == null || entity.isDead());
		if(ents.isEmpty())
		{
			breaking = false;
			return;
		}

		// Only zombies still held outside it break it: ones already through (released) walking past it from inside don't
		if(ents.stream().anyMatch(this::isClawing))
		{
			// The zombies at it claw at the boards: an arm swing for everyone, and "mz_swing" (the tick) tells
			// TacticalMovementPlugin to play the claw animation on the BetterModel zombie for players without the mod.
			if(stage < 5)
				for(Entity entity : ents)
					if(entity instanceof org.bukkit.entity.LivingEntity living && isClawing(entity))
					{
						living.swingMainHand();
						if(living instanceof org.bukkit.entity.Mob m)
							game.spawnManager.markActive(m);
						living.setMetadata("mz_swing", new org.bukkit.metadata.FixedMetadataValue(COMZombies.getPlugin(), org.bukkit.Bukkit.getCurrentTick()));
					}
			damage();
		}

		COMZombies.scheduleTask(60, () -> breakTick(loopId));
	}

	/**
	 * Places a zombie can stand right beside this barrier's blocks, nearest to the given location first: on the zombie side
	 * when that is known, otherwise either side. At their real floor heights.
	 */
	public List<Location> approachLocations(Location from)
	{
		List<Location> out = new ArrayList<>();
		Map<Long, Double> floors = new HashMap<>();
		List<BlockFace> faces = zombieSide != null ? List.of(zombieSide) : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
		for(BlockFace face : faces)
			for(int[] c : cellsBeside(face))
				out.add(new Location(from.getWorld(), c[0] + 0.5,
						com.theprogrammingturkey.comz.game.ZombiePathing.floorAt(from.getWorld(), c[0], c[1], c[2], floors), c[2] + 0.5));
		out.sort(Comparator.comparingDouble(loc -> loc.distanceSquared(from)));
		return out;
	}

	/** Is the entity within this many blocks (horizontally, and about level) of any block of this barrier? */
	public boolean isNear(Entity ent, double range)
	{
		Location loc = ent.getLocation();
		for(Block block : blocks.keySet())
		{
			if(!loc.getWorld().equals(block.getWorld()))
				continue;
			double dx = loc.getX() - (block.getX() + 0.5);
			double dz = loc.getZ() - (block.getZ() + 0.5);
			double dy = loc.getY() - block.getY();
			if(dx * dx + dz * dz <= range * range && dy >= -1 && dy <= 1)
				return true;
		}
		return false;
	}

	/** Held to it, at it, and on the zombies' side of it: only then does a zombie break it (never from the inside). */
	private boolean isClawing(Entity ent)
	{
		if(!isHeldBack(ent) || !isAtBarrier(ent))
			return false;
		double[] side = storedSide();
		return side == null || sideOffset(side, ent.getLocation()) > -0.2;
	}

	private boolean isAtBarrier(Entity ent)
	{
		Location loc = ent.getLocation();
		for(Block block : blocks.keySet())
		{
			if(!loc.getWorld().equals(block.getWorld()))
				continue;
			double dx = loc.getX() - (block.getX() + 0.5);
			double dz = loc.getZ() - (block.getZ() + 0.5);
			double dy = loc.getY() - block.getY();
			if(dx * dx + dz * dz <= BREAK_RANGE_SQUARED && dy >= -1 && dy <= 1)
				return true;
		}
		return false;
	}
}
