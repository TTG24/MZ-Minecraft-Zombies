package com.theprogrammingturkey.comz.game;

import com.destroystokyo.paper.entity.Pathfinder;
import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.features.Barrier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Lets zombies drop down ledges to get where they are going. Minecraft only plans drops of about 3 blocks for most mobs:
 * it goes by the mob's health and the difficulty (on peaceful, nearly always 3), not by its safe fall distance. So a player
 * or barrier down a taller drop looked unreachable and zombies never jumped down. When a zombie's own route cannot reach
 * its goal (the barrier it is going for, or the nearest player), a route is planned here that may drop up to
 * config.gameSettings.zombieSafeFallDistance blocks, and the zombie is walked along it (straight off the edge at a deep
 * drop). If BlockBlocker is installed, its barriers count as solid, so routes never go through or drop onto one.
 */
public final class ZombiePathing
{
	private static final int MAX_NODES = 3000;
	private static final int MAX_RANGE = 64;
	private static final int SEARCHES_PER_TICK = 2;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private static final class Plan
	{
		List<int[]> nodes;
		int index;
		Location goal;
		long madeTick;
		int pushTicks;
		// Planned because Minecraft's route only got NEAR the goal (a wall, fence, glass... in between), not for a drop
		boolean chase;
	}

	private static final Map<UUID, Plan> PLANS = new HashMap<>();
	private static final Map<UUID, Long> NEXT_CHECK = new HashMap<>();
	private static long budgetTick = -1;
	private static int searchesLeft;

	private static Object blockBlocker;
	private static Method isBarrierCell;
	private static Method crossesBarrier;
	private static boolean lookedUp;

	private ZombiePathing()
	{
	}

	/** True while a zombie is being walked along a planned route: the plugin's other steering leaves it alone meanwhile. */
	public static boolean isSteering(Mob mob)
	{
		return PLANS.containsKey(mob.getUniqueId());
	}

	public static void start(Plugin plugin)
	{
		Bukkit.getScheduler().runTaskTimer(plugin, ZombiePathing::tick, 20L, 1L);
	}

