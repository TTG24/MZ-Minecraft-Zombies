package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;

public class BlockUtils
{
	public static boolean isWallSign(Material mat)
	{
		return mat.data == WallSign.class;
	}

	public static boolean isStandingSign(Material mat)
	{
		return mat.data == Sign.class;
	}

	public static boolean isSign(Block block)
	{
		return block != null && isSign(block.getType());
	}

	public static boolean isSign(Material mat)
	{
		return isStandingSign(mat) || isWallSign(mat);
	}

	public static boolean isZombiesSign(Block block)
	{
		return isSign(block) &&
				ChatColor.stripColor(((org.bukkit.block.Sign) block.getState()).getLine(0)).equalsIgnoreCase("[Zombies]");
	}

	/**
	 * Checks by location, not sign text, so the sign text can be anything.
	 */
	public static boolean isBarrierRepairSign(Block block, Player player)
	{
		if(!isSign(block) || !GameManager.INSTANCE.isPlayerInGame(player))
			return false;
		Game game = GameManager.INSTANCE.getGame(player);
		return game.barrierManager.getBarrierFromRepair(block.getLocation()) != null;
	}

	public static Material getMaterialFromKey(String key)
	{
		return getMaterialFromKey(NamespacedKey.minecraft(key));
	}

	public static Material getMaterialFromKey(NamespacedKey key)
	{
		for(Material mat : Material.values())
			if(mat.getKey().equals(key))
				return mat;
		return Material.IRON_BARS;
	}

	public static void setBlockTypeHelper(Block block, Material type)
	{
		block.setType(type);
		BlockData data = block.getBlockData();
		if(data instanceof MultipleFacing)
		{
			Location loc = block.getLocation();
			MultipleFacing dataFace = (MultipleFacing) data;
			dataFace.setFace(BlockFace.NORTH, !loc.add(0, 0, -1).getBlock().isPassable());
			dataFace.setFace(BlockFace.EAST, !loc.add(1, 0, 1).getBlock().isPassable());
			dataFace.setFace(BlockFace.SOUTH, !loc.add(-1, 0, 1).getBlock().isPassable());
			dataFace.setFace(BlockFace.WEST, !loc.add(-1, 0, -1).getBlock().isPassable());
			block.setBlockData(data);
		}
	}

	public static void setBlockToAir(Location location)
	{
		setBlockToAir(location.getBlock());
	}

	public static void setBlockToAir(Block block)
	{
		block.setType(Material.AIR);
	}
}
