package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.ChatColor;
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

		CommandUtil.sendMessageToPlayer(player, ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "---------------" + ChatColor.RED + "Arenas" + ChatColor.DARK_GREEN + "" + ChatColor.STRIKETHROUGH + "---------------");
		for(Game game : GameManager.INSTANCE.getGames())
			CommandUtil.sendMessageToPlayer(player, ChatColor.RED + game.getName() + ChatColor.GRAY + ": Players: " + game.getPlayersInGame().size() + ", Status: " + game.getStatus().toString().toLowerCase());

		return true;
	}
}