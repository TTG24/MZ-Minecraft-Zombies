package com.theprogrammingturkey.comz.game;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.util.PeacefulArenaMobs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The arena mobs' AI (zombies and hellhounds). Minecraft's AI goals are taken off each mob and this decides everything it
 * does each tick:
 * <ul>
 * <li>where to go: the barrier it is held to (BarrierManager / Barrier decide which, and when it is through), otherwise
 * the nearest player;</li>
 * <li>how to get there: always a route from {@link ZombiePathing}'s planner, which knows what Minecraft's pathfinder
 * doesn't (BlockBlocker barriers are solid like stone, with tops to stand on; deep drops; stairs and slabs; fences and walls
 * are 1.5 high), re-planned when the goal moves or the zombie stops getting anywhere;</li>
 * <li>walking it: Minecraft's own movement control is given the next step of the route, so it turns, walks and jumps up
 * steps like a normal mob;</li>
 * <li>hitting: a player within reach with nothing in between (ZombiePathing.canHit) is hit about once a second.</li>
 * </ul>
 * Rising zombies (no AI while they dig out) and zombies held still (a crawler falling) are left alone. Hellhounds are
 * never held at barriers: they go straight for the nearest player.
 */
public final class ZombieBrain
{
	private static final int SEARCHES_PER_TICK = 4;
	private static final int REPLAN_TICKS = 40;
	private static final int FAILED_RETRY_TICKS = 60;
	private static final double REACH_SQUARED = 2.4;
	private static final int ATTACK_TICKS = 20;

	private static final class State
	{
		List<int[]> route;
		int index;
		Location routeGoal;
		long routeTick = Long.MIN_VALUE / 2;
		long failedUntil;
		Location stuckFrom;
		long stuckSince;
		long lastAttack = Long.MIN_VALUE / 2;
		/** What it is doing (for /zombies debug zombies and the stuck log), and where it is going. */
		String job = "";
		Location goal;
		/** Where it last made real progress (a block), and when; whether this lack of progress has been logged. */
		Location progressFrom;
		long progressSince;
		boolean reported;
	}

	/** A mob's AI as /zombies debug zombies shows it. {@code route} null: none (straight to its goal, or no way there). */
	public record View(String job, Location goal, List<int[]> route, int index, boolean noRoute, long stuckTicks) {}

	/** What the mob's AI is doing now, or null if it has none yet. */
	public static View view(Mob mob, long tick)
	{
		State s = STATES.get(mob.getUniqueId());
		if(s == null)
			return null;
		return new View(s.job, s.goal, s.route, s.index, s.route == null && tick < s.failedUntil, tick - s.progressSince);
	}

	/** No progress (a block) for this long, while not where it means to be: logged once, with what it was doing. */
	private static final int STUCK_REPORT_TICKS = 100;

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static long budgetTick = -1;
	private static int searchesLeft;

	private ZombieBrain()
	{
	}

	public static void start(Plugin plugin)
	{
		Bukkit.getScheduler().runTaskTimer(plugin, ZombieBrain::tick, 1L, 1L);
	}

	/** Forget its route (it was teleported somewhere else, e.g. respawned near a player). */
	public static void forget(Mob mob)
	{
		STATES.remove(mob.getUniqueId());
	}

	private static void tick()
	{
		long tick = Bukkit.getCurrentTick();
		for(Game game : GameManager.INSTANCE.getGames())
		{
			if(game.getStatus() != Game.GameStatus.INGAME)
				continue;
			for(Mob mob : new ArrayList<>(game.spawnManager.getEntities()))
				if(mob.isValid())
					think(game, mob, tick);
		}
		if(tick % 100 == 0)
			STATES.keySet().removeIf(id -> !(Bukkit.getEntity(id) instanceof Mob m) || !m.isValid());
		if(tick % 5 == 0)
			ZombieDebug.update(tick);
	}

