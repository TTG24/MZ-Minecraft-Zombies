package com.theprogrammingturkey.comz.util;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/** The custom resource-pack sounds (mz:*). Clients without the pack simply hear nothing. */
public final class MzSounds
{
	private MzSounds()
	{
	}

	/** The cash register style sound for a purchase: only the buyer hears it. */
	public static void purchase(Player player)
	{
		player.playSound(player.getLocation(), "mz:purchase", SoundCategory.PLAYERS, 1f, 1f);
	}
}
