package com.theprogrammingturkey.comz.game.signs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.util.CommandUtil;

import java.util.HashMap;
import java.util.Map;

public class PackAPunchSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "pack_a_punch";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException
	{
		String cost = lines[2];
		if(cost.isEmpty())
		{
			cost = "5000";
		}
		else if(!cost.matches("[0-9]{1,5}"))
		{
			if(player != null)
				CommandUtil.sendMessageToPlayer(player, cost + " is not a valid amount!");
			cost = "2000";
		}

		Map<String, String> data = new HashMap<>();
		data.put("price", cost);
		return data;
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		if(game.hasPower() && !game.isPowered())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must turn on the power before You can Pack-A-punch!");
			PerkType.noPower(player);
			return;
		}

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		if(!manager.isHeldItemGun())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must hold the gun you want to pack-a-punch!");
			return;
		}

		GunInstance gun = manager.getGun(player.getInventory().getHeldItemSlot());

		int cost = Integer.parseInt(data.get("price"));
		if(PointManager.INSTANCE.canBuy(player, cost))
		{
			if(gun.isPackOfPunched())
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Your " + ChatColor.GOLD + gun.getType().getName() + ChatColor.RED + " is already Pack-A-Punched!");
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Your " + ChatColor.GOLD + gun.getType().getName() + ChatColor.RED + " was Pack-A-Punched");
				player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1, 1);
				gun.setPackOfPunch();
				PointManager.INSTANCE.takePoints(player, cost);
			}
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You do not have enough points to Pack-A-Punch your " + gun.getType().getName() + "!");
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
