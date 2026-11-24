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

	/** {@code floor}: the height its feet are really at in this cell (on snow layers, a slab, carpet... not the block grid). */
	private record Node(int x, int y, int z, double floor, double g, double f, Node parent) {}

	/** Rises up to this it walks straight up (Minecraft's step height for mobs). */
	static final double STEP = 0.6;
	/** Rises up to this it jumps onto (a zombie's jump reaches about 1.25). Anything higher is not a way at all. */
	static final double MAX_JUMP = 1.2;
	private static final double HALF_WIDTH = 0.3, HEIGHT = 1.95;

	/** A route step's real floor height (route entries are {x, cell y, z, floor in thousandths}). */
	public static double floorOf(int[] step)
	{
		return step.length > 3 ? step[3] / 1000.0 : step[1];
	}

	/**
	 * A* over the blocks (see the class description), stopping at a node near the goal from which nothing is between it
	 * and {@code goal} (or, with no goal, any node within a block of it). Every move is one Minecraft really lets a zombie
	 * make, measured from real floor heights: a rise up to {@link #STEP} is walked, up to {@link #MAX_JUMP} jumped (with head
	 * room for the jump), drops up to {@code maxDrop}; its whole body has to fit wherever it stands.
	 *
	 * @param startAt where it really is now (its feet): whether it is already there is judged from here, not from the
	 *                middle of its block (beside a wall corner the middle can see the goal while it can't, and it waited
	 *                there forever, told it had arrived)
	 * @return the route as {x, cell y, z, floor in thousandths} from start to goal, or null when there is none within reach
	 */
	static List<int[]> search(World world, int sx, int sy, int sz, Location startAt, int gx, int gy, int gz, int maxDrop, Location goal,
	                          boolean orNearest)
	{
		double startFloor = startAt.getY();
		Map<Long, Boolean> openCache = new HashMap<>();
		Map<Long, Double> floors = new HashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
		Map<Long, Double> best = new HashMap<>();
		open.add(new Node(sx, sy, sz, startFloor, 0, h(sx, sy, sz, gx, gy, gz), null));
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
					&& (goal == null || arrivesAt(cur.parent() == null ? startAt : new Location(world, cur.x(), cur.floor(), cur.z()), goal)))
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
				double from = cur.floor();
				int ny = Integer.MIN_VALUE;
				double to = Double.NaN;
				// Level with it, or the cell above (a step or a jump up), whichever it can stand in
				for(int y = cur.y(); y <= cur.y() + 1 && ny == Integer.MIN_VALUE; y++)
				{
					double f = floorAt(world, nx, y, nz, floors);
					if(!Double.isNaN(f) && f - from <= MAX_JUMP && from - f <= maxDrop)
					{
						ny = y;
						to = f;
					}
				}
				// Off an edge: it walks out over the drop (its body has to fit there at its own height), then falls
				if(ny == Integer.MIN_VALUE && !diagonal && bodyFits(world, nx, from, nz))
					for(int drop = 1; drop <= maxDrop + 1; drop++)
					{
						int y = cur.y() - drop;
						double f = floorAt(world, nx, y, nz, floors);
						if(!Double.isNaN(f))
						{
							if(from - f <= maxDrop)
							{
								ny = y;
								to = f;
							}
							break;
						}
						if(!open(world, nx, y, nz, openCache))
							break;
					}
				if(ny == Integer.MIN_VALUE)
					continue;
				double rise = to - from;
				// Stairs (and anything with a lower near side) are walked up in two steps, not jumped: the half of the block
				// it steps onto first is within a step of where it is, and the rest within a step of that
				double lift = to;
				boolean twoSteps = false;
				if(rise > STEP && !diagonal && world.getBlockAt(nx, ny, nz).isPassable() && !barrierCell(world, nx, ny - 1, nz))
				{
					double near = nearSideTop(world.getBlockAt(nx, ny - 1, nz), d[0], d[1]);
					if(near - from <= STEP && to - near <= STEP)
					{
						twoSteps = true;
						lift = near;
					}
				}
				// A jump or a diagonal only where it really fits: a corner is cut walking, never jumping, and a jump needs head
				// room above where it takes off
				if(diagonal && (Math.abs(rise) > STEP || !bodyFits(world, cur.x() + d[0], Math.max(from, to), cur.z())
						|| !bodyFits(world, cur.x(), Math.max(from, to), cur.z() + d[1])))
					continue;
				if(rise > 0.01 && !bodyFits(world, cur.x(), lift, cur.z()))
					continue;
				if(twoSteps)
					rise = STEP; // costed as a walk up
				if(moveBlocked(world, cur.x(), cur.y(), cur.z(), nx, ny, nz))
					continue;
				double step = (diagonal ? 1.414 : 1.0) + (rise > STEP ? 0.6 : rise > 0.01 ? 0.2 : rise < -STEP ? 0.15 * -rise : 0);
				double g = cur.g() + step;
				long k = key(nx, ny, nz);
				Double old = best.get(k);
				if(old != null && old <= g)
					continue;
				best.put(k, g);
				open.add(new Node(nx, ny, nz, to, g, g + h(nx, ny, nz, gx, gy, gz), cur));
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
		// The cell whose real floor is where the feet are (on 3 snow layers the feet are in the snow's block, but that block
		// is not a cell to stand in: the one above it is, with its floor a quarter block up the snow's block)
		Map<Long, Double> floors = new HashMap<>();
		int best = Integer.MIN_VALUE;
		double bestErr = 0.6;
		for(int cy = y - 1; cy <= y + 1; cy++)
		{
			double f = floorAt(at.getWorld(), at.getBlockX(), cy, at.getBlockZ(), floors);
			if(!Double.isNaN(f) && Math.abs(f - at.getY()) < bestErr)
			{
				bestErr = Math.abs(f - at.getY());
				best = cy;
			}
		}
		if(best != Integer.MIN_VALUE)
			return best;
		// Its body does not fit standing at the middle of its block (it is off to one side of something): by the blocks alone
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
			route = search(mob.getWorld(), at.getBlockX(), feetCell(at), at.getBlockZ(), at, goal.getBlockX(), feetCell(goal),
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
	 * Can a mob standing at {@code from} walk in a straight line to the middle of route node {@code to}, without a jump?
	 * Every point along the line, and a body's width either side of it, has to be ground it can stand on, each no more than
	 * a step ({@link #STEP}) above or below the last (BlockBlocker barriers count as solid, as in the planner).
	 */
	static boolean walkableLine(World world, Location from, int[] to)
	{
		double ax = from.getX(), az = from.getZ(), bx = to[0] + 0.5, bz = to[2] + 0.5;
		double dx = bx - ax, dz = bz - az, len = Math.sqrt(dx * dx + dz * dz);
		if(len < 1.0E-3)
			return true;
		// a body's half width (0.3) sideways, plus room to spare: right at 0.3 it scraped past block corners and slowed down
		double px = -dz / len * 0.45, pz = dx / len * 0.45;
		Map<Long, Double> floors = new HashMap<>();
		int steps = (int) Math.ceil(len / 0.25);
		double last = from.getY();
		for(int i = 0; i <= steps; i++)
		{
			double x = ax + dx * i / steps, z = az + dz * i / steps;
			double here = Double.NaN;
			for(int side = -1; side <= 1; side++)
			{
				int cx = (int) Math.floor(x + px * side), cz = (int) Math.floor(z + pz * side);
				// the floor there, in the cell level with the line, or a step up or down from it
				double f = Double.NaN;
				for(int cy = to[1] - 1; cy <= to[1] + 1 && Double.isNaN(f); cy++)
				{
					double c = floorAt(world, cx, cy, cz, floors);
					if(!Double.isNaN(c) && Math.abs(c - last) <= STEP)
						f = c;
				}
				if(Double.isNaN(f))
					return false;
				if(side == 0)
					here = f;
			}
			last = here;
		}
		return Math.abs(last - floorOf(to)) <= STEP;
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
			out.add(new int[] {n.x(), n.y(), n.z(), (int) Math.round(n.floor() * 1000)});
		Collections.reverse(out);
		return out;
	}

	/**
	 * Where a zombie's feet are standing in the middle of this cell, or NaN when it can't stand there. The cell holds its
	 * feet: either open, with ground below it, or with only a thin cover in it (carpet, snow up to two layers: no more than
	 * 0.13 high). The ground is a block whose top is no higher than a full block (fences, walls and closed gates, 1.5 high,
	 * are not stood on) or a BlockBlocker barrier (stood on like stone). Its whole body, 0.6 wide and 1.95 tall from that
	 * real floor, has to fit (real shapes: a low ceiling, a top slab, a lantern, a fence arm), with no BlockBlocker barrier
	 * in it.
	 */
	public static double floorAt(World world, int x, int y, int z, Map<Long, Double> cache)
	{
		long k = key(x, y, z);
		Double known = cache.get(k);
		if(known != null)
			return known;
		double f = floorHere(world, x, y, z);
		cache.put(k, f);
		return f;
	}

	private static double floorHere(World world, int x, int y, int z)
	{
		if(!world.isChunkLoaded(x >> 4, z >> 4) || barrierCell(world, x, y, z))
			return Double.NaN;
		Block here = world.getBlockAt(x, y, z);
		if(avoiding != null && avoiding.contains(here))
			return Double.NaN;
		double floor;
		if(!here.isPassable())
		{
			double top = collisionTop(here);
			if(top > 0.13)
				return Double.NaN;
			floor = y + top;
		}
		else if(barrierCell(world, x, y - 1, z))
			floor = y; // the top of a BlockBlocker barrier is ground, like a stone block's
		else
		{
			Block below = world.getBlockAt(x, y - 1, z);
			double top = below.isPassable() ? 0 : collisionTop(below);
			// A thin cover below is the floor of the cell it is in, not of this one (no two cells for one place to stand)
			if(top <= 0.13 || top > 1.0001)
				return Double.NaN;
			floor = y - 1 + top;
		}
		if(!bodyFits(world, x, floor, z))
			return Double.NaN;
		return floor;
	}

	/**
	 * The top of the half of this block a mob walking in direction (dx, dz) steps onto first (world height). A stair entered
	 * from its low side is half a block high there; a full block or a slab is the same all over.
	 */
	private static double nearSideTop(Block ground, int dx, int dz)
	{
		double top = 0;
		for(var b : ground.getCollisionShape().getBoundingBoxes())
		{
			boolean near = (dx > 0 ? b.getMinX() < 0.5 : dx < 0 ? b.getMaxX() > 0.5 : true)
					&& (dz > 0 ? b.getMinZ() < 0.5 : dz < 0 ? b.getMaxZ() > 0.5 : true);
			if(near)
				top = Math.max(top, b.getMaxY());
		}
		return ground.getY() + top;
	}

	/**
	 * Does a zombie's body (0.6 wide, 1.95 tall) fit at the middle of this column with its feet at {@code floor}? Real block
	 * shapes, and no BlockBlocker barrier in the cells it fills.
	 */
	static boolean bodyFits(World world, int x, double floor, int z)
	{
		if(!world.isChunkLoaded(x >> 4, z >> 4))
			return false;
		double minY = floor + 0.01, maxY = floor + HEIGHT;
		// its body across the cell (block-relative), a hair narrower so a wall it only touches does not count
		double lo = 0.5 - HALF_WIDTH + 0.01, hi = 0.5 + HALF_WIDTH - 0.01;
		int bottom = (int) Math.floor(minY);
		// From the block below its feet (a fence or wall there reaches up 1.5) to the one its head is in. Block shapes stay
		// inside their own block (only that 1.5 height reaches above), so this column is all that can be in its way.
		for(int y = bottom - 1; y <= (int) Math.floor(maxY); y++)
		{
			Block b = world.getBlockAt(x, y, z);
			if(y >= bottom && (barrierCell(world, x, y, z) || (avoiding != null && avoiding.contains(b))))
				return false;
			if(b.isPassable())
				continue;
			for(var box : b.getCollisionShape().getBoundingBoxes())
			{
				if(y + box.getMaxY() <= minY || y + box.getMinY() >= maxY)
					continue;
				if(box.getMaxX() <= lo || box.getMinX() >= hi || box.getMaxZ() <= lo || box.getMinZ() >= hi)
					continue;
				return false;
			}
		}
		return true;
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
