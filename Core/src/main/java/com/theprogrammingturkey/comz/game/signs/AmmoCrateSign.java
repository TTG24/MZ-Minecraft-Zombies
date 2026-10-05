package com.theprogrammingturkey.comz.game.signs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.managers.PlayerWeaponManager;
import com.theprogrammingturkey.comz.game.weapons.GunInstance;
import com.theprogrammingturkey.comz.util.CommandUtil;

import java.util.HashMap;
import java.util.Map;

public class AmmoCrateSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "ammo_crate";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException
	{
		String cost = lines[2].trim();
		if(!cost.matches("[0-9]+"))
			throw new SignParseException(ChatColor.RED + "" + ChatColor.BOLD + "Invalid cost?");

		Map<String, String> data = new HashMap<>();
		data.put("price", cost);
		return data;
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		int buyPoints = Integer.parseInt(data.get("price"));

		PlayerWeaponManager manager = game.getPlayersWeapons(player);
		if(manager.isHeldItemWeapon())
		{
			if(PointManager.INSTANCE.canBuy(player, buyPoints))
			{
				PointManager.INSTANCE.takePoints(player, buyPoints);
				PointManager.INSTANCE.notifyPlayer(player);
				GunInstance gun = manager.getGun(player.getInventory().getHeldItemSlot());
				gun.clipAmmo = gun.getType().clipAmmo;
				gun.maxAmmo();
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, "You do not have enough points to buy that!");
			}
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, "You must be holding a gun to use the ammo crate!");
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
