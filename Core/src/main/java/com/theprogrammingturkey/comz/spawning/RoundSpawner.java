package com.theprogrammingturkey.comz.spawning;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.ApiCompat;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;

public abstract class RoundSpawner
{
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

	/**
	 * Falls of up to config.gameSettings.zombieSafeFallDistance blocks don't hurt (MZ's planner takes drops that deep), and
	 * with zombieFallDamage false (the default) no fall does.
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
