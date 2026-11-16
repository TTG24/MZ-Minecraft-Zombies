package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /zombies barriers <arena>: lists the arena's barriers (id, number of blocks, damage stage) and outlines their blocks until
 * the command is run again (a toggle). Green = intact, yellow = damaged, red = fully broken. The outline is on its own
 * layer, so it stays visible while adding or editing barriers, and barriers added meanwhile show up too.
 */
public class BarriersCommand extends SubCommand
{
	private static final java.util.Set<java.util.UUID> SHOWING = new java.util.HashSet<>();

	/** Turns this outline off for the player (used by /z showall to turn every outline off). */
	public static boolean stopShowing(java.util.UUID id)
	{
		return SHOWING.remove(id);
	}

	public BarriersCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ZOMBIE_SPAWNS.hasPerm(player))
		{
			CommandUtil.noPermission(player, "view barriers");
			return true;
		}
		if(SHOWING.remove(player.getUniqueId()))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Barrier outlines turned off.");
			return true;
		}
		Game game = args.length > 1 ? GameManager.INSTANCE.getGame(args[1]) : GameManager.INSTANCE.getGame(player);
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z barriers <arena> (or run it inside a game)");
			return true;
		}

		List<Barrier> barriers = new ArrayList<>(game.barrierManager.getBarriers());
		if(barriers.isEmpty())
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "This arena has no barriers.");
			return true;
		}

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + barriers.size() + " barrier(s), outlined until you run the command again (green = intact, yellow = damaged, red = broken):");
		for(Barrier b : barriers)
		{
			String stage = b.getStage() >= 5 ? "broken" : b.getStage() < 0 ? "intact" : "damage " + b.getStage() + "/5";
			Location first = b.getBlocks().isEmpty() ? null : b.getBlocks().get(0).getLocation();
			player.sendMessage(Colors.GRAY + " - " + b.getID() + ": " + b.getBlocks().size() + " block(s), " + stage
					+ (first == null ? "" : " near " + first.getBlockX() + ", " + first.getBlockY() + ", " + first.getBlockZ()));
		}
		SHOWING.add(player.getUniqueId());
		highlight(player, game);
		return true;
	}

	private static void highlight(Player player, Game game)
	{
		if(!SHOWING.contains(player.getUniqueId()) || !player.isOnline())
		{
			SHOWING.remove(player.getUniqueId());
			return;
		}
		for(Barrier b : game.barrierManager.getBarriers())
		{
			int stage = b.getStage();
			Color color = stage >= 5 ? Highlight.BROKEN : stage >= 0 ? Highlight.DAMAGED : Highlight.INTACT;
			Highlight.blocks(player, b.getBlocks(), color, 2);
		}
		COMZombies.scheduleTask(10, () -> highlight(player, game));
	}
}