	private static void think(Game game, Mob mob, long tick)
	{
		State s = STATES.get(mob.getUniqueId());
		if(s == null)
		{
			s = new State();
			STATES.put(mob.getUniqueId(), s);
			// Minecraft's own AI off for good: nothing else picks its targets or walks it
			Bukkit.getMobGoals().removeAllGoals(mob);
			mob.getPathfinder().stopPathfinding();
		}
		if(mob.getTarget() != null)
			mob.setTarget(null);
		if(!mob.hasAI())
			return; // digging itself up (ZombieRise)
		// Never riding anything (e.g. a chicken jockey Minecraft rolled): off it, and the mount goes
		if(mob.getVehicle() != null)
		{
			org.bukkit.entity.Entity mount = mob.getVehicle();
			mob.leaveVehicle();
			if(!(mount instanceof Player))
				mount.remove();
		}

		Barrier held = game.barrierManager.heldBy(mob);
		Player player = held == null ? game.spawnManager.getNearestPlayer(mob) : null;
		Location goal = held != null ? held.approachFor(mob) : player != null ? player.getLocation() : null;
		if(goal == null || goal.getWorld() != mob.getWorld())
			return;
		Location at = mob.getLocation();
		s.job = held != null ? "window " + held.getID() : player != null ? "hunting " + player.getName() : "idle";
		s.goal = goal;
		watchProgress(mob, s, at, goal, held, player, tick);

		if(player != null)
		{
			attack(game, mob, player, s, tick);
			if(at.distanceSquared(player.getLocation()) < 64)
				mob.lookAt(player);
		}

		// Right there with nothing in between: straight to it (a player is a moving target; a barrier spot is just reached)
		// ...unless it has stopped moving: the line was clear but its body is caught on something, so it plans a way round
		// (not one standing still where it means to be: waiting at its spot or in reach of the player)
		boolean stuck = isStuck(s, at, tick) && at.distanceSquared(goal) > 1.0;
		if(!stuck && at.distanceSquared(goal) < 6.25 && ZombiePathing.arrivesAt(at, goal))
		{
			s.route = null;
			// (level distance: on a half slab it stands half a block below its spot, which is at the window's floor)
			double fx = at.getX() - goal.getX(), fz = at.getZ() - goal.getZ();
			if(held == null || fx * fx + fz * fz > 0.25)
				walkTo(mob, goal.getX(), goal.getY(), goal.getZ());
			else
			{
				// At its spot: stand still facing the boards. Walking on to a point it is already on made Minecraft's
				// movement overshoot and flip its heading every tick, so it spun on the spot.
				Location face = held.getNearestBlockCenter(mob);
				if(face != null)
					mob.lookAt(face.getX(), face.getY() + 0.5, face.getZ());
			}
			return;
		}

		boolean replan = s.route == null || s.routeGoal == null || s.routeGoal.getWorld() != goal.getWorld()
				|| s.routeGoal.distanceSquared(goal) > 4 || tick - s.routeTick > REPLAN_TICKS || stuck;
		if(replan && tick >= s.failedUntil && mob.isOnGround() && takeSearch(tick))
		{
			int maxDrop = Math.max(3, Math.min(32, COMZombies.getPlugin().getConfig().getInt("config.gameSettings.zombieSafeFallDistance", 16)));
			List<int[]> route = ZombiePathing.search(mob.getWorld(), at.getBlockX(), ZombiePathing.feetCell(at), at.getBlockZ(), at,
					goal.getBlockX(), ZombiePathing.feetCell(goal), goal.getBlockZ(), maxDrop, goal, true);
			s.routeTick = tick;
			s.routeGoal = goal.clone();
			s.stuckFrom = null;
			if(route == null || route.size() < 2)
			{
				// No way there from here (walled in, or too far round): try again in a while, standing still meanwhile
				s.route = null;
				s.failedUntil = tick + FAILED_RETRY_TICKS;
			}
			else
			{
				s.route = route;
				s.index = 1;
			}
		}
		if(s.route == null)
			return;

		// The next step it hasn't reached yet
		while(s.index < s.route.size())
		{
			int[] n = s.route.get(s.index);
			double dx = n[0] + 0.5 - at.getX(), dz = n[2] + 0.5 - at.getZ();
			// Where the route turns, it walks nearer the middle of the step before heading on: turning early cut the corner,
			// it scraped along the block there and slowed down
			boolean turns = s.index > 0 && s.index + 1 < s.route.size() && turnsAt(s.route, s.index);
			// The last step all the way to its middle: that is where the planner found the goal in reach, and stopping 0.6 short
			// (beside a wall corner) could leave it where it isn't
			boolean last = s.index == s.route.size() - 1;
			double near = last ? 0.2 * 0.2 : turns ? 0.3 * 0.3 : 0.36;
			if(dx * dx + dz * dz < near && Math.abs(ZombiePathing.floorOf(n) - at.getY()) < 1.0)
				s.index++;
			else
				break;
		}
		if(s.index >= s.route.size())
		{
			s.route = null; // there; the next tick goes straight to the goal or plans again
			return;
		}
		// The furthest of the next few steps on the same level that it can walk to in a straight line: smoother than going
		// block by block (which zig-zags on a diagonal)
		int aim = s.index;
		int[] first = s.route.get(s.index);
		for(int k = s.index + 1; k < Math.min(s.route.size(), s.index + 5); k++)
		{
			int[] n = s.route.get(k);
			// (on one level: no step it would have to jump between them)
			if(Math.abs(ZombiePathing.floorOf(n) - ZombiePathing.floorOf(first)) > ZombiePathing.STEP
					|| !ZombiePathing.walkableLine(mob.getWorld(), at, n))
				break;
			aim = k;
		}
		int[] next = s.route.get(aim);
		// to its real floor: Minecraft's movement jumps when that is more than a step above its feet, and only then
		walkTo(mob, next[0] + 0.5, ZombiePathing.floorOf(next), next[2] + 0.5);
	}

