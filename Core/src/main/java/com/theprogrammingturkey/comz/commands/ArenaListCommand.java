package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

public class ArenaListCommand extends SubCommand
{
	public ArenaListCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.LIST_ARENAS.hasPerm(player))
		{
			CommandUtil.noPermission(player, "view the arena list");
			return true;
		}

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "---------------" + Colors.RED + "Arenas" + Colors.DARK_GREEN + "" + Colors.STRIKETHROUGH + "---------------");
		for(Game game : GameManager.INSTANCE.getGames())
			CommandUtil.sendMessageToPlayer(player, Colors.RED + game.getName() + Colors.GRAY + ": Players: " + game.getPlayersInGame().size() + ", Status: " + game.getStatus().toString().toLowerCase());

		return true;
	}
}