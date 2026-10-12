package com.theprogrammingturkey.comz.util;

import java.lang.reflect.Method;
import java.util.logging.Level;

import org.bukkit.Difficulty;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;

import com.theprogrammingturkey.comz.COMZombies;

/** Keeps plugin-spawned zombies in Peaceful using Paper/Purpur's per-mob override. */
public final class PeacefulArenaMobs
{
	private static final Method SET_OVERRIDE;
	private static final Object KEEP_IN_PEACEFUL;
	private static boolean warned;

	static
	{
		Method setter = null;
		Object value = null;
		try
		{
			Class<?> triState = Class.forName("net.kyori.adventure.util.TriState");
			setter = Mob.class.getMethod("setDespawnInPeacefulOverride", triState);
			value = triState.getField("FALSE").get(null);
		}
		catch(ReflectiveOperationException ignored)
		{
			// Spigot has no equivalent. Do not change the entire world's difficulty.
		}
		SET_OVERRIDE = setter;
		KEEP_IN_PEACEFUL = value;
	}

	private PeacefulArenaMobs() {}

	/** Vanilla refuses player targets on Peaceful, even with the despawn override. */
	public static boolean needsControlledCombat(Mob mob)
	{
		return mob.getWorld().getDifficulty() == Difficulty.PEACEFUL
				&& COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.zombiesInPeaceful", true);
	}

	public static void attackPlayer(Mob mob, Player player)
	{
		// A direct attacker keeps the normal EntityDamageByEntityEvent and armor handling.
		// Leave the causing entity unset so vanilla does not scale damage to zero on Peaceful.
		DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(mob).build();
		mob.swingMainHand();
		player.damage(1, source); // EntityListener applies arena damage, perks, downing, and healing rules.
	}

	/** Called before the zombie is added to the world. Applies even if difficulty changes later. */
	public static void prepare(Mob mob)
	{
		if(!COMZombies.getPlugin().getConfig().getBoolean("config.gameSettings.zombiesInPeaceful", true))
			return;
		if(SET_OVERRIDE == null)
		{
			if(!warned && mob.getWorld().getDifficulty() == Difficulty.PEACEFUL)
			{
				warned = true;
				COMZombies.log.warning("Arena zombies on Peaceful require Paper/Purpur with the per-mob Peaceful override (supported on 26.3). This server does not expose that API.");
			}
			return;
		}
		try
		{
			SET_OVERRIDE.invoke(mob, KEEP_IN_PEACEFUL);
		}
		catch(ReflectiveOperationException e)
		{
			if(!warned)
			{
				warned = true;
				COMZombies.log.log(Level.WARNING, "Could not keep arena zombies alive on Peaceful", e);
			}
		}
	}
}