	/**
	 * Heading diagonally into a wall, Minecraft drops the part of the move that goes into it and the mob slides along at a
	 * fraction of its speed. When a short step towards the point would hit a block but a step along x or along z alone
	 * would not, it is aimed straight along the wall instead (as far as the point is along that way), so it slides at full
	 * speed. Not for a step up (a block it is about to jump onto is not a wall).
	 *
	 * @return the x and z to walk to
	 */
	private static double[] alongWall(Mob mob, double x, double y, double z)
	{
		Location at = mob.getLocation();
		double dx = x - at.getX(), dz = z - at.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if(len < 0.3 || y > at.getY() + 0.5)
			return new double[] {x, z};
		org.bukkit.World world = mob.getWorld();
		// its real box, a hair above the floor and a hair narrower (so the floor and a wall it already touches don't count)
		org.bukkit.util.BoundingBox box = mob.getBoundingBox().expand(-0.01, -0.01, -0.01);
		double step = 0.2;
		if(free(world, box, dx / len * step, dz / len * step))
			return new double[] {x, z};
		boolean xFree = Math.abs(dx) > 1.0E-3 && free(world, box, Math.signum(dx) * step, 0);
		boolean zFree = Math.abs(dz) > 1.0E-3 && free(world, box, 0, Math.signum(dz) * step);
		double[] slid;
		if(xFree && (!zFree || Math.abs(dx) >= Math.abs(dz)))
			slid = new double[] {x, at.getZ()};
		else if(zFree)
			slid = new double[] {at.getX(), z};
		else
			return new double[] {x, z};
		// Aimed at a point right beside itself it would overshoot and turn on the spot every tick: keep the real aim
		double sx = slid[0] - at.getX(), sz = slid[1] - at.getZ();
		if(sx * sx + sz * sz < 0.3 * 0.3)
			return new double[] {x, z};
		return slid;
	}

