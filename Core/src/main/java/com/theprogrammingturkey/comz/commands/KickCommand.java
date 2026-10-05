package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.Game.GameStatus;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "Please specify a player to kick!");
		}
		else if(Bukkit.getPlayer(args[1]) != null)
		{
			Player kick = Bukkit.getPlayer(args[1]);
			if(kick == null || !GameManager.INSTANCE.isPlayerInGame(kick))
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "This player is not contained in any arena!");
				return true;
			}

			Game game = GameManager.INSTANCE.getGame(kick);
			if(game.getStatus() == GameStatus.DISABLED)
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "That player's arena is " + game.getStatus().toString().toLowerCase() + "!");
				return true;
			}

			if(kick.equals(player))
			{
				CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "You may not kick yourself! " + ChatColor.GRAY + "Type /z leave to leave!");
			}
			else
			{
				game.removePlayer(kick);
				CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "You kicked " + ChatColor.RED + kick.getName() + ChatColor.DARK_GREEN + " from the arena " + ChatColor.RED + game.getName() + ChatColor.DARK_GREEN + "!");
			}
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + "There is no player called " + args[1] + "!");
		}
		return true;
	}

}
