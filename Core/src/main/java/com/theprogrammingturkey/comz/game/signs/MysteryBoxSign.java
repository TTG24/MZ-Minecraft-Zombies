package com.theprogrammingturkey.comz.game.signs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
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
		if(!cost.matches("[0-9]+"))
			cost = "950";

		Map<String, String> data = new HashMap<>();
		data.put("price", cost);
		return data;
	}

	@Override
	public void onCreate(Game game, Player player, Location location, Map<String, String> data)
	{
		BlockFace facing = ((Directional) location.getBlock().getBlockData()).getFacing();
		RandomBox box = new RandomBox(location, facing, game, game.boxManager.getNextBoxName(), Integer.parseInt(data.get("price")));
		game.boxManager.addBox(box);
		player.sendMessage(ChatColor.DARK_GREEN + "Random Weapon Box Created!");
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
				player.getLocation().getWorld().playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1, 1);
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			}
		}
		else if(box.canPickWeapon(player))
		{
			box.pickUpWeapon(player);
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
