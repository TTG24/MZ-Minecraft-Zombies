package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.Color;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * /zombies showdoors [arena]: toggles a permanent outline of every door's blocks (cyan = normal door, purple = needs power),
 * on its own layer so it stays visible during setup/edit commands. Run it again to turn it off.
 */
public class ShowDoorsCommand extends SubCommand
{
	private static final Set<UUID> SHOWING = new HashSet<>();

	/** Turns this outline off for the player (used by /z showall to turn every outline off). */
	public static boolean stopShowing(java.util.UUID id)
	{
		return SHOWING.remove(id);
	}
	private static final Color POWER_DOOR = Color.fromRGB(190, 80, 255);

	public ShowDoorsCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.ADD_DOOR.hasPerm(player))
		{
			CommandUtil.noPermission(player, "edit doors");
			return true;
		}
		if(SHOWING.remove(player.getUniqueId()))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Door outlines turned off.");
			return true;
		}
		Game game = args.length > 1 ? GameManager.INSTANCE.getGame(args[1]) : GameManager.INSTANCE.getGame(player.getLocation());
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z showdoors <arena> (or run it while standing in the arena)");
			return true;
		}
		SHOWING.add(player.getUniqueId());
		CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Showing " + game.doorManager.getDoors().size() + " door(s): cyan = normal, purple = needs power. Run the command again to turn it off.");
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
		for(Door door : game.doorManager.getDoors())
			Highlight.blocks(player, door.getBlocks(), door.requiresPower() ? POWER_DOOR : Highlight.DOOR, 2);
		COMZombies.scheduleTask(10, () -> loop(player, game));
	}
}