	/**
	 * Can its body make this small move, the way Minecraft moves a mob: straight on, or lifted by a step's height (0.6) when
	 * that is in the way (a slab, stair or snow it walks up onto is not a wall)? Its real box is tested both times, so a
	 * low ceiling it fits under never counts as a wall (testing a box simply raised 0.6 hit such ceilings, and zombies in a
	 * low passage circled on the spot).
	 */
	private static boolean free(org.bukkit.World world, org.bukkit.util.BoundingBox box, double mx, double mz)
	{
		org.bukkit.util.BoundingBox moved = box.clone().shift(mx, 0, mz);
		if(!world.hasCollisionsIn(moved))
			return true;
		// stepping up: room above where it stands, then room for the move up there (a half-block step only needs a half
		// block of lift, so a low lintel over a window it fits through does not count either)
		for(double lift : new double[] {0.51, 0.6})
			if(!world.hasCollisionsIn(box.clone().shift(0, lift, 0)) && !world.hasCollisionsIn(moved.clone().shift(0, lift, 0)))
				return true;
		return false;
	}

	/** Does the route change direction at step i (the way in differs from the way out)? */
	private static boolean turnsAt(List<int[]> route, int i)
	{
		int[] a = route.get(i - 1), b = route.get(i), c = route.get(i + 1);
		return Integer.signum(b[0] - a[0]) != Integer.signum(c[0] - b[0]) || Integer.signum(b[2] - a[2]) != Integer.signum(c[2] - b[2]);
	}

	/** Hasn't moved half a block in the last second although it is trying to: its route needs redoing. */
	private static boolean isStuck(State s, Location at, long tick)
	{
		if(s.stuckFrom == null || s.stuckFrom.getWorld() != at.getWorld() || s.stuckFrom.distanceSquared(at) > 0.25)
		{
			s.stuckFrom = at.clone();
			s.stuckSince = tick;
			return false;
		}
		return tick - s.stuckSince > 20;
	}

	/**
	 * Watches that the mob gets somewhere: a block of progress, or being where it means to be (waiting at its window spot,
	 * or within reach of its player), counts. {@link #STUCK_REPORT_TICKS} without either and it is logged once, with what it
	 * was doing and what is in its way, so a zombie that got stuck can be explained from the log alone.
	 */
	private static void watchProgress(Mob mob, State s, Location at, Location goal, Barrier held, Player player, long tick)
	{
		double fx = at.getX() - goal.getX(), fz = at.getZ() - goal.getZ();
		// (in reach of its player only counts if it can really hit them: beside a corner it can be in reach and blocked)
		boolean content = (held != null && fx * fx + fz * fz < 0.36)
				|| (player != null && at.distanceSquared(player.getLocation()) <= REACH_SQUARED * 1.5 && ZombiePathing.canHit(mob, player, null));
		if(content || s.progressFrom == null || s.progressFrom.getWorld() != at.getWorld() || s.progressFrom.distanceSquared(at) > 1.0)
		{
			s.progressFrom = at.clone();
			s.progressSince = tick;
			s.reported = false;
			return;
		}
		if(s.reported || tick - s.progressSince < STUCK_REPORT_TICKS)
			return;
		s.reported = true;
		COMZombies.log.warning(stuckReport(mob, s, at, goal, tick));
	}

	private static String stuckReport(Mob mob, State s, Location at, Location goal, long tick)
	{
		StringBuilder r = new StringBuilder("[ZombieBrain] ");
		r.append(mob.getType().name().toLowerCase()).append(String.format(" no progress for %ds at %.2f %.2f %.2f (%s)", (tick - s.progressSince) / 20,
				at.getX(), at.getY(), at.getZ(), at.getWorld().getName()));
		r.append(" | ").append(s.job).append(String.format(", goal %.2f %.2f %.2f (%.1f away)", goal.getX(), goal.getY(), goal.getZ(),
				Math.sqrt(at.distanceSquared(goal))));
		double aimX = goal.getX(), aimZ = goal.getZ();
		if(s.route != null && s.index < s.route.size())
		{
			int[] n = s.route.get(s.index);
			aimX = n[0] + 0.5;
			aimZ = n[2] + 0.5;
			r.append(String.format(" | route step %d/%d: %d %.2f %d", s.index, s.route.size() - 1, n[0], ZombiePathing.floorOf(n), n[2]));
		}
		else
			r.append(tick < s.failedUntil ? " | no way there found (waiting to try again)" : " | going straight to it");
		// What is in front of it, towards where it is heading
		double dx = aimX - at.getX(), dz = aimZ - at.getZ(), len = Math.sqrt(dx * dx + dz * dz);
		if(len > 1.0E-3)
		{
			org.bukkit.block.Block ahead = at.getWorld().getBlockAt((int) Math.floor(at.getX() + dx / len * 0.6), (int) Math.floor(at.getY() + 0.01),
					(int) Math.floor(at.getZ() + dz / len * 0.6));
			r.append(" | ahead: below=").append(name(ahead.getRelative(0, -1, 0))).append(", feet=").append(name(ahead))
					.append(", head=").append(name(ahead.getRelative(0, 1, 0))).append(", above=").append(name(ahead.getRelative(0, 2, 0)));
		}
		r.append(" | standing on ").append(name(at.getWorld().getBlockAt(at.getBlockX(), (int) Math.floor(at.getY() - 0.01), at.getBlockZ())));
		return r.toString();
	}

