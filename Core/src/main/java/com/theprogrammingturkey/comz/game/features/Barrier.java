package com.theprogrammingturkey.comz.game.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.signs.SignText;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.PaperPathfinding;

public class Barrier
{
	// A zombie must be next to a barrier block to break it: within this horizontal distance (in blocks) of the
	// block's centre, which covers touching it from the side or diagonally, and at about the same height
	private static final double BREAK_RANGE_SQUARED = 1.5 * 1.5;
	// A held back zombie is let go to fight if a player gets this close (in blocks)
	private static final double PLAYER_RELEASE_RANGE_SQUARED = 4 * 4;

	private final Map<Block, Material> blocks = new HashMap<>();
	private Location repairLoc;
	private BlockFace signFacing;
	private final List<SpawnPoint> spawns = new ArrayList<>();

	private int stage;
	private boolean breaking = false;
	// Lets an old breaking loop notice a newer one has replaced it, so two never run at once
	private int breakingLoopId = 0;

	private final String id;

	private final Game game;

	private int reward;

	private final List<Entity> ents = new ArrayList<>();
	// Zombies from this barrier's spawns that are free to chase players
	private final Set<Entity> releasedZombies = new HashSet<>();
	private final HashMap<Player, Integer> earnedPoints = new HashMap<>();

	public Barrier(String id, Game game)
	{
		stage = 0;
		this.id = id;
		this.game = game;
	}

	public boolean damage()
	{
		stage++;

		if(stage > 5)
			stage = 5;

		game.updateBarrierDamage(stage, blocks.keySet());

		if(stage >= 5)
		{
			for(Block b : blocks.keySet())
				BlockUtils.setBlockToAir(b);
			return true;
		}
		else
		{
			if(stage > -1)
			{
				Block block = repairLoc.getBlock();
				block.setType(Material.ACACIA_WALL_SIGN);
				BlockData blockData = block.getBlockData();
				((Directional) blockData).setFacing(signFacing);
				block.setBlockData(blockData);
				Sign sign = (Sign) block.getState();
				SignText.apply(sign, SignText.render("barrier"));
			}
			return false;
		}
	}

	public boolean repair(Player player)
	{
		stage--;

		if(stage < -1)
			stage = -1;

		game.updateBarrierDamage(stage, blocks.keySet());
		int pointsEarned = earnedPoints.getOrDefault(player, 0);
		//TODO: Make configurable
		if(pointsEarned < reward * 6)
		{
			earnedPoints.put(player, pointsEarned + reward);
			PointManager.INSTANCE.addPoints(player, reward);
			PointManager.INSTANCE.notifyPlayer(player);
		}

		if(stage == -1)
			BlockUtils.setBlockToAir(repairLoc);

		for(Block b : blocks.keySet())
			if(game.getWorld().getBlockAt(b.getLocation()).getType().equals(Material.AIR))
				BlockUtils.setBlockTypeHelper(game.getWorld().getBlockAt(b.getLocation()), blocks.get(b));
		return stage <= -1;
	}

	public void repairFull()
	{
		stage = -1;

		game.updateBarrierDamage(-1, blocks.keySet());

		for(Block b : blocks.keySet())
			if(b.getType().equals(Material.AIR))
				BlockUtils.setBlockTypeHelper(b, blocks.get(b));

		BlockUtils.setBlockToAir(repairLoc);
		// The breaking loop is left running: it stops by itself once its zombies are dead (e.g. at game end),
		// and stopping it here would leave a barrier unbreakable after a mid-game Carpenter power-up.
	}

	public void resetEarnedPoints()
	{
		earnedPoints.replaceAll((p, v) -> 0);
	}

	/**
	 * Re-sends the crack animation. Clients drop a block's cracks after 20 seconds without an update,
	 * so without this they vanish during quiet periods like the break between rounds.
	 */
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

	public void setReward(int reward)
	{
		this.reward = reward;
	}

	public Location getRepairLoc()
	{
		return repairLoc;
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
		if(!breaking)
		{
			breaking = true;
			int loopId = ++breakingLoopId;
			COMZombies.scheduleTask(60, () -> breakTick(loopId));
			if(PaperPathfinding.isAvailable())
				COMZombies.scheduleTask(1, () -> steerTick(loopId));
		}
	}

	/**
	 * Zombies from this barrier's spawns are held back (walked to the barrier and kept off players) until the
	 * barrier is broken, otherwise they chase the nearest player and can end up stuck at the wrong wall.
	 */
	public boolean isHeldBack(Entity ent)
	{
		return PaperPathfinding.isAvailable() && ents.contains(ent) && !releasedZombies.contains(ent);
	}

	/**
	 * Runs every half second alongside {@link #breakTick}, walking held back zombies to this barrier.
	 */
	private void steerTick(int loopId)
	{
		if(!breaking || loopId != breakingLoopId)
			return;

		for(Entity ent : new ArrayList<>(ents))
		{
			if(!(ent instanceof Mob) || ent.isDead() || releasedZombies.contains(ent))
				continue;
			Mob mob = (Mob) ent;

			if(stage >= 5 || isPlayerNear(mob))
			{
				release(mob);
				continue;
			}

			// Undo any target the plugin gave it (e.g. after being shot) and keep it walking to the barrier
			mob.setTarget(null);
			if(!isAtBarrier(mob))
				PaperPathfinding.moveTo(mob, getNearestBlockCenter(mob));
		}

		COMZombies.scheduleTask(10, () -> steerTick(loopId));
	}

	private void release(Mob mob)
	{
		releasedZombies.add(mob);
		mob.setTarget(game.spawnManager.getNearestPlayer(mob));
	}

	private boolean isPlayerNear(Entity ent)
	{
		for(Player player : game.getPlayersInGame())
			if(player.getWorld().equals(ent.getWorld()) && player.getLocation().distanceSquared(ent.getLocation()) <= PLAYER_RELEASE_RANGE_SQUARED)
				return true;
		return false;
	}

	private Location getNearestBlockCenter(Entity ent)
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

		ents.removeIf(Entity::isDead);
		releasedZombies.removeIf(Entity::isDead);
		if(ents.isEmpty())
		{
			breaking = false;
			return;
		}

		if(ents.stream().anyMatch(this::isAtBarrier))
			damage();

		COMZombies.scheduleTask(60, () -> breakTick(loopId));
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
