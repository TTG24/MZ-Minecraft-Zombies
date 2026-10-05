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

	static
	{
		Method getPathfinder = null;
		Method moveTo = null;
		try
		{
			getPathfinder = Mob.class.getMethod("getPathfinder");
			moveTo = Class.forName("com.destroystokyo.paper.entity.Pathfinder").getMethod("moveTo", Location.class);
		} catch(ReflectiveOperationException e)
		{
			COMZombies.log.info("Paper's pathfinder isn't available, so zombies won't be steered to barriers.");
		}
		GET_PATHFINDER = getPathfinder;
		MOVE_TO = moveTo;
	}

	public static boolean isAvailable()
	{
		return MOVE_TO != null;
	}

	/**
	 * Starts the mob walking towards the location, or as close as it can get to it.
	 */
	public static void moveTo(Mob mob, Location location)
	{
		if(!isAvailable())
			return;
		try
		{
			MOVE_TO.invoke(GET_PATHFINDER.invoke(mob), location);
		} catch(ReflectiveOperationException e)
		{
			COMZombies.log.log(Level.WARNING, "Failed to steer a mob", e);
		}
	}
}
