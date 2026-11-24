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
	 * @param goalBody height and eye height of who is at the goal (a player's real size, crouched or prone included), for
	 *                 judging that the goal is in reach exactly as a hit is
	 * @return the route as {x, cell y, z, floor in thousandths} from start to goal, or null when there is none within reach
	 */
	static List<int[]> search(World world, int sx, int sy, int sz, Location startAt, int gx, int gy, int gz, int maxDrop, Location goal,
	                          double[] goalBody, boolean orNearest)
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
					&& (goal == null || arrivesAt(cur.parent() == null ? startAt : new Location(world, cur.x(), cur.floor(), cur.z()), goal, goalBody)))
				return build(cur);
			Double known = best.get(key(cur.x(), cur.y(), cur.z()));
			if(known != null && known < cur.g())
				continue;
			for(Move m : moves(world, cur.x(), cur.y(), cur.z(), cur.floor(), maxDrop, floors, openCache))
			{
				if(Math.abs(m.x() - sx) > MAX_RANGE || Math.abs(m.z() - sz) > MAX_RANGE)
					continue;
				double g = cur.g() + m.cost();
				long k = key(m.x(), m.y(), m.z());
				Double old = best.get(k);
				if(old != null && old <= g)
					continue;
				best.put(k, g);
				open.add(new Node(m.x(), m.y(), m.z(), m.floor(), g, g + h(m.x(), m.y(), m.z(), gx, gy, gz), cur));
			}
		}
		// No way all the way: the closest it got, if that is a real step nearer than where it started
		if(orNearest && nearest != null && nearestH < h(sx, sy, sz, gx, gy, gz) - 2)
			return build(nearest);
		return null;
	}

	/** One move the planner may make from a cell: where to, its real floor there, and what it costs. */
	record Move(int x, int y, int z, double floor, double cost) {}

	/**
	 * Every move a zombie standing in cell (x, y, z) with its feet at {@code from} can really make to a neighbouring cell:
	 * level or up a step / jump (no more than {@link #MAX_JUMP} up, with head room to jump), off an edge (falls up to
	 * {@code maxDrop}), stairs walked up from their low side, corners cut only walking and only where its body clears both
	 * sides. The route search and the inside/outside fill both use exactly these moves.
	 */
	static List<Move> moves(World world, int x, int y, int z, double from, int maxDrop, Map<Long, Double> floors, Map<Long, Boolean> openCache)
	{
		List<Move> out = new ArrayList<>(8);
		for(int[] d : DIRS)
		{
			int nx = x + d[0], nz = z + d[1];
			boolean diagonal = d[0] != 0 && d[1] != 0;
			int ny = Integer.MIN_VALUE;
			double to = Double.NaN;
			// Level with it, or the cell above (a step or a jump up), whichever it can stand in
			for(int cy = y; cy <= y + 1 && ny == Integer.MIN_VALUE; cy++)
			{
				double f = floorAt(world, nx, cy, nz, floors);
				if(!Double.isNaN(f) && f - from <= MAX_JUMP && from - f <= maxDrop)
				{
					ny = cy;
					to = f;
				}
			}
			// Off an edge: it walks out over the drop (its body has to fit there at its own height), then falls
			if(ny == Integer.MIN_VALUE && !diagonal && bodyFits(world, nx, from, nz))
				for(int drop = 1; drop <= maxDrop + 1; drop++)
				{
					int cy = y - drop;
					double f = floorAt(world, nx, cy, nz, floors);
					if(!Double.isNaN(f))
					{
						if(from - f <= maxDrop)
						{
							ny = cy;
							to = f;
						}
						break;
					}
					if(!open(world, nx, cy, nz, openCache))
						break;
				}
			if(ny == Integer.MIN_VALUE)
				continue;
			double rise = to - from;
			// Stairs (and anything with a lower near side) are walked up in two steps, not jumped: the half of the block it
			// steps onto first is within a step of where it is, and the rest within a step of that
			double lift = to;
			boolean twoSteps = false;
			if(rise > STEP && !diagonal && isPassable(world.getBlockAt(nx, ny, nz)) && !barrierCell(world, nx, ny - 1, nz))
			{
				double near = nearSideTop(world.getBlockAt(nx, ny - 1, nz), d[0], d[1]);
				if(near - from <= STEP && to - near <= STEP)
				{
					twoSteps = true;
					lift = near;
				}
			}
			// A jump or a diagonal only where it really fits: a corner is cut walking, never jumping, and a jump needs head room
			// above where it takes off
			if(diagonal && (Math.abs(rise) > STEP || !bodyFits(world, x + d[0], Math.max(from, to), z)
					|| !bodyFits(world, x, Math.max(from, to), z + d[1])))
				continue;
			if(rise > 0.01 && !bodyFits(world, x, lift, z))
				continue;
			if(twoSteps)
				rise = STEP; // costed as a walk up
			if(moveBlocked(world, x, y, z, nx, ny, nz))
				continue;
			double cost = (diagonal ? 1.414 : 1.0) + (rise > STEP ? 0.6 : rise > 0.01 ? 0.2 : rise < -STEP ? 0.15 * -rise : 0);
			out.add(new Move(nx, ny, nz, to, cost));
		}
		return out;
	}

	/** Blocks that count as open air for the current fill (the arena's doors: players open them); null for none. */
	private static java.util.Collection<Block> passing;

	/** Passable, or one of the blocks the current fill treats as open (a door). */
	private static boolean isPassable(Block b)
	{
		return b.isPassable() || (passing != null && passing.contains(b));
	}

	/**
	 * Every cell a zombie could walk to from the seeds (with Minecraft's own movement, as in {@link #moves}) without going
	 * through any of {@code walls} (an arena's windows, open or not), treating {@code doors} as open, and staying inside
	 * min..max (a block of margin). Used once per arena to tell each window's two sides apart: the side the players can walk
	 * to is inside.
	 *
	 * @return the cells reached, as {@link #key} values
	 */
	public static java.util.Set<Long> reachable(World world, List<Location> seeds, Location min, Location max, java.util.Collection<Block> walls,
	                                            java.util.Collection<Block> doors, int maxDrop)
	{
		java.util.Set<Long> seen = new java.util.HashSet<>();
		avoiding = walls == null || walls.isEmpty() ? null : walls;
		passing = doors == null || doors.isEmpty() ? null : doors;
		try
		{
			Map<Long, Double> floors = new HashMap<>();
			Map<Long, Boolean> openCache = new HashMap<>();
			java.util.ArrayDeque<double[]> queue = new java.util.ArrayDeque<>();
			int x0 = Math.min(min.getBlockX(), max.getBlockX()) - 1, x1 = Math.max(min.getBlockX(), max.getBlockX()) + 1;
			int y0 = Math.min(min.getBlockY(), max.getBlockY()) - 1, y1 = Math.max(min.getBlockY(), max.getBlockY()) + 1;
			int z0 = Math.min(min.getBlockZ(), max.getBlockZ()) - 1, z1 = Math.max(min.getBlockZ(), max.getBlockZ()) + 1;
			for(Location s : seeds)
			{
				int[] cell = nearestStandable(world, s, floors);
				if(cell != null && seen.add(key(cell[0], cell[1], cell[2])))
					queue.add(new double[] {cell[0], cell[1], cell[2], floorAt(world, cell[0], cell[1], cell[2], floors)});
			}
			while(!queue.isEmpty() && seen.size() < 400_000)
			{
				double[] c = queue.poll();
				for(Move m : moves(world, (int) c[0], (int) c[1], (int) c[2], c[3], maxDrop, floors, openCache))
				{
					if(m.x() < x0 || m.x() > x1 || m.z() < z0 || m.z() > z1 || m.y() < y0 || m.y() > y1)
						continue;
					if(seen.add(key(m.x(), m.y(), m.z())))
						queue.add(new double[] {m.x(), m.y(), m.z(), m.floor()});
				}
			}
		}
		finally
		{
			avoiding = null;
			passing = null;
		}
		return seen;
	}

	/** The standable cell nearest a location (within 2 blocks): {x, y, z}, or null. */
	private static int[] nearestStandable(World world, Location at, Map<Long, Double> floors)
	{
		int bx = at.getBlockX(), by = at.getBlockY(), bz = at.getBlockZ();
		for(int r = 0; r <= 2; r++)
			for(int dy : new int[] {0, 1, -1, 2, -2})
				for(int dx = -r; dx <= r; dx++)
					for(int dz = -r; dz <= r; dz++)
						if(Math.max(Math.abs(dx), Math.abs(dz)) == r && !Double.isNaN(floorAt(world, bx + dx, by + dy, bz + dz, floors)))
							return new int[] {bx + dx, by + dy, bz + dz};
		return null;
	}

	/** The cell whose floor is at this height (feet on 3 snow layers at y + 0.25 are in the cell above the snow's block). */
	public static int cellOf(double floor)
	{
		return (int) Math.floor(floor - 0.135) + 1;
	}

	/** The key {@link #reachable} uses for a cell. */
	public static long cellKey(int x, int y, int z)
	{
		return key(x, y, z);
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
					goal.getBlockZ(), maxDrop, goal, STANDING, false);
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
		if(!isPassable(here))
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
			double top = isPassable(below) ? 0 : collisionTop(below);
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
			if(isPassable(b))
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

	/** How far past its own body a zombie's arms reach, sideways (Minecraft's melee reach for mobs: sqrt(2.04) - 0.6). */
	public static final double ARM_REACH = 0.83;

	/**
	 * Is the player within the zombie's arm reach (Minecraft's own rule for mobs: the zombie's box, widened by
	 * {@link #ARM_REACH} sideways, touches the player's)? Whether anything is in between is {@link #canHit}.
	 */
	public static boolean inReach(Mob mob, Player target)
	{
		return mob.getWorld().equals(target.getWorld())
				&& mob.getBoundingBox().expand(ARM_REACH, 0, ARM_REACH).overlaps(target.getBoundingBox());
	}

	/**
	 * Is there nothing between the zombie and the player for a hit? A line from its eyes to theirs, or from its middle to
	 * theirs, has to be clear (whichever is: a player crouched or prone behind a low wall is lower than a standing one, and
	 * {@code getHeight} / {@code getEyeHeight} follow that). See {@link #clearLine} for what is in the way.
	 *
	 * @param ignore blocks that do not count (the zombie's own barrier, which it hits players through), or null
	 */
	public static boolean canHit(Mob mob, Player target, java.util.Collection<Block> ignore)
	{
		World world = mob.getWorld();
		if(!world.equals(target.getWorld()))
			return false;
		Location m = mob.getLocation(), t = target.getLocation();
		return sightBetween(world, m.getX(), m.getY(), m.getZ(), mob.getHeight(), mob.getEyeHeight(), mob.getBoundingBox(),
				t.getX(), t.getY(), t.getZ(), target.getHeight(), target.getEyeHeight(), target.getBoundingBox(), ignore);
	}

	/** Eyes to eyes, or middle to middle: either line clear. Boxes: the two bodies (nothing inside them is in the way). */
	private static boolean sightBetween(World world, double ax, double ay, double az, double ah, double ae, org.bukkit.util.BoundingBox aBox,
	                                    double bx, double by, double bz, double bh, double be, org.bukkit.util.BoundingBox bBox,
	                                    java.util.Collection<Block> ignore)
	{
		return clearLine(world, ax, ay + ae, az, bx, by + be, bz, aBox, bBox, ignore)
				|| clearLine(world, ax, ay + ah * 0.5, az, bx, by + bh * 0.5, bz, aBox, bBox, ignore);
	}

	/** A standing player's height and eye height, for a goal that is a place and not a player. */
	public static final double[] STANDING = {1.8, 1.62};
	/** A zombie's height and eye height. */
	public static final double[] ZOMBIE = {HEIGHT, 1.74};

	/**
	 * Does a route that ends at {@code end} really get to {@code goal}: could a zombie standing there hit someone of this
	 * size ({@code body}: height, eye height) standing at the goal? Minecraft calls a route "reaching" when it ends near the
	 * goal even with a wall or a fence in between (a zombie then presses against it forever). The same rule as
	 * {@link #canHit}, and within {@link #ARM_REACH} of its body, or 2.5 blocks for a place (a window spot).
	 */
	public static boolean arrivesAt(Location end, Location goal, double[] body)
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
		org.bukkit.util.BoundingBox a = new org.bukkit.util.BoundingBox(ex - HALF_WIDTH, ey, ez - HALF_WIDTH, ex + HALF_WIDTH, ey + HEIGHT, ez + HALF_WIDTH);
		org.bukkit.util.BoundingBox b = new org.bukkit.util.BoundingBox(gx - HALF_WIDTH, gy, gz - HALF_WIDTH, gx + HALF_WIDTH, gy + body[0], gz + HALF_WIDTH);
		return sightBetween(world, ex, ey, ez, ZOMBIE[0], ZOMBIE[1], a, gx, gy, gz, body[0], body[1], b, null);
	}

	/** {@link #arrivesAt(Location, Location, double[])} for a standing player's size. */
	public static boolean arrivesAt(Location end, Location goal)
	{
		return arrivesAt(end, goal, STANDING);
	}

	/**
	 * Is the straight line clear for a hit? Blocks are their real shapes (the line can go over a slab or past the low side
	 * of a stair), except fences, walls, panes, bars and fence gates: those count as their whole block, and a fence or wall
	 * also fills the half block above it (it is 1.5 high), so nobody hits through the gaps in them or over them. BlockBlocker
	 * barriers are solid. Nothing inside either body counts.
	 */
	private static boolean clearLine(World world, double ax, double ay, double az, double bx, double by, double bz,
	                                 org.bukkit.util.BoundingBox mobBox, org.bukkit.util.BoundingBox targetBox, java.util.Collection<Block> ignore)
	{
		if(segmentBlocked(world, ax, ay, az, bx, by, bz))
			return false;
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int steps = Math.max(1, (int) Math.ceil(len / 0.05));
		for(int i = 0; i <= steps; i++)
		{
			double f = (double) i / steps;
			double px = ax + dx * f, py = ay + dy * f, pz = az + dz * f;
			// Inside the zombie or the player themselves: nothing there is between them
			if(mobBox.contains(px, py, pz) || targetBox.contains(px, py, pz))
				continue;
			if(inTheWay(world, px, py, pz, ignore))
				return false;
		}
		return true;
	}

	/** Is this point inside something a hit can't pass (see {@link #clearLine})? */
	private static boolean inTheWay(World world, double px, double py, double pz, java.util.Collection<Block> ignore)
	{
		int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
		if(!world.isChunkLoaded(x >> 4, z >> 4))
			return true;
		Block block = world.getBlockAt(x, y, z);
		if(ignore == null || !ignore.contains(block))
		{
			if(thinWall(block))
				return true;
			if(!block.isPassable())
				for(var box : block.getCollisionShape().getBoundingBoxes())
					if(box.contains(px - x, py - y, pz - z))
						return true;
		}
		Block below = world.getBlockAt(x, y - 1, z);
		if((ignore == null || !ignore.contains(below)) && py - y < 0.5 && thinWall(below) && collisionTop(below) > 1.0001)
			return true; // a fence or wall below reaches up into this cell
		return barrierCell(world, x, y, z);
	}

	/** Fences, walls, panes, iron bars, closed fence gates: a hit never passes their gaps. */
	private static boolean thinWall(Block block)
	{
		String n = block.getType().name();
		if(n.endsWith("_FENCE_GATE"))
			return !block.isPassable();
		return n.endsWith("_FENCE") || n.endsWith("_WALL") || n.endsWith("_PANE") || n.equals("GLASS_PANE") || n.equals("IRON_BARS");
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
		return isPassable(block) || collisionTop(block) <= 0.13;
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
