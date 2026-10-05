package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class DoorSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "door";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines)
	{
		// Door signs only get their price once the door is set up
		return new HashMap<>();
	}

	@Override
	public void onBreak(Game game, Player player, Location location)
	{
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		Door door = game.doorManager.getDoorFromSign(location);
		if(door == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "An error occurred when trying to open this door! Please leave the game and contact an admin.");
		}
		else if(door.isOpened())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This door is already open!");
		}
		else if(door.requiresPower() && !game.isPowered())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This door requires power to open!");
		}
		else if(!door.canOpen(PointManager.INSTANCE.getPlayerPoints(player).getPoints()))
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
		}
		else
		{
			door.openDoor();
			door.playerDoorOpenSound();
			PointManager.INSTANCE.takePoints(player, door.getCost());
			PointManager.INSTANCE.notifyPlayer(player);
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "Door opened!");
		}
	}

	@Override
	public String[] getText(Map<String, String> data)
	{
		// Leave the typed text until the door is set up, which then draws the sign
		return null;
	}

	@Override
	public boolean requiresGame()
	{
		return true;
	}

	@Override
	public boolean isRegistered()
	{
		// Doors already keep track of their signs
		return false;
	}
}
