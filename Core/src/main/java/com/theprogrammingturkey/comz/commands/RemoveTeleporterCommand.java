package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RemoveTeleporterCommand extends SubCommand
{
	public RemoveTeleporterCommand(COMZPermission permission)
	{
		super(permission);
	}

	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.REMOVE_TELEPORTER.hasPerm(player))
		{
			CommandUtil.noPermission(player, "remove a teleporter");
			return true;
		}

		Location loc = player.getLocation();
		Game arena = GameManager.INSTANCE.getGame(loc);
		if(arena == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You must be in an arena!");
			return true;
		}
		if(args.length == 1)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Please specify a teleporter name!");
			return true;
		}
		arena.teleporterManager.removedTeleporter(args[1], player);
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Teleporter removed for arena: " + Colors.RED + arena.getName() + Colors.DARK_GREEN + "!");
		return true;
	}
}