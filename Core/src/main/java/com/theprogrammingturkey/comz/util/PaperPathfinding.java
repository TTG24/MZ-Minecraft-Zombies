package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Walks mobs to a location with Paper's pathfinder (Mob#getPathfinder). Spigot has no equivalent, so it is looked
 * up by reflection: the plugin still builds against the Spigot API and simply can't steer mobs on Spigot servers.
 */
public class PaperPathfinding
{
	private static final Method GET_PATHFINDER;
	private static final Method MOVE_TO;
	private static final Method FIND_PATH;
	private static final Method CAN_REACH_FINAL_POINT;
	private static final Method GET_POINTS;

	static
	{
		Method getPathfinder = null;
		Method moveTo = null;
		Method findPath = null;
		Method canReach = null;
		Method getPoints = null;
		try
		{
			getPathfinder = Mob.class.getMethod("getPathfinder");
			Class<?> pathfinder = Class.forName("com.destroystokyo.paper.entity.Pathfinder");
			moveTo = pathfinder.getMethod("moveTo", Location.class);
			// Optional extras: without them canReach() just answers "unknown" (false).
			try
			{
				findPath = pathfinder.getMethod("findPath", Location.class);
				Class<?> pathResult = Class.forName("com.destroystokyo.paper.entity.Pathfinder$PathResult");
				canReach = pathResult.getMethod("canReachFinalPoint");
				try
				{
					getPoints = pathResult.getMethod("getPoints");
				} catch(ReflectiveOperationException ignored)
				{
					getPoints = null; // then route lengths are simply unknown
				}
			} catch(ReflectiveOperationException ignored)
			{
				findPath = null;
				canReach = null;
			}
		} catch(ReflectiveOperationException e)
		{
			COMZombies.log.info("Paper's pathfinder isn't available, so zombies won't be steered to barriers.");
		}
		GET_PATHFINDER = getPathfinder;
		MOVE_TO = moveTo;
		FIND_PATH = findPath;
		CAN_REACH_FINAL_POINT = canReach;
		GET_POINTS = getPoints;
	}

	/**
	 * How long the mob's walk to the location would be, in blocks, if there is a real route to it right now. Only asks the
	 * pathfinder; it does not move the mob.
	 *
	 * @return the length, or -1 when there is no route or the pathfinder can't say
	 */
	public static double routeLength(Mob mob, Location location)
	{
		if(FIND_PATH == null || CAN_REACH_FINAL_POINT == null || GET_POINTS == null || location == null || !mob.isValid())
			return -1;
		try
		{
			Object result = FIND_PATH.invoke(GET_PATHFINDER.invoke(mob), location);
			if(result == null || !Boolean.TRUE.equals(CAN_REACH_FINAL_POINT.invoke(result)))
				return -1;
			java.util.List<?> points = (java.util.List<?>) GET_POINTS.invoke(result);
			if(points == null || points.isEmpty())
				return -1;
			double length = 0;
			Location previous = mob.getLocation();
			for(Object point : points)
			{
				Location next = (Location) point;
				if(previous.getWorld() != null && previous.getWorld().equals(next.getWorld()))
					length += previous.distance(next);
				previous = next;
			}
			return length;
		} catch(ReflectiveOperationException | ClassCastException e)
		{
			COMZombies.log.log(Level.WARNING, "Failed to measure a mob's route", e);
		}
		return -1;
	}

	/**
	 * Is there a real route for this mob to the location right now (e.g. through a window that is already broken)?
	 * Only asks the pathfinder; it does not move the mob. False when unknown or when the route is blocked.
	 */
	public static boolean canReach(Mob mob, Location location)
	{
		if(FIND_PATH == null || CAN_REACH_FINAL_POINT == null || location == null || !mob.isValid())
			return false;
		try
		{
			Object result = FIND_PATH.invoke(GET_PATHFINDER.invoke(mob), location);
			return result != null && Boolean.TRUE.equals(CAN_REACH_FINAL_POINT.invoke(result));
		} catch(ReflectiveOperationException e)
		{
			COMZombies.log.log(Level.WARNING, "Failed to ask a mob's pathfinder for a route", e);
		}
		return false;
	}

	public static boolean isAvailable()
	{
		return MOVE_TO != null;
	}

	/**
	 * Starts the mob walking towards the location, or as close as it can get to it.
	 */
	public static boolean moveTo(Mob mob, Location location)
	{
		if(!isAvailable() || location == null)
			return false;
		try
		{
			return Boolean.TRUE.equals(MOVE_TO.invoke(GET_PATHFINDER.invoke(mob), location));
		} catch(ReflectiveOperationException e)
		{
			COMZombies.log.log(Level.WARNING, "Failed to steer a mob", e);
		}
		return false;
	}
}
