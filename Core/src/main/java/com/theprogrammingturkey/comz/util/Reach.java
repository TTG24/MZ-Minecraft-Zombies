package com.theprogrammingturkey.comz.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * How far a player is from a block, for the interact-key prompts (doors, barriers, perks, guns, the box...): the gap
 * between the player's body and the block's cube, so "within 1 block" means one block of space in any direction
 * (sideways, diagonally, above or below), not a distance from the feet to the block's corner.
 */
public final class Reach
{
	private Reach()
	{
	}

	/** Squared gap between the player's hitbox and the block cell at loc (0 when touching or inside it). */
	public static double gapSquared(Player player, Location block)
	{
		if(block == null || block.getWorld() == null || !block.getWorld().equals(player.getWorld()))
			return Double.POSITIVE_INFINITY;
		BoundingBox box = player.getBoundingBox();
		int bx = block.getBlockX(), by = block.getBlockY(), bz = block.getBlockZ();
		double dx = gap(box.getMinX(), box.getMaxX(), bx, bx + 1);
		double dy = gap(box.getMinY(), box.getMaxY(), by, by + 1);
		double dz = gap(box.getMinZ(), box.getMaxZ(), bz, bz + 1);
		return dx * dx + dy * dy + dz * dz;
	}

	private static double gap(double aMin, double aMax, double bMin, double bMax)
	{
		return aMax < bMin ? bMin - aMax : bMax < aMin ? aMin - bMax : 0;
	}
}
