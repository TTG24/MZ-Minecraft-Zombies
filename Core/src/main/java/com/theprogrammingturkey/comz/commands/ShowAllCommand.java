package com.theprogrammingturkey.comz.commands;

import com.theprogrammingturkey.comz.COMZombies;
import com.theprogrammingturkey.comz.game.Arena;
import com.theprogrammingturkey.comz.game.Game;
import com.theprogrammingturkey.comz.game.GameManager;
import com.theprogrammingturkey.comz.game.features.Barrier;
import com.theprogrammingturkey.comz.game.features.Door;
import com.theprogrammingturkey.comz.game.features.RandomBox;
import com.theprogrammingturkey.comz.spawning.SpawnPoint;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /zombies showall (sa) [arena]: toggles an outline of EVERYTHING set up in the arena at once, through walls, only for you:
 * barriers, zombie spawns, doors, mystery boxes, game signs (perks, weapons, power, pack-a-punch...), teleporters, the
 * player/spectator/lobby warps and the arena's corner box. Uses the same colours as the single show commands. Run it again
 * to turn it off. Without an arena, the one you are standing in.
 */
public class ShowAllCommand extends SubCommand
{
	private static final Set<UUID> SHOWING = new HashSet<>();
	private static final Color POWER_DOOR = Color.fromRGB(190, 80, 255);
	private static final Color CHEST_BOX = Color.fromRGB(255, 140, 0);
	private static final Color SIGN_BOX = Color.fromRGB(190, 80, 255);
	private static final Color ACTIVE_BOX = Color.fromRGB(40, 120, 255);
	private static final Color SIGN = Color.fromRGB(240, 240, 240);
	private static final Color TELEPORTER = Color.fromRGB(255, 80, 200);
	private static final Color WARP = Color.fromRGB(255, 255, 120);
	private static final Color BOUNDS = Color.fromRGB(120, 160, 255);

	public ShowAllCommand(COMZPermission permission)
	{
		super(permission);
	}

