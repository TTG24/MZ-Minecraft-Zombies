package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.actions.ArenaSetupAction;
import com.theprogrammingturkey.comz.util.Colors;
import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;

public class EditCommand extends SubCommand
{
	public EditCommand(COMZPermission permission)
	{
		super(permission);
	}
	
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ARENA.hasPerm(player))
		{
			CommandUtil.noPermission(player, "edit this arena");
			return true;
		}

		COMZombies plugin = COMZombies.getPlugin();
		if(plugin.activeActions.containsKey(player))
		{
			CommandUtil.sendMessageToPlayer(player, "You are currently performing another action and cannot edit an arena right now!");
		}
		else if(args.length == 1)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Please specify an arena to edit!");
		}
		else if(GameManager.INSTANCE.isValidArena(args[1]))
		{
			Game game = GameManager.INSTANCE.getGame(args[1]);
			if(game.isInstance())
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + game.getName() + " is a temporary copy; edit " + game.getTemplateName() + " instead.");
				return true;
			}
			// Editing stops every match on this map: never by accident while people are playing
			int playing = 0;
			for(Game g : GameManager.INSTANCE.getGames())
				if(g == game || g.isInstance() && g.getTemplateName().equals(game.getName()))
					playing += g.getPlayersInGame().size();
			if(playing > 0 && !(args.length > 2 && args[2].equalsIgnoreCase("force")))
			{
				CommandUtil.sendMessageToPlayer(player, Colors.RED + playing + " player(s) are playing " + game.getName()
						+ " right now; editing ends their matches. Use /z edit " + game.getName() + " force to do it anyway.");
				return true;
			}
			boolean wasOpen = game.getStatus() != com.theprogrammingturkey.comz.game.Game.GameStatus.DISABLED;
			game.setDisabled();
			ArenaSetupAction action = new ArenaSetupAction(player, game);
			action.reopenOnCancel = wasOpen;
			plugin.activeActions.put(player, action);
		}
		else
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + args[1] + " is not a valid arena!");
		}
		return true;
	}
}
