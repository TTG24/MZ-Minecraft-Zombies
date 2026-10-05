package com.theprogrammingturkey.comz.game.signs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.listeners.customEvents.PlayerPerkPurchaseEvent;
import com.theprogrammingturkey.comz.util.CommandUtil;

import java.util.HashMap;
import java.util.Map;

public class PerkMachineSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "perk";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException
	{
		PerkType type = PerkType.getPerkType(lines[2]);
		if(type == null)
			throw new SignParseException(ChatColor.RED + "" + ChatColor.BOLD + "No such", ChatColor.RED + "" + ChatColor.BOLD + "perk!");

		String cost = lines[3];
		if(!cost.matches("[0-9]{1,5}"))
		{
			if(player != null)
				CommandUtil.sendMessageToPlayer(player, cost + " is not a valid amount!");
			cost = "2000";
		}

		Map<String, String> data = new HashMap<>();
		data.put("perk", type.toString().toLowerCase());
		data.put("price", cost);
		return data;
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		PerkType perk = PerkType.getPerkType(data.get("perk"));
		if(game.hasPower() && !game.isPowered())
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must turn on the power first!");
			PerkType.noPower(player);
			return;
		}

		if(perk == null)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "An error occured when trying to buy this perk! Leave the game and contact an admin please.");
			return;
		}

		int playerPoints = PointManager.INSTANCE.getPlayersPoints(player);
		int cost = Integer.parseInt(data.get("price"));

		if(playerPoints < cost)
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You do not have enough points to buy this!");
			return;
		}

		if(perk == PerkType.DER_WUNDERFIZZ)
		{
			perk = game.perkManager.getRandomPerk(player);
			if(perk == null)
			{
				player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "You already have all the perks!");
				return;
			}
		}

		if(!game.perkManager.addPerk(player, perk))
			return;

		Bukkit.getPluginManager().callEvent(new PlayerPerkPurchaseEvent(player, perk));
		CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "" + ChatColor.BOLD + "You now have " + perk.toString().toLowerCase() + "!");
		int slot = game.perkManager.getAvailablePerkSlot(player);
		perk.initialEffect(player, perk, slot);
		if(perk.equals(PerkType.STAMIN_UP))
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));

		PointManager.INSTANCE.takePoints(player, cost);
		PointManager.INSTANCE.notifyPlayer(player);
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
