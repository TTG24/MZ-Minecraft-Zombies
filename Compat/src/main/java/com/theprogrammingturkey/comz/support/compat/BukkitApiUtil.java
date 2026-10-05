package com.theprogrammingturkey.comz.support.compat;

import com.theprogrammingturkey.comz.api.INMSUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Version independent implementation that only uses the public Bukkit API,
 * replacing the old per Minecraft version NMS modules.
 */
public class BukkitApiUtil implements INMSUtil
{
	public void playChestAction(Location location, boolean open)
	{
		if(location.getWorld() == null)
			return;
		BlockState bs = location.getWorld().getBlockState(location);
		if(bs instanceof Lidded)
		{
			if(open)
				((Lidded) bs).open();
			else
				((Lidded) bs).close();
		}
	}

	public void playBlockBreakAction(List<Player> players, int damage, Block block)
	{
		// The API takes progress from 0.0 to 1.0 and turns it back into a crack stage (0-9).
		// 0.0 clears the cracks, matching the old packet's behaviour for negative stages.
		float progress = damage < 0 ? 0f : Math.min(1f, (damage + 0.5f) / 9f);

		// The client tracks one crack per source id, so give every block its own id
		// (negative so it never collides with a real entity id).
		int sourceId = -1 - ((((block.getX() * 31) + block.getY()) * 31 + block.getZ()) & 0x7FFFFFFF);

		Location loc = block.getLocation();
		for(Player player : players)
			player.sendBlockDamage(loc, progress, sourceId);
	}
}
