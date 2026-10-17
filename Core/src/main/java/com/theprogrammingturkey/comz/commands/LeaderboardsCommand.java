package com.theprogrammingturkey.comz.commands;

import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.leaderboards.Leaderboard;
import com.theprogrammingturkey.comz.leaderboards.StatsCategory;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;

public class LeaderboardsCommand extends SubCommand
{
	public LeaderboardsCommand(COMZPermission permission)
	{
		super(permission);
	}
	
	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.LEADERBOARDS.hasPerm(player))
		{
			CommandUtil.noPermission(player, "view the leaderboards");
			return true;
		}

		if(args.length < 2 || args.length > 3)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Try /z leaderboard <category> [count]");
			sendCategories(player);
			return true;
		}

		int count = 15;
		if(args.length == 3)
		{
			try
			{
				count = Integer.parseInt(args[2]);
			}
			catch(NumberFormatException e)
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Count must be a number!");
				return true;
			}
			// Clamp to a sane range; getTopX caps the displayed top-20 anyway.
			count = Math.max(1, Math.min(count, 100));
		}

		String catName = args[1].toLowerCase();
		for(StatsCategory cat : StatsCategory.values())
		{
			if(cat.name().toLowerCase().equals(catName))
			{
				Leaderboard.getTopX(cat, count, player);
				return true;
			}
		}
		CommandUtil.sendMessageToPlayer(player, Colors.RED + catName + " is not a valid category!");
		sendCategories(player);
		return true;
	}

	private void sendCategories(Player player)
	{
		StringBuilder cats = new StringBuilder();
		for(StatsCategory cat : StatsCategory.values())
			cats.append(cat.name().toLowerCase()).append(", ");
		cats.delete(cats.length() - 2, cats.length());
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Categories: " + cats);
	}
}