	@Override
	public boolean onCommand(Player player, String[] args)
	{
		if(!COMZPermission.EDIT_ARENA.hasPerm(player))
		{
			CommandUtil.noPermission(player, "view the arena setup");
			return true;
		}
		// If anything is outlined (this, or /z barriers, showdoors, showspawns, showboxes...), turn all of it off
		UUID id = player.getUniqueId();
		boolean wasOn = SHOWING.remove(id) | BarriersCommand.stopShowing(id) | ShowDoorsCommand.stopShowing(id)
				| ShowMysteryBoxCommand.stopShowing(id) | ShowSpawnsCommand.stopShowing(id) | AlwaysSpawnCommand.stopShowing(id);
		if(wasOn)
		{
			Highlight.clear(player);
			CommandUtil.sendMessageToPlayer(player, Colors.GRAY + "All outlines turned off.");
			return true;
		}
		Game game = args.length > 1 ? GameManager.INSTANCE.getGame(args[1]) : GameManager.INSTANCE.getGame(player.getLocation());
		if(game == null)
		{
			CommandUtil.sendMessageToPlayer(player, Colors.RED + "Usage: /z showall <arena> (or run it while standing in the arena)");
			return true;
		}

		Arena a = game.arena;
		int warps = (a.hasPlayerTPLocation() ? 1 : 0) + (a.hasSecondPlayerTPLocation() ? 1 : 0) + (a.hasSpectateLocation() ? 1 : 0)
				+ (a.hasLobbyLocation() ? 1 : 0);
		CommandUtil.sendMessageToPlayer(player, Colors.DARK_GREEN + "Showing everything in " + game.getName() + ". Run it again to turn every outline off.");
		player.sendMessage(Colors.GRAY + " - " + Colors.AQUA + "Barriers" + Colors.GRAY + " (" + game.barrierManager.getBarriers().size()
				+ "): green intact, yellow damaged, red broken");
		player.sendMessage(Colors.GRAY + " - " + Colors.AQUA + "Zombie spawns" + Colors.GRAY + " (" + game.spawnManager.getPoints().size()
				+ "): green always on, red off");
		player.sendMessage(Colors.GRAY + " - " + Colors.DARK_AQUA + "Doors" + Colors.GRAY + " (" + game.doorManager.getDoors().size()
				+ "): cyan normal, " + Colors.LIGHT_PURPLE + "purple" + Colors.GRAY + " needs power");
		player.sendMessage(Colors.GRAY + " - " + Colors.GOLD + "Mystery boxes" + Colors.GRAY + " (" + game.boxManager.getNumBoxes()
				+ "): orange chest, purple sign, blue active");
		player.sendMessage(Colors.GRAY + " - " + Colors.WHITE + "Game signs" + Colors.GRAY + " (" + game.signManager.gameSigns.size()
				+ "): white (perks, weapons, power, pack-a-punch...)");
		player.sendMessage(Colors.GRAY + " - " + Colors.LIGHT_PURPLE + "Teleporters" + Colors.GRAY + " (" + game.teleporterManager.getTeleporters().size()
				+ "): pink");
		player.sendMessage(Colors.GRAY + " - " + Colors.YELLOW + "Warps" + Colors.GRAY + " (" + warps + "): pale yellow (player spawn, spectator, lobby)");
		player.sendMessage(Colors.GRAY + " - " + Colors.GREEN + "Arena corners" + Colors.GRAY + ": blue box");
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
		// Same layers as the single commands, so running one of those as well doesn't fight over the colour
		for(Barrier b : game.barrierManager.getBarriers())
		{
			int stage = b.getStage();
			Highlight.blocks(player, b.getBlocks(), stage >= 5 ? Highlight.BROKEN : stage >= 0 ? Highlight.DAMAGED : Highlight.INTACT, 2);
		}
		for(SpawnPoint sp : game.spawnManager.getPoints())
			Highlight.box(player, sp.getLocation(), sp.isAlwaysActive() ? Highlight.LINKED_SPAWN : Highlight.OTHER_SPAWN, 1);
		for(Door door : game.doorManager.getDoors())
			Highlight.blocks(player, door.getBlocks(), door.requiresPower() ? POWER_DOOR : Highlight.DOOR, 2);
		boolean running = game.getStatus() == Game.GameStatus.INGAME;
		for(RandomBox box : game.boxManager.getBoxes())
		{
			boolean active = running && (game.boxManager.isMultiBox() || game.isFireSale() || game.boxManager.getCurrentbox() == box);
			Color color = active ? ACTIVE_BOX : box.isChestBox() ? CHEST_BOX : SIGN_BOX;
			Highlight.box(player, box.getLocation(), color, 2);
			if(box.isChestBox())
			{
				Block other = BlockUtils.doubleChestPartner(box.getLocation().getBlock());
				if(other != null)
					Highlight.box(player, other.getLocation(), color, 2);
			}
		}
		for(Location sign : game.signManager.gameSigns)
			Highlight.box(player, sign, SIGN, 0);
		for(Map.Entry<String, Location> tele : game.teleporterManager.getTeleporters().entrySet())
			if(tele.getValue() != null)
				Highlight.box(player, tele.getValue(), TELEPORTER, 1);
		Arena a = game.arena;
		if(a.hasPlayerTPLocation())
			warp(player, a.getPlayerTPLocation());
		if(a.hasSecondPlayerTPLocation())
			warp(player, a.getSecondPlayerTPLocation());
		if(a.hasSpectateLocation())
			warp(player, a.getSpectateLocation());
		if(a.hasLobbyLocation())
			warp(player, a.getLobbyLocation());
		if(a.hasMin() && a.hasMax())
			bounds(player, a.getMin(), a.getMax());
		COMZombies.scheduleTask(10, () -> loop(player, game));
	}

	/** A warp point: the two blocks a player stands in. */
	private static void warp(Player player, Location at)
	{
		Highlight.box(player, at, WARP, 1);
		Highlight.box(player, at.clone().add(0, 1, 0), WARP, 1);
	}

	/** The 12 edges of the arena's corner box. */
	private static void bounds(Player player, Location min, Location max)
	{
		double x0 = Math.min(min.getBlockX(), max.getBlockX()), x1 = Math.max(min.getBlockX(), max.getBlockX()) + 1;
		double y0 = Math.min(min.getBlockY(), max.getBlockY()), y1 = Math.max(min.getBlockY(), max.getBlockY()) + 1;
		double z0 = Math.min(min.getBlockZ(), max.getBlockZ()), z1 = Math.max(min.getBlockZ(), max.getBlockZ()) + 1;
		var w = min.getWorld();
		Location[] c = new Location[8];
		for(int i = 0; i < 8; i++)
			c[i] = new Location(w, (i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for(int[] e : edges)
			Highlight.line(player, c[e[0]], c[e[1]], BOUNDS);
	}
}
