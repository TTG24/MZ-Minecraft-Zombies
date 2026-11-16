package com.theprogrammingturkey.comz.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;

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

	/** The other half of a double chest, or null for a single chest / not a chest. */
	public static Block doubleChestPartner(Block block)
	{
		if(!(block.getBlockData() instanceof org.bukkit.block.data.type.Chest data) || data.getType() == org.bukkit.block.data.type.Chest.Type.SINGLE)
			return null;
		for(org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[] {org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST})
		{
			Block other = block.getRelative(face);
			if(other.getType() == block.getType() && other.getBlockData() instanceof org.bukkit.block.data.type.Chest od
					&& od.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE && od.getType() != data.getType() && od.getFacing() == data.getFacing())
				return other;
		}
		return null;
	}

	public static boolean isZombiesSign(Block block)
	{
		return isSign(block) &&
				isZombiesHeader(Colors.plain(((org.bukkit.block.Sign) block.getState()).getSide(Side.FRONT).line(0)));
	}

	/** The first line of a game sign: "[Zombies]", or just "Zombies" without the brackets (any capitals). */
	public static boolean isZombiesHeader(String line)
	{
		String text = line == null ? "" : line.trim();
		return text.equalsIgnoreCase("[Zombies]") || text.equalsIgnoreCase("Zombies");
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
		if(data instanceof MultipleFacing dataFace)
		{
			Location loc = block.getLocation();
			dataFace.setFace(BlockFace.NORTH, !loc.add(0, 0, -1).getBlock().isPassable());
			dataFace.setFace(BlockFace.EAST, !loc.add(1, 0, 1).getBlock().isPassable());
			dataFace.setFace(BlockFace.SOUTH, !loc.add(-1, 0, 1).getBlock().isPassable());
			dataFace.setFace(BlockFace.WEST, !loc.add(-1, 0, -1).getBlock().isPassable());
			block.setBlockData(data);
		}
	}

	/**
	 * The full block state as text ("minecraft:cobblestone_wall[east=low,north=none,...]"), so a door/barrier block comes
	 * back exactly as it was built (wall connections, stair facing and shape, slab half...), not as a freshly placed block.
	 */
	public static String dataOf(Block block)
	{
		return block.getType().isAir() ? null : block.getBlockData().getAsString();
	}

	/** Parses saved block data, null when missing, unreadable, or not this material (then the material alone is used). */
	public static BlockData parseData(String data, Material mat)
	{
		if(data == null || data.isEmpty() || mat == null)
			return null;
		try
		{
			BlockData d = org.bukkit.Bukkit.createBlockData(data);
			return d.getMaterial() == mat ? d : null;
		}
		catch(IllegalArgumentException e)
		{
			return null;
		}
	}

	/**
	 * Puts a door's or barrier's blocks back. Each block gets its saved state exactly; blocks without one are placed as
	 * before (setBlockTypeHelper). Spots that already hold the right block are left alone (onlyMissing), so an intact
	 * door/barrier is never rebuilt. Placed twice: once with physics, so the blocks around it (a wall beside a door) join
	 * back on, then again without, so the blocks' own states stay exactly as saved instead of being re-worked out.
	 */
	public static void restoreBlocks(java.util.Map<Block, Material> mats, java.util.Map<Block, String> data)
	{
		java.util.Map<Block, BlockData> exact = new java.util.LinkedHashMap<>();
		for(java.util.Map.Entry<Block, Material> e : mats.entrySet())
		{
			Block b = e.getKey();
			Material mat = e.getValue();
			if(mat == null || mat.isAir() || b.getType() == mat)
				continue;
			BlockData d = parseData(data.get(b), mat);
			if(d == null)
				setBlockTypeHelper(b, mat);
			else
			{
				b.setBlockData(d, true);
				exact.put(b, d);
			}
		}
		for(java.util.Map.Entry<Block, BlockData> e : exact.entrySet())
			e.getKey().setBlockData(e.getValue(), false);
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
