package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.entity.Player;

/** Configure now, apply with a restart/reload so world copies never interrupt a live match. */
public class InstancesCommand extends SubCommand
{
	public InstancesCommand(COMZPermission permission) { super(permission); }

	@Override public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.CREATE_ARENA.hasPerm(player))
		{
			CommandUtil.noPermission(player, "configure arena instances");
			return true;
		}
		if(args.length != 3)
		{
			CommandUtil.sendMessageToPlayer(player, "Usage: /zombies instances <arena> <0-16> (0 disables the pool)");
			return true;
		}
		Game template = GameManager.INSTANCE.getGames().stream()
				.filter(g -> !g.isInstance() && g.getName().equalsIgnoreCase(args[1])).findFirst().orElse(null);
		if(template == null || !template.arena.isSetupComplete())
		{
			CommandUtil.sendMessageToPlayer(player, "Specify a fully configured template arena.");
			return true;
		}
		try
		{
			int count = Integer.parseInt(args[2]);
			if(count < 0 || count > 16) throw new NumberFormatException();
			if(!template.getPlayersAndSpectators().isEmpty())
			{
				CommandUtil.sendMessageToPlayer(player, "End the template's match before configuring instances.");
				return true;
			}
			template.setInstanceCount(count);
			GameManager.INSTANCE.saveAllGames();
			if(count == 0)
			{
				CommandUtil.sendMessageToPlayer(player, "Disabled instances for " + template.getName() + ". Restart or /zombies reload between games to unload copies and delete their owned world folders. The original arena is kept.");
				return true;
			}
			CommandUtil.sendMessageToPlayer(player, "Saved " + count + " instances for " + template.getName() + ". Restart or /zombies reload between games to apply. Players join with /zombies join " + template.getName());
		}
		catch(NumberFormatException invalid)
		{
			CommandUtil.sendMessageToPlayer(player, "Instance count must be a whole number from 0 to 16.");
		}
		return true;
	}
}
