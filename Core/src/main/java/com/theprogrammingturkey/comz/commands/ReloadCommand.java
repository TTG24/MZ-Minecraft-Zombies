package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;

public class ReloadCommand extends SubCommand
{
	public ReloadCommand(COMZPermission permission)
	{
		super(permission);
	}
	
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.RELOAD.hasPerm(player))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You do not have permission to reload MinecraftZombies!");
			return true;
		}

		COMZombies plugin = COMZombies.getPlugin();

		// Players in a match are told why it ended
		for(Game game : GameManager.INSTANCE.getGames())
			for(Player p : game.getPlayersInGame())
				p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
						"&8[&4☠&8] &cThe server is reloading the arenas: this match has ended. &7Rejoin in a moment."));
		for(Game game : GameManager.INSTANCE.getGames())
			game.endGame();
		plugin.clearAllSetup();

		// This may not go well?
//		Bukkit.getServer().getPluginManager().disablePlugin(plugin);
//		Bukkit.getServer().getPluginManager().enablePlugin(plugin);

		plugin.loadConfigFiles();
		GameManager.INSTANCE.loadAllGames();

		int signsRefreshed = com.theprogrammingturkey.comz.game.signs.SignRegistry.INSTANCE.refreshAllText();

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "MinecraftZombies has been reloaded! (" + signsRefreshed + " sign" + (signsRefreshed == 1 ? "" : "s") + " refreshed.)");
		return true;
	}
}
