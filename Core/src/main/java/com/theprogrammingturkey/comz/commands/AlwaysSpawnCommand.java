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

/**
 * /zombies alwaysspawn <arena> [spawnId]: toggles a spawn point between "locked by doors" and "always active". An always
 * active spawn spawns from round 1 even when a door lists it in its room. Without an id, the spawn point nearest to the
 * player (within 6 blocks) is used.
 */
public class AlwaysSpawnCommand extends SubCommand
{
	public AlwaysSpawnCommand(COMZPermission permission)
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
		if(args.length < 2)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z alwaysspawn <arena> [spawnId|show] (or stand next to the spawn). \"show\" highlights which spawns are on/off.");
			return true;
		}
		Game game = GameManager.INSTANCE.getGame(args[1]);
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + args[1] + " is not a valid arena! Type /z la for a list of arenas!");
			return true;
		}

		if(args.length > 2 && args[2].equalsIgnoreCase("show"))
		{
			if(!SHOWING.add(player.getUniqueId()))
			{
				SHOWING.remove(player.getUniqueId());
				CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Always spawn outlines turned off.");
				return true;
			}
			int on = 0;
			for(SpawnPoint sp : game.spawnManager.getPoints())
				if(sp.isAlwaysActive())
					on++;
			CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Green" + Colors.GRAY + " = always spawn ON (" + on + "), "
					+ Colors.RED + "red" + Colors.GRAY + " = OFF, locked by doors (" + (game.spawnManager.getPoints().size() - on) + "). Use the command again to turn it off.");
			loop(player, game);
			return true;
		}

		SpawnPoint point = null;
		if(args.length > 2)
		{
			point = game.spawnManager.getSpawnPoint(args[2]);
		}
		else
		{
			double best = 6 * 6;
			for(SpawnPoint sp : game.spawnManager.getPoints())
			{
				if(!sp.getLocation().getWorld().equals(player.getWorld()))
					continue;
				double d = sp.getLocation().distanceSquared(player.getLocation());
				if(d <= best)
				{
					best = d;
					point = sp;
				}
			}
		}
		if(point == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "No spawn point found! Give its id, or stand within 6 blocks of it.");
			return true;
		}

		point.setAlwaysActive(!point.isAlwaysActive());
		show(player, game, 6);
		GameManager.INSTANCE.saveAllGames();
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Spawn " + point.getID() + " is now "
				+ (point.isAlwaysActive() ? "always active (spawns from round 1, even if a door lists it)" : "locked by its doors again"));
		return true;
	}

	private static final java.util.Set<java.util.UUID> SHOWING = new java.util.HashSet<>();

	/** Turns this outline off for the player (used by /z showall to turn every outline off). */
	public static boolean stopShowing(java.util.UUID id)
	{
		return SHOWING.remove(id);
	}

	/** Keeps outlining until the player runs the command again (or leaves). */
	private static void loop(Player player, Game game)
	{
		if(!SHOWING.contains(player.getUniqueId()) || !player.isOnline())
		{
			SHOWING.remove(player.getUniqueId());
			return;
		}
		for(SpawnPoint sp : game.spawnManager.getPoints())
			Highlight.box(player, sp.getLocation(), sp.isAlwaysActive() ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN);
		COMZombies.scheduleTask(10, () -> loop(player, game));
	}

	/** Outlines every spawn of the arena for a while: green when always spawn is on, red when it is off. */
	private static void show(Player player, Game game, int times)
	{
		if(times <= 0 || !player.isOnline())
			return;
		for(SpawnPoint sp : game.spawnManager.getPoints())
			Highlight.box(player, sp.getLocation(), sp.isAlwaysActive() ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN);
		COMZombies.scheduleTask(10, () -> show(player, game, times - 1));
	}
}