	private static void tick()
	{
		long tick = Bukkit.getCurrentTick();
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
				continue;
			for(Mob mob : new ArrayList<>(game.spawnManager.getEntities()))
			{
				if(!mob.isValid())
					continue;
				Plan plan = PLANS.get(mob.getUniqueId());
				if(plan != null)
					follow(game, mob, plan, tick);
				else if(tick >= NEXT_CHECK.getOrDefault(mob.getUniqueId(), 0L))
				{
					NEXT_CHECK.put(mob.getUniqueId(), tick + 10);
					check(game, mob, tick);
				}
			}
		}
		if(tick % 100 == 0)
		{
			PLANS.keySet().removeIf(id -> !(Bukkit.getEntity(id) instanceof Mob m) || !m.isValid());
			NEXT_CHECK.keySet().removeIf(id -> !(Bukkit.getEntity(id) instanceof Mob m) || !m.isValid());
		}
	}

	/** Where the zombie is meant to go: the barrier holding it, else the nearest player. */
	private static Location goalOf(Game game, Mob mob)
	{
		Barrier held = game.barrierManager.heldBy(mob);
		if(held != null)
			return held.approachFor(mob);
		Player p = game.spawnManager.getNearestPlayer(mob);
		return p == null || p.getWorld() != mob.getWorld() ? null : p.getLocation();
	}

	/**
	 * Plans a route when its own one cannot get there: a deep drop on the way, or Minecraft's route only ends NEAR the goal
	 * with something in between (its pathfinder then walks the zombie up to the fence, wall, glass or gap between posts
	 * and leaves it pressing there). This planner treats fences, walls, panes, bars, intact barrier boards and BlockBlocker
	 * barriers as solid, so it goes around: through an open door, a broken barrier, up the stairs...
	 */
	private static void check(Game game, Mob mob, long tick)
	{
		Location goal = goalOf(game, mob);
		if(goal == null || !mob.isOnGround())
			return;
		// Already there, nothing in between: its own AI does the rest
		if(goal.distanceSquared(mob.getLocation()) < 4 && arrivesAt(mob.getLocation(), goal))
			return;
		if(reaches(mob, goal))
			return;
		if(plan(mob, goal, tick))
		{
			Plan made = PLANS.get(mob.getUniqueId());
			if(made != null)
				made.chase = goal.getY() > mob.getLocation().getY() - 3;
		}
	}

	/** Can its own pathfinder get (about) there? */
	private static boolean reaches(Mob mob, Location goal)
	{
		Pathfinder.PathResult path = mob.getPathfinder().getCurrentPath();
		if(path != null && path.getFinalPoint() != null && path.canReachFinalPoint() && arrivesAt(path.getFinalPoint(), goal))
			return true;
		path = mob.getPathfinder().findPath(goal);
		return path != null && path.getFinalPoint() != null && path.canReachFinalPoint() && arrivesAt(path.getFinalPoint(), goal);
	}

	/** @return false when this tick's search budget was used up (try again next tick) */
	private static boolean plan(Mob mob, Location goal, long tick)
	{
		if(budgetTick != tick)
		{
			budgetTick = tick;
			searchesLeft = SEARCHES_PER_TICK;
		}
		if(searchesLeft <= 0)
			return false;
		searchesLeft--;
		Location at = mob.getLocation();
		int maxDrop = Math.max(3, Math.min(32, COMZombies.getPlugin().getConfig().getInt("config.gameSettings.zombieSafeFallDistance", 16)));
		List<int[]> nodes = search(mob.getWorld(), at.getBlockX(), (int) Math.floor(at.getY() + 0.01), at.getBlockZ(),
				goal.getBlockX(), (int) Math.floor(goal.getY() + 0.01), goal.getBlockZ(), maxDrop, goal);
		if(nodes == null || nodes.size() < 2)
		{
			PLANS.remove(mob.getUniqueId());
			// Nothing better than what it does already: leave it be for a while
			NEXT_CHECK.put(mob.getUniqueId(), tick + 40);
			return true;
		}
		Plan plan = new Plan();
		plan.nodes = nodes;
		plan.index = 1;
		plan.goal = goal.clone();
		plan.madeTick = tick;
		PLANS.put(mob.getUniqueId(), plan);
		return true;
	}

	private static void follow(Game game, Mob mob, Plan plan, long tick)
	{
		Location at = mob.getLocation();
		Location goal = goalOf(game, mob);
		if(goal == null || goal.getWorld() != at.getWorld())
		{
			PLANS.remove(mob.getUniqueId());
			return;
		}
		// Close to the player (or barrier) with nothing in between, or (a drop plan) level with it now: its own AI carries on
		if(goal.distanceSquared(at) < 9 && arrivesAt(at, goal) || !plan.chase && goal.getY() > at.getY() - 3 && mob.isOnGround())
		{
			PLANS.remove(mob.getUniqueId());
			return;
		}
		if(goal.distanceSquared(plan.goal) > 16 || tick - plan.madeTick > 80)
		{
			if(mob.isOnGround() && reaches(mob, goal))
			{
				PLANS.remove(mob.getUniqueId());
				return;
			}
			boolean chase = plan.chase;
			if(mob.isOnGround() && !plan(mob, goal, tick))
				plan.madeTick = tick - 70; // no budget: try again in a moment, keep following meanwhile
			plan = PLANS.get(mob.getUniqueId());
			if(plan == null)
				return;
			plan.chase = plan.chase || chase;
		}
		while(plan.index < plan.nodes.size())
		{
			int[] n = plan.nodes.get(plan.index);
			double dx = n[0] + 0.5 - at.getX(), dz = n[2] + 0.5 - at.getZ();
			if(dx * dx + dz * dz < 0.6 && Math.abs(n[1] - at.getY()) < 1.5)
				plan.index++;
			else
				break;
		}
		if(plan.index >= plan.nodes.size())
		{
			// There: its own AI carries on
			PLANS.remove(mob.getUniqueId());
			return;
		}
		// Its own target-chasing would walk it back to the edge; the plugin gives the target back once the plan ends
		if(mob.getTarget() != null)
			mob.setTarget(null);
		int[] next = plan.nodes.get(plan.index);
		int feetY = (int) Math.floor(at.getY() + 0.01);
		if(next[1] < feetY - 3)
		{
			// A drop deeper than Minecraft's own pathfinder takes: walk straight off the edge
			mob.getPathfinder().stopPathfinding();
			if(++plan.pushTicks > 60)
			{
				// Could not get off the edge (something in the way): give up on this route for a while
				PLANS.remove(mob.getUniqueId());
				NEXT_CHECK.put(mob.getUniqueId(), tick + 40);
				return;
			}
			Vector dir = new Vector(next[0] + 0.5 - at.getX(), 0, next[2] + 0.5 - at.getZ());
			if(dir.lengthSquared() > 1.0E-4 && mob.isOnGround())
			{
				dir.normalize().multiply(0.3);
				mob.setVelocity(new Vector(dir.getX(), mob.getVelocity().getY(), dir.getZ()));
				mob.setRotation((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())), 0);
			}
			return;
		}
		if(!mob.isOnGround())
			return; // falling: let it land
		// Aim a few nodes ahead (smoother), not past a deep drop
		int aim = plan.index;
		for(int k = plan.index + 1; k < Math.min(plan.nodes.size(), plan.index + 4); k++)
		{
			if(plan.nodes.get(k)[1] < plan.nodes.get(k - 1)[1] - 3)
				break;
			aim = k;
		}
		int[] n = plan.nodes.get(aim);
		mob.getPathfinder().moveTo(new Location(at.getWorld(), n[0] + 0.5, n[1], n[2] + 0.5), 1.0);
	}

	// ------------------------------------------------------------------ A*

	private record Node(int x, int y, int z, double g, double f, Node parent) {}

	/**
	 * A* over the blocks: a zombie can stand where its feet and head cells are passable and the block below is solid; it can
	 * step up 1 block and drop down maxDrop.
	 *
	 * @return the route as feet positions from start to goal, or null when there is none within reach
	 */
	private static List<int[]> search(World world, int sx, int sy, int sz, int gx, int gy, int gz, int maxDrop)
	{
		return search(world, sx, sy, sz, gx, gy, gz, maxDrop, null);
	}

	private static List<int[]> search(World world, int sx, int sy, int sz, int gx, int gy, int gz, int maxDrop, Location goal)
	{
		Map<Long, Boolean> openCache = new HashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
		Map<Long, Double> best = new HashMap<>();
		open.add(new Node(sx, sy, sz, 0, h(sx, sy, sz, gx, gy, gz), null));
		best.put(key(sx, sy, sz), 0.0);
		int visited = 0;
		while(!open.isEmpty() && visited++ < MAX_NODES)
		{
			Node cur = open.poll();
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
		return null;
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
		boolean result = world.isChunkLoaded(x >> 4, z >> 4) && passableOrFlat(world.getBlockAt(x, y, z)) && !barrierCell(world, x, y, z)
				&& collisionTop(world.getBlockAt(x, y - 1, z)) <= 1.0001;
		cache.put(k, result);
		return result;
	}

	/** Asks BlockBlocker (if installed) whether the cell is part of one of its barriers. */
	private static boolean barrierCell(World world, int x, int y, int z)
	{
		if(!lookedUp)
		{
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
		barrierCell(world, 0, -10000, 0); // makes sure BlockBlocker was looked up
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
		for(double h : new double[] {y + 0.3, y + 1.2, top + 0.3, top + 1.2})
			if(segmentBlocked(world, x + 0.5, h, z + 0.5, nx + 0.5, h, nz + 0.5))
				return true;
		return false;
	}

	/**
	 * Is a step of a Minecraft path (from one point to the next) blocked by a BlockBlocker barrier? Minecraft's own
	 * pathfinder does not know about them, so a route it calls open may really run into one.
	 */
	public static boolean pathStepBlocked(Location from, Location to)
	{
		World world = to.getWorld();
		if(world == null)
			return false;
		int x = to.getBlockX(), y = to.getBlockY(), z = to.getBlockZ();
		if(barrierCell(world, x, y, z) || barrierCell(world, x, y + 1, z))
			return true;
		return from.getWorld() == world && moveBlocked(world, from.getBlockX(), from.getBlockY(), from.getBlockZ(), x, y, z);
	}

	private static long key(int x, int y, int z)
	{
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}
}
