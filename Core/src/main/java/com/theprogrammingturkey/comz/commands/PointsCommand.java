package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.economy.PointManager;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Debug: /zombies points <add|take|set> <amount> [player]. The player must be in a game; defaults to yourself. */
public class PointsCommand extends SubCommand
{
	public PointsCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.DEBUG.hasPerm(player))
		{
			CommandUtil.noPermission(player, "change points");
			return true;
		}
		if(args.length < 3)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z points <add|take|set> <amount> [player]");
			return true;
		}

		int amount;
		try
		{
			amount = Integer.parseInt(args[2]);
		} catch(NumberFormatException e)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "'" + args[2] + "' is not a whole number!");
			return true;
		}
		if(amount < 0)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "The amount can't be negative. Use take to remove points.");
			return true;
		}

		Player target = args.length > 3 ? Bukkit.getPlayerExact(args[3]) : player;
		if(target == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "That player is not online!");
			return true;
		}
		if(!GameManager.INSTANCE.isPlayerInGame(target))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + target.getName() + " is not in a game!");
			return true;
		}

		PointManager points = PointManager.INSTANCE;
		int current = points.getPlayersPoints(target);
		switch(args[1].toLowerCase())
		{
			case "add" -> points.addPoints(target, amount);
			case "take" -> points.takePoints(target, Math.min(amount, current));
			case "set" ->
			{
				if(amount >= current)
					points.addPoints(target, amount - current);
				else
					points.takePoints(target, current - amount);
			}
			default ->
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "Use add, take or set.");
				return true;
			}
		}
		points.notifyPlayer(target);
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + target.getName() + " now has " + points.getPlayersPoints(target) + " points.");
		return true;
	}

	@Override
	public java.util.List<String> onTabComplete(Player player, String[] args)
	{
		if(!COMZPermission.DEBUG.hasPerm(player))
			return java.util.List.of();
		// <add|take|set>
		if(args.length == 2)
		{
			return java.util.stream.Stream.of("add", "take", "set")
					.filter(a -> a.startsWith(args[1].toLowerCase())).collect(java.util.stream.Collectors.toList());
		}
		// <amount>
		if(args.length == 3)
		{
			return java.util.stream.Stream.of("500", "1000", "5000", "10000", "100000")
					.filter(n -> n.startsWith(args[2])).collect(java.util.stream.Collectors.toList());
		}
		// [player]: players currently in a game
		if(args.length == 4)
		{
			return Bukkit.getOnlinePlayers().stream()
					.filter(p -> GameManager.INSTANCE.isPlayerInGame(p))
					.map(Player::getName)
					.filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
					.collect(java.util.stream.Collectors.toList());
		}
		return java.util.List.of();
	}
}
