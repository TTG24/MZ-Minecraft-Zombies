package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.entity.Player;

/** Debug: /zombies round <number> [arena] jumps the running game to that round. */
public class RoundCommand extends SubCommand
{
	public RoundCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.DEBUG.hasPerm(player))
		{
			CommandUtil.noPermission(player, "change the round");
			return true;
		}
		if(args.length < 2)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z round <number> [arena]");
			return true;
		}

		int round;
		try
		{
			round = Integer.parseInt(args[1]);
		} catch(NumberFormatException e)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "'" + args[1] + "' is not a number!");
			return true;
		}

		Game game;
		if(args.length > 2)
		{
			if(!GameManager.INSTANCE.isValidArena(args[2]))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "No such game!");
				return true;
			}
			game = GameManager.INSTANCE.getGame(args[2]);
		}
		else
		{
			game = GameManager.INSTANCE.getGame(player);
		}

		if(game == null)
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You must be in a game or specify one! /z round <number> [arena]");
		else if(game.forceRound(round))
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Jumping to round " + round + "...");
		else
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "The game must be in progress, and the round at least 1!");
		return true;
	}

	@Override
	public java.util.List<String> onTabComplete(Player player, String[] args)
	{
		if(!COMZPermission.DEBUG.hasPerm(player))
			return java.util.List.of();
		// <number>
		if(args.length == 2)
		{
			return java.util.stream.Stream.of("1", "5", "10", "20", "30", "50", "75", "100")
					.filter(n -> n.startsWith(args[1])).collect(java.util.stream.Collectors.toList());
		}
		// [arena]
		if(args.length == 3)
		{
			return GameManager.INSTANCE.getArenas().stream()
					.filter(a -> a.toLowerCase().startsWith(args[2].toLowerCase())).collect(java.util.stream.Collectors.toList());
		}
		return java.util.List.of();
	}
}
