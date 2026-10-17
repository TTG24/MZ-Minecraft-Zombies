package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

public class LeaveCommand extends SubCommand
{
	public LeaveCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(com.theprogrammingturkey.comz.game.ArenaInstances.INSTANCE.cancelQueue(player.getUniqueId()))
		{
			CommandUtil.sendMessageToPlayer(player, "You left the arena queue.");
			return true;
		}
		Game game = GameManager.INSTANCE.getGame(player);

		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You are not in a game!");
			return true;
		}

		if(game.isPlayerSpectating(player))
		{
			game.removeSpectator(player);
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "You are no longer spectating!");
		}
		else if(game.isPlayerPlaying(player))
		{
			game.removePlayer(player);
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "You have left the game!");
		}

		return true;
	}
}
