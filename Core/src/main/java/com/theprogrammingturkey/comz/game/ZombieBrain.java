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
	}

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

		if(player != null)
		{
			attack(game, mob, player, s, tick);
			if(at.distanceSquared(player.getLocation()) < 64)
				mob.lookAt(player);
		}

		// Right there with nothing in between: straight to it (a player is a moving target; a barrier spot is just reached)
		if(at.distanceSquared(goal) < 6.25 && ZombiePathing.arrivesAt(at, goal))
		{
			s.route = null;
			if(held == null || at.distanceSquared(goal) > 0.09)
				walkTo(mob, goal.getX(), goal.getY(), goal.getZ());
			return;
		}

		boolean stuck = isStuck(s, at, tick);
		boolean replan = s.route == null || s.routeGoal == null || s.routeGoal.getWorld() != goal.getWorld()
				|| s.routeGoal.distanceSquared(goal) > 4 || tick - s.routeTick > REPLAN_TICKS || stuck;
		if(replan && tick >= s.failedUntil && mob.isOnGround() && takeSearch(tick))
		{
			int maxDrop = Math.max(3, Math.min(32, COMZombies.getPlugin().getConfig().getInt("config.gameSettings.zombieSafeFallDistance", 16)));
			List<int[]> route = ZombiePathing.search(mob.getWorld(), at.getBlockX(), ZombiePathing.feetCell(at), at.getBlockZ(),
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
			if(dx * dx + dz * dz < 0.36 && Math.abs(n[1] - at.getY()) < 1.5)
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
			if(n[1] != first[1] || !ZombiePathing.walkableLine(mob.getWorld(), at, n))
				break;
			aim = k;
		}
		int[] next = s.route.get(aim);
		walkTo(mob, next[0] + 0.5, next[1], next[2] + 0.5);
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
