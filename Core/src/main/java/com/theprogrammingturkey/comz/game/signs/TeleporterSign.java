package com.theprogrammingturkey.comz.game.signs;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.PerkType;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class TeleporterSign implements IGameSign
{
	@Override
	public String getType()
	{
		return "teleporter";
	}

	@Override
	public Map<String, String> parse(Game game, Player player, String[] lines) throws SignParseException
	{
		String teleporterName = null;
		for(String name : game.teleporterManager.getTeleporters().keySet())
			if(name.equalsIgnoreCase(lines[2]))
				teleporterName = name;

		if(teleporterName == null)
			throw new SignParseException(ChatColor.RED + "" + ChatColor.BOLD + "No such", ChatColor.RED + "" + ChatColor.BOLD + "teleporter!");

		String cost = lines[3];
		if(!cost.matches("[0-9]+"))
			cost = "500";

		Map<String, String> data = new HashMap<>();
		data.put("teleporter", teleporterName);
		data.put("price", cost);
		return data;
	}

	@Override
	public void onInteract(Game game, Player player, Location location, Map<String, String> data)
	{
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			Location destination = null;
			for(Map.Entry<String, Location> teleporter : game.teleporterManager.getTeleporters().entrySet())
				if(teleporter.getKey().equalsIgnoreCase(data.get("teleporter")))
					destination = teleporter.getValue();

			if(destination != null)
			{
				if(game.hasPower() && !game.isPowered())
				{
					CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You must turn on the power first!");
					PerkType.noPower(player);
					return;
				}

				int points = Integer.parseInt(data.get("price"));
				if(PointManager.INSTANCE.canBuy(player, points))
				{
					player.teleport(destination);
					player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 30));

					for(int i = 0; i < 50; i++)
					{
						Location loc = player.getLocation();
						player.getWorld().spawnParticle(Particle.WITCH, loc.getX(), loc.getY(), loc.getZ(), 1, COMZombies.rand.nextFloat(), COMZombies.rand.nextFloat(), COMZombies.rand.nextFloat(), 1);
					}
					PointManager.INSTANCE.takePoints(player, points);
					PointManager.INSTANCE.notifyPlayer(player);
				}
				else
				{
					CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You don't have enough points!");
				}
			}
			else
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "ERROR teleporter does not exist!");
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
