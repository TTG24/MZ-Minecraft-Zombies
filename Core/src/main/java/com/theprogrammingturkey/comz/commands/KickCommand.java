package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

public class KickCommand extends SubCommand
{
	public KickCommand(COMZPermission permission)
	{
		super(permission);
	}
	
	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.KICK.hasPerm(player))
		{
			CommandUtil.noPermission(player, "kick a player");
			return true;
		}

		if(args.length == 1)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Please specify a player to kick!");
		}
		else if(Bukkit.getPlayer(args[1]) != null)
		{
			Player kick = Bukkit.getPlayer(args[1]);
			if(kick == null || !GameManager.INSTANCE.isPlayerInGame(kick))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "This player is not contained in any arena!");
				return true;
			}

			Game game = GameManager.INSTANCE.getGame(kick);
			if(game.getStatus() == GameStatus.DISABLED)
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "That player's arena is " + game.getStatus().toString().toLowerCase() + "!");
				return true;
			}

			if(kick.equals(player))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + "You may not kick yourself! " + Colors.GRAY + "Type /z leave to leave!");
			}
			else
			{
				game.removePlayer(kick);
				CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "You kicked " + Colors.RED + kick.getName() + Colors.DARK_GREEN + " from the arena " + Colors.RED + game.getName() + Colors.DARK_GREEN + "!");
			}
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "There is no player called " + args[1] + "!");
		}
		return true;
	}

}