	private static String name(org.bukkit.block.Block b)
	{
		return b.getBlockData().getAsString().replace("minecraft:", "");
	}

	private static boolean takeSearch(long tick)
	{
		if(budgetTick != tick)
		{
			budgetTick = tick;
			searchesLeft = SEARCHES_PER_TICK;
		}
		if(searchesLeft <= 0)
			return false;
		searchesLeft--;
		return true;
	}

	/** Hits the player when they are within reach with nothing in between, about once a second. */
	private static void attack(Game game, Mob mob, Player player, State s, long tick)
	{
		if(tick - s.lastAttack < ATTACK_TICKS || game.downedPlayerManager.isDownedPlayer(player)
				|| mob.getLocation().distanceSquared(player.getLocation()) > REACH_SQUARED
				|| !ZombiePathing.canHit(mob, player, null))
			return;
		s.lastAttack = tick;
		game.spawnManager.markActive(mob);
		PeacefulArenaMobs.attackPlayer(mob, player);
	}

	// ------------------------------------------------------------------------------------------- Minecraft's movement

	private static Method getHandle, getMoveControl, setWantedPosition;
	private static boolean moveControlBroken;

	/**
	 * Walks the mob towards a point with Minecraft's own movement control (it turns to it, walks at the mob's speed and
	 * jumps when the point is a step up), or, if that can't be reached on this server, by setting its velocity directly.
	 */
	private static void walkTo(Mob mob, double x, double y, double z)
	{
		double[] slid = alongWall(mob, x, y, z);
		x = slid[0];
		z = slid[1];
		if(!moveControlBroken)
		{
			try
			{
				if(getHandle == null)
					getHandle = mob.getClass().getMethod("getHandle");
				Object handle = getHandle.invoke(mob);
				if(getMoveControl == null)
					getMoveControl = handle.getClass().getMethod("getMoveControl");
				Object control = getMoveControl.invoke(handle);
				if(setWantedPosition == null)
					setWantedPosition = control.getClass().getMethod("setWantedPosition", double.class, double.class, double.class, double.class);
				setWantedPosition.invoke(control, x, y, z, 1.0);
				return;
			}
			catch(ReflectiveOperationException | RuntimeException e)
			{
				moveControlBroken = true;
				COMZombies.log.warning("[ZombieBrain] Minecraft's movement control can't be used (" + e + "); zombies are moved directly");
			}
		}
		Location at = mob.getLocation();
		Vector dir = new Vector(x - at.getX(), 0, z - at.getZ());
		if(dir.lengthSquared() < 1.0E-4)
			return;
		var speed = mob.getAttribute(com.theprogrammingturkey.comz.util.ApiCompat.MOVEMENT_SPEED);
		double perTick = (speed != null ? speed.getValue() : 0.23) * 0.5;
		dir.normalize().multiply(perTick);
		boolean jump = y > at.getY() + 0.6 && mob.isOnGround() && dir.lengthSquared() > 0
				&& (x - at.getX()) * (x - at.getX()) + (z - at.getZ()) * (z - at.getZ()) < 1.6 * 1.6;
		mob.setVelocity(new Vector(dir.getX(), jump ? 0.42 : mob.getVelocity().getY(), dir.getZ()));
		mob.setRotation((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())), 0);
	}
}
