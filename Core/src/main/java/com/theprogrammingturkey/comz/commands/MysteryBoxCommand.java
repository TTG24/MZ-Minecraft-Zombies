package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.actions.MysteryBoxSetupAction;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import org.bukkit.entity.Player;

/** /zombies mysterybox (mb) <arena> [cost]: add or remove chest mystery boxes by clicking chests. */
public class MysteryBoxCommand extends SubCommand
{
	public MysteryBoxCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ARENA.hasPerm(player))
		{
			CommandUtil.noPermission(player, "edit mystery boxes");
			return true;
		}
		COMZombies plugin = COMZombies.getPlugin();
		if(plugin.activeActions.containsKey(player))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You are currently performing another action! Finish it first (type done or cancel).");
			return true;
		}
		if(args.length < 2)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z mb <arena> [cost]");
			return true;
		}
		Game game = GameManager.INSTANCE.getGame(args[1]);
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + args[1] + " is not a valid arena!");
			return true;
		}
		int cost = 950;
		if(args.length > 2)
		{
			if(!args[2].matches("[0-9]{1,6}"))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + args[2] + " is not a number!");
				return true;
			}
			cost = Integer.parseInt(args[2]);
		}
		plugin.activeActions.put(player, new MysteryBoxSetupAction(player, game, cost));
		return true;
	}
}
