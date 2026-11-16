package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.ApiCompat;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;

public abstract class RoundSpawner
{
	/** The largest value the follow range attribute accepts: as close to "infinite" as vanilla allows. */
	public static final int MAX_FOLLOW_RANGE = 2048;

	/**
	 * True while this plugin spawns a round's zombie or hellhound. Protection plugins (WorldGuard's "mob-spawning: deny" on
	 * the __global__ region, which instance worlds copy) cancel plugin spawns too, so the game would wait forever for zombies
	 * that never appeared; EntityListener lets spawns made while this is set through.
	 */
	public static boolean spawningOwn;

	/** Runs a spawn of the game's own, letting it through protection plugins (see spawningOwn). */
	public static <T> T ownSpawn(java.util.function.Supplier<T> spawn)
	{
		boolean was = spawningOwn;
		spawningOwn = true;
		try
		{
			return spawn.get();
		}
		finally
		{
			spawningOwn = was;
		}
	}

	public abstract Mob spawnEntity(Game game, SpawnPoint loc, int wave);

	public void setFollowDistance(Mob mob, int dist)
	{
		AttributeInstance attr = mob.getAttribute(ApiCompat.FOLLOW_RANGE);
		if(attr != null)
			attr.setBaseValue(Math.min(dist, MAX_FOLLOW_RANGE));
	}

	/**
	 * The pathfinder only plans drops up to the mob's safe fall distance (3 blocks by default), so zombies refused to jump
	 * down higher ledges and just stood at the edge. config.gameSettings.zombieSafeFallDistance raises it, and
	 * zombieFallDamage (false by default) decides whether they still get hurt by the fall.
	 */
	public void setFallTolerance(Mob mob)
	{
		org.bukkit.configuration.file.FileConfiguration config = com.theprogrammingturkey.comz.COMZombies.getPlugin().getConfig();
		double safe = config.getDouble("config.gameSettings.zombieSafeFallDistance", 16);
		if(ApiCompat.SAFE_FALL_DISTANCE != null)
		{
			AttributeInstance attr = mob.getAttribute(ApiCompat.SAFE_FALL_DISTANCE);
			if(attr != null)
				attr.setBaseValue(safe);
		}
		if(ApiCompat.FALL_DAMAGE_MULTIPLIER != null && !config.getBoolean("config.gameSettings.zombieFallDamage", false))
		{
			AttributeInstance attr = mob.getAttribute(ApiCompat.FALL_DAMAGE_MULTIPLIER);
			if(attr != null)
				attr.setBaseValue(0);
		}
	}

	public void setSpeed(Mob mob, float mult)
	{
		AttributeInstance attr = mob.getAttribute(ApiCompat.MOVEMENT_SPEED);
		if(attr != null)
			attr.setBaseValue(attr.getValue() * mult);
	}

	public void setMaxHealth(Mob mob, float strength)
	{
		AttributeInstance attr = mob.getAttribute(ApiCompat.MAX_HEALTH);
		if(attr != null)
			attr.setBaseValue(strength);
	}
}
