package com.theprogrammingturkey.comz.commands;

import org.bukkit.entity.Player;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.ArenaInstances;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;

/**
 * Spins up a private, single-player copy of an arena on demand and drops the player in.
 * The copy is destroyed automatically the moment the player leaves or the match ends.
 */
public class JoinSoloCommand extends SubCommand
{
	public JoinSoloCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(GameManager.INSTANCE.isPlayerInGame(player))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "You are already in a game. Leave it first!");
			return true;
		}
		if(args.length < 2 || !GameManager.INSTANCE.isValidArena(args[1]))
		{
			CommandUtil.sendMessageToPlayer(player, "Usage: /zombies solo <arena>");
			return true;
		}
		Game template = GameManager.INSTANCE.getGame(args[1]);
		if(template == null || template.isInstance())
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Specify a fully configured arena.");
			return true;
		}
		Game match = ArenaInstances.INSTANCE.createSolo(player, template);
		if(match == null)
			return true;

		// Creating the copy can take a moment on large arenas; tell the player before
		// they are teleported in so the pause is not a surprise.
		player.sendRawMessage(COMZombies.PREFIX + "Your solo arena is ready!");
		match.addPlayer(player);
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "You joined your solo arena " + match.getName() + "!");
		return true;
	}
}
