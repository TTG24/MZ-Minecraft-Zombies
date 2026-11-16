package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * /zombies showspawns [arena]: toggles a permanent outline of every spawn point (green = always spawn on, red = off). It is
 * drawn on its own layer, so it stays visible during every other setup/edit command. Without an arena, the one you are in.
 */
public class ShowSpawnsCommand extends SubCommand
{
	private static final Set<UUID> SHOWING = new HashSet<>();

	/** Turns this outline off for the player (used by /z showall to turn every outline off). */
	public static boolean stopShowing(java.util.UUID id)
	{
		return SHOWING.remove(id);
	}

	public ShowSpawnsCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ZOMBIE_SPAWNS.hasPerm(player))
		{
			CommandUtil.noPermission(player, "edit zombie spawns");
			return true;
		}
		if(SHOWING.remove(player.getUniqueId()))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Spawn outlines turned off.");
			return true;
		}
		Game game = args.length > 1 ? GameManager.INSTANCE.getGame(args[1]) : GameManager.INSTANCE.getGame(player.getLocation());
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z showspawns <arena> (or run it while standing in the arena)");
			return true;
		}
		SHOWING.add(player.getUniqueId());
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Showing every spawn: " + Colors.DARK_GREEN + "green" + Colors.GRAY + " = always spawn ON, "
				+ Colors.RED + "red" + Colors.GRAY + " = OFF. Stays on during setup commands. Run the command again to turn it off.");
		loop(player, game);
		return true;
	}

	private static void loop(Player player, Game game)
	{
		if(!SHOWING.contains(player.getUniqueId()) || !player.isOnline())
		{
			SHOWING.remove(player.getUniqueId());
			return;
		}
		for(SpawnPoint sp : game.spawnManager.getPoints())
			Highlight.box(player, sp.getLocation(), sp.isAlwaysActive() ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN, 1);
		COMZombies.scheduleTask(10, () -> loop(player, game));
	}
}
