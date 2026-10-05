package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PowerSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "power";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines)
	{
		return new HashMap<>();
	}

	@Override
	public void onCreate(Game game, Player player, Location location, Map<String, String> data)
	{
		//TODO: Check that there are no other power signs
		game.enablePower(player);
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		if(game.hasPower())
		{
			if(game.isPowered())
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "The power is already on!");
				return;
			}
			game.turnOnPower();
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Power on!");
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
}
