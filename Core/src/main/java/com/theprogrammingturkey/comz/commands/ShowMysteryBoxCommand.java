package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.util.BlockUtils;
import com.theprogrammingturkey.comz.util.COMZPermission;
import com.theprogrammingturkey.comz.util.Colors;
import com.theprogrammingturkey.comz.util.CommandUtil;
import com.theprogrammingturkey.comz.util.Highlight;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * /zombies showmysterybox (smb) [arena]: toggles an outline of every mystery box of the arena (orange = chest box, purple =
 * sign box, blue = the box that is active in a running game), on its own layer so it stays visible during setup commands.
 */
public class ShowMysteryBoxCommand extends SubCommand
{
	private static final Set<UUID> SHOWING = new HashSet<>();

	/** Turns this outline off for the player (used by /z showall to turn every outline off). */
	public static boolean stopShowing(java.util.UUID id)
	{
		return SHOWING.remove(id);
	}
	private static final Color CHEST_BOX = Color.fromRGB(255, 140, 0);
	private static final Color SIGN_BOX = Color.fromRGB(190, 80, 255);
	private static final Color ACTIVE = Color.fromRGB(40, 120, 255);

	public ShowMysteryBoxCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ARENA.hasPerm(player))
		{
			CommandUtil.noPermission(player, "view mystery boxes");
			return true;
		}
		if(SHOWING.remove(player.getUniqueId()))
		{
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "Mystery box outlines turned off.");
			return true;
		}
		Game game = args.length > 1 ? GameManager.INSTANCE.getGame(args[1]) : null;
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z smb <arena> (or run it while standing in the arena)");
			return true;
		}

		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "" + game.boxManager.getNumBoxes() + " mystery box(es) in " + game.getName() + ": "
				+ Colors.GOLD + "orange" + Colors.GRAY + " = chest box, " + Colors.LIGHT_PURPLE + "purple" + Colors.GRAY + " = sign box, "
				+ Colors.BLUE + "blue" + Colors.GRAY + " = active in the game. Run the command again to turn it off.");
		for(RandomBox box : game.boxManager.getBoxes())
		{
			Location l = box.getLocation();
			player.sendMessage(Colors.GRAY + " - " + box.getId() + ": " + (box.isChestBox() ? "chest" : "sign") + ", " + box.getCost() + " points, at "
					+ l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
		}
		SHOWING.add(player.getUniqueId());
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
		boolean running = game.getStatus() == Game.GameStatus.INGAME;
		for(RandomBox box : game.boxManager.getBoxes())
		{
			boolean active = running && (game.boxManager.isMultiBox() || game.isFireSale() || game.boxManager.getCurrentbox() == box);
			Color color = active ? ACTIVE : box.isChestBox() ? CHEST_BOX : SIGN_BOX;
			Highlight.box(player, box.getLocation(), color, 2);
			if(box.isChestBox())
			{
				Block other = BlockUtils.doubleChestPartner(box.getLocation().getBlock());
				if(other != null)
					Highlight.box(player, other.getLocation(), color, 2);
			}
		}
		COMZombies.scheduleTask(10, () -> loop(player, game));
	}
}
