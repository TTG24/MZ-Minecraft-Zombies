package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.util.CommandUtil;

import java.util.HashMap;
import java.util.Map;

public class MysteryBoxSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "mystery_box";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines)
	{
		String cost = lines[2];
		if(!cost.matches("[0-9]{1,6}"))
			cost = "950";

		Map<String, String> data = new HashMap<>();
		data.put("price", cost);
		return data;
	}

	@Override
	public void onCreate(Game game, Player player, Location location, Map<String, String> data)
	{
		// A wall sign faces a side; a standing (floor) sign has a rotation instead (casting it to Directional used to throw)
		var blockData = location.getBlock().getBlockData();
		BlockFace facing;
		if(blockData instanceof Directional directional)
			facing = directional.getFacing();
		else if(blockData instanceof org.bukkit.block.data.Rotatable rotatable)
			facing = rotatable.getRotation();
		else
			facing = player.getFacing().getOppositeFace();
		// The box's sign is redrawn as a wall sign, which only faces north, east, south or west
		facing = switch(facing)
		{
			case NORTH, NORTH_NORTH_EAST, NORTH_NORTH_WEST -> BlockFace.NORTH;
			case EAST, EAST_NORTH_EAST, EAST_SOUTH_EAST, NORTH_EAST -> BlockFace.EAST;
			case SOUTH, SOUTH_SOUTH_EAST, SOUTH_SOUTH_WEST, SOUTH_EAST -> BlockFace.SOUTH;
			case WEST, WEST_NORTH_WEST, WEST_SOUTH_WEST, SOUTH_WEST, NORTH_WEST -> BlockFace.WEST;
			default -> player.getFacing().getOppositeFace();
		};
		RandomBox box = new RandomBox(location, facing, game, game.boxManager.getNextBoxName(), Integer.parseInt(data.get("price")));
		game.boxManager.addBox(box);
		player.sendMessage(Colors.DARK_GREEN + "Random Weapon Box Created!");
	}

	@Override
	public void onBreak(Game game, Player player, Location location)
	{
		RandomBox box = game.boxManager.getBox(location);
		if(box != null)
			game.boxManager.removeBox(player, box);
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		RandomBox box = game.boxManager.getBox(location);
		if(box == null)
			return;

		if(box.canActivate())
		{
			// The box knows its own cost, including fire sales
			int points = box.getCost();

			if(PointManager.INSTANCE.canBuy(player, points))
			{
				box.Start(player, points);
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "You don't have enough points!");
			}
		}
		else if(box.canPickWeapon(player))
		{
			box.pickUpWeapon(player);
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Someone else is using the mystery box!");
		}
	}

	@Override
	public String[] getText(Map<String, String> data)
	{
		return SignText.render(getType(), data);
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}

	@Override
	public boolean isRegistered()
	{
		// The box moves around, so its sign is found through the game's box manager instead
		return false;
	}
}
