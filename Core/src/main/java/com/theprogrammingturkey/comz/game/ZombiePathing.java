package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The arena mobs' route planner and line-of-reach checks, used by {@link ZombieBrain} (which walks every arena mob) and by
 * BarrierManager (which barrier a zombie can get to).
 * <ul>
 * <li>Routes ({@link #search}, {@link #routeLength}): A* over the blocks. A mob stands where its feet and head are free and
 * the block below is solid (fences and walls, 1.5 high, are not stood on); it steps up one block and drops down as far as
 * config.gameSettings.zombieSafeFallDistance. BlockBlocker barriers are solid like stone: never gone through, but their
 * tops can be stood on.</li>
 * <li>Reach ({@link #canHit}, {@link #arrivesAt}): nothing at all between a mob and a player (fences, walls, slabs,
 * barriers all count as full).</li>
 * </ul>
 * In the arena worlds BlockBlocker only stops mobs moving into its barriers (its no-route-planning-worlds setting): all
 * planning and steering is MZ's.
 */
public final class ZombiePathing
{
	private static final int MAX_NODES = 3000;
	private static final int MAX_RANGE = 64;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private static Object blockBlocker;
	private static Method isBarrierCell;
	private static Method crossesBarrier;
	private static boolean lookedUp;

	private ZombiePathing()
	{
	}

	// ------------------------------------------------------------------ A*

	private record Node(int x, int y, int z, double g, double f, Node parent) {}

	/**
	 * A* over the blocks (see the class description), stopping at a node near the goal from which nothing is between it
	 * and {@code goal} (or, with no goal, any node within a block of it).
	 *
	 * @return the route as feet positions from start to goal, or null when there is none within reach
	 */
	static List<int[]> search(World world, int sx, int sy, int sz, int gx, int gy, int gz, int maxDrop, Location goal, boolean orNearest)
	{
		Map<Long, Boolean> openCache = new HashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
		Map<Long, Double> best = new HashMap<>();
		open.add(new Node(sx, sy, sz, 0, h(sx, sy, sz, gx, gy, gz), null));
		best.put(key(sx, sy, sz), 0.0);
		int visited = 0;
		// The node that got nearest to the goal, in case none gets there (a player flying overhead, on an unreachable ledge,
		// behind a wall): the zombie then goes to the closest point it can reach and waits under / beside them, not stands still
		Node nearest = null;
		double nearestH = Double.MAX_VALUE;
		while(!open.isEmpty() && visited++ < MAX_NODES)
		{
			Node cur = open.poll();
			double hc = h(cur.x(), cur.y(), cur.z(), gx, gy, gz);
			if(hc < nearestH)
			{
				nearestH = hc;
				nearest = cur;
			}
			if(Math.abs(cur.x() - gx) <= 1 && Math.abs(cur.z() - gz) <= 1 && Math.abs(cur.y() - gy) <= 1
					&& (goal == null || arrivesAt(new Location(world, cur.x(), cur.y(), cur.z()), goal)))
				return build(cur);
			Double known = best.get(key(cur.x(), cur.y(), cur.z()));
			if(known != null && known < cur.g())
				continue;
			for(int[] d : DIRS)
			{
				int nx = cur.x() + d[0], nz = cur.z() + d[1];
				if(Math.abs(nx - sx) > MAX_RANGE || Math.abs(nz - sz) > MAX_RANGE)
					continue;
				boolean diagonal = d[0] != 0 && d[1] != 0;
				if(diagonal && (!open(world, cur.x() + d[0], cur.y(), cur.z(), openCache) || !open(world, cur.x(), cur.y(), cur.z() + d[1], openCache)
						|| !open(world, cur.x() + d[0], cur.y() + 1, cur.z(), openCache) || !open(world, cur.x(), cur.y() + 1, cur.z() + d[1], openCache)))
					continue;
				int ny = Integer.MIN_VALUE;
				if(standable(world, nx, cur.y(), nz, openCache))
					ny = cur.y();
				else if(!diagonal && standable(world, nx, cur.y() + 1, nz, openCache) && open(world, cur.x(), cur.y() + 2, cur.z(), openCache))
					ny = cur.y() + 1;
				else if(!diagonal && open(world, nx, cur.y(), nz, openCache) && open(world, nx, cur.y() + 1, nz, openCache))
					for(int drop = 1; drop <= maxDrop; drop++)
					{
						if(!open(world, nx, cur.y() - drop, nz, openCache))
						{
							if(standable(world, nx, cur.y() - drop + 1, nz, openCache))
								ny = cur.y() - drop + 1;
							break;
						}
					}
				if(ny == Integer.MIN_VALUE || ny > cur.y() + 1)
					continue;
				if(moveBlocked(world, cur.x(), cur.y(), cur.z(), nx, ny, nz))
					continue;
				double step = (diagonal ? 1.414 : 1.0) + (ny > cur.y() ? 0.4 : ny < cur.y() ? 0.15 * (cur.y() - ny) : 0);
				double g = cur.g() + step;
				long k = key(nx, ny, nz);
				Double old = best.get(k);
				if(old != null && old <= g)
					continue;
				best.put(k, g);
				open.add(new Node(nx, ny, nz, g, g + h(nx, ny, nz, gx, gy, gz), cur));
			}
		}
		// No way all the way: the closest it got, if that is a real step nearer than where it started
		if(orNearest && nearest != null && nearestH < h(sx, sy, sz, gx, gy, gz) - 2)
			return build(nearest);
		return null;
	}

	/**
	 * The block a route node for someone standing here is: the one their feet are in. Standing on a stair or slab, the
	 * feet are half way up the stair / slab block itself, which the search can't stand in (it is solid), so it is the block
	 * above. (Starting a search inside the stair used to find no way out at all, and the zombie stood stuck on it.)
	 */
	static int feetCell(Location at)
	{
		int y = (int) Math.floor(at.getY() + 0.01);
		Block feet = at.getWorld().getBlockAt(at.getBlockX(), y, at.getBlockZ());
		return passableOrFlat(feet) ? y : y + 1;
	}

	/** Blocks the current search may not go through (see {@link #routeLength}); null for none. The server is one thread. */
	private static java.util.Collection<Block> avoiding;

	/**
	 * How long the mob's walk to the location is (blocks, along the route), or -1 when there is none within reach: the same
	 * planner its walking uses, so BlockBlocker barriers are solid. {@code avoid}: blocks it may not go through (an arena's
	 * window barriers, to ask whether it has an open way that needs none of them), or null.
	 */
	public static double routeLength(Mob mob, Location goal, java.util.Collection<Block> avoid)
	{
		if(goal == null || goal.getWorld() != mob.getWorld())
			return -1;
		Location at = mob.getLocation();
		int maxDrop = Math.max(3, Math.min(32, COMZombies.getPlugin().getConfig().getInt("config.gameSettings.zombieSafeFallDistance", 16)));
		avoiding = avoid == null || avoid.isEmpty() ? null : avoid;
		List<int[]> route;
		try
		{
			route = search(mob.getWorld(), at.getBlockX(), feetCell(at), at.getBlockZ(), goal.getBlockX(), feetCell(goal),
					goal.getBlockZ(), maxDrop, goal, false);
		}
		finally
		{
			avoiding = null;
		}
		if(route == null)
			return -1;
		double length = 0;
		for(int i = 1; i < route.size(); i++)
		{
			int[] a = route.get(i - 1), b = route.get(i);
			length += Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
		}
		return length;
	}

	/**
	 * Can a mob standing at {@code from} walk in a straight line to the middle of route node {@code to}, on the same level?
	 * Every point along the line, and a body's width either side of it, has to be ground it can stand on (BlockBlocker
	 * barriers count as solid, as in the planner).
	 */
	static boolean walkableLine(World world, Location from, int[] to)
	{
		double ax = from.getX(), az = from.getZ(), bx = to[0] + 0.5, bz = to[2] + 0.5;
		double dx = bx - ax, dz = bz - az, len = Math.sqrt(dx * dx + dz * dz);
		if(len < 1.0E-3)
			return true;
		// a body's half width (0.3) sideways, plus room to spare: right at 0.3 it scraped past block corners and slowed down
		double px = -dz / len * 0.45, pz = dx / len * 0.45;
		Map<Long, Boolean> cache = new HashMap<>();
		int steps = (int) Math.ceil(len / 0.25);
		for(int i = 0; i <= steps; i++)
		{
			double x = ax + dx * i / steps, z = az + dz * i / steps;
			for(int side = -1; side <= 1; side++)
				if(!standable(world, (int) Math.floor(x + px * side), to[1], (int) Math.floor(z + pz * side), cache))
					return false;
		}
		return true;
	}

	private static double h(int x, int y, int z, int gx, int gy, int gz)
	{
		int dx = Math.abs(x - gx), dz = Math.abs(z - gz);
		return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + Math.abs(y - gy) * 0.5;
	}

	private static List<int[]> build(Node end)
	{
		List<int[]> out = new ArrayList<>();
		for(Node n = end; n != null; n = n.parent())
			out.add(new int[] {n.x(), n.y(), n.z()});
		Collections.reverse(out);
		return out;
	}

	/**
	 * Feet and head open, solid ground below that is not a BlockBlocker barrier cell. Ground taller than a full block
	 * (fences, walls, closed fence gates: 1.5 high) is not something a zombie can step onto or stand on.
	 */
	private static boolean standable(World world, int x, int y, int z, Map<Long, Boolean> cache)
	{
		if(!open(world, x, y, z, cache) || !open(world, x, y + 1, z, cache))
			return false;
		// The top of a BlockBlocker barrier is ground, like a stone block's
		if(barrierCell(world, x, y - 1, z))
			return true;
		Block below = world.getBlockAt(x, y - 1, z);
		if(below.isPassable() || below.getType().isAir())
			return false;
		double top = collisionTop(below);
		return top > 0 && top <= 1.0001;
	}

	/**
	 * Can the zombie hit the player from where it stands? Both a line at body height and one at eye height have to be
	 * clear. Every block with any collision counts as a FULL block (fences, walls, slabs, panes, stairs...), and a fence or
	 * wall also fills the cell above it (it is 1.5 high), so a zombie never hits through or over them. BlockBlocker barriers
	 * count as solid too. Low blocks that both stand on or above (carpet, a slab floor) are the floor, not in the way.
	 *
	 * @param ignore blocks that do not count (the zombie's own barrier, which it hits players through), or null
	 */
	public static boolean canHit(Mob mob, Player target, java.util.Collection<Block> ignore)
	{
		World world = mob.getWorld();
		if(!world.equals(target.getWorld()))
			return false;
		Location m = mob.getLocation(), t = target.getLocation();
		double floor = Math.min(m.getY(), t.getY()) + 0.01;
		var mobBox = mob.getBoundingBox();
		var targetBox = target.getBoundingBox();
		double mh = mob.getHeight(), th = target.getHeight();
		return clearLine(world, m.getX(), m.getY() + mh * 0.5, m.getZ(), t.getX(), t.getY() + th * 0.5, t.getZ(), floor, mobBox, targetBox, ignore)
				&& clearLine(world, m.getX(), m.getY() + mob.getEyeHeight(), m.getZ(), t.getX(), t.getY() + target.getEyeHeight(), t.getZ(), floor, mobBox, targetBox, ignore);
	}

	/**
	 * Does a route that ends at {@code end} really get to {@code goal}? Minecraft calls a route "reaching" when it ends
	 * near the goal, even with a wall, a fence or a gap between unconnected posts between the end and the goal (a zombie
	 * then presses against it forever). Here the end must be close AND have nothing in the way to the goal (the same rules
	 * as {@link #canHit}: fences, walls, slabs... are full blocks, BlockBlocker barriers are solid).
	 */
	public static boolean arrivesAt(Location end, Location goal)
	{
		if(end == null || goal == null || end.getWorld() == null || !end.getWorld().equals(goal.getWorld()))
			return false;
		// Path points are block positions (stand in the middle of the block); a mob's own location is used as it is
		boolean blockPos = end.getX() == Math.floor(end.getX()) && end.getZ() == Math.floor(end.getZ());
		double ex = blockPos ? end.getBlockX() + 0.5 : end.getX(), ey = end.getY(), ez = blockPos ? end.getBlockZ() + 0.5 : end.getZ();
		double gx = goal.getX(), gy = goal.getY(), gz = goal.getZ();
		double dx = ex - gx, dy = ey - gy, dz = ez - gz;
		if(dx * dx + dy * dy + dz * dz >= 6.25)
			return false;
		World world = end.getWorld();
		double floor = Math.min(ey, gy) + 0.01;
		org.bukkit.util.BoundingBox a = new org.bukkit.util.BoundingBox(ex - 0.3, ey, ez - 0.3, ex + 0.3, ey + 1.95, ez + 0.3);
		org.bukkit.util.BoundingBox b = new org.bukkit.util.BoundingBox(gx - 0.3, gy, gz - 0.3, gx + 0.3, gy + 1.8, gz + 0.3);
		return clearLine(world, ex, ey + 0.95, ez, gx, gy + 0.9, gz, floor, a, b, null)
				&& clearLine(world, ex, ey + 1.74, ez, gx, gy + 1.62, gz, floor, a, b, null);
	}

	private static boolean clearLine(World world, double ax, double ay, double az, double bx, double by, double bz, double floor,
	                                 org.bukkit.util.BoundingBox mobBox, org.bukkit.util.BoundingBox targetBox, java.util.Collection<Block> ignore)
	{
		if(segmentBlocked(world, ax, ay, az, bx, by, bz))
			return false;
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int steps = Math.max(1, (int) Math.ceil(len / 0.1));
		long last = Long.MIN_VALUE;
		for(int i = 0; i <= steps; i++)
		{
			double f = (double) i / steps;
			double px = ax + dx * f, py = ay + dy * f, pz = az + dz * f;
			// Inside the zombie or the player themselves: nothing there is between them
			if(mobBox.contains(px, py, pz) || targetBox.contains(px, py, pz))
				continue;
			int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
			long k = key(x, y, z);
			if(k == last)
				continue;
			last = k;
			if(solidForSight(world, x, y, z, floor, ignore))
				return false;
		}
		return true;
	}

	/** Is the cell in the way of a hit (see {@link #canHit})? */
	private static boolean solidForSight(World world, int x, int y, int z, double floor, java.util.Collection<Block> ignore)
	{
		if(!world.isChunkLoaded(x >> 4, z >> 4))
			return true;
		Block block = world.getBlockAt(x, y, z);
		if(ignore == null || !ignore.contains(block))
		{
			if(!block.isPassable())
			{
				double top = collisionTop(block);
				// A full block for this purpose, unless it is low enough that both stand on top of it (the floor)
				if(top <= 0 || y + top > floor)
					return true;
			}
		}
		Block below = world.getBlockAt(x, y - 1, z);
		if((ignore == null || !ignore.contains(below)) && collisionTop(below) > 1.0001)
			return true; // a fence or wall below reaches up into this cell
		return barrierCell(world, x, y, z);
	}

	/** How high (0..1.5) a block's collision reaches above its bottom; 0 for none. */
	private static double collisionTop(Block block)
	{
		double top = 0;
		for(var box : block.getCollisionShape().getBoundingBoxes())
			top = Math.max(top, box.getMaxY());
		return top;
	}

	/** Passable, or only a thin cover on the floor (carpet, a snow layer...) that a zombie just walks over. */
	private static boolean passableOrFlat(Block block)
	{
		return block.isPassable() || collisionTop(block) <= 0.13;
	}

	/** Passable, loaded, and not part of a BlockBlocker barrier. */
	private static boolean open(World world, int x, int y, int z, Map<Long, Boolean> cache)
	{
		long k = key(x, y, z);
		Boolean known = cache.get(k);
		if(known != null)
			return known;
		// A fence or wall below reaches half way up into this cell, so nothing walks through it at that height either
		Block block = world.getBlockAt(x, y, z);
		boolean result = world.isChunkLoaded(x >> 4, z >> 4) && passableOrFlat(block) && !barrierCell(world, x, y, z)
				&& collisionTop(world.getBlockAt(x, y - 1, z)) <= 1.0001 && (avoiding == null || !avoiding.contains(block));
		cache.put(k, result);
		return result;
	}

	/** Finds BlockBlocker's methods once (both lookups below need them). */
	private static void lookUp()
	{
		if(lookedUp)
			return;
		lookedUp = true;
		try
		{
			Plugin bb = Bukkit.getPluginManager().getPlugin("BlockBlocker");
			if(bb != null && bb.isEnabled())
			{
				isBarrierCell = bb.getClass().getMethod("isBarrierCell", World.class, int.class, int.class, int.class);
				blockBlocker = bb;
				try
				{
					crossesBarrier = bb.getClass().getMethod("crossesBarrier", World.class, double.class, double.class, double.class,
							double.class, double.class, double.class);
				}
				catch(NoSuchMethodException older)
				{
					crossesBarrier = null;
				}
			}
		}
		catch(ReflectiveOperationException | LinkageError ignored)
		{
			isBarrierCell = null;
		}
	}

	/** Asks BlockBlocker (if installed) whether the cell is part of one of its barriers. */
	private static boolean barrierCell(World world, int x, int y, int z)
	{
		lookUp();
		if(isBarrierCell == null)
			return false;
		try
		{
			return (Boolean) isBarrierCell.invoke(blockBlocker, world, x, y, z);
		}
		catch(ReflectiveOperationException | RuntimeException e)
		{
			return false;
		}
	}

	/** Does a straight move from a to b go through a BlockBlocker barrier (thin pieces included)? False without BlockBlocker. */
	private static boolean segmentBlocked(World world, double ax, double ay, double az, double bx, double by, double bz)
	{
		lookUp(); // (this used to ask BlockBlocker about a made-up cell just to make sure it was looked up)
		if(crossesBarrier == null)
			return false;
		try
		{
			return (Boolean) crossesBarrier.invoke(blockBlocker, world, ax, ay, az, bx, by, bz);
		}
		catch(ReflectiveOperationException | RuntimeException e)
		{
			return false;
		}
	}

	/** Stepping from one cell to the next: through a BlockBlocker barrier, at foot or head height? */
	private static boolean moveBlocked(World world, int x, int y, int z, int nx, int ny, int nz)
	{
		int top = Math.max(y, ny);
		// A step up goes over the block (or barrier) it steps onto: only the height it steps up to has to be clear
		if(ny > y)
			return segmentBlocked(world, x + 0.5, top + 0.3, z + 0.5, nx + 0.5, top + 0.3, nz + 0.5)
					|| segmentBlocked(world, x + 0.5, top + 1.2, z + 0.5, nx + 0.5, top + 1.2, nz + 0.5);
		if(segmentBlocked(world, x + 0.5, y + 0.3, z + 0.5, nx + 0.5, y + 0.3, nz + 0.5)
				|| segmentBlocked(world, x + 0.5, y + 1.2, z + 0.5, nx + 0.5, y + 1.2, nz + 0.5))
			return true;
		// A step up: also at the height it steps up to (a flat step has nothing more to check)
		return top != y && (segmentBlocked(world, x + 0.5, top + 0.3, z + 0.5, nx + 0.5, top + 0.3, nz + 0.5)
				|| segmentBlocked(world, x + 0.5, top + 1.2, z + 0.5, nx + 0.5, top + 1.2, nz + 0.5));
	}

	private static long key(int x, int y, int z)
	{
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}
}
