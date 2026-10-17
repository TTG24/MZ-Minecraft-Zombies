package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

public class SpectateCommand extends SubCommand
{
	public SpectateCommand(COMZPermission permission)
	{
		super(permission);
	}
	
	public boolean onCommand(Player player, String[] args)
	{
		if(args.length == 1)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Please specify an arena to spectate!");
		}
		else if(GameManager.INSTANCE.getGame(player) != null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You must leave your current game before you can spectate another one!");
		}
		else if(GameManager.INSTANCE.isValidArena(args[1]))
		{
			if(!COMZPermission.SPECTATE.hasPerm(player))
			{
				CommandUtil.noPermission(player, "spectate");
				return true;
			}
			Game game = GameManager.INSTANCE.getGame(args[1]);
			game.addSpectator(player);
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "You are now spectating " + Colors.RED + game.getName() + Colors.DARK_GREEN + "!");
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "That is not a valid arena!");
		}

		return true;
	}
}