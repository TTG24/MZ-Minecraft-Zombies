package com.theprogrammingturkey.comz.game.signs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.Weapon;
import com.theprogrammingturkey.comz.util.CommandUtil;

import java.util.HashMap;
import java.util.Map;

public class GrenadeSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "grenade";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines)
	{
		String cost = lines[2];
		if(!cost.matches("[0-9]+"))
			cost = "250";

		Map<String, String> data = new HashMap<>();
		data.put("price", cost);
		return data;
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		int buyPoints = Integer.parseInt(data.get("price"));
		Weapon w = WeaponManager.getWeapon("grenade");
		PlayerWeaponManager manager = game.getPlayersWeapons(player);

		if(PointManager.INSTANCE.canBuy(player, buyPoints))
		{
			if(manager.hasFullGrenades())
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You already have grenades!");
				return;
			}

			manager.addWeapon(w);
			CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "Bought grenades!");
			PointManager.INSTANCE.takePoints(player, buyPoints);
			PointManager.INSTANCE.notifyPlayer(player);
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
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
