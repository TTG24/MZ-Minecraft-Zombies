package com.theprogrammingturkey.comz.game.signs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.managers.WeaponManager;
import com.theprogrammingturkey.comz.game.weapons.BaseGun;
import com.theprogrammingturkey.comz.util.CommandUtil;

import java.util.HashMap;
import java.util.Map;

public class GunSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "gun";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException
	{
		String gunName = lines[2];
		if(gunName.isEmpty())
			throw new SignParseException(ChatColor.RED + "" + ChatColor.BOLD + "No gun?");

		BaseGun gunType = WeaponManager.getGun(gunName);
		if(gunType == null)
			throw new SignParseException(ChatColor.RED + "Invalid Gun!");

		// Typed as "buy / refill", e.g. "500/250"
		String buyPrice = "200";
		String refillPrice = "100";
		String prices = lines[3];
		int split = prices.indexOf("/");
		if(split != -1)
		{
			String buy = prices.substring(0, split).trim();
			String refill = prices.substring(split + 1).trim();
			if(buy.matches("[0-9]+") && refill.matches("[0-9]+"))
			{
				buyPrice = buy;
				refillPrice = refill;
			}
		}

		Map<String, String> data = new HashMap<>();
		data.put("gun", gunType.getName());
		data.put("price", buyPrice);
		data.put("refill", refillPrice);
		return data;
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		int buyPoints = Integer.parseInt(data.get("price"));
		int refillPoints = Integer.parseInt(data.get("refill"));
		BaseGun gunType = WeaponManager.getGun(data.get("gun"));

		if(gunType == null)
		{
			player.sendRawMessage(COMZombies.PREFIX + " Sorry! That gun doesn't seem to exist!");
			return;
		}

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		if(manager.hasGun(gunType))
		{
			if(PointManager.INSTANCE.canBuy(player, refillPoints))
			{
				manager.getGun(gunType).maxAmmo();
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "Filling ammo!");
				PointManager.INSTANCE.takePoints(player, refillPoints);
				PointManager.INSTANCE.notifyPlayer(player);
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			}
		}
		else
		{
			int slot = manager.getCorrectSlot(gunType);
			if(PointManager.INSTANCE.canBuy(player, buyPoints))
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You got the " + ChatColor.GOLD + "" + ChatColor.BOLD + gunType.getName() + ChatColor.RED + ChatColor.BOLD + "!");
				manager.removeWeapon(manager.getGun(slot));
				manager.addWeapon(gunType.getNewInstance(player, slot));
				player.getLocation().getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1, 1);
				PointManager.INSTANCE.takePoints(player, buyPoints);
				PointManager.INSTANCE.notifyPlayer(player);
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
			}
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
